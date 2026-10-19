package com.dalilyemen.app

import android.app.Application
import com.dalilyemen.app.data.repository.DalilRepositoryProvider

/**
 * نقطة تهيئة التطبيق.
 *
 * لا تُنشأ هنا أي بيانات مختلقة. المصدر الحقيقي للمحلات يُربط لاحقاً
 * عبر [DalilRepositoryProvider] (قاعدة بيانات محلية أو خدمة خلفية).
 */
class DalilApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DalilRepositoryProvider.init()
    }
}
