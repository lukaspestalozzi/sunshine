package com.sunshine.app.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/**
 * Paces the requests of region downloads to one server (offline-regions spec, "Download rate
 * limit"; design D4 of add-offline-regions): starts at least [minIntervalMillis] apart, so at most
 * 5 per second at 200 ms, and at most [maxInFlight] in progress. [now] is a monotonic clock in ms.
 */
class RateLimiter(
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    maxInFlight: Int = MAX_IN_FLIGHT,
    private val now: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    private val slots = Semaphore(maxInFlight)
    private val pace = Mutex()
    private var nextStart: Long? = null // none before the first start

    /** Waits for a slot and the next start time. Every successful call needs one [release]. */
    suspend fun acquire() {
        slots.acquire()
        try {
            pace.withLock {
                val wait = nextStart?.let { it - now() } ?: 0
                if (wait > 0) delay(wait)
                nextStart = now() + minIntervalMillis
            }
        } catch (cancelled: Throwable) {
            slots.release()
            throw cancelled
        }
    }

    fun release() = slots.release()

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    private companion object {
        const val MIN_INTERVAL_MILLIS = 200L
        const val MAX_IN_FLIGHT = 2
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/** One [RateLimiter] per server, shared by the map's and the elevation's region downloads. */
class RateLimiters(
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val limiters = HashMap<String, RateLimiter>()

    fun forHost(host: String): RateLimiter = synchronized(limiters) { limiters.getOrPut(host) { RateLimiter(now = now) } }
}
