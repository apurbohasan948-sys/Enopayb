package com.example.core.health

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkStatus {
    ONLINE,
    OFFLINE,
    LIMITED
}

class NetworkStateMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networkStatus = MutableStateFlow(checkCurrentStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkStatus.value = checkCurrentStatus()
        }

        override fun onLost(network: Network) {
            _networkStatus.value = NetworkStatus.OFFLINE
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _networkStatus.value = when {
                hasInternet && isValidated -> NetworkStatus.ONLINE
                hasInternet -> NetworkStatus.LIMITED
                else -> NetworkStatus.OFFLINE
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            // Fallback for restricted test environments
        }
    }

    fun checkCurrentStatus(): NetworkStatus {
        val cm = connectivityManager ?: return NetworkStatus.OFFLINE
        val activeNetwork = cm.activeNetwork ?: return NetworkStatus.OFFLINE
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus.OFFLINE

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return when {
            hasInternet && isValidated -> NetworkStatus.ONLINE
            hasInternet -> NetworkStatus.LIMITED
            else -> NetworkStatus.OFFLINE
        }
    }

    fun isOnline(): Boolean = _networkStatus.value == NetworkStatus.ONLINE
}
