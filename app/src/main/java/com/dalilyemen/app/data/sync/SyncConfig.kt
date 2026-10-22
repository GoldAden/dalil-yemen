package com.dalilyemen.app.data.sync

import com.dalilyemen.app.BuildConfig

/**
 * إعداد القاعدة المشتركة (Supabase/PostgREST).
 *
 * القيم تُحقن وقت البناء من ملف إعدادات محلي غير مرفوع إلى المستودع،
 * ولا تُكتب أي مفاتيح داخل الشيفرة. إن كانت القيم فارغة تعمل المزامنة
 * المشتركة كـ«مُعطّلة» بهدوء، ويبقى التطبيق يعمل محلياً بشكل كامل.
 */
data class SyncConfig(
    val baseUrl: String,
    val apiKey: String,
) {

    /** هل الإعداد مكتمل ويمكن استخدام المزامنة؟ */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    companion object {

        /** إعداد فارغ يعني: لا مزامنة مشتركة. */
        val DISABLED: SyncConfig = SyncConfig(baseUrl = "", apiKey = "")

        /** يبني إعداداً منظّفاً من قيم نصية قد تكون ناقصة. */
        fun from(url: String?, key: String?): SyncConfig = SyncConfig(
            baseUrl = url?.trim().orEmpty().trimEnd('/'),
            apiKey = key?.trim().orEmpty(),
        )
    }
}

/**
 * مصدر الإعداد المستخدم في التطبيق.
 *
 * يقرأ القيم التي حُقنت في البناء، وليس أي نص مكتوب في الشيفرة.
 */
object SyncConfigProvider {

    /** الإعداد الحالي؛ فارغ إن لم يُضبط وقت البناء. */
    val current: SyncConfig by lazy {
        SyncConfig.from(
            url = BuildConfig.SHARED_DB_URL,
            key = BuildConfig.SHARED_DB_KEY,
        )
    }
}
