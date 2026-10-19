package com.dalilyemen.app.data.repository

import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place

/**
 * العقد الذي يحدد كيف يحصل التطبيق على بيانات المحلات.
 *
 * الشاشات تعتمد على هذه الواجهة فقط، لذلك يمكن لاحقاً تبديل التنفيذ
 * إلى Room أو Retrofit أو Firebase دون تعديل واجهات المستخدم.
 */
interface PlaceRepository {

    /** المحلات المقترحة في الشاشة الرئيسية. */
    suspend fun getFeatured(limit: Int = 10): List<Place>

    /** المحلات الأقرب إلى موقع المستخدم داخل نطاق محدد. */
    suspend fun getNearby(
        origin: LatLng,
        radiusKm: Double = 25.0,
        limit: Int = 20,
    ): List<Place>

    /** بحث نصي مع تصفية اختيارية بالمدينة والتصنيف. */
    suspend fun search(
        query: String,
        cityId: String? = null,
        categoryId: String? = null,
    ): List<Place>

    /** محلات تصنيف معيّن. */
    suspend fun getByCategory(
        categoryId: String,
        cityId: String? = null,
    ): List<Place>

    /** تفاصيل محل واحد. */
    suspend fun getById(id: String): Place?
}
