package com.sunshine.app.about

import com.sunshine.app.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// The About page (map-view spec, "About and attributions"; design D8 of add-sun-exposure-heatmap).
class AboutEntriesTest {
    @Test
    fun `the about page lists the version, both attributions with their links, and the icon credit`() {
        assertEquals(
            listOf(
                AboutEntry(R.string.about_version, argument = "0.1.0"),
                AboutEntry(R.string.map_attribution, url = "https://www.openstreetmap.org/copyright"),
                AboutEntry(R.string.elevation_attribution, url = "https://mapterhorn.com/attribution/"),
                AboutEntry(R.string.about_icons),
            ),
            aboutEntries(versionName = "0.1.0"),
        )
    }
}
