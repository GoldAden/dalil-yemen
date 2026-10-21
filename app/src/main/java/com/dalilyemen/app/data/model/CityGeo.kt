package com.dalilyemen.app.data.model

/**
 * مركز كل مدينة يمنية مشمولة بالتصفية.
 *
 * هذه إحداثيات مراكز المدن كما هي مسجّلة في OpenStreetMap، تُستخدم
 * لتوجيه البحث الجغرافي حول المدينة المختارة بدل البحث في البلد كله.
 */
object CityGeo {

    /** أقصى مسافة نقبلها لربط مكان بمدينة معيّنة، بالكيلومترات. */
    const val CITY_MATCH_RADIUS_KM: Double = 60.0

    /** نصف قطر البحث الافتراضي حول مركز المدينة، بالكيلومترات. */
    const val DEFAULT_SEARCH_RADIUS_KM: Double = 12.0

    private val centers: Map<String, LatLng> = mapOf(
        "sanaa" to LatLng(15.3539, 44.2059),
        "aden" to LatLng(12.7896, 45.0285),
        "taiz" to LatLng(13.5752, 44.0215),
        "hodeidah" to LatLng(14.7979, 42.9545),
        "mukalla" to LatLng(14.5425, 49.1231),
        "ibb" to LatLng(13.9702, 44.1779),
        "dhamar" to LatLng(14.5430, 44.4001),
        "hajjah" to LatLng(15.6942, 43.6058),
        "saada" to LatLng(16.9409, 43.7630),
        "marib" to LatLng(15.4579, 45.3230),
        "hadramout" to LatLng(15.9441, 48.7926),
        "lahj" to LatLng(13.0578, 44.8836),
        "abyan" to LatLng(13.1286, 45.3812),
        "shabwah" to LatLng(14.5277, 46.8454),
        "albayda" to LatLng(13.9881, 45.5729),
        "aldhale" to LatLng(13.7017, 44.7314),
        "aljawf" to LatLng(16.1634, 44.7766),
        "amran" to LatLng(15.6628, 43.9430),
        "almahwit" to LatLng(15.4701, 43.5448),
        "raymah" to LatLng(14.7104, 43.6082),
        "socotra" to LatLng(12.6512, 54.0237),
    )

    /** المدن التي نعرف مركزها بدقة. */
    val knownCityIds: List<String> = centers.keys.toList()

    fun centerOf(cityId: String?): LatLng? = cityId?.let { centers[it] }

    /** أقرب مدينة معروفة إلى نقطة، إن كانت قريبة بدرجة معقولة. */
    fun nearestCityId(point: LatLng): String? {
        var bestId: String? = null
        var bestDistance = Double.MAX_VALUE
        for ((id, center) in centers) {
            val distance = point.distanceKmTo(center)
            if (distance < bestDistance) {
                bestDistance = distance
                bestId = id
            }
        }
        return if (bestDistance <= CITY_MATCH_RADIUS_KM) bestId else null
    }
}
