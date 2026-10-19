package com.dalilyemen.app.data.repository

/**
 * نقطة واحدة لتزويد التطبيق بمصدر البيانات.
 *
 * خطوات التوسعة لاحقاً:
 *  1. أنشئ تنفيذاً جديداً لـ [PlaceRepository]، مثلاً:
 *     - [RoomPlaceRepository] لقاعدة بيانات محلية.
 *     - [RemotePlaceRepository] لخدمة خلفية عبر الشبكة.
 *  2. بدّل السطر في [init] ليصبح التنفيذ الجديد.
 *
 * لا تحتاج أي شاشة إلى تعديل.
 */
object DalilRepositoryProvider {

    @Volatile
    private var repository: PlaceRepository = EmptyPlaceRepository()

    fun init() {
        // مثال مستقبلي:
        // repository = RemotePlaceRepository(apiClient = DalilApi.create())
        repository = EmptyPlaceRepository()
    }

    fun places(): PlaceRepository = repository
}
