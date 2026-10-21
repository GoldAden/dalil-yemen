package com.dalilyemen.app.data.remote

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/** خطأ في الاتصال بمصدر البيانات، مع رسالة عربية صالحة للعرض. */
class OsmNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * عميل HTTP بسيط مبني على أدوات النظام.
 *
 * لا نضيف أي مكتبة خارجية: الطلبات قليلة وصيغتها ثابتة (نصوص JSON/XML)،
 * فيكفي عميل صغير مع مهلات واضحة وترويسة تعريف بالتطبيق كما تطلب
 * سياسات الاستخدام في OpenStreetMap.
 */
object OsmHttp {

    const val USER_AGENT: String =
        "DalilYemen/1.2 (Android; contact: https://github.com/GoldAden/dalil-yemen)"

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 45_000

    /** طلب GET يعيد جسم الاستجابة نصاً. */
    fun get(url: String): String = getStream(url) { input ->
        input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * طلب GET يمرّر جسم الاستجابة كتدفّق دون تجميعه في الذاكرة.
     *
     * مفيد للردود الكبيرة (مثل ملفات المناطق من الخرائط) حيث نحتاج
     * قراءة العنصر ورميه أولاً بأول بدل تحميل الملف كله.
     */
    fun <T> getStream(url: String, consume: (InputStream) -> T): T {
        val connection = open(url, "GET")
        return try {
            withBody(connection, url) { stream ->
                val decoded = if (connection.contentEncoding?.contains("gzip", true) == true) {
                    GZIPInputStream(stream)
                } else {
                    stream
                }
                consume(decoded)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** طلب POST بجسم مُرمَّز كالنماذج (وهو ما تفهمه خدمة الاستعلام). */
    fun postForm(url: String, fields: Map<String, String>): String {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        val connection = open(url, "POST")
        return try {
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8",
            )
            connection.outputStream.use { out -> out.write(body.toByteArray(Charsets.UTF_8)) }
            withBody(connection, url) { stream ->
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw OsmNetworkException("تعذّر الاتصال بالخادم", e)
        }
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json, application/xml, text/xml, */*")
        connection.setRequestProperty("Accept-Encoding", "gzip")
        return connection
    }

    private fun <T> withBody(
        connection: HttpURLConnection,
        url: String,
        consume: (InputStream) -> T,
    ): T {
        val status = try {
            connection.responseCode
        } catch (e: Exception) {
            throw OsmNetworkException("لا يوجد اتصال بالإنترنت", e)
        }

        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            ?: throw OsmNetworkException("رد غير متوقّع من الخادم ($status)")

        if (status !in 200..299) {
            stream.close()
            val hint = if (status == 429 || status == 504) "الخادم مزدحم" else "خطأ $status"
            throw OsmNetworkException("$hint من مصدر البيانات (${shortHost(url)})")
        }
        return stream.use(consume)
    }

    private fun shortHost(url: String): String =
        runCatching { URL(url).host }.getOrDefault(url)
}
