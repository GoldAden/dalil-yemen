package com.dalilyemen.app.monetization

/**
 * هيكل جاهز لمصادر الدخل المستقبلية (إعلانات مدفوعة واشتراكات المحلات).
 *
 * لا يوجد أي إعلان فعلي ولا أي مفتاح وهمي في هذه النسخة. التنفيذات
 * أدناه "صامتة" (No-Op) حتى تُربط شبكة إعلانات حقيقية أو بوابة دفع.
 */

/** مواضع ظهور الإعلان داخل التطبيق. */
enum class AdPlacement {
    HOME_BANNER,
    SEARCH_RESULTS,
    PLACE_DETAIL,
}

/** مزوّد الإعلانات: يُستبدل لاحقاً بمزوّد حقيقي. */
interface AdProvider {

    /** هل الإعلانات مُفعّلة أصلاً في هذه النسخة؟ */
    fun isEnabled(): Boolean

    /** معرّف وحدة الإعلان للموضع المطلوب، أو null إذا لم تُضبط بعد. */
    fun adUnitId(placement: AdPlacement): String?
}

/** مزوّد الاشتراكات المدفوعة للمحلات. */
interface SubscriptionProvider {

    suspend fun isSubscribed(placeId: String): Boolean
}

/**
 * التنفيذ الافتراضي: لا إعلانات.
 * لا نضع أي معرّفات وهمية، لأن المعرّف الوهمي قد يسبب أخطاءً في التشغيل.
 */
object NoOpAdProvider : AdProvider {
    override fun isEnabled(): Boolean = false
    override fun adUnitId(placement: AdPlacement): String? = null
}

object NoOpSubscriptionProvider : SubscriptionProvider {
    override suspend fun isSubscribed(placeId: String): Boolean = false
}

object MonetizationProvider {
    val ads: AdProvider = NoOpAdProvider
    val subscriptions: SubscriptionProvider = NoOpSubscriptionProvider
}
