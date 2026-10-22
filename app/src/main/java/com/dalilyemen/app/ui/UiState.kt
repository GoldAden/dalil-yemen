package com.dalilyemen.app.ui

import com.dalilyemen.app.data.model.Place

/**
 * حالات الواجهة المعروضة، منفصلة عن أي اعتماد على نظام أندرويد.
 *
 * فصلها هنا يسمح باختبار قواعد تحديثها منطقياً بلا محاكي، ويُبقي
 * عقل الشاشات مسؤولاً عن جلب البيانات فقط.
 */
data class PlacesUiState(
    val isLoading: Boolean = false,
    val places: List<Place> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedCityId: String? = null,
    /** رسالة خطأ قابلة للعرض إن فشل الجلب. */
    val errorMessage: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<Place> = emptyList(),
    val hasSearched: Boolean = false,
    val selectedCityId: String? = null,
    val selectedCategoryId: String? = null,
    val errorMessage: String? = null,
)

/**
 * قواعد انتقال حالة البحث.
 *
 * القاعدة الأساسية: أي تعديل على نص الاستعلام يُلغي نتيجة البحث السابقة.
 * بدون ذلك تبقى شاشة البحث عالقة على «لا توجد نتائج» من استعلام قديم
 * بينما المستخدم يكتب استعلاماً جديداً لم يُنفَّذ بعد.
 */
object SearchTransitions {

    /** المستخدم يكتب أو يمسح نص البحث الآن. */
    fun queryChanged(state: SearchUiState, value: String): SearchUiState =
        state.copy(
            query = value,
            hasSearched = false,
            results = emptyList(),
            errorMessage = null,
        )

    /** بدأ تنفيذ بحث: نُعلن التحميل ونمسح الخطأ القديم. */
    fun started(state: SearchUiState): SearchUiState =
        state.copy(isLoading = true, hasSearched = true, errorMessage = null)

    /** هل نعرض للمستخدم شاشة «اكتب اسماً للبحث» بدل أي رسالة أخرى؟ */
    fun isIdle(state: SearchUiState): Boolean =
        !state.hasSearched && !state.isLoading && state.results.isEmpty()

    /**
     * هل انتهى بحث فعلي بلا نتائج؟
     *
     * لا نُعلن ذلك قبل تنفيذ بحث، ولا أثناء التحميل، ولا إذا كان الخطأ
     * هو سبب الفراغ — في هذه الحالة نوضّح السبب الحقيقي للمستخدم.
     */
    fun isEmptyResult(state: SearchUiState): Boolean =
        state.hasSearched && !state.isLoading &&
            state.results.isEmpty() && state.errorMessage == null
}
