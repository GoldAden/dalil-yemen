package com.dalilyemen.app.data.repository

import android.content.Context
import com.dalilyemen.app.data.model.Place

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
        return runCatching { LocalPlaceCodec.decode(body) }.getOrDefault(emptyList())
    }

    fun addUserPlace(place: Place) {
        val current = userPlaces().filterNot { it.id == place.id }
        val updated = current + place
        prefs.edit().putString(KEY_USER_PLACES, LocalPlaceCodec.encode(updated)).apply()
    }

    fun removeUserPlace(placeId: String) {
        val updated = userPlaces().filterNot { it.id == placeId }
        prefs.edit().putString(KEY_USER_PLACES, LocalPlaceCodec.encode(updated)).apply()
    }
}
