package com.retinasight.ai.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reports whether the device currently has usable internet.
 *
 * Note NET_CAPABILITY_VALIDATED, not merely CONNECTED. A phone attached to a
 * captive-portal Wi-Fi at a rural clinic is "connected" and cannot reach
 * anything; treating that as online would make sync retry forever and drain
 * the battery it is supposed to be saving.
 */
class ConnectivityObserver(context: Context) {

    private val manager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Emits on every change, starting with the current state. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(hasValidatedInternet())
            }

            override fun onLost(network: Network) {
                trySend(hasValidatedInternet())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                trySend(hasValidatedInternet())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        manager.registerNetworkCallback(request, callback)
        trySend(hasValidatedInternet())

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    fun hasValidatedInternet(): Boolean {
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
