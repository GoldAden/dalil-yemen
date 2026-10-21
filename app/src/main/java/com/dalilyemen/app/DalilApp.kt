package com.dalilyemen.app

import android.app.Application
import com.dalilyemen.app.data.repository.DalilRepositoryProvider

/**
 * نقطة تهيئة التطبيق.
 *
 * نهيّئ هنا مصدر البيانات الحقيقي: لقطة مضمّنة من OpenStreetMap تعمل دون
 * إنترنت، مع بحث حيّ عند توفّر الاتصال. التهيئة تقبل سياق التطبيق لأن
 * المصدر يقرأ اللقطة المضمّنة ويحفظ كاشاً محلياً على الجهاز.
 */
class DalilApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DalilRepositoryProvider.init(this)
    }
}
