package com.dalilyemen.app.data.repository

import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * ترميز وفك ترميز محلات المستخدم المحفوظة على الجهاز.
 *
 * فصلناه عن التخزين نفسه لأنه منطق خالص لا يحتاج نظام أندرويد، فيمكن
 * اختباره مباشرة. الحقلان المهمّان هنا هما «نوع النشاط» (kindValue) ووسم
 * «قادم من القاعدة المشتركة» (isShared): بلا حفظهما يُستعاد المحل ناقصاً
 * بعد إغلاق التطبيق، فيضيع نوعه ويظهر كأنه من إضافة محلية.
 */
object LocalPlaceCodec {

    private const val ROOT = "places"

    /** يحوّل قائمة المحلات إلى نص واحد قابل للحفظ. */
    fun encode(places: List<Place>): String {
        val array = JSONArray()
        places.forEach { place ->
            val item = JSONObject()
            item.put("id", place.id)
            item.put("name", place.name)
            item.put("categoryId", place.categoryId)
            item.put("source", place.source.name)
            place.description?.let { item.put("description", it) }
            place.phone?.let { item.put("phone", it) }
            place.address?.let { item.put("address", it) }
            place.cityId?.let { item.put("cityId", it) }
            place.latitude?.let { item.put("lat", it) }
            place.longitude?.let { item.put("lon", it) }
            place.openingHours?.let { item.put("openingHours", it) }
            place.kindKey?.let { item.put("kindKey", it) }
            place.kindValue?.let { item.put("kindValue", it) }
            place.operatorName?.let { item.put("operatorName", it) }
            item.put("approx", place.coordinatesApproximate)
            item.put("isShared", place.isShared)
            array.put(item)
        }
        return JSONObject().apply { put(ROOT, array) }.toString()
    }

    /**
     * يستعيد قائمة المحلات من النص المحفوظ.
     *
     * أي نص تالف أو سجل بلا معرّف/اسم يُتجاهل بلا تعطيل بقية القائمة،
     * فلا تتحوّل بيانات محلية معطوبة إلى انهيار في الواجهة.
     */
    fun decode(body: String): List<Place> {
        val array = runCatching { JSONObject(body).optJSONArray(ROOT) }.getOrNull()
            ?: return emptyList()
        val places = ArrayList<Place>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
            places += Place(
                id = id,
                name = name,
                description = item.optString("description").takeIf { it.isNotBlank() },
                phone = item.optString("phone").takeIf { it.isNotBlank() },
                address = item.optString("address").takeIf { it.isNotBlank() },
                cityId = item.optString("cityId").takeIf { it.isNotBlank() },
                categoryId = item.optString("categoryId").takeIf { it.isNotBlank() }
                    ?: com.dalilyemen.app.data.remote.OsmSource.DEFAULT_CATEGORY,
                latitude = item.optString("lat").toDoubleOrNull(),
                longitude = item.optString("lon").toDoubleOrNull(),
                openingHours = item.optString("openingHours").takeIf { it.isNotBlank() },
                kindKey = item.optString("kindKey").takeIf { it.isNotBlank() },
                kindValue = item.optString("kindValue").takeIf { it.isNotBlank() },
                operatorName = item.optString("operatorName").takeIf { it.isNotBlank() },
                source = runCatching { PlaceSource.valueOf(item.optString("source")) }
                    .getOrDefault(PlaceSource.USER),
                coordinatesApproximate = item.optBoolean("approx", false),
                isShared = item.optBoolean("isShared", false),
            )
        }
        return places
    }
}
