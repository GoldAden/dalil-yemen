package com.dalilyemen.app.data.repository

import android.content.Context
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * تخزين دائم على الجهاز لما يخصّ المستخدم: المفضلة والمحلات التي أضافها.
 *
 * التخزين محلي فقط (لا حساب ولا مزامنة سحابية في هذه النسخة)، ويبقى
 * بعد إغلاق التطبيق لأن المستخدم يتوقّع أن تبقى إضافاته ومفضلته.
 */
class LocalPlaceStore(context: Context) {

    private val prefs = context.getSharedPreferences("dalil_local", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_USER_PLACES = "user_places"
    }

    // ----------------------------- المفضلة -----------------------------

    fun favorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    fun isFavorite(placeId: String): Boolean = favorites().contains(placeId)

    /** يبدّل حالة المفضلة ويعيد الحالة الجديدة. */
    fun toggleFavorite(placeId: String): Boolean {
        val current = favorites().toMutableSet()
        val nowFavorite = if (current.contains(placeId)) {
            current.remove(placeId)
            false
        } else {
            current.add(placeId)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return nowFavorite
    }

    // --------------------------- محلات المستخدم ---------------------------

    fun userPlaces(): List<Place> {
        val body = prefs.getString(KEY_USER_PLACES, null) ?: return emptyList()
        return runCatching { decode(body) }.getOrDefault(emptyList())
    }

    fun addUserPlace(place: Place) {
        val current = userPlaces().filterNot { it.id == place.id }
        val updated = current + place
        prefs.edit().putString(KEY_USER_PLACES, encode(updated)).apply()
    }

    fun removeUserPlace(placeId: String) {
        val updated = userPlaces().filterNot { it.id == placeId }
        prefs.edit().putString(KEY_USER_PLACES, encode(updated)).apply()
    }

    private fun encode(places: List<Place>): String {
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
            item.put("approx", place.coordinatesApproximate)
            array.put(item)
        }
        return JSONObject().apply { put("places", array) }.toString()
    }

    private fun decode(body: String): List<Place> {
        val array = JSONObject(body).optJSONArray("places") ?: return emptyList()
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
                source = runCatching { PlaceSource.valueOf(item.optString("source")) }
                    .getOrDefault(PlaceSource.USER),
                coordinatesApproximate = item.optBoolean("approx", false),
            )
        }
        return places
    }
}
