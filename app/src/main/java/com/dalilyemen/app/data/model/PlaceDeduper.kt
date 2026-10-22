package com.dalilyemen.app.data.model

import com.dalilyemen.app.data.remote.OsmText

/**
 * توحيد النتائج القادمة من أكثر من مصدر في قائمة واحدة بلا تكرار.
 *
 * المشكلة العملية: نفس المحل قد يظهر مرتين — مرة من القاعدة المضمّنة
 * (لقطة OpenStreetMap) ومرة من البحث الحي، أو مرة كعنصر نقطة ومرة كعنصر
 * مسار (way) في المصدر نفسه. المعرّف يختلف بينهما، لذا نعتمد على
 * «الاسم بعد التوحيد + القرب الجغرافي» كدليل على أنه المكان نفسه.
 *
 * القاعدة محافظة عن قصد: الدمج لا يحدث إلا إذا تطابق الاسم بعد التوحيد
 * وكانت المسافة بين الإحداثيين صغيرة جداً، حتى لا ندمج محلين مختلفين
 * تشابهت أسماؤهما.
 */
object PlaceDeduper {

    /** أقصى مسافة نعتبر عندها الإحداثيين لنفس المكان، بالكيلومترات. */
    const val SAME_PLACE_RADIUS_KM: Double = 0.12

    /**
     * يدمج القائمة ويُبقي عنصراً واحداً لكل مكان.
     *
     * الترتيب الأصلي محفوظ (الأول أولاً)، وعند التكرار نحتفظ بالسجل
     * الأغنى بيانات — لا بالذي وصل أولاً.
     */
    fun merge(places: List<Place>): List<Place> {
        val result = ArrayList<Place>(places.size)
        // فهرس بالاسم الموحّد -> مواضع السجلات المحفوظة في result
        val byName = HashMap<String, MutableList<Int>>()

        for (place in places) {
            val name = OsmText.normalize(place.name)
            if (name.isEmpty()) {
                result += place
                continue
            }
            val candidates = byName.getOrPut(name) { mutableListOf() }
            val duplicateAt = candidates.firstOrNull { index ->
                isSamePlace(result[index], place)
            }
            if (duplicateAt == null) {
                candidates += result.size
                result += place
            } else {
                result[duplicateAt] = richerOf(result[duplicateAt], place)
            }
        }
        return result
    }

    /** هل السجلان يمثّلان المكان نفسه (نفس الاسم وقريبان جغرافياً)؟ */
    fun isSamePlace(first: Place, second: Place): Boolean {
        if (OsmText.normalize(first.name) != OsmText.normalize(second.name)) return false

        val firstLat = first.latitude
        val firstLon = first.longitude
        val secondLat = second.latitude
        val secondLon = second.longitude

        // بلا إحداثيات: نكتفي بتطابق الاسم داخل المدينة نفسها.
        if (firstLat == null || firstLon == null || secondLat == null || secondLon == null) {
            return first.cityId != null && first.cityId == second.cityId
        }
        val distance = LatLng(firstLat, firstLon)
            .distanceKmTo(LatLng(secondLat, secondLon))
        return distance <= SAME_PLACE_RADIUS_KM
    }

    /**
     * يختار السجل الأغنى بيانات.
     *
     * الترتيب: البيانات الحيّة أو إضافات المستخدم تتقدّم على اللقطة المضمّنة،
     * لأنها أحدث؛ ومع التساوي نرجّح من يملك حقولاً أكثر (هاتف، عنوان، ساعات…).
     */
    fun richerOf(current: Place, challenger: Place): Place {
        val currentRank = rank(current)
        val challengerRank = rank(challenger)
        if (challengerRank > currentRank) return challenger
        if (challengerRank < currentRank) return current
        return if (richness(challenger) > richness(current)) challenger else current
    }

    /** أولوية المصدر: إضافة المستخدم ثم الحيّ ثم اللقطة. */
    private fun rank(place: Place): Int = when (place.source) {
        PlaceSource.USER -> 3
        PlaceSource.OPENSTREETMAP -> 2
        PlaceSource.OPENSTREETMAP_SNAPSHOT -> 1
    }

    /** عدد الحقول المفيدة المملوءة في السجل. */
    fun richness(place: Place): Int = listOfNotNull(
        place.phone?.takeIf { it.isNotBlank() },
        place.address?.takeIf { it.isNotBlank() },
        place.openingHours?.takeIf { it.isNotBlank() },
        place.description?.takeIf { it.isNotBlank() },
        place.operatorName?.takeIf { it.isNotBlank() },
    ).size + if (place.photoUrls.isNotEmpty()) 1 else 0
}
