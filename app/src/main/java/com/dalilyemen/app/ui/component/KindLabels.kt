package com.dalilyemen.app.ui.component

/**
 * ترجمة مختصرة لنوع النشاط القادم من وسوم المصدر.
 *
 * القيمة الأصلية في OpenStreetMap إنجليزية مثل pharmacy أو mobile_phone،
 * فنحوّلها إلى وصف عربي مفهوم. ما لا نعرفه نعرضه كما هو بدل اختلاق معنى.
 */
fun kindLabel(kindValue: String?): String? {
    val key = kindValue?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when (key) {
        "restaurant", "food_court" -> "مطعم"
        "cafe", "internet_cafe" -> "مقهى"
        "fast_food" -> "وجبات سريعة"
        "ice_cream" -> "آيس كريم"
        "bar", "pub" -> "مقهى"
        "bakery", "pastry", "confectionery" -> "مخبز وحلويات"
        "beverages", "coffee", "tea" -> "مشروبات"
        "butcher" -> "جزارة"
        "greengrocer" -> "خضار وفواكه"
        "seafood" -> "أسماك"
        "pharmacy", "chemist" -> "صيدلية"
        "hospital" -> "مستشفى"
        "clinic", "doctors" -> "عيادة"
        "dentist" -> "عيادة أسنان"
        "veterinary" -> "عيادة بيطرية"
        "medical_supply" -> "مستلزمات طبية"
        "optician" -> "بصريات"
        "fuel", "gas" -> "محطة وقود"
        "charging_station" -> "شاحن كهربائي"
        "car_wash" -> "غسيل سيارات"
        "car_repair", "car_repair_shop" -> "ورشة سيارات"
        "car_parts", "tyres" -> "قطع غيار"
        "mobile_phone", "telecommunication" -> "جوالات"
        "electronics", "computer" -> "إلكترونيات"
        "hardware", "doityourself" -> "عدد وأدوات"
        "clothes", "fashion" -> "ملابس"
        "shoes" -> "أحذية"
        "supermarket", "convenience", "grocery" -> "بقالة"
        "department_store" -> "مركز تجاري"
        "furniture", "interior_decoration" -> "أثاث"
        "stationery", "books" -> "قرطاسية"
        "jewelry", "watches" -> "مجوهرات"
        "perfumery", "beauty" -> "عطور وتجميل"
        "hairdresser" -> "صالون"
        "travel_agency" -> "وكالة سفر"
        "bank" -> "بنك"
        "hotel", "guest_house" -> "فندق"
        "school", "kindergarten" -> "مدرسة"
        else -> key.replace('_', ' ')
    }
}

/**
 * ينسّق المسافة بنصّ عربي قصير: أقل من كيلومتر، أو بالكيلومترات.
 * المدخل بالكيلومترات.
 */
fun formatDistance(km: Double): String = when {
    km < 1.0 -> "${(km * 1000).toInt()} م"
    km < 10.0 -> String.format(java.util.Locale.US, "%.1f كم", km)
    else -> "${km.toInt()} كم"
}
