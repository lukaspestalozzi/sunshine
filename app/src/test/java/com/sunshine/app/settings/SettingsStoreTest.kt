package com.sunshine.app.settings

import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SettingsStoreTest {
    @TempDir
    lateinit var directory: File

    private val file get() = File(directory, "settings.preferences_pb")

    @Test
    fun `settings are read back by a new store on the same file`() =
        runBlocking {
            val job = Job()
            val store = SettingsStore.open(file, CoroutineScope(Dispatchers.IO + job))
            store.setCoordinates(CoordinateFormat.LV95)
            store.setPreset(Preset.FAST)
            store.setLastView(LastView(46.6863, 7.8632, 13.0))
            job.cancelAndJoin()

            val reopened = SettingsStore.open(file, CoroutineScope(Dispatchers.IO + Job()))

            assertEquals(CoordinateFormat.LV95, reopened.settings.value.coordinates)
            assertEquals(Preset.FAST, reopened.settings.value.preset)
            assertEquals(LastView(46.6863, 7.8632, 13.0), reopened.settings.value.lastView)
        }

    @Test
    fun `a change reaches the settings flow`() =
        runBlocking {
            val store = SettingsStore.open(file, CoroutineScope(Dispatchers.IO + Job()))

            store.setOverlayOpacity(30)

            assertEquals(30, store.settings.first { it.overlayOpacityPercent == 30 }.overlayOpacityPercent)
        }

    @Test
    fun `custom values are clamped when written`() =
        runBlocking {
            val store = SettingsStore.open(file, CoroutineScope(Dispatchers.IO + Job()))

            store.setCustom(Resolution(0, 12, 40, 20))

            assertEquals(Resolution(1, 10, 32, 20), store.settings.first { it.custom != Resolution.NORMAL }.custom)
        }

    @Test
    fun `a corrupt file reads as every default`() {
        file.writeBytes(Random(42).nextBytes(256))

        val store = SettingsStore.open(file, CoroutineScope(Dispatchers.IO + Job()))

        assertEquals(Settings(), store.settings.value)
    }
}
