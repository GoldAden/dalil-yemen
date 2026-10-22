package com.dalilyemen.app.data.repository

import android.content.Context
import com.dalilyemen.app.data.model.Categories
import com.dalilyemen.app.data.model.CityGeo
import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceDeduper
import com.dalilyemen.app.data.model.YemenCities
import com.dalilyemen.app.data.remote.NetworkMonitor
import com.dalilyemen.app.data.remote.OsmApiClient
import com.dalilyemen.app.data.remote.OsmPlaceMapper
import com.dalilyemen.app.data.remote.OsmSource
import com.dalilyemen.app.data.remote.OsmText
import com.dalilyemen.app.data.remote.SearchSynonyms
import com.dalilyemen.app.data.sync.SharedPlaceSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * المصدر الحقيقي لبيانات الدليل.
 *
 * الطبقات بالترتيب:
 *  1. قاعدة مضمّنة مع التطبيق (لقطة من OpenStreetMap) → تعمل دون إنترنت.
 *  2. ذاكرة الجلسة → كل ما شاهده المستخدم في هذه الجلسة.
 *  3. إضافات المستخدم ومفضلته المحفوظة على الجهاز.
 *  4. القاعدة المشتركة (اختيارية): مساهمات مستخدمين آخرين، وتكون
 *     معطّلة بهدوء تماماً إن لم يُضبط إعدادها.
 *  5. بحث حيّ في خدمة التسمية الجغرافية الرسمية عند توفّر الاتصال.
 *  6. كاش على الجهاز يقلّل الطلبات ويعمل عند انقطاع الشبكة.
 *
 * لا تُختلق أي بيانات: إن لم نجد شيئاً نُعيد قائمة فارغة وتشرح الواجهة
 * السبب للمستخدم.
 */
class OsmPlaceRepository(context: Context) : PlaceRepository {

    private val appContext = context.applicationContext
    private val snapshot = SnapshotPlaceSource(appContext)
    private val memory = PlaceMemoryStore()
    private val disk = PlaceDiskCache(appContext)
    private val local = LocalPlaceStore(appContext)
    private val network = NetworkMonitor(appContext)

    /**
     * القاعدة المشتركة. معطّلة بهدوء إن لم تُضبط إعداداتها وقت البناء،
     * وحينها لا يُلمس الشبكة إطلاقاً.
     */
    private val shared = SharedPlaceSource()

    /** نطاق خلفي لنشر المساهمات دون تعطيل واجهة المستخدم. */
    private val publishScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** نجلب القاعدة المشتركة مرة واحدة في الجلسة كي لا نُثقل الاتصال. */
    @Volatile
    private var sharedLoaded = false

    // --------------------------- إمكانات المصدر ---------------------------

    override fun isOnline(): Boolean = runCatching { network.isOnline() }.getOrDefault(false)

    override fun hasOfflineData(): Boolean = runCatching { snapshot.isAvailable() }.getOrDefault(false)

    // ------------------------------ المقترحة ------------------------------

    override suspend fun getFeatured(limit: Int): List<Place> = withContext(Dispatchers.IO) {
        refreshSharedIfPossible()
        val pool = pool()
        if (pool.isEmpty()) return@withContext emptyList()

        // نأخذ الأفضل من كل تصنيف ثم نتناوب بينها حتى تتنوّع المقترحات.
        val buckets: List<List<Place>> = OsmSource.allCategories.map { category ->
            pool.asSequence()
                .filter { it.categoryId == category }
                .sortedWith(compareByDescending<Place> { quality(it) }.thenBy { it.name })
                .take(40)
                .toList()
        }

        val picked = ArrayList<Place>(limit)
        var depth = 0
        while (picked.size < limit && buckets.any { depth < it.size }) {
            for (bucket in buckets) {
                if (depth < bucket.size && picked.size < limit) picked += bucket[depth]
            }
            depth++
        }
        picked
    }

    // --------------------------- الأماكن القريبة ---------------------------

    override suspend fun getNearby(
        origin: LatLng,
        radiusKm: Double,
        limit: Int,
    ): List<Place> = withContext(Dispatchers.IO) {
        refreshSharedIfPossible()
        val pool = pool()

        // نوسّع النطاق تدريجياً بدل إظهار حالة فارغة بسرعة.
        val steps = listOf(radiusKm, radiusKm * 2, radiusKm * 4, 80.0)
        var found: List<Place> = emptyList()
        for (radius in steps) {
            found = around(pool, origin, radius)
            if (found.size >= limit || found.size >= 8) break
        }

        val merged = LinkedHashMap<String, Place>()
        found.take(limit).forEach { merged[it.id] = it }

        // تعزيز حيّ لمنطقة المستخدم إن كان الاتصال متاحاً والنتائج قليلة.
        if (merged.size < limit && isOnline()) {
            val live = runCatching {
                OsmApiClient.fetchArea(origin) { elements -> elements }
            }.getOrDefault(emptyList())

            val livePlaces = live.asSequence()
                .mapNotNull { OsmPlaceMapper.toPlace(it) }
                .filter { place ->
                    val lat = place.latitude ?: return@filter false
                    val lon = place.longitude ?: return@filter false
                    origin.distanceKmTo(LatLng(lat, lon)) <= 80.0
                }
                .toList()

            if (livePlaces.isNotEmpty()) storeLive(livePlaces)
            livePlaces.forEach { place ->
                if (merged.size < limit || merged.containsKey(place.id)) {
                    merged[place.id] = place
                }
            }
        }

        val result = PlaceDeduper.merge(merged.values.take(limit).toList())
        if (result.isNotEmpty()) {
            disk.write(nearbyKey(origin), result)
            return@withContext result
        }

        // لا نتائج محلية ولا اتصال: نعرض آخر ما خزّنّاه في نفس المنطقة.
        disk.read(nearbyKey(origin), allowExpired = true).orEmpty()
    }

    // ------------------------------- البحث -------------------------------

    override suspend fun search(
        query: String,
        cityId: String?,
        categoryId: String?,
    ): List<Place> = withContext(Dispatchers.IO) {
        refreshSharedIfPossible()
        val rawQuery = query.trim()
        val normalized = OsmText.normalize(rawQuery)

        // إن كتب المستخدم اسم تصنيف («صيدلية») نضيف نتائج التصنيف كاملة.
        val synonymCategory = if (categoryId == null && normalized.isNotEmpty()) {
            SearchSynonyms.categoryForQuery(rawQuery)
        } else {
            null
        }
        val effectiveCategory = categoryId ?: synonymCategory

        val merged = LinkedHashMap<String, Place>()

        pool().asSequence()
            .filter { matchesCity(it, cityId) && matchesCategory(it, effectiveCategory) }
            .mapNotNull { place ->
                val score = textScore(place, normalized)
                if (score > 0) place to score else null
            }
            .sortedWith(
                compareByDescending<Pair<Place, Int>> { it.second }
                    .thenBy { it.first.name }
            )
            .take(90)
            .forEach { merged[it.first.id] = it.first }

        if (isOnline()) {
            val liveQuery = if (rawQuery.isEmpty()) {
                categoryKeyword(effectiveCategory, cityId)
            } else {
                rawQuery
            }
            if (!liveQuery.isNullOrBlank()) {
                val live = lookupLive(liveQuery, cityId, effectiveCategory)
                merged.putAll(live.associateBy({ it.id }, { it }))
            }
        }

        val result = PlaceDeduper.merge(merged.values.toList())
        if (result.isNotEmpty()) {
            disk.write(searchKey(rawQuery, cityId, categoryId), result)
            return@withContext result
        }

        disk.read(searchKey(rawQuery, cityId, categoryId), allowExpired = true).orEmpty()
    }

    // ------------------------------ بالتصنيف ------------------------------

    override suspend fun getByCategory(categoryId: String, cityId: String?): List<Place> =
        withContext(Dispatchers.IO) {
            refreshSharedIfPossible()
            val merged = LinkedHashMap<String, Place>()

            pool().asSequence()
                .filter { it.categoryId == categoryId && matchesCity(it, cityId) }
                .sortedWith(compareByDescending<Place> { quality(it) }.thenBy { it.name })
                .take(200)
                .forEach { merged[it.id] = it }

            if (merged.size < 20 && isOnline()) {
                val query = categoryKeyword(categoryId, cityId)
                if (!query.isNullOrBlank()) {
                    lookupLive(query, cityId, categoryId).forEach { place ->
                        merged.putIfAbsent(place.id, place)
                    }
                }
            }

            val result = PlaceDeduper.merge(merged.values.toList())
            if (result.isNotEmpty()) {
                disk.write(categoryKey(categoryId, cityId), result)
                return@withContext result
            }

            disk.read(categoryKey(categoryId, cityId), allowExpired = true).orEmpty()
        }

    // ------------------------------- التفاصيل -------------------------------

    override suspend fun getById(id: String): Place? = withContext(Dispatchers.IO) {
        cachedPlace(id) ?: run {
            if (!isOnline()) return@withContext null
            val element = runCatching { OsmApiClient.lookupById(id) }.getOrNull()
                ?: return@withContext null
            val place = OsmPlaceMapper.toPlace(element) ?: return@withContext null
            storeLive(listOf(place))
            place
        }
    }

    // ------------------------------- المفضلة -------------------------------

    override fun favoriteIds(): Set<String> = runCatching { local.favorites() }.getOrDefault(emptySet())

    override fun toggleFavorite(placeId: String): Boolean =
        runCatching { local.toggleFavorite(placeId) }.getOrDefault(false)

    override fun userPlaces(): List<Place> = runCatching { local.userPlaces() }.getOrDefault(emptyList())

    /**
     * يحفظ محلأ أضافه المستخدم على الجهاز، ويضيفه إلى الذاكرة فوراً
     * حتى يظهر في كل الشاشات دون إعادة تحميل.
     */
    override fun addUserPlace(place: Place): Place {
        runCatching { local.addUserPlace(place) }
        memory.put(place)
        // مشاركة اختيارية: تُرسل في الخلف بحالة «قيد المراجعة»، ولا تؤثّر
        // على نجاح الإضافة محلياً إن فشلت أو كانت الميزة غير مُعدّة.
        if (shared.isEnabled) {
            publishScope.launch {
                runCatching { shared.publish(place) }
            }
        }
        return place
    }

    /**
     * يجلب سجلّات القاعدة المشتركة مرة واحدة في الجلسة إن كانت مُفعّلة.
     * لا يرفع استثناء ولا يعطّل أي مسار أساسي: أي فشل يعني قائمة فارغة.
     */
    private fun refreshSharedIfPossible() {
        if (!shared.isEnabled || sharedLoaded) return
        sharedLoaded = true
        runCatching {
            val fetched = shared.fetch()
            if (fetched.isNotEmpty()) memory.putAll(fetched)
        }
    }

    // ------------------------------- أدوات داخلية -------------------------------

    /**
     * كل ما نعرفه عن الأماكن، بلا تكرار.
     *
     * أولوية الحقول: إضافات المستخدم، ثم الحيّ من الشبكة، ثم اللقطة المضمّنة.
     */
    private fun pool(): List<Place> {
        val map = LinkedHashMap<String, Place>()
        snapshot.places().forEach { map[it.id] = it }
        memory.all().forEach { map[it.id] = it }
        local.userPlaces().forEach { map[it.id] = it }
        // مساهمات القاعدة المشتركة التي جُلبت في هذه الجلسة (إن وُجدت).
        shared.cached().forEach { map[it.id] = it }
        return PlaceDeduper.merge(map.values.toList())
    }

    private fun cachedPlace(id: String): Place? =
        memory.get(id)
            ?: shared.cached().firstOrNull { it.id == id }
            ?: snapshot.byId(id)
            ?: local.userPlaces().firstOrNull { it.id == id }
            ?: disk.read("place:$id", allowExpired = true)?.firstOrNull()

    /** يحفظ النتائج الحيّة في الذاكرة والكاش لتعمل عند انقطاع الشبكة. */
    private fun storeLive(places: List<Place>) {
        if (places.isEmpty()) return
        memory.putAll(places)
        places.take(60).forEach { disk.write("place:${it.id}", listOf(it)) }
    }

    /** بحث حيّ في خدمة التسمية الجغرافية الرسمية ثم تحويله إلى نموذج العرض. */
    private fun lookupLive(
        query: String,
        cityId: String?,
        categoryId: String?,
    ): List<Place> {
        val center = CityGeo.centerOf(cityId)
        val elements = runCatching {
            OsmApiClient.searchByName(query, center, limit = 40)
        }.getOrDefault(emptyList())
        if (elements.isEmpty()) return emptyList()

        val places = elements.asSequence()
            .mapNotNull { OsmPlaceMapper.toPlace(it, categoryId) }
            .filter { place ->
                when {
                    cityId == null -> true
                    place.cityId == null -> true
                    else -> place.cityId == cityId
                }
            }
            .sortedWith(compareByDescending<Place> { quality(it) }.thenBy { it.name })
            .toList()

        storeLive(places)
        return places
    }

    /** أماكن داخل نطاق محدد مرتّبة بالأقرب. */
    private fun around(places: List<Place>, origin: LatLng, radiusKm: Double): List<Place> =
        places.asSequence()
            .mapNotNull { place ->
                val lat = place.latitude ?: return@mapNotNull null
                val lon = place.longitude ?: return@mapNotNull null
                val distance = origin.distanceKmTo(LatLng(lat, lon))
                if (distance <= radiusKm) place to distance else null
            }
            .sortedBy { it.second }
            .map { it.first }
            .toList()

    private fun matchesCity(place: Place, cityId: String?): Boolean =
        cityId == null || place.cityId == cityId

    private fun matchesCategory(place: Place, categoryId: String?): Boolean =
        categoryId == null || place.categoryId == categoryId

    /**
     * درجة مطابقة نصية: الاسم المطابق تماماً يتقدّم، ثم بداية الاسم،
     * ثم أي ورود داخل الاسم أو العنوان أو نوع النشاط.
     * الصفر يعني لا مطابقة.
     */
    private fun textScore(place: Place, normalizedQuery: String): Int {
        if (normalizedQuery.isEmpty()) return 1

        val name = OsmText.normalize(place.name)
        val tokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
        val haystack = OsmText.normalize(
            listOfNotNull(
                place.name,
                place.address,
                place.operatorName,
                place.kindValue,
                place.description,
            ).joinToString(" ")
        )

        if (tokens.any { !haystack.contains(it) }) return 0

        var score = 10
        if (name.contains(normalizedQuery)) score += 25
        if (name.startsWith(normalizedQuery)) score += 15
        if (name == normalizedQuery) score += 40
        if (!place.phone.isNullOrBlank()) score += 3
        if (!place.address.isNullOrBlank()) score += 2
        return score
    }

    /**
     * درجة اكتمال بيانات المكان، تُستخدم للترتيب: الأغنى بيانات أولاً.
     * لا تعني تقييماً حقيقياً من المستخدمين، ولا ندّعي ذلك في الواجهة.
     */
    private fun quality(place: Place): Int {
        var score = 0
        if (!place.phone.isNullOrBlank()) score += 4
        if (!place.address.isNullOrBlank()) score += 3
        if (!place.openingHours.isNullOrBlank()) score += 2
        if (place.addedByUser) score += 5
        return score
    }

    /** نص بحث حيّ مناسب لتصنيف معيّن داخل مدينة. */
    private fun categoryKeyword(categoryId: String?, cityId: String?): String? {
        val keyword = when (categoryId ?: return null) {
            Categories.RESTAURANTS -> "مطعم"
            Categories.HEALTH -> "صيدلية"
            Categories.FUEL -> "محطة وقود"
            Categories.MAINTENANCE -> "ورشة سيارات"
            Categories.ELECTRONICS -> "محل جوالات"
            else -> "سوق"
        }
        val city = YemenCities.find(cityId)?.nameAr ?: "اليمن"
        return "$keyword في $city"
    }

    // ------------------------------ مفاتيح الكاش ------------------------------

    private fun nearbyKey(origin: LatLng): String = String.format(
        Locale.US,
        "nearby:%.2f,%.2f",
        origin.latitude,
        origin.longitude,
    )

    private fun searchKey(query: String, cityId: String?, categoryId: String?): String =
        "search:${OsmText.normalize(query)}|${cityId.orEmpty()}|${categoryId.orEmpty()}"

    private fun categoryKey(categoryId: String, cityId: String?): String =
        "category:$categoryId|${cityId.orEmpty()}"
}
