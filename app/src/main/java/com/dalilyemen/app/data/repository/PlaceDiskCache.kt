package com.dalilyemen.app.data.repository

import android.content.Context
import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.model.PlaceSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * نسخة مخزّنة على الجهاز تُقلّل طلبات الشبكة وتجعل التطبيق مفيداً
 * عند انقطاع الاتصال.
 *
 * التخزين بسيط ومقصود: ملف واحد لكل مفتاح بحث، مع مدة صلاحية وسقف
 * حجم حتى لا ينمو التخزين بلا نهاية.
 */
class PlaceDiskCache(context: Context) {

    private val directory = File(context.cacheDir, "dalil-cache")

    /** مدة صلاحية النتائج المخزّنة: 12 ساعة. */
    private val ttlMillis = 12L * 60L * 60L * 1000L

    /** سقف عدد ملفات الكاش. */
    private val maxFiles = 40

    /** يقرأ النتائج المخزّنة إن كانت صالحة، وإلا null. */
    fun read(key: String, allowExpired: Boolean = false): List<Place>? {
        val file = fileFor(key)
        if (!file.exists()) return null
        if (!allowExpired && isExpired(file)) return null
        return try {
            val places = decode(file.readText(Charsets.UTF_8))
            places.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /** يكتب النتائج مع طابع وقت. */
    fun write(key: String, places: List<Place>) {
        if (places.isEmpty()) return
        try {
            if (!directory.exists()) directory.mkdirs()
            val json = encode(places)
            fileFor(key).writeText(json, Charsets.UTF_8)
            trimIfNeeded()
        } catch (_: Exception) {
            // فشل الكتابة لا يُعطّل التطبيق: النتائج موجودة في الذاكرة.
        }
    }

    private fun isExpired(file: File): Boolean =
        System.currentTimeMillis() - file.lastModified() > ttlMillis

    /** يُبقي عدد ملفات الكاش ضمن السقف بحذف الأقدم. */
    private fun trimIfNeeded() {
        val files = directory.listFiles() ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxFiles)
            .forEach { it.delete() }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }

    // ------------------------- التحويل من/إلى JSON -------------------------

    private fun encode(places: List<Place>): String {
        val array = JSONArray()
        places.forEach { place ->
            val item = JSONObject()
            item.put("id", place.id)
            item.put("name", place.name)
            item.put("categoryId", place.categoryId)
            place.description?.let { item.put("description", it) }
            place.phone?.let { item.put("phone", it) }
            place.address?.let { item.put("address", it) }
            place.cityId?.let { item.put("cityId", it) }
            place.latitude?.let { item.put("lat", it) }
            place.longitude?.let { item.put("lon", it) }
            place.openingHours?.let { item.put("openingHours", it) }
            place.kindKey?.let { item.put("kindKey", it) }
            place.kindValue?.let { item.put("kindValue", it) }
            place.operatorName?.let { item.put("operator", it) }
            item.put("source", place.source.name)
            if (place.photoUrls.isNotEmpty()) {
                item.put("photos", JSONArray(place.photoUrls))
            }
            array.put(item)
        }
        return JSONObject().apply { put("places", array) }.toString()
    }

    private fun decode(body: String): List<Place> {
        val root = JSONObject(body)
        val array = root.optJSONArray("places") ?: return emptyList()
        val places = ArrayList<Place>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
            val photos = item.optJSONArray("photos")?.let { photos ->
                (0 until photos.length()).mapNotNull { photos.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()

            places += Place(
                id = id,
                name = name,
                description = item.optString("description").takeIf { it.isNotBlank() },
                phone = item.optString("phone").takeIf { it.isNotBlank() },
                address = item.optString("address").takeIf { it.isNotBlank() },
                cityId = item.optString("cityId").takeIf { it.isNotBlank() },
                categoryId = item.optString("categoryId").takeIf { it.isNotBlank() }
                    ?: com.dalilyemen.app.data.remote.OsmSource.DEFAULT_CATEGORY,
                latitude = item.optString("lat").toDoubleOrNull(),
                longitude = item.optString("lon").toDoubleOrNull(),
                openingHours = item.optString("openingHours").takeIf { it.isNotBlank() },
                photoUrls = photos,
                kindKey = item.optString("kindKey").takeIf { it.isNotBlank() },
                kindValue = item.optString("kindValue").takeIf { it.isNotBlank() },
                operatorName = item.optString("operator").takeIf { it.isNotBlank() },
                source = runCatching { PlaceSource.valueOf(item.optString("source")) }
                    .getOrDefault(PlaceSource.OPENSTREETMAP),
            )
        }
        return places
    }
}
