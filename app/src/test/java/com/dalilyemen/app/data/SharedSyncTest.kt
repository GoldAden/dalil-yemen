package com.dalilyemen.app.data

import com.dalilyemen.app.data.model.Categories
import com.dalilyemen.app.data.model.PlaceSource
import com.dalilyemen.app.data.sync.SharedPlaceMapper
import com.dalilyemen.app.data.sync.SyncConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات القاعدة المشتركة: التحقّق من السجلّات القادمة من الشبكة،
 * وسلوك الإعداد عند غياب القيم.
 *
 * الهدف: ألا يدخل الدليل أي سجل ناقص أو خارج نطاق اليمن، وأن تبقى
 * الميزة معطّلة بهدوء إن لم يُضبط إعدادها.
 */
class SharedSyncTest {

    @Test
    fun config_isDisabledWhenValuesMissing() {
        val config = SyncConfig.from(null, "")
        assertFalse(config.isConfigured)
        assertFalse(SyncConfig.DISABLED.isConfigured)
    }

    @Test
    fun config_isEnabledWhenBothValuesPresent_andCleansTrailingSlash() {
        val config = SyncConfig.from(" https://example.test/ ", " key ")
        assertTrue(config.isConfigured)
        assertEquals("https://example.test", config.baseUrl)
        assertEquals("key", config.apiKey)
    }

    @Test
    fun mapper_acceptsValidApprovedRecord() {
        val row = JSONObject()
            .put("id", 7)
            .put("name", "مطعم الشام")
            .put("category_id", Categories.RESTAURANTS)
            .put("city_id", "sanaa")
            .put("latitude", 15.35)
            .put("longitude", 44.20)
            .put("status", "approved")

        val place = SharedPlaceMapper.toPlace(row)
        assertTrue(place != null)
        assertEquals("مطعم الشام", place!!.name)
        assertEquals(Categories.RESTAURANTS, place.categoryId)
        assertEquals(PlaceSource.USER, place.source)
        assertTrue(place.isShared)
        assertFalse(place.coordinatesApproximate)
    }

    @Test
    fun mapper_rejectsRecordWithoutName() {
        val row = JSONObject().put("id", 1).put("name", "   ").put("status", "approved")
        assertNull(SharedPlaceMapper.toPlace(row))
    }

    @Test
    fun mapper_rejectsUnapprovedRecordByDefault() {
        val row = JSONObject().put("id", 2).put("name", "محل قيد المراجعة").put("status", "pending")
        assertNull(SharedPlaceMapper.toPlace(row))
        assertTrue(SharedPlaceMapper.toPlace(row, requireApproved = false) != null)
    }

    @Test
    fun mapper_dropsCoordinatesOutsideYemen_butKeepsTheRecord() {
        val row = JSONObject()
            .put("id", 3)
            .put("name", "محل بإحداثيات خاطئة")
            .put("latitude", 48.85)
            .put("longitude", 2.35)
            .put("status", "approved")

        val place = SharedPlaceMapper.toPlace(row)
        assertTrue(place != null)
        assertNull(place!!.latitude)
        assertNull(place.longitude)
        assertTrue(place.coordinatesApproximate)
        assertNull(SharedPlaceMapper.validLatLng(place))
    }

    @Test
    fun mapper_fallsBackToDefaultCategory_forUnknownCategory() {
        val row = JSONObject()
            .put("name", "محل بتصنيف غير معروف")
            .put("category_id", "something_unknown")
            .put("status", "approved")
        val place = SharedPlaceMapper.toPlace(row)
        assertEquals(Categories.STORES, place!!.categoryId)
    }

    @Test
    fun mapper_parsesListAndSkipsBadEntries() {
        val body = """
            [
              {"id":1,"name":"محل جيد","status":"approved","latitude":15.4,"longitude":44.2},
              {"id":2,"name":"","status":"approved"},
              {"id":3,"name":"محل معلّق","status":"pending"}
            ]
        """.trimIndent()
        val places = SharedPlaceMapper.toPlaces(body)
        assertEquals(1, places.size)
        assertEquals("محل جيد", places.first().name)
    }

    @Test
    fun mapper_returnsEmptyListOnBrokenJson() {
        assertTrue(SharedPlaceMapper.toPlaces("not json at all").isEmpty())
        assertTrue(SharedPlaceMapper.toPlaces("").isEmpty())
    }
}
