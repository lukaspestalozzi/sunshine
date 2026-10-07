package com.sunshine.app.settings

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

// One full-suite run hung in "a change reaches the settings flow" (2026-10-04) and did not
// reproduce in four further runs. The timeout makes a recurrence fail fast with the test thread's
// stack instead of hanging the build; it does not skip anything.
@Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SettingsStoreTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    // Every store's scope, cancelled after each test so that no DataStore outlives its test.
    private val jobs = mutableListOf<Job>()

    private fun open(): SettingsStore = SettingsStore.open(file, CoroutineScope(Dispatchers.IO + Job().also { jobs += it }))

    @AfterEach
    fun closeStores() =
        runBlocking {
            jobs.forEach { it.cancelAndJoin() }
        }

    @Test
    fun `settings are read back by a new store on the same file`() =
        runBlocking {
            val store = open()
            store.setCoordinates(CoordinateFormat.LV95)
            store.setPreset(Preset.FAST)
            store.setLastView(LastView(46.6863, 7.8632, 13.0))
            jobs.forEach { it.cancelAndJoin() }

            val reopened = open()

            assertEquals(CoordinateFormat.LV95, reopened.settings.value.coordinates)
            assertEquals(Preset.FAST, reopened.settings.value.preset)
            assertEquals(LastView(46.6863, 7.8632, 13.0), reopened.settings.value.lastView)
        }

    @Test
    fun `debug switches are read back by a new store on the same file`() =
        runBlocking {
            val store = open()
            store.setDebug { it.copy(timings = true) }
            store.setDebug { it.copy(agreementCheck = true) }
            jobs.forEach { it.cancelAndJoin() }

            val reopened = open()

            assertEquals(DebugSwitches(timings = true, agreementCheck = true), reopened.settings.value.debug)
        }

    @Test
    fun `the panel's details and the dismissed hint are read back by a new store`() =
        runBlocking {
            val store = open()
            store.setDetailsExpanded(true)
            store.setHintDismissed()
            jobs.forEach { it.cancelAndJoin() }

            val reopened = open()

            assertEquals(true, reopened.settings.value.detailsExpanded)
            assertEquals(true, reopened.settings.value.hintDismissed)
        }

    @Test
    fun `a change reaches the settings flow`() =
        runBlocking {
            val store = open()

            store.setOverlayOpacity(30)

            assertEquals(30, store.settings.first { it.overlayOpacityPercent == 30 }.overlayOpacityPercent)
        }

    @Test
    fun `custom values are clamped when written`() =
        runBlocking {
            val store = open()

            store.setCustom(Resolution(0, 12, 40, 20))

            assertEquals(Resolution(1, 10, 32, 20), store.settings.first { it.custom != Resolution.NORMAL }.custom)
        }

    @Test
    fun `a corrupt file reads as every default`() {
        file.writeBytes(Random(42).nextBytes(256))

        val store = open()

        assertEquals(Settings(), store.settings.value)
    }
}
