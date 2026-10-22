package com.dalilyemen.app.data

import com.dalilyemen.app.data.model.Categories
import com.dalilyemen.app.data.model.LatLng
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceDeduper
import com.dalilyemen.app.data.model.PlaceSource
import com.dalilyemen.app.data.repository.PlaceMemoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق الأماكن: المسافات، دمح التكرار، الذاكرة، والبحث.
 */
class PlaceLogicTest {

    // ------------------------------ المسافات ------------------------------

    @Test
    fun distanceKm_betweenSamePoint_isZero() {
        val point = LatLng(15.3539, 44.2059)
        assertEquals(0.0, point.distanceKmTo(point), 0.0001)
    }

    @Test
    fun distanceKm_sanaaToAden_isAboutThreeHundredKm() {
        val sanaa = LatLng(15.3539, 44.2059)
        val aden = LatLng(12.7896, 45.0285)
        val distance = sanaa.distanceKmTo(aden)
        assertTrue("المسافة المتوقّعة بين 250 و350 كم لكن كانت $distance", distance in 250.0..350.0)
    }

    // ------------------------------ دمح التكرار ------------------------------

    @Test
    fun merge_removesDuplicateSameNameNearby() {
        val first = place("osm_1", "صيدلية النور", 15.3500, 44.2000, PlaceSource.OPENSTREETMAP_SNAPSHOT)
        val second = place("osm_2", "صيدلية النور", 15.3501, 44.2001, PlaceSource.OPENSTREETMAP)
        val merged = PlaceDeduper.merge(listOf(first, second))
        assertEquals(1, merged.size)
        // الأغنى/الأحدث يفوز: الحيّ يتقدّم على اللقطة.
        assertEquals(PlaceSource.OPENSTREETMAP, merged.first().source)
    }

    @Test
    fun merge_keepsDifferentNamesAtSameSpot() {
        val first = place("a", "صيدلية النور", 15.3500, 44.2000, PlaceSource.OPENSTREETMAP)
        val second = place("b", "مخبز الصفا", 15.3500, 44.2000, PlaceSource.OPENSTREETMAP)
        assertEquals(2, PlaceDeduper.merge(listOf(first, second)).size)
    }

    @Test
    fun merge_keepsSameNameFarApart() {
        val first = place("a", "مطعم الشام", 15.3500, 44.2000, PlaceSource.OPENSTREETMAP)
        val second = place("b", "مطعم الشام", 12.7900, 45.0300, PlaceSource.OPENSTREETMAP)
        assertEquals(2, PlaceDeduper.merge(listOf(first, second)).size)
    }

    @Test
    fun merge_prefersRicherRecordOnSameSource() {
        val poor = place("a", "مكتب النور", 15.3500, 44.2000, PlaceSource.OPENSTREETMAP)
        val rich = poor.copy(phone = "777000111", address = "شارع الزبيري")
        val merged = PlaceDeduper.merge(listOf(poor, rich))
        assertEquals(1, merged.size)
        assertNotNull(merged.first().phone)
        assertEquals("شارع الزبيري", merged.first().address)
    }

    @Test
    fun merge_preservesOrderOfFirstAppearance() {
        val a = place("a", "أ", 15.0, 44.0, PlaceSource.OPENSTREETMAP)
        val b = place("b", "ب", 15.1, 44.1, PlaceSource.OPENSTREETMAP)
        assertEquals(listOf("a", "b"), PlaceDeduper.merge(listOf(a, b)).map { it.id })
    }

    @Test
    fun isSamePlace_withoutCoordinates_requiresSameCity() {
        val a = place("a", "مستشفى السلام", null, null, PlaceSource.USER).copy(cityId = "sanaa")
        val b = place("b", "مستشفى السلام", null, null, PlaceSource.USER).copy(cityId = "aden")
        assertFalse(PlaceDeduper.isSamePlace(a, b))
        assertTrue(PlaceDeduper.isSamePlace(a, a.copy(id = "c")))
    }

    // ------------------------------ الذاكرة ------------------------------

    @Test
    fun memorySearch_findsByNormalizedName() {
        val store = PlaceMemoryStore()
        store.put(place("a", "صيدلية النور", 15.35, 44.20, PlaceSource.OPENSTREETMAP))
        val found = store.searchInMemory("صيدليه")
        assertEquals(1, found.size)
        assertEquals("a", found.first().id)
    }

    @Test
    fun memorySearch_respectsCategoryAndCityFilters() {
        val store = PlaceMemoryStore()
        store.put(
            place("a", "مطعم الشام", 15.35, 44.20, PlaceSource.OPENSTREETMAP)
                .copy(categoryId = Categories.RESTAURANTS, cityId = "sanaa")
        )
        store.put(
            place("b", "مطعم الشام", 12.79, 45.03, PlaceSource.OPENSTREETMAP)
                .copy(categoryId = Categories.RESTAURANTS, cityId = "aden")
        )
        assertEquals(2, store.searchInMemory("مطعم").size)
        assertEquals(1, store.searchInMemory("مطعم", cityId = "aden").size)
        assertEquals(0, store.searchInMemory("مطعم", categoryId = Categories.FUEL).size)
    }

    @Test
    fun memoryAround_sortsByDistance() {
        val store = PlaceMemoryStore()
        val origin = LatLng(15.3539, 44.2059)
        store.put(place("far", "بعيد", 15.50, 44.30, PlaceSource.OPENSTREETMAP))
        store.put(place("near", "قريب", 15.3540, 44.2060, PlaceSource.OPENSTREETMAP))
        val around = store.around(origin, radiusKm = 50.0)
        assertEquals(listOf("near", "far"), around.map { it.id })
    }

    @Test
    fun memoryAround_excludesPlacesOutsideRadius() {
        val store = PlaceMemoryStore()
        store.put(place("aden", "مكان بعيد", 12.79, 45.03, PlaceSource.OPENSTREETMAP))
        assertTrue(store.around(LatLng(15.3539, 44.2059), radiusKm = 20.0).isEmpty())
    }

    @Test
    fun memoryAround_skipsPlacesWithoutCoordinates() {
        val store = PlaceMemoryStore()
        store.put(place("nocoord", "بلا موقع", null, null, PlaceSource.USER))
        assertTrue(store.around(LatLng(15.35, 44.20), radiusKm = 20.0).isEmpty())
    }

    private fun place(
        id: String,
        name: String,
        latitude: Double?,
        longitude: Double?,
        source: PlaceSource,
    ): Place = Place(
        id = id,
        name = name,
        categoryId = Categories.STORES,
        latitude = latitude,
        longitude = longitude,
        source = source,
    )
}
