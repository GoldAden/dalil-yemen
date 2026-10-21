package com.dalilyemen.app.data.repository

import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.remote.OsmText

/**
 * ذاكرة داخلية لكل ما شاهده المستخدم من أماكن.
 *
 * الفائدة مزدوجة:
 *  1. تفاصيل المكان تُعرض فوراً من الذاكرة دون طلب شبكة جديد.
 *  2. البحث والتصفية يعملان على ما جمعناه حتى لو تعذّر الوصول للشبكة.
 */
class PlaceMemoryStore {

    private val byId = LinkedHashMap<String, Place>()

    /** ترتيب الإضافة، لنعرف أحدث ما جمعناه. */
    private val insertionOrder = ArrayDeque<String>()

    private val maxEntries = 3000

    @Synchronized
    fun putAll(places: List<Place>) {
        places.forEach { put(it) }
    }

    @Synchronized
    fun put(place: Place) {
        if (!byId.containsKey(place.id)) {
            insertionOrder.addLast(place.id)
        }
        byId[place.id] = place
        while (insertionOrder.size > maxEntries) {
            val oldest = insertionOrder.removeFirst()
            byId.remove(oldest)
        }
    }

    @Synchronized
    fun get(id: String): Place? = byId[id]

    @Synchronized
    fun all(): List<Place> = byId.values.toList()

    @Synchronized
    fun size(): Int = byId.size

    @Synchronized
    fun remove(id: String) {
        byId.remove(id)
        insertionOrder.remove(id)
    }

    /**
     * بحث نصي داخل الذاكرة عن الاسم والعنوان والجهة المشغّلة.
     *
     * نُطبّع النص العربي قبل المقارنة حتى يجد المستخدم "صيدليه" مثل
     * "صيدلية"، وأيضاً المطابقة الجزئية في وسط الاسم.
     */
    fun searchInMemory(
        query: String,
        cityId: String? = null,
        categoryId: String? = null,
    ): List<Place> {
        val needle = OsmText.normalize(query)
        val tokens = needle.split(' ').filter { it.isNotBlank() }

        return all().filter { place ->
            if (cityId != null && place.cityId != cityId) return@filter false
            if (categoryId != null && place.categoryId != categoryId) return@filter false
            if (tokens.isEmpty()) return@filter true

            val haystack = OsmText.normalize(
                listOfNotNull(place.name, place.address, place.operatorName, place.description)
                    .joinToString(" ")
            )
            tokens.all { haystack.contains(it) }
        }
    }

    /** أماكن داخل مسافة محدّدة من نقطة، مرتّبة بالأقرب أولاً. */
    fun around(origin: LatLng, radiusKm: Double): List<Place> =
        all()
            .mapNotNull { place ->
                val latitude = place.latitude ?: return@mapNotNull null
                val longitude = place.longitude ?: return@mapNotNull null
                val distance = origin.distanceKmTo(LatLng(latitude, longitude))
                if (distance > radiusKm) null else place to distance
            }
            .sortedBy { it.second }
            .map { it.first }
}
