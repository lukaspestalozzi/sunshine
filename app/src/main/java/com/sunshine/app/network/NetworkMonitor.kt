package com.sunshine.app.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the device has validated internet access, starting with the current state. */
class NetworkMonitor(
    private val connectivityManager: ConnectivityManager,
) {
    val isOnline: Flow<Boolean> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        trySend(capabilities.hasValidatedInternet())
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }
                }
            // The callback does not fire at all while there is no network, so emit the current state first.
            trySend(connectivityManager.hasValidatedInternet())
            connectivityManager.registerDefaultNetworkCallback(callback)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()
}

private fun ConnectivityManager.hasValidatedInternet(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false // no active network
    return capabilities.hasValidatedInternet()
}

private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
