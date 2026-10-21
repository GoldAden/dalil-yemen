package com.dalilyemen.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/**
 * فحص سريع لتوفّر الإنترنت.
 *
 * الهدف تجربة مستخدم أفضل: إذا كان الجهاز بلا اتصال نعرض البيانات
 * المخزّنة فوراً برسالة واضحة، بدل انتظار مهلة الشبكة بلا فائدة.
 */
class NetworkMonitor(private val context: Context) {

    fun isOnline(): Boolean {
        val manager = context.getSystemService<ConnectivityManager>() ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
