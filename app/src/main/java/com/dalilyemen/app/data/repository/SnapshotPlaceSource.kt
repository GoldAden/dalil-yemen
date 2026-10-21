package com.dalilyemen.app.data.repository

import android.content.Context
import com.dalilyemen.app.data.model.CityGeo
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceSource
import com.dalilyemen.app.data.remote.OsmElement
import com.dalilyemen.app.data.remote.OsmPlaceMapper
import org.json.JSONObject

/**
 * قاعدة الأماكن المضمّنة مع التطبيق.
 *
 * الملف المضمّن مُستخرج من بيانات OpenStreetMap المفتوحة (ترخيص ODbL)
 * لمراكز المحافظات اليمنية، ويُقرأ مرة واحدة عند أول استخدام. فائدة ذلك أن
 * الدليل يعمل مباشرة عند التثبيت ودون إنترنت، ثم تُضاف البيانات الحيّة
 * فوقه عند توفّر الاتصال.
 *
 * لا يُخترع شيء هنا: كل عنصر في الملف له اسم ووسوم فعلية في المصدر.
 */
class SnapshotPlaceSource(private val context: Context) {

    private val lock = Any()

    @Volatile
    private var cache: List<Place>? = null

    @Volatile
    private var index: Map<String, Place>? = null

    private companion object {
        const val ASSET_NAME = "places_snapshot.json"
    }

    /** كل الأماكن المضمّنة، أو قائمة فارغة إن تعذّرت القراءة. */
    fun places(): List<Place> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val loaded = runCatching { load() }.getOrDefault(emptyList())
            index = loaded.associateBy { it.id }
            cache = loaded
            return loaded
        }
    }

    /** مكان واحد بالمعرّف، أو null إن لم يكن ضمن اللقطة. */
    fun byId(id: String): Place? {
        if (index == null) places()
        return index?.get(id)
    }

    /** هل نجح تحميل اللقطة؟ نستخدمها لإظهار تنبيه التغطية للمستخدم. */
    fun isAvailable(): Boolean = places().isNotEmpty()

    private fun load(): List<Place> {
        val body = context.assets.open(ASSET_NAME).use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        }
        val root = JSONObject(body)
        val items = root.optJSONArray("items") ?: return emptyList()
        val result = ArrayList<Place>(items.length())

        for (position in 0 until items.length()) {
            val item = items.optJSONObject(position) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue
            val rawTags = item.optJSONObject("t") ?: continue

            val tags = LinkedHashMap<String, String>()
            val keys = rawTags.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = rawTags.optString(key, "")
                if (value.isNotBlank()) tags[key] = value
            }

            val place = OsmPlaceMapper.toPlace(OsmElement(id, lat, lon, tags)) ?: continue
            result += place.copy(
                source = PlaceSource.OPENSTREETMAP_SNAPSHOT,
                cityId = place.cityId ?: CityGeo.nearestCityId(
                    com.dalilyemen.app.data.model.LatLng(lat, lon)
                ),
            )
        }
        return result
    }
}
