package com.dalilyemen.app.data.remote

import org.json.JSONObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParserFactory
import java.io.InputStream

/** عنصر خام قادم من مصدر الخرائط: نقطة أو مركز مساحة. */
data class OsmElement(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val tags: Map<String, String>,
)

/**
 * قراءة ردود مصدر الخرائط بالصيغتين المدعومتين:
 *  - JSON من خدمة الاستعلام.
 *  - XML من خدمة بيانات الخريطة الأساسية.
 *
 * لا نستنتج أي بيانات غير موجودة في الرد: ما لا يحمل إحداثيات واضحة
 * يُهمَل بهدوء، وما لا اسم له لا نعرضه كاسم مختلق.
 */
object OsmParser {

    /** أقل عدد أوسمة مقبول لاعتبار العنصر مكاناً معروضاً. */
    private const val MIN_TAGS = 1

    // ----------------------------- JSON -----------------------------

    fun parseOverpassJson(body: String): List<OsmElement> {
        if (body.isBlank()) return emptyList()
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return emptyList()
        }
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val result = ArrayList<OsmElement>(elements.length())

        for (index in 0 until elements.length()) {
            val item = elements.optJSONObject(index) ?: continue
            val kind = item.optString("type")

            val tags = item.optJSONObject("tags")?.let { objectToMap(it) } ?: emptyMap()
            if (tags.size < MIN_TAGS) continue

            val latitude: Double
            val longitude: Double
            if (kind == "node") {
                if (!item.has("lat") || !item.has("lon")) continue
                latitude = item.optDouble("lat", Double.NaN)
                longitude = item.optDouble("lon", Double.NaN)
            } else {
                val center = item.optJSONObject("center") ?: continue
                latitude = center.optDouble("lat", Double.NaN)
                longitude = center.optDouble("lon", Double.NaN)
            }
            if (latitude.isNaN() || longitude.isNaN()) continue

            result += OsmElement(
                id = "$kind/${item.optLong("id")}",
                latitude = latitude,
                longitude = longitude,
                tags = tags,
            )
        }
        return result
    }

    private fun objectToMap(json: JSONObject): Map<String, String> {
        val map = HashMap<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optString(key, "")
            if (value.isNotBlank()) map[key] = value
        }
        return map
    }

    // ------------------------------ XML ------------------------------ 

    /**
     * يقرأ ملف XML من خدمة بيانات الخريطة على شكل تدفّق.
     *
     * نُبقي إحداثيات النقاط في الذاكرة فقط لربطها بالمساحات، ثم نرمي
     * كل ما لا يحمل وسماً مفيداً — بهذا نتعامل مع ملفات كبيرة بأمان.
     */
    fun parseOsmXmlStream(stream: InputStream): List<OsmElement> {
        val handler = MapXmlHandler()
        try {
            // قارئ XML القياسي من منصّة جافا — متوفّر على أندرويد دون أي مكتبة إضافية.
            val reader = SAXParserFactory.newInstance().newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.parse(InputSource(stream))
        } catch (_: Exception) {
            // ملف مقطوع أو غير مكتمل: نعرض ما نجمع نجمع حتى اللحظة
            return handler.collected()
        }
        return handler.collected()
    }

    /** قراءة عنصر أو كائن خريطة واحد من نص يحمل صيغة الخريطة. */
    fun parseOsmXml(body: String): List<OsmElement> =
        parseOsmXmlStream(body.toByteArray(Charsets.UTF_8).inputStream())

    /**
     * مُعالج XML يحوّل نقاط الخريطة ومساحاتها إلى عناصر قابلة للعرض.
     */
    internal class MapXmlHandler : DefaultHandler() {

        private val nodeCoordinates = HashMap<Long, DoubleArray>()
        private val nodeTags = HashMap<Long, Map<String, String>>()
        private val wayRefs = HashMap<Long, MutableList<Long>>()
        private val wayTags = HashMap<Long, Map<String, String>>()

        private var currentNodeId: Long = -1L
        private var currentWayId: Long = -1L
        private var currentTags: HashMap<String, String>? = null
        private var inRelation = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName ?: return
            val attrs = attributes ?: return
            when (name) {
                "node" -> {
                    val id = attrs.longAttr("id") ?: return
                    currentNodeId = id
                    currentTags = HashMap()
                    val lat = attrs.doubleAttr("lat")
                    val lon = attrs.doubleAttr("lon")
                    if (lat != null && lon != null) nodeCoordinates[id] = doubleArrayOf(lat, lon)
                }

                "way" -> {
                    val id = attrs.longAttr("id") ?: return
                    currentWayId = id
                    currentTags = HashMap()
                    wayRefs.getOrPut(id) { ArrayList() }
                }

                "relation" -> {
                    inRelation = true
                    currentTags = null
                }

                "nd" -> {
                    val ref = attrs.longAttr("ref") ?: return
                    if (currentWayId > 0) wayRefs.getOrPut(currentWayId) { ArrayList() } += ref
                }

                "tag" -> {
                    val key = attrs.value("k") ?: return
                    val value = attrs.value("v") ?: return
                    currentTags?.put(key, value)
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = localName?.takeIf { it.isNotEmpty() } ?: qName ?: return
            when (name) {
                "node" -> {
                    val tags = currentTags
                    if (tags != null && tags.isNotEmpty() && currentNodeId > 0) {
                        nodeTags[currentNodeId] = tags
                    }
                    currentTags = null
                    currentNodeId = -1L
                }

                "way" -> {
                    val tags = currentTags
                    if (tags != null && tags.isNotEmpty() && currentWayId > 0) {
                        wayTags[currentWayId] = tags
                    }
                    currentTags = null
                    currentWayId = -1L
                }

                "relation" -> {
                    inRelation = false
                    currentTags = null
                }
            }
        }

        /** يجمع النقاط المسمّاة ومراكز المساحات المسمّاة في قائمة واحدة. */
        fun collected(): List<OsmElement> {
            val result = ArrayList<OsmElement>(nodeTags.size + wayTags.size)

            for ((id, tags) in nodeTags) {
                val coords = nodeCoordinates[id] ?: continue
                result += OsmElement("node/$id", coords[0], coords[1], tags)
            }

            for ((id, tags) in wayTags) {
                val refs = wayRefs[id].orEmpty()
                val points = refs.mapNotNull { nodeCoordinates[it] }
                if (points.isEmpty()) continue
                val lat = points.sumOf { it[0] } / points.size
                val lon = points.sumOf { it[1] } / points.size
                result += OsmElement("way/$id", lat, lon, tags)
            }

            return result
        }
    }
}

private fun Attributes.value(name: String): String? {
    for (index in 0 until length) {
        if (getLocalName(index) == name || getQName(index) == name) return getValue(index)
    }
    return null
}

private fun Attributes.longAttr(name: String): Long? = value(name)?.toLongOrNull()

private fun Attributes.doubleAttr(name: String): Double? = value(name)?.toDoubleOrNull()
