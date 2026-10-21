package com.dalilyemen.app.data.remote

import com.dalilyemen.app.data.model.LatLng
import org.json.JSONObject
import java.util.Locale

/**
 * مسار بديل مباشر إلى خدمة خريطة OpenStreetMap الرسمية.
 *
 * عندما تكون خدمة الاستعلام مزدحمة، نجلب منطقة صغيرة حول المدينة من
 * الخدمة الرسمية نفسها ثم نُصفّي النتائج محلياً. المنطقة محدودة المساحة
 * بحسب سياسة الخدمة، لذلك النتائج أقل شمولاً من الاستعلام العادي
 * ونُعلن ذلك للمستخدم بدل التظاهر بتغطية كاملة.
 */
object OsmApiClient {

    private const val MAP_ENDPOINT = "https://api.openstreetmap.org/api/0.6/map"
    private const val ELEMENT_ENDPOINT = "https://api.openstreetmap.org/api/0.6"
    private const val NOMINATIM_ENDPOINT = "https://nominatim.openstreetmap.org/search"

    /**
     * أقصى نصف فرق في الإحداثيات للمنطقة الواحدة.
     * السبب: سياسة الخدمة ترفض المساحات الكبيرة جداً.
     */
    const val MAX_BBOX_HALF_DEGREES: Double = 0.085

    /** أقصى نصف فرق نستخدمه فعلياً حتى لا نُثقل الاتصال. */
    private const val REQUEST_HALF_DEGREES: Double = 0.06

    /** هل نحن قادرون على تغطية هذا النطاق بمسار المنطقة المحدودة؟ */
    fun supportsRadius(radiusKm: Double): Boolean =
        radiusKm <= MAX_BBOX_HALF_DEGREES * 111.0

    /**
     * يجلب عناصر منطقة صغيرة حول نقطة ويُمرّرها للمُعالج دون تحميل الملف كاملاً.
     */
    fun <T> fetchArea(origin: LatLng, consume: (List<OsmElement>) -> T): T {
        val half = REQUEST_HALF_DEGREES
        val south = (origin.latitude - half).coerceAtLeast(11.5)
        val north = (origin.latitude + half).coerceAtMost(19.5)
        val west = (origin.longitude - half).coerceAtLeast(41.5)
        val east = (origin.longitude + half).coerceAtMost(55.5)
        val bbox = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", west, south, east, north)
        val url = "$MAP_ENDPOINT?bbox=$bbox"

        return OsmHttp.getStream(url) { stream ->
            consume(OsmParser.parseOsmXmlStream(stream))
        }
    }

    /**
     * بحث بالاسم في خدمة التسمية الجغرافية الرسمية داخل اليمن.
     *
     * يُستخدم كمصدر ثانٍ للبحث النصي، خصوصاً عندما يكون الاستعلام
     * الجغرافي المباشر مشغولاً أو غير متاح.
     */
    fun searchByName(query: String, cityCenter: LatLng?, limit: Int = 40): List<OsmElement> {
        val builder = StringBuilder(NOMINATIM_ENDPOINT)
        builder.append("?q=").append(urlEncode(query))
        builder.append("&format=jsonv2&countrycodes=ye&limit=").append(limit)
        builder.append("&extratags=1&addressdetails=1&namedetails=1")
        if (cityCenter != null) {
            val viewBox = String.format(
                Locale.US,
                "%.4f,%.4f,%.4f,%.4f",
                cityCenter.longitude - 0.25,
                cityCenter.latitude + 0.25,
                cityCenter.longitude + 0.25,
                cityCenter.latitude - 0.25,
            )
            builder.append("&viewbox=").append(viewBox).append("&bounded=0")
        }

        val body = OsmHttp.get(builder.toString())
        return parseNominatim(body)
    }

    /**
     * يجلب عنصراً واحداً بمعرّفه من الخدمة الرسمية.
     *
     * المعرّف مخزَّن عندنا بالشكل «النوع/الرقم» (مثل node/12345)، وهو
     * نفس الشكل الذي تعيده خدمة التسمية الجغرافية، فنستخدمه للتفاصيل.
     */
    fun lookupById(id: String): OsmElement? {
        val parts = id.split('/')
        if (parts.size != 2) return null
        val type = parts[0].lowercase(Locale.US)
        val number = parts[1].toLongOrNull() ?: return null
        if (type != "node" && type != "way" && type != "relation") return null

        val body = OsmHttp.get("$ELEMENT_ENDPOINT/$type/$number")
        return OsmParser.parseOsmXml(body).firstOrNull()
    }

    /** يحوّل نتائج البحث النصي إلى عناصر موحّدة داخل التطبيق. */
    internal fun parseNominatim(body: String): List<OsmElement> {
        if (body.isBlank()) return emptyList()
        val array = try {
            org.json.JSONArray(body)
        } catch (_: Exception) {
            return emptyList()
        }

        val result = ArrayList<OsmElement>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val latitude = item.optString("lat").toDoubleOrNull() ?: continue
            val longitude = item.optString("lon").toDoubleOrNull() ?: continue

            val tags = LinkedHashMap<String, String>()
            item.optString("name").takeIf { it.isNotBlank() }?.let { tags["name"] = it }
            namedetails(item)?.let { tags.putAll(it) }

            val type = item.optString("osm_type")
            val osmId = item.optString("osm_id")
            val kind = item.optString("type")
            val category = item.optString("category")
            if (kind.isNotBlank()) {
                when (category) {
                    "amenity" -> tags["amenity"] = kind
                    "shop" -> tags["shop"] = kind
                    "craft" -> tags["craft"] = kind
                    "healthcare" -> tags["healthcare"] = kind
                    "leisure" -> tags["leisure"] = kind
                    "tourism" -> tags["tourism"] = kind
                    "office" -> tags["office"] = kind
                    else -> Unit
                }
            }
            item.optJSONObject("extratags")?.let { extra ->
                val keys = extra.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = extra.optString(key, "")
                    if (value.isNotBlank() && !tags.containsKey(key)) tags[key] = value
                }
            }
            item.optJSONObject("address")?.let { address ->
                val keys = address.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = address.optString(key, "")
                    if (value.isNotBlank()) tags["addr:$key"] = value
                }
            }
            if (tags.isEmpty()) continue

            result += OsmElement(
                id = "${type.ifBlank { "node" }}/${osmId.ifBlank { "$latitude,$longitude" }}",
                latitude = latitude,
                longitude = longitude,
                tags = tags,
            )
        }
        return result
    }

    private fun namedetails(item: JSONObject): Map<String, String>? {
        val details = item.optJSONObject("namedetails") ?: return null
        val map = HashMap<String, String>()
        val keys = details.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = details.optString(key, "")
            if (value.isNotBlank()) map[key] = value
        }
        return map.ifEmpty { null }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
