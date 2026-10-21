package com.dalilyemen.app.data.repository

import android.content.Context

/**
 * نقطة واحدة لتزويد التطبيق بمصدر البيانات.
 *
 * الخطوة الأولى للتهيئة تمرّر سياق التطبيق حتى يقرأ المصدر اللقطة المضمّنة
 * والكاش المحلي. من أراد تغيير المصدر لاحقاً (قاعدة بيانات أو خدمة خلفية)
 * يُنشئ تنفيذاً آخر لـ [PlaceRepository] ويبدّل السطر في [init] فقط،
 * دون لمس أي شاشة.
 */
object DalilRepositoryProvider {

    @Volatile
    private var repository: PlaceRepository? = null

    /** يُنشئ المستودع مرة واحدة (نداءات متكرّرة لا تُنشئ نسخاً جديدة). */
    @Synchronized
    fun init(context: Context) {
        if (repository == null) {
            repository = OsmPlaceRepository(context.applicationContext)
        }
    }

    /**
     * المستودع الحالي.
     *
     * إن نُودع قبل التهيئة نُرجع تنفيذاً آمناً لا يعطّل التطبيق، مع بقاء
     * التهيئة مسؤولية فئة التطبيق عند الإقلاع.
     */
    fun places(): PlaceRepository = repository ?: SafeEmptyRepository()
}
