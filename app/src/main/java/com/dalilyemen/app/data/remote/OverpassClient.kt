package com.dalilyemen.app.data.remote

import com.dalilyemen.app.data.model.LatLng
import java.util.Locale

/**
 * عميل خدمة الاستعلام الجغرافي (Overpass).
 *
 * الخدمة العامة مزدحمة أحياناً وتُعيد أخطاء مؤقتة، لذلك نجرّب عدة خوادم
 * بالتتابع ونُعيد المحاولة على الخطأ المؤقت قبل أن نُعلن الفشل
 * لواجهة المستخدم. لا تُختلق نتائج عند الفشل: نُعيد خطأً يشرح السبب.
 */
object OverpassClient {

    /** خوادم خدمة الاستعلام، مرتّبة من الأكثر استقراراً إلى البديل. */
    private val mirrors: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.osm.ch/api/interpreter",
        "https://overpass.osm.jp/api/interpreter",
    )

    @Volatile
    private var preferredIndex: Int = 0

    /** أقصى عدد عناصر نطلبها في الاستجابة الواحدة. */
    private const val MAX_ELEMENTS = 220

    private const val TIMEOUT_SECONDS = 40

    /**
     * ينفّذ استعلاماً حول نقطة جغرافية.
     *
     * @param origin نقطة المركز.
     * @param radiusKm نصف قطر البحث بالكيلومترات.
     * @param filters تصفية الأوسمة المطلوبة.
     * @param nameQuery نص لبحث الأسماء، أو null للاكتفاء بالتصنيف.
     */
    fun queryAround(
        origin: LatLng,
        radiusKm: Double,
        filters: List<TagFilter>,
        nameQuery: String? = null,
    ): List<OsmElement> {
        val radiusMeters = (radiusKm * 1000).toInt().coerceIn(500, 50_000)
        val point = String.format(Locale.US, "%.5f,%.5f", origin.latitude, origin.longitude)
        return runQuery { body ->
            body.append("(")
            if (!nameQuery.isNullOrBlank()) {
                body.append(nameSelector(nameQuery))
                body.append("(around:").append(radiusMeters).append(',').append(point).append(");")
            }
            filters.forEach { filter ->
                body.append(filter.expression())
                body.append("(around:").append(radiusMeters).append(',').append(point).append(");")
            }
            body.append(");")
            body.append("out center ").append(MAX_ELEMENTS).append(';')
        }
    }

    /**
     * ينفّذ استعلاماً داخل مستطيل جغرافي (يُستخدم لبحث الأسماء في البلد كله).
     *
     * @param south,west,north,east حدود المستطيل.
     */
    fun queryBbox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        filters: List<TagFilter>,
        nameQuery: String? = null,
    ): List<OsmElement> {
        val box = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", south, west, north, east)
        return runQuery { body ->
            body.append("(")
            if (!nameQuery.isNullOrBlank()) {
                body.append(nameSelector(nameQuery)).append('(').append(box).append(");")
            }
            filters.forEach { filter ->
                body.append(filter.expression()).append('(').append(box).append(");")
            }
            body.append(");")
            body.append("out center ").append(MAX_ELEMENTS).append(';')
        }
    }

    /**
     * ينفّذ استعلاماً واحداً على خوادم الخدمة بالترتيب، مع إعادة محاولة
     * واحدة لكل خادم عند الأخطاء المؤقتة.
     */
    private fun runQuery(buildQuery: (StringBuilder) -> Unit): List<OsmElement> {
        val queryBody = StringBuilder().also(buildQuery).toString()
        val query = "[out:json][timeout:$TIMEOUT_SECONDS];" + queryBody

        val start = preferredIndex
        var lastError: Exception? = null

        for (step in mirrors.indices) {
            val index = (start + step) % mirrors.size
            val endpoint = mirrors[index]
            for (attempt in 0..1) {
                try {
                    val response = OsmHttp.postForm(endpoint, mapOf("data" to query))
                    val elements = OsmParser.parseOverpassJson(response)
                    preferredIndex = index
                    return elements
                } catch (e: Exception) {
                    lastError = e
                    // 4xx يعني أن الطلب نفسه غير مقبول: لا فائدة من الإعادة.
                    val message = e.message.orEmpty()
                    if (message.contains("400") || message.contains("403")) break
                }
            }
        }

        throw OsmNetworkException(
            "خدمة الخرائط غير متاحة حالياً",
            lastError,
        )
    }

    /**
     * يبني شرط البحث بالاسم.
     *
     * نشتق صيغاً قريبة من كتابة المستخدم (همزات وألف مقصورة وتاء مربوطة)
     * لأن أسماء OpenStreetMap لا تُكتب دائماً بنفس الشكل، ثم نطلب مطابقة
     * غير حساسة لحالة الأحرف.
     */
    private fun nameSelector(rawQuery: String): String {
        val variants = nameVariants(rawQuery)
        val pattern = variants.joinToString("|") { escapeRegex(it) }
        return "nwr[\"name\"~\"" + escapeQuoted(pattern) + "\",i]"
    }

    /** صيغ الكتابة المحتملة لنص البحث العربي. */
    fun nameVariants(rawQuery: String): List<String> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return emptyList()

        val variants = linkedSetOf(trimmed)
        variants += trimmed
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه')
        variants += trimmed
            .replace('ه', 'ة')
        variants += trimmed
            .replace('ا', 'أ')

        return variants.filter { it.isNotBlank() }.take(4)
    }

    private fun escapeRegex(input: String): String {
        val meta = "\\^$.|?*+()[]{}"
        val out = StringBuilder(input.length + 8)
        for (ch in input) {
            if (meta.indexOf(ch) >= 0) out.append('\\')
            out.append(ch)
        }
        return out.toString()
    }

    private fun escapeQuoted(input: String): String =
        input.replace("\\", "\\\\").replace("\"", "\\\"")
}
