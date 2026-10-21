package com.dalilyemen.app.data.repository

import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place

/**
 * العقد الذي يحدد كيف يحصل التطبيق على بيانات المحلات.
 *
 * الشاشات تعتمد على هذه الواجهة فقط، لذلك يمكن لاحقاً تبديل التنفيذ
 * إلى قاعدة بيانات محلية أو خدمة خلفية دون تعديل واجهات المستخدم.
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

    // --- معلومات الحالة ---

    /** هل يوجد اتصال بالإنترنت الآن؟ */
    fun isOnline(): Boolean

    /** هل توجد بيانات محفوظة تعمل دون إنترنت؟ */
    fun hasOfflineData(): Boolean

    // --- المفضلة ---

    /** معرّفات المحلات في مفضلة المستخدم. */
    fun favoriteIds(): Set<String>

    /** يبدّل حالة المفضلة ويعيد الحالة الجديدة. */
    fun toggleFavorite(placeId: String): Boolean

    /** كل المحلات التي أضافها المستخدم بنفسه. */
    fun userPlaces(): List<Place>

    /**
     * إضافة محل جديد من المستخدم.
     *
     * @return المكان كما حُفظ فعلياً على الجهاز.
     */
    fun addUserPlace(place: Place): Place
}
