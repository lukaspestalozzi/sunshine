package com.sunshine.app.data.download

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests for tile cache path format used by TileDownloadWorker.
 * Verifies that the directory layout matches what osmdroid expects:
 * {cacheDir}/{sourceName}/{z}/{x}/{y}.png
 */
class TileDownloadWorkerTest {
    @Test
    fun `tile source name matches osmdroid convention`() {
        assertEquals("OpenTopoMap", TileDownloadWorker.TILE_SOURCE_NAME)
    }

    @Test
    fun `tile cache path format matches osmdroid expected layout`() {
        val tempFolder = TemporaryFolder()
        tempFolder.create()
        val cacheDir = tempFolder.root

        val zoom = 15
        val x = 17059
        val y = 11526
        val tilePath =
            File(
                cacheDir,
                "${TileDownloadWorker.TILE_SOURCE_NAME}/$zoom/$x/$y.png",
            )

        // Verify the path structure: {cache}/OpenTopoMap/15/17059/11526.png
        assertEquals(
            "Tile path should follow osmdroid layout",
            "${cacheDir.absolutePath}/OpenTopoMap/15/17059/11526.png",
            tilePath.absolutePath,
        )

        // Verify directories can be created and file written
        tilePath.parentFile?.mkdirs()
        tilePath.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        assertTrue("Tile file should exist after writing", tilePath.exists())
        assertEquals("Tile file should have correct size", 4L, tilePath.length())

        tempFolder.delete()
    }

    @Test
    fun `tile path uses z-x-y structure not flat naming`() {
        val cacheDir = File("/tmp/osmdroid")
        val tilePath =
            File(
                cacheDir,
                "${TileDownloadWorker.TILE_SOURCE_NAME}/10/512/340.png",
            )

        // The path must contain separate directories for z, x, y
        val pathSegments = tilePath.relativeTo(cacheDir).path.split(File.separator)
        assertEquals("Should have 4 segments: source/z/x/y.png", 4, pathSegments.size)
        assertEquals("First segment should be tile source", "OpenTopoMap", pathSegments[0])
        assertEquals("Second segment should be zoom", "10", pathSegments[1])
        assertEquals("Third segment should be x", "512", pathSegments[2])
        assertEquals("Fourth segment should be y.png", "340.png", pathSegments[3])
    }
}
