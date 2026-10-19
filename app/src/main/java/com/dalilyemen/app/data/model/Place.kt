package com.dalilyemen.app.data.model

/**
 * نموذج المحل/الخدمة المعروض في التطبيق.
 *
 * ملاحظة مهمة: لا يحتوي المشروع على أي بيانات محلات حقيقية أو مختلقة.
 * هذا النموذج هو العقد الذي تُملأ به البيانات عند ربط قاعدة البيانات
 * أو الخدمة الخلفية.
 */
data class Place(
    val id: String,
    val name: String,
    val description: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val cityId: String? = null,
    val categoryId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val openingHours: String? = null,
    val photoUrls: List<String> = emptyList(),
    /** يُستخدم لاحقاً لتمييز المحلات المشتركة في الإعلانات المدفوعة. */
    val isSponsored: Boolean = false,
)

/** إحداثيات جغرافية بسيطة مستقلة عن أي مكتبة خارجية. */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
) {
    /**
     * مسافة تقريبية بالكيلومترات باستخدام صيغة هافرساين.
     * كافية للترتيب العرضي "الأقرب أولاً".
     */
    fun distanceKmTo(other: LatLng): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusKm * c
    }
}
