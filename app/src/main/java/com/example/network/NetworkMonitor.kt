package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.diagnostics.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NetworkStatus {
    ONLINE,          // Stable connection
    WEAK_CONNECTION, // High latency, packet loss, or captive/poor connection
    OFFLINE          // No network connection
}

/**
 * Universal real-time network connectivity monitor for CineStream Admin.
 * Detects offline status, flaky connections, and automatically informs the UI
 * to manage offline queues and pending sync states.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(getCurrentNetworkStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            AppLogger.i("NetworkMonitor", "Network available: $network")
            updateStatus()
        }

        override fun onLost(network: Network) {
            AppLogger.w("NetworkMonitor", "Network lost: $network")
            updateStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isPoor = !isValidated || networkCapabilities.linkDownstreamBandwidthKbps in 1..250
            
            AppLogger.d("NetworkMonitor", "Capabilities changed: hasInternet=$hasInternet, isValidated=$isValidated, isPoor=$isPoor")
            
            val newStatus = when {
                !hasInternet -> NetworkStatus.OFFLINE
                isPoor -> NetworkStatus.WEAK_CONNECTION
                else -> NetworkStatus.ONLINE
            }
            scope.launch {
                _status.value = newStatus
                _isOnline.value = (newStatus != NetworkStatus.OFFLINE)
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            updateStatus()
        } catch (e: Exception) {
            AppLogger.e("NetworkMonitor", "Failed to register network callback: ${e.message}", e)
        }
    }

    private fun updateStatus() {
        scope.launch {
            val newStatus = getCurrentNetworkStatus()
            _status.value = newStatus
            _isOnline.value = (newStatus != NetworkStatus.OFFLINE)
        }
    }

    fun isCurrentlyOnline(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun getCurrentNetworkStatus(): NetworkStatus {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus.OFFLINE
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus.OFFLINE
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!hasInternet) return NetworkStatus.OFFLINE
            
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
            if (!isValidated || (downstreamKbps in 1..250)) {
                NetworkStatus.WEAK_CONNECTION
            } else {
                NetworkStatus.ONLINE
            }
        } catch (e: Exception) {
            NetworkStatus.OFFLINE
        }
    }

    companion object {
        @Volatile
        private var instance: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
            }
        }
    }
}
