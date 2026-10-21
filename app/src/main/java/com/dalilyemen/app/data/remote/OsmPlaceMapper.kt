package com.dalilyemen.app.data.remote

import com.dalilyemen.app.data.model.CityGeo
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceSource

/**
 * يحوّل عناصر الخريطة إلى نموذج العرض داخل التطبيق.
 *
 * القاعدة: كل ما نعرضه مأخوذ حرفياً من وسوم المصدر. ما لا وجود له
 * في المصدر يبقى فارغاً ويُعرض في الواجهة "غير متوفر" بدل اختلاق قيمة.
 */
object OsmPlaceMapper {

    /** تصنيفات لا تُعرض في دليل محلات: طرق، حدود إدارية، تضاريس … */
    private val excludedAmenities = setOf(
        "parking", "parking_space", "bicycle_parking", "motorcycle_parking",
        "bus_station", "taxi", "toilets", "shelter", "bench", "waste_basket",
        "drinking_water", "fountain", "grave_yard", "place_of_worship",
        "police", "fire_station", "post_box", "recycling", "bicycle_rental",
        "clock", "atm", "vending_machine", "kindergarten", "school",
        "college", "university", "library", "townhall", "courthouse",
        "embassy", "prison", "ranger_station", "public_bath", "shower",
    )

    private val excludedKeys = setOf(
        "highway", "railway", "waterway", "boundary", "admin_level",
        "natural", "landuse", "building", "building:part", "barrier",
        "power", "man_made", "route", "aeroway", "place", "contour",
        "water", "ford", "entrance", "traffic_calming", "military",
    )

    /**
     * يُنشئ مكاناً للعرض، أو يعيد null إذا كان العنصر غير صالح للعرض.
     *
     * @param element عنصر المصدر.
     * @param forceCategory تصنيف مفروض (عند التصفية داخل تصنيف معيّن).
     */
    fun toPlace(element: OsmElement, forceCategory: String? = null): Place? {
        val tags = element.tags
        if (isNoise(tags)) return null

        val name = displayName(tags) ?: return null
        val category = forceCategory ?: OsmSource.categoryForTags(tags)
            ?: if (hasShopLikeTag(tags)) OsmSource.DEFAULT_CATEGORY else return null

        return Place(
            id = element.id,
            name = name,
            description = null,
            phone = phoneOf(tags),
            address = addressOf(tags),
            cityId = CityGeo.nearestCityId(
                com.dalilyemen.app.data.model.LatLng(element.latitude, element.longitude)
            ),
            categoryId = category,
            latitude = element.latitude,
            longitude = element.longitude,
            openingHours = tags["opening_hours"]?.takeIf { it.isNotBlank() },
            photoUrls = emptyList(),
            kindKey = kindOf(tags)?.first,
            kindValue = kindOf(tags)?.second,
            operatorName = tags["operator"]?.takeIf { it.isNotBlank() },
            source = PlaceSource.OPENSTREETMAP,
        )
    }

    /** الاسم المعروض: الاسم المحلي أولاً ثم العربي ثم الإنجليزي. */
    fun displayName(tags: Map<String, String>): String? {
        val candidates = listOf(
            tags["name"],
            tags["name:ar"],
            tags["name:en"],
            tags["int_name"],
            tags["official_name"],
            brandOrOperator(tags),
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun brandOrOperator(tags: Map<String, String>): String? {
        val brand = tags["brand"]?.takeIf { it.isNotBlank() }
        val operator = tags["operator"]?.takeIf { it.isNotBlank() }
        // اسم العلامة وحده لا يكفي للتعريف بمحل محدّد إلا إذا كان فرعاً معلَماً.
        val branch = tags["branch"]?.takeIf { it.isNotBlank() }
        return when {
            brand != null && branch != null -> "$brand — $branch"
            operator != null -> operator
            else -> null
        }
    }

    /** رقم الهاتف كما هو مسجَّل، بعد التحقق من أنه أرقام قابلة للاتصال. */
    fun phoneOf(tags: Map<String, String>): String? {
        val raw = tags["phone"] ?: tags["contact:phone"] ?: tags["phone:mobile"] ?: return null
        val cleaned = raw.substringBefore(';').trim()
        if (cleaned.count { it.isDigit() } < 6) return null
        return cleaned
    }

    /** العنوان المُركَّب من وسوم العنوان الفعلية فقط. */
    fun addressOf(tags: Map<String, String>): String? {
        val direct = tags["addr:full"]?.takeIf { it.isNotBlank() }
        if (direct != null) return direct

        val parts = listOf(
            tags["addr:street"],
            tags["addr:housenumber"],
            tags["addr:district"]?.takeIf { it.isNotBlank() } ?: tags["addr:subdistrict"],
            tags["addr:city"],
        ).filter { !it.isNullOrBlank() }

        if (parts.isEmpty()) return null
        return parts.joinToString("، ")
    }

    /** التصنيف الأصلي في المصدر، مثل shop إلى mobile_phone. */
    fun kindOf(tags: Map<String, String>): Pair<String, String>? {
        for (key in listOf("shop", "amenity", "craft", "healthcare", "office", "leisure", "tourism")) {
            val value = tags[key]?.takeIf { it.isNotBlank() } ?: continue
            return key to value
        }
        return null
    }

    private fun hasShopLikeTag(tags: Map<String, String>): Boolean =
        listOf("shop", "craft", "office", "healthcare").any { !tags[it].isNullOrBlank() }

    /** يستبعد الطرق والحدود والعناصر غير الصالحة كمدخلات دليل محلات. */
    private fun isNoise(tags: Map<String, String>): Boolean {
        for (key in excludedKeys) {
            if (!tags[key].isNullOrBlank()) return true
        }
        val amenity = tags["amenity"]
        if (amenity != null && excludedAmenities.contains(amenity)) return true
        if (tags["shop"] == "vacant" || tags["disused:shop"] != null) return true
        if (tags["abandoned:shop"] != null) return true
        return false
    }
}
