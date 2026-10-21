package com.dalilyemen.app.data.repository

import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place

/**
 * تنفيذ احتياطي للطوارئ فقط.
 *
 * لا يُستخدم في السياق الطبيعي: التطبيق يهيّئ المصدر الحقيقي عند الإقلاع.
 * وُجد هنا حتى لا يسقط التطبيق إن نودي المستودع قبل التهيئة (مثلاً في
 * اختبار أو معاينة تصميم)، ولا يُختلق فيه أي بيانات.
 */
class SafeEmptyRepository : PlaceRepository {

    override suspend fun getFeatured(limit: Int): List<Place> = emptyList()

    override suspend fun getNearby(origin: LatLng, radiusKm: Double, limit: Int): List<Place> =
        emptyList()

    override suspend fun search(query: String, cityId: String?, categoryId: String?): List<Place> =
        emptyList()

    override suspend fun getByCategory(categoryId: String, cityId: String?): List<Place> =
        emptyList()

    override suspend fun getById(id: String): Place? = null

    override fun isOnline(): Boolean = false

    override fun hasOfflineData(): Boolean = false

    override fun favoriteIds(): Set<String> = emptySet()

    override fun toggleFavorite(placeId: String): Boolean = false

    override fun userPlaces(): List<Place> = emptyList()

    override fun addUserPlace(place: Place): Place = place
}
