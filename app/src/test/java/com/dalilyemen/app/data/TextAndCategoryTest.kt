package com.dalilyemen.app.data

import com.dalilyemen.app.data.model.Categories
import com.dalilyemen.app.data.remote.OsmSource
import com.dalilyemen.app.data.remote.OsmText
import com.dalilyemen.app.data.remote.SearchSynonyms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات معالجة النصوص العربية واستنتاج التصنيف.
 *
 * هذه أهم نقطة جودة في التطبيق: إن فشل توحيد النص العربي فلن يجد
 * المستخدم ما يبحث عنه حتى لو كانت البيانات موجودة.
 */
class TextAndCategoryTest {

    @Test
    fun normalize_removesDiacritics_andUnifiesLetters() {
        assertEquals("صيدليه", OsmText.normalize("صَيْدَلِيَّةٌ"))
        assertEquals("احمد", OsmText.normalize("أحمد"))
        assertEquals("ايمن", OsmText.normalize("إيمن"))
        assertEquals("يحيي", OsmText.normalize("يحيى"))
    }

    @Test
    fun normalize_lowercasesLatin_andCollapsesSpaces() {
        assertEquals("pharmacy center", OsmText.normalize("  PHARMACY   Center "))
    }

    @Test
    fun normalize_handlesEmptyInput() {
        assertEquals("", OsmText.normalize("   "))
    }

    @Test
    fun categoryForQuery_mapsArabicWord() {
        assertEquals(Categories.HEALTH, SearchSynonyms.categoryForQuery("صيدلية"))
        assertEquals(Categories.RESTAURANTS, SearchSynonyms.categoryForQuery("مطعم"))
    }

    @Test
    fun categoryForQuery_mapsEnglishWord() {
        assertEquals(Categories.FUEL, SearchSynonyms.categoryForQuery("fuel"))
    }

    @Test
    fun categoryForQuery_returnsNullForUnknownWord() {
        assertNull(SearchSynonyms.categoryForQuery("كلمة غير معروفة تماما هنا"))
    }

    @Test
    fun categoryForTags_prefersHeathOverGenericShop() {
        val tags = mapOf("shop" to "chemist", "name" to "صيدلية النور")
        assertEquals(Categories.HEALTH, OsmSource.categoryForTags(tags))
    }

    @Test
    fun categoryForTags_fallsBackToStoresForAnyShop() {
        val tags = mapOf("shop" to "clothes")
        assertEquals(Categories.STORES, OsmSource.categoryForTags(tags))
    }

    @Test
    fun categoryForTags_returnsNullWhenNoKnownTag() {
        assertNull(OsmSource.categoryForTags(mapOf("name" to "مكان بلا وسم معروف")))
    }

    @Test
    fun everyCategory_hasAtLeastOneFilter() {
        OsmSource.allCategories.forEach { category ->
            assertTrue(
                "التصنيف $category بلا قواعد مطابقة",
                OsmSource.filtersForCategory(category).isNotEmpty(),
            )
        }
    }
}
