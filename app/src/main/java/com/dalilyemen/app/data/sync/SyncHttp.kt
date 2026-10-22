package com.dalilyemen.app.data.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** خطأ في المزامنة مع القاعدة المشتركة، برسالة عربية صالحة للعرض. */
class SyncException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * عميل HTTP صغير للقاعدة المشتركة.
 *
 * مبني على أدوات النظام نفسها، بلا أي مكتبة إضافية، ويدعم ما نحتاجه فقط:
 * طلب قراءة (GET) وطلب كتابة (POST) مع تراويس المصادقة. المهلات واضحة
 * والهدف ألا ينتظر المستخدم طويلاً إن تعذّر الوصول.
 */
internal object SyncHttp {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000

    /** طلب قراءة يعيد جسم الاستجابة نصاً. */
    fun get(url: String, headers: Map<String, String>): String {
        val connection = open(url, "GET", headers)
        return try {
            readBody(connection)
        } finally {
            connection.disconnect()
        }
    }

    /** طلب كتابة بجسم JSON. */
    fun post(url: String, headers: Map<String, String>, body: String): String {
        val connection = open(url, "POST", headers)
        return try {
            connection.doOutput = true
            connection.outputStream.use { out -> out.write(body.toByteArray(Charsets.UTF_8)) }
            readBody(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String, headers: Map<String, String>): HttpURLConnection {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw SyncException("تعذّر الوصول إلى القاعدة المشتركة", e)
        }
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return connection
    }

    private fun readBody(connection: HttpURLConnection): String {
        val status = try {
            connection.responseCode
        } catch (e: Exception) {
            throw SyncException("لا يوجد اتصال بالإنترنت", e)
        }
        if (status in 200..299) {
            return connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                .orEmpty()
        }
        // جسم الخطأ لا يُعرض للمستخدم، لكنه يُستهلك لإغلاق الاتصال بنظافة.
        connection.errorStream?.use { it.readBytes() }
        val hint = when (status) {
            401, 403 -> "تحتاج القاعدة المشتركة إلى إعداد صحيح"
            429 -> "الخادم مزدحم"
            else -> "خطأ $status من القاعدة المشتركة"
        }
        throw SyncException(hint)
    }
}
