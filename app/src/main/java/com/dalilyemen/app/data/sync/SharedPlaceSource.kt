package com.dalilyemen.app.data.sync

import com.dalilyemen.app.data.model.Place
import com.dalilyemen.app.data.repository.PlaceMemoryStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * القاعدة المشتركة: طبقة قراءة/كتابة فوق واجهة REST للقاعدة.
 *
 * الفكرة: بدل أن يبقى كل ما يضيفه المستخدم على جهازه وحده، يمكن — عند
 * ضبط الإعداد — مشاركته عبر قاعدة واحدة، فيرى بقية المستخدمين المحلات
 * التي أضافوها. الميزة اختيارية تماماً: إن لم تُضبط الإعدادات تبقى
 * معطّلة بهدوء ويعمل التطبيق محلياً كما هو.
 *
 * كل ما يُقرأ يمرّ بتحقّق صارم في [SharedPlaceMapper]، وكل ما يُكتب يُرسل
 * كسجل بحالة «pending» حتى يراجعه صاحب القاعدة قبل أن يظهر للعموم.
 */
class SharedPlaceSource(
    private val config: SyncConfig = SyncConfigProvider.current,
) {

    private val memory = PlaceMemoryStore()

    /** هل الإعداد مكتمل؟ إن لا، لا نلمس الشبكة إطلاقاً. */
    val isEnabled: Boolean get() = config.isConfigured

    /** آخر ما جلبه المستخدم من القاعدة المشتركة (للبحث المحلي السريع). */
    fun cached(): List<Place> = memory.all()

    /**
     * يجلب أحدث السجلّات المعتمدة من القاعدة المشتركة.
     *
     * @return قائمة الأماكن، أو قائمة فارغة عند أي فشل — لا نرفع استثناء
     *         لأن ميزة إضافية كهذه يجب ألا تكسر تجربة الاستخدام الأساسية.
     */
    fun fetch(limit: Int = 500): List<Place> {
        if (!isEnabled) return emptyList()
        val url = buildString {
            append(config.baseUrl).append("/rest/v1/places")
            append("?select=*&status=eq.approved")
            append("&order=updated_at.desc&limit=").append(limit.coerceIn(1, 2000))
        }
        val body = runCatching { SyncHttp.get(url, authHeaders()) }.getOrNull()
            ?: return emptyList()
        val places = SharedPlaceMapper.toPlaces(body)
        if (places.isNotEmpty()) memory.putAll(places)
        return places
    }

    /** بحث محلي داخل ما جُلب من القاعدة المشتركة. */
    fun search(query: String, cityId: String? = null, categoryId: String? = null): List<Place> =
        memory.searchInMemory(query, cityId, categoryId)

    /**
     * ينشر محلأ أضافه المستخدم في القاعدة المشتركة.
     *
     * لا نُنشر بلا إعداد مكتمل، ولا نُنشر إحداثيات خارجة عن نطاق اليمن.
     *
     * @return true إن نجحت الكتابة فعلاً كما أكدّها الخادم.
     */
    fun publish(place: Place): Boolean {
        if (!isEnabled) return false
        if (place.name.isBlank()) return false
        if (place.latitude != null && place.longitude != null) {
            if (SharedPlaceMapper.validLatLng(place) == null) return false
        }

        val row = JSONObject().apply {
            put("name", place.name.trim())
            put("category_id", place.categoryId)
            place.cityId?.let { put("city_id", it) }
            place.phone?.let { put("phone", it) }
            place.address?.let { put("address", it) }
            place.openingHours?.let { put("opening_hours", it) }
            place.description?.let { put("notes", it) }
            place.latitude?.let { put("latitude", it) }
            place.longitude?.let { put("longitude", it) }
            place.kindKey?.let { put("kind_key", it) }
            place.kindValue?.let { put("kind_value", it) }
            // يراجعها صاحب القاعدة قبل ظهورها للجميع.
            put("status", "pending")
        }

        val url = "${config.baseUrl}/rest/v1/places"
        val headers = authHeaders() + mapOf("Prefer" to "return=representation")
        val sent = runCatching { SyncHttp.post(url, headers, JSONArray().put(row).toString()) }
        return sent.isSuccess
    }

    private fun authHeaders(): Map<String, String> = mapOf(
        "apikey" to config.apiKey,
        "Authorization" to "Bearer ${config.apiKey}",
        "Content-Type" to "application/json",
    )
}
