package com.retinasight.ai.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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

    /**
     * Emits on every change, starting with the current state.
     *
     * Registered as a DEFAULT network callback, and each callback answers from
     * the arguments it is handed rather than re-reading `activeNetwork`.
     *
     * That distinction is the whole bug this had: on Wi-Fi loss, `onLost` fired
     * while `activeNetwork` still pointed at the network that was going away,
     * so re-querying returned "online" and no further callback ever arrived to
     * correct it. The badge stayed on Online with the radio off. A callback
     * knows what happened; the manager has not necessarily caught up yet.
     */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Available, but not necessarily validated yet - the
                // capabilities callback that follows settles it.
                trySend(isValidated(manager.getNetworkCapabilities(network)))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                trySend(isValidated(capabilities))
            }

            override fun onLost(network: Network) {
                // The default network is gone. Say so, rather than asking the
                // manager, which may still be reporting it.
                trySend(false)
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        manager.registerDefaultNetworkCallback(callback)
        trySend(hasValidatedInternet())

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    private fun isValidated(caps: NetworkCapabilities?): Boolean =
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    fun hasValidatedInternet(): Boolean {
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
