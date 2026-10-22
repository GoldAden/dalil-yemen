package com.dalilyemen.app.data

import com.dalilyemen.app.data.model.Categories
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceSource
import com.dalilyemen.app.data.repository.LocalPlaceCodec
import com.dalilyemen.app.ui.SearchTransitions
import com.dalilyemen.app.ui.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات قواعد شاشة البحث ومنطق حفظ محلات المستخدم.
 *
 * كلها منطق خالص بلا نظام أندرويد، فلا تحتاج محاكياً ولا جهازاً.
 */
class UiStateAndStoreTest {

    // ------------------------- قواعد حالة البحث -------------------------

    @Test
    fun queryChange_clearsPreviousResultAndSearchFlag() {
        val afterSearch = SearchUiState(
            query = "قديم",
            hasSearched = true,
            results = listOf(samplePlace("osm_1", "صيدلية")),
            errorMessage = "خطأ قديم",
        )
        val typing = SearchTransitions.queryChanged(afterSearch, "جديد")

        assertEquals("جديد", typing.query)
        assertFalse("يجب إلغاء علم البحث عند تغيير النص", typing.hasSearched)
        assertTrue("يجب إخلاء النتائج القديمة", typing.results.isEmpty())
        assertEquals(null, typing.errorMessage)
    }

    @Test
    fun emptyResult_isReportedOnlyAfterAFinishedSearch() {
        val beforeSearch = SearchUiState(query = "مطعم")
        assertFalse(SearchTransitions.isEmptyResult(beforeSearch))
        assertTrue("قبل أي بحث نعرض شاشة الدعوة للكتابة", SearchTransitions.isIdle(beforeSearch))

        val loading = SearchTransitions.started(beforeSearch)
        assertTrue(loading.isLoading)
        assertTrue(loading.hasSearched)
        assertFalse("لا نُعلن فراغ النتائج أثناء التحميل", SearchTransitions.isEmptyResult(loading))

        val finished = loading.copy(isLoading = false)
        assertTrue(SearchTransitions.isEmptyResult(finished))
        assertFalse(SearchTransitions.isIdle(finished))
    }

    @Test
    fun emptyResult_isNotReportedWhenFailureIsTheRealReason() {
        val failed = SearchUiState(
            query = "مطعم",
            hasSearched = true,
            isLoading = false,
            results = emptyList(),
            errorMessage = "تعذّر الوصول إلى الشبكة",
        )
        assertFalse(
            "عند وجود خطأ نعرض سببه لا رسالة «لا توجد نتائج»",
            SearchTransitions.isEmptyResult(failed),
        )
    }

    @Test
    fun started_keepsQueryButClearsOldError() {
        val withError = SearchUiState(query = "مقهى", errorMessage = "خطأ")
        val started = SearchTransitions.started(withError)
        assertEquals("مقهى", started.query)
        assertEquals(null, started.errorMessage)
        assertTrue(started.isLoading)
    }

    // ---------------------- حفظ محلات المستخدم محلياً ----------------------

    @Test
    fun codec_keepsKindAndSharedFlagAfterRoundTrip() {
        val shared = samplePlace("shared_9", "شركة النفط").copy(
            kindKey = "shop",
            kindValue = "صيدلية",
            operatorName = "شركة النفط",
            isShared = true,
            source = PlaceSource.USER,
        )
        val restored = LocalPlaceCodec.decode(LocalPlaceCodec.encode(listOf(shared)))

        assertEquals(1, restored.size)
        val place = restored.first()
        assertEquals("صيدلية", place.kindValue)
        assertEquals("shop", place.kindKey)
        assertEquals("شركة النفط", place.operatorName)
        assertTrue("يجب ألا يفقد المحل وسم كونه من القاعدة المشتركة", place.isShared)
        assertEquals(PlaceSource.USER, place.source)
    }

    @Test
    fun codec_keepsCoordinatesAndApproximateFlag() {
        val approximate = samplePlace("user_1", "محل بلا موقع دقيق").copy(
            latitude = 15.35,
            longitude = 44.20,
            coordinatesApproximate = true,
        )
        val restored = LocalPlaceCodec.decode(LocalPlaceCodec.encode(listOf(approximate)))
        val place = restored.single()

        assertEquals(15.35, place.latitude!!, 0.0001)
        assertEquals(44.20, place.longitude!!, 0.0001)
        assertTrue(place.coordinatesApproximate)
    }

    @Test
    fun codec_ignoresBrokenEntriesWithoutLosingTheRest() {
        // نبني النص بأيدينا لنتحكّم في وجود سجل تالف وسط السليمين.
        val body = """
            {"places":[
              {"id":"osm_2","name":"مطعم الشام","categoryId":"stores"},
              {"id":"osm_bad","name":"   ","categoryId":"stores"},
              {"id":"osm_3","name":"مقهى الصفوة","categoryId":"stores"}
            ]}
        """.trimIndent()

        val restored = LocalPlaceCodec.decode(body)

        assertEquals("السجل التالف يُتجاهل ولا يُسقط بقية القائمة", 2, restored.size)
        assertTrue(restored.none { it.id == "osm_bad" })
        assertEquals(listOf("osm_2", "osm_3"), restored.map { it.id })
    }

    @Test
    fun codec_roundTripKeepsAllGoodEntries() {
        val two = listOf(samplePlace("osm_2", "مطعم الشام"), samplePlace("osm_3", "مقهى الصفوة"))
        val restored = LocalPlaceCodec.decode(LocalPlaceCodec.encode(two))
        assertEquals(2, restored.size)
        assertEquals(listOf("مطعم الشام", "مقهى الصفوة"), restored.map { it.name })
    }

    @Test
    fun codec_returnsEmptyListOnCorruptText() {
        assertTrue(LocalPlaceCodec.decode("ليس نصاً صالحاً").isEmpty())
        assertTrue(LocalPlaceCodec.decode("").isEmpty())
    }

    // ------------------------------ أدوات ------------------------------

    private fun samplePlace(id: String, name: String) = Place(
        id = id,
        name = name,
        categoryId = Categories.STORES,
        source = PlaceSource.USER,
    )
}
