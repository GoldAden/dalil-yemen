package com.dalilyemen.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.dalilyemen.app.data.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * الحصول على موقع المستخدم بعد موافقته الصريحة.
 *
 * يستخدم [LocationManager] المدمج في النظام، فلا حاجة إلى مكتبات خارجية.
 * إذا رفض المستخدم الإذن أو كانت خدمة الموقع مغلقة، نعيد null بهدوء
 * ونترك الواجهة تعرض الحالة المناسبة دون تعطّل التطبيق.
 */
class LocationProvider(private val context: Context) {

    /** هل منح المستخدم إذن الموقع (تقريبي أو دقيق)؟ */
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** هل خدمة الموقع مفعّلة في إعدادات الجهاز؟ */
    fun isLocationServiceEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * آخر موقع معروف من مزوّدي GPS والشبكة.
     *
     * لا نطلب تحديثات مستمرة، وهذا مناسب لعرض "الأماكن القريبة" فقط،
     * ويوفّر طاقة البطارية.
     */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLatLng(): LatLng? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        var best: Location? = null
        for (provider in providers) {
            val candidate = try {
                if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
            } catch (_: Exception) {
                null
            }
            if (candidate != null) {
                if (best == null || candidate.time > best.time) best = candidate
            }
        }

        best?.let { LatLng(it.latitude, it.longitude) }
    }
}
