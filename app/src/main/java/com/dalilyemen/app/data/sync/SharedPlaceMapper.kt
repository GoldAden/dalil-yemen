package com.dalilyemen.app.data.sync

import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceSource
import com.dalilyemen.app.data.remote.OsmSource
import com.dalilyemen.app.data.remote.OsmText
import org.json.JSONObject

/**
 * تحويل سجلّات القاعدة المشتركة إلى نموذج العرض، مع تحقّق صارم.
 *
 * لا نثق بما يأتي من الشبكة حتى لو كانت القاعدة ملكنا: كل سجل يجب أن
 * يحمل اسماً غير فارغ وتصنيفاً معروفاً، وموقعاً داخل حدود اليمن إن وُجد.
 * أي سجل لا يجتاز التحقّق يُهمَل بصمت بدل إدخال بيانات فاسدة إلى الدليل.
 */
object SharedPlaceMapper {

    /** حدود اليمن التقريبية: جنوب، غرب، شمال، شرق. */
    private const val SOUTH = 12.0
    private const val WEST = 42.0
    private const val NORTH = 19.2
    private const val EAST = 54.8

    /**
     * يُنشئ مكاناً من سجل مشترك، أو null إن لم يجتز التحقّق.
     *
     * @param requireApproved هل نرفض السجل غير المعتمد؟ (نستخدمه للقراءة العامة)
     */
    fun toPlace(row: JSONObject, requireApproved: Boolean = true): Place? {
        val status = row.optString("status").trim()
        if (requireApproved && status.isNotEmpty() && status != "approved") return null

        val name = row.optString("name").trim()
        if (name.isEmpty()) return null

        val categoryId = row.optString("category_id").trim()
            .takeIf { it.isNotBlank() && it in OsmSource.allCategories }
            ?: OsmSource.DEFAULT_CATEGORY

        val latitude = row.opt("latitude").asDoubleOrNull()
        val longitude = row.opt("longitude").asDoubleOrNull()
        val coordinatesValid = latitude != null && longitude != null &&
            latitude in SOUTH..NORTH && longitude in WEST..EAST

        val id = row.opt("id").asIdString()
            ?: "shared_" + OsmText.normalize(name).replace(' ', '_')

        return Place(
            id = "shared:$id",
            name = name,
            description = row.optString("notes").takeIf { it.isNotBlank() },
            phone = row.optString("phone").takeIf { it.isNotBlank() },
            address = row.optString("address").takeIf { it.isNotBlank() },
            cityId = row.optString("city_id").takeIf { it.isNotBlank() },
            categoryId = categoryId,
            latitude = if (coordinatesValid) latitude else null,
            longitude = if (coordinatesValid) longitude else null,
            openingHours = row.optString("opening_hours").takeIf { it.isNotBlank() },
            kindKey = row.optString("kind_key").takeIf { it.isNotBlank() },
            kindValue = row.optString("kind_value").takeIf { it.isNotBlank() },
            // سجلّ مشترك: أضافه مستخدم من جهاز آخر.
            source = PlaceSource.USER,
            isShared = true,
            coordinatesApproximate = !coordinatesValid,
        )
    }

    /** يقرأ قائمة سجلّات من ردّ JSON؛ أي خطأ في الصياغة يعطي قائمة فارغة. */
    fun toPlaces(body: String, requireApproved: Boolean = true): List<Place> {
        if (body.isBlank()) return emptyList()
        val array = runCatching { org.json.JSONArray(body) }.getOrNull() ?: return emptyList()
        val places = ArrayList<Place>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            toPlace(row, requireApproved)?.let { places += it }
        }
        return places
    }

    /** يحوّل إحداثيات مكان إلى زوج إحداثيات إن كانت صالحة، وإلا null. */
    fun validLatLng(place: Place): LatLng? {
        val latitude = place.latitude ?: return null
        val longitude = place.longitude ?: return null
        if (latitude !in SOUTH..NORTH || longitude !in WEST..EAST) return null
        return LatLng(latitude, longitude)
    }

    private fun Any?.asIdString(): String? = when (this) {
        null -> null
        is Number -> toLong().toString()
        else -> toString().trim().takeIf { it.isNotBlank() }
    }

    /** يقرأ رقماً قد يصل كنص أو كرقم فعلي، وإلا null. */
    private fun Any?.asDoubleOrNull(): Double? = when (this) {
        null -> null
        is Number -> toDouble()
        else -> toString().trim().toDoubleOrNull()
    }
}
