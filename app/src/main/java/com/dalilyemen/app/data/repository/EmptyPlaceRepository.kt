package com.dalilyemen.app.data.repository

import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * تنفيذ مبدئي فارغ.
 *
 * الغرض منه أن يكون التطبيق قابلاً للبناء والتثبيت والعرض بالكامل
 * (واجهات، تنقّل، بحث، تصفية، إذن الموقع) دون أي بيانات محلات.
 *
 * لماذا فارغ؟
 * لأن البيانات الحقيقية للمحلات لا تُختلق. عند توفّر قاعدة بيانات أو
 * خدمة خلفية، أنشئ تنفيذاً آخر لـ [PlaceRepository] واستبدله في
 * [DalilRepositoryProvider] — دون لمس أي شاشة.
 */
class EmptyPlaceRepository : PlaceRepository {

    override suspend fun getFeatured(limit: Int): List<Place> = emptyList()

    override suspend fun getNearby(origin: LatLng, radiusKm: Double, limit: Int): List<Place> =
        emptyList()

    override suspend fun search(
        query: String,
        cityId: String?,
        categoryId: String?,
    ): List<Place> = emptyList()

    override suspend fun getByCategory(categoryId: String, cityId: String?): List<Place> =
        emptyList()

    override suspend fun getById(id: String): Place? = withContext(Dispatchers.IO) { null }
}
