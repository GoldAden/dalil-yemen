package com.dalilyemen.app.data.model

/** من أين جاءت بيانات هذا المكان. */
enum class PlaceSource {

    /** بيانات حيّة مباشرة من OpenStreetMap. */
    OPENSTREETMAP,

    /** نسخة مخزّنة (لقطة) من OpenStreetMap مضمّنة مع التطبيق للعمل دون إنترنت. */
    OPENSTREETMAP_SNAPSHOT,

    /** مكان أضافه المستخدم بنفسه داخل التطبيق. */
    USER,
}

/**
 * نموذج المحل/الخدمة المعروض في التطبيق.
 *
 * كل حقل يأتي من مصدره الحقيقي: بيانات OpenStreetMap كما هي في الوسوم،
 * أو ما كتبه المستخدم في نموذج الإضافة. لا تُختلق أسماء ولا أرقام.
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
    /** مفتاح الوسم الأصلي في OpenStreetMap، مثل: shop. */
    val kindKey: String? = null,
    /** قيمة الوسم الأصلي، مثل: mobile_phone. */
    val kindValue: String? = null,
    /** الجهة المشغّلة كما هي مكتوبة في المصدر، مثل: شركة النفط. */
    val operatorName: String? = null,
    /**
     * هل الإحداثيات تقريبية؟
     *
     * يصير هذا true عندما يضيف المستخدم محلاً بلا موقع دقيق من الخريطة،
     * فنعتمد مركز مدينته ونُعلن أن الموقع تقريبي بدل ادّعاء دقة غير موجودة.
     */
    val coordinatesApproximate: Boolean = false,
    val source: PlaceSource = PlaceSource.OPENSTREETMAP,
    /**
     * هل جاء هذا المكان من القاعدة المشتركة (أضافه مستخدم من جهاز آخر)؟
     *
     * نُظهر وسم «أضافه مستخدم» لمثل هذه السجلّات حتى يعرف المستخدم أن
     * المعلومة من مساهمة بشرية لا من خريطة مرجعية.
     */
    val isShared: Boolean = false,
) {

    /** هل أضاف هذا المكان مستخدم التطبيق؟ */
    val addedByUser: Boolean get() = source == PlaceSource.USER
}

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
