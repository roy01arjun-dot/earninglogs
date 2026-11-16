package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.ConcurrentHashMap

/**
 * Online = at least one network with validated internet access.
 *
 * Bugs fixed: `onAvailable` reported online before the network was validated (captive portals),
 * `onCapabilitiesChanged` of one network could flip the app offline while another network was
 * still connected, and `onLost` re-read a possibly stale active network.
 */
class NetworkConnectivityMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        val validated = ConcurrentHashMap.newKeySet<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasInternet()) validated.add(network) else validated.remove(network)
                trySend(validated.isNotEmpty())
            }

            override fun onLost(network: Network) {
                validated.remove(network)
                trySend(validated.isNotEmpty())
            }
        }

        trySend(isCurrentlyOnline())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { connectivityManager?.registerNetworkCallback(request, callback) }

        awaitClose { runCatching { connectivityManager?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    fun isCurrentlyOnline(): Boolean {
        val cm = connectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasInternet()
    }

    private fun NetworkCapabilities.hasInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
