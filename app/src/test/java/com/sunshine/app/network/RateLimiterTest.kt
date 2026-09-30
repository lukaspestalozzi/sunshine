package com.sunshine.app.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RateLimiterTest {
    private fun TestScope.limiter(maxInFlight: Int = 2) =
        RateLimiter(minIntervalMillis = 200, maxInFlight = maxInFlight, now = { testScheduler.currentTime })

    @Test
    fun `starts are spaced 200 ms apart, so no second holds more than 5`() =
        runTest {
            val limiter = limiter()
            val starts = mutableListOf<Long>()

            repeat(20) { launch { limiter.withPermit { starts += testScheduler.currentTime } } }
            advanceTimeBy(10_000)

            assertEquals((0 until 20).map { it * 200L }, starts)
            assertTrue(starts.all { start -> starts.count { it in start until start + 1000 } <= 5 })
        }

    @Test
    fun `never more than 2 are held at once`() =
        runTest {
            val limiter = limiter()
            var held = 0
            var mostHeld = 0

            repeat(10) {
                launch {
                    limiter.withPermit {
                        held++
                        mostHeld = maxOf(mostHeld, held)
                        delay(1000)
                        held--
                    }
                }
            }
            advanceTimeBy(20_000)

            assertEquals(2, mostHeld)
            assertEquals(0, held)
        }

    @Test
    fun `a cancelled wait for a slot releases nothing and does not block the next one`() =
        runTest {
            val limiter = limiter(maxInFlight = 1)
            val holder = launch { limiter.withPermit { awaitCancellation() } }
            runCurrent()
            val waiting = launch { limiter.withPermit { error("the cancelled request must not start") } }
            runCurrent()

            waiting.cancel()
            holder.cancel()
            var startedAt = -1L
            launch { limiter.withPermit { startedAt = testScheduler.currentTime } }
            advanceTimeBy(1000)

            assertEquals(200, startedAt)
        }

    @Test
    fun `a cancelled wait for the interval does not delay the next one`() =
        runTest {
            val limiter = limiter()
            limiter.withPermit { }
            val waiting =
                launch(start = CoroutineStart.UNDISPATCHED) { limiter.withPermit { error("the cancelled request must not start") } }
            advanceTimeBy(100)

            waiting.cancel()
            var startedAt = -1L
            launch { limiter.withPermit { startedAt = testScheduler.currentTime } }
            advanceTimeBy(1000)

            assertEquals(200, startedAt)
        }
}
