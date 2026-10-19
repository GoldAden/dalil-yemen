package com.dalilyemen.app.data.model

import androidx.annotation.StringRes

/**
 * تصنيف الخدمة أو المحل (مطاعم، صيدليات، وقود …).
 *
 * النص يأتي من موارد الترجمة حتى يبقى التطبيق قابلاً للتعريب والتوسعة.
 */
data class Category(
    val id: String,
    @StringRes val titleRes: Int,
)

/** التصنيفات الأساسية التي يعرضها التطبيق في الشاشة الرئيسية. */
object Categories {
    const val RESTAURANTS = "restaurants"
    const val HEALTH = "health"
    const val FUEL = "fuel"
    const val MAINTENANCE = "maintenance"
    const val ELECTRONICS = "electronics"
    const val STORES = "stores"
}
