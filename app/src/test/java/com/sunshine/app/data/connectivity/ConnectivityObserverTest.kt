package com.sunshine.app.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectivityObserverTest {
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var observer: ConnectivityObserver

    private val callbackSlot = slot<ConnectivityManager.NetworkCallback>()

    @Before
    fun setup() {
        connectivityManager = mockk(relaxed = true)
        context =
            mockk {
                every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
            }

        // Mock NetworkRequest.Builder which is an Android framework class
        mockkConstructor(NetworkRequest.Builder::class)
        val mockRequest = mockk<NetworkRequest>()
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } returns mockk(relaxed = true)
        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockRequest

        every {
            connectivityManager.registerNetworkCallback(
                any<NetworkRequest>(),
                capture(callbackSlot),
            )
        } answers {}
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `emits true when device has active internet connection`() =
        runTest {
            setupActiveConnection(hasInternet = true)
            observer = ConnectivityObserver(context)

            observer.isOnline.test {
                assertTrue("Should emit true when online", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits false when device has no active connection`() =
        runTest {
            setupNoActiveConnection()
            observer = ConnectivityObserver(context)

            observer.isOnline.test {
                assertFalse("Should emit false when offline", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits true when onAvailable callback fires`() =
        runTest {
            setupNoActiveConnection()
            observer = ConnectivityObserver(context)

            observer.isOnline.test {
                assertFalse("Initial state should be offline", awaitItem())

                callbackSlot.captured.onAvailable(mockk())
                assertTrue("Should emit true after onAvailable", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits false when onLost fires with no remaining connection`() =
        runTest {
            setupActiveConnection(hasInternet = true)
            observer = ConnectivityObserver(context)

            observer.isOnline.test {
                assertTrue("Initial state should be online", awaitItem())

                setupNoActiveConnection()
                callbackSlot.captured.onLost(mockk())
                assertFalse("Should emit false after losing connection", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits true on capabilities change with internet`() =
        runTest {
            setupNoActiveConnection()
            observer = ConnectivityObserver(context)

            observer.isOnline.test {
                assertFalse("Initial state should be offline", awaitItem())

                val caps = mockCapabilities(hasInternet = true)
                callbackSlot.captured.onCapabilitiesChanged(mockk(), caps)
                assertTrue("Should emit true with internet capability", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits false on capabilities change without internet`() =
        runTest {
            setupActiveConnection(hasInternet = true)
            observer = ConnectivityObserver(context)

            observer.isOnline.test {
                assertTrue("Initial state should be online", awaitItem())

                val caps = mockCapabilities(hasInternet = false)
                callbackSlot.captured.onCapabilitiesChanged(mockk(), caps)
                assertFalse("Should emit false without internet capability", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `registers network callback when flow is collected`() =
        runTest {
            setupNoActiveConnection()
            observer = ConnectivityObserver(context)

            observer.isOnline.test {
                awaitItem()
                verify {
                    connectivityManager.registerNetworkCallback(
                        any<NetworkRequest>(),
                        any<ConnectivityManager.NetworkCallback>(),
                    )
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `hasActiveConnection returns true with internet capability`() {
        setupActiveConnection(hasInternet = true)
        observer = ConnectivityObserver(context)

        assertTrue(observer.hasActiveConnection())
    }

    @Test
    fun `hasActiveConnection returns false with no active network`() {
        setupNoActiveConnection()
        observer = ConnectivityObserver(context)

        assertFalse(observer.hasActiveConnection())
    }

    @Test
    fun `hasActiveConnection returns false without internet capability`() {
        setupActiveConnection(hasInternet = false)
        observer = ConnectivityObserver(context)

        assertFalse(observer.hasActiveConnection())
    }

    private fun setupActiveConnection(hasInternet: Boolean) {
        val network = mockk<Network>()
        val capabilities = mockCapabilities(hasInternet)
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
    }

    private fun setupNoActiveConnection() {
        every { connectivityManager.activeNetwork } returns null
    }

    private fun mockCapabilities(hasInternet: Boolean): NetworkCapabilities =
        mockk {
            every {
                hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } returns hasInternet
        }
}
