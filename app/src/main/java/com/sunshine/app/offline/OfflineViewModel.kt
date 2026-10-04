package com.sunshine.app.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.map.formatCoordinates
import com.sunshine.app.settings.CoordinateFormat
import com.sunshine.app.settings.Settings
import com.sunshine.core.DownloadEstimate
import com.sunshine.core.GeoBounds
import com.sunshine.core.GeoPoint
import com.sunshine.core.MapArea
import com.sunshine.core.crossesAntimeridian
import com.sunshine.core.downloadEstimate
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The state of the region download work (design D7 of add-offline-regions). */
enum class DownloadWork { RUNNING, WAITING_FOR_NETWORK, WAITING_FOR_STORAGE, IDLE }

/** Storage of the stored tiles, regions and browsed together (user decision: totals per kind). */
data class StorageUse(
    val mapBytes: Long,
    val demBytes: Long,
)

data class RegionItem(
    val id: Long,
    val name: String,
    val status: String,
)

/** Why the visible area cannot be downloaded (offline-regions spec, "Download the visible area"). */
enum class DownloadBlock { ZOOMED_OUT, ACROSS_180 }

/** The Offline page (offline-regions spec). [estimate] is `null` when the area is [blocked]. */
data class OfflineUiState(
    val estimate: String?,
    val blocked: DownloadBlock? = null,
    val regions: List<RegionItem> = emptyList(),
    val mapStorage: String = formatMebibytes(0),
    val demStorage: String = formatMebibytes(0),
    val confirmDelete: RegionItem? = null,
    /** The browsed tiles' limit per kind, e.g. `512 MiB` (offline-regions spec, "Storage usage"). */
    val browsedLimit: String = formatMebibytes(Settings().browsedLimitMib * MEBIBYTE),
) {
    val canDownload: Boolean get() = estimate != null
}

/**
 * The Offline page for the map [area] visible when it was opened (design D9 of
 * add-offline-regions). [regions] come newest first; [download] queues a region, [delete] deletes
 * one. Region names follow the coordinate format of [settings].
 */
class OfflineViewModel(
    private val area: MapArea,
    regions: Flow<List<RegionSummary>>,
    work: Flow<DownloadWork>,
    private val storage: suspend () -> StorageUse,
    private val download: suspend (MapArea) -> Unit,
    private val delete: suspend (Long) -> Unit,
    private val zone: ZoneId,
    settings: Flow<Settings> = flowOf(Settings()),
) : ViewModel() {
    // Deleted regions leave the list at once, before the database says so.
    private val deleted = MutableStateFlow(emptySet<Long>())
    private val confirmDelete = MutableStateFlow<RegionItem?>(null)
    private val blocked =
        when {
            area.zoom < MIN_DOWNLOAD_ZOOM -> DownloadBlock.ZOOMED_OUT
            area.crossesAntimeridian() -> DownloadBlock.ACROSS_180
            else -> null
        }
    private val estimate = if (blocked == null) formatEstimate(downloadEstimate(GeoBounds.of(area))) else null

    val uiState: StateFlow<OfflineUiState> =
        combine(regions, work, deleted, confirmDelete, settings) { summaries, work, deleted, confirm, settings ->
            val use = storage()
            OfflineUiState(
                estimate = estimate,
                blocked = blocked,
                regions = items(summaries.filter { it.region.id !in deleted }, work, settings.coordinates),
                mapStorage = formatMebibytes(use.mapBytes),
                demStorage = formatMebibytes(use.demBytes),
                confirmDelete = confirm,
                browsedLimit = formatMebibytes(settings.browsedLimitMib * MEBIBYTE),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, OfflineUiState(estimate, blocked))

    fun onDownload() {
        viewModelScope.launch { download(area) }
    }

    fun onDeleteRequested(id: Long) {
        confirmDelete.value = uiState.value.regions.find { it.id == id }
    }

    fun onDeleteConfirmed() {
        val region = confirmDelete.value ?: return
        confirmDelete.value = null
        deleted.update { it + region.id }
        viewModelScope.launch { delete(region.id) }
    }

    fun onDeleteCancelled() {
        confirmDelete.value = null
    }

    private fun items(
        summaries: List<RegionSummary>,
        work: DownloadWork,
        coordinates: CoordinateFormat,
    ): List<RegionItem> {
        // The oldest queued region is the one being downloaded (design D7).
        val first =
            summaries.filter { it.region.state == RegionState.QUEUED }.minWithOrNull(
                compareBy({ it.region.createdAt }, { it.region.id }),
            )
        return summaries.map { summary ->
            val region = summary.region
            RegionItem(
                region.id,
                formatCoordinates(GeoPoint(region.centreLat, region.centreLon), coordinates),
                formatRegionStatus(
                    status(
                        summary,
                        summary == first,
                        work,
                    ),
                ),
            )
        }
    }

    private fun status(
        summary: RegionSummary,
        isFirst: Boolean,
        work: DownloadWork,
    ): RegionStatus {
        val region = summary.region
        val completedAt = region.completedAt
        if (region.state == RegionState.COMPLETE && completedAt != null) {
            return RegionStatus.Complete(Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate(), region.mapBytes + summary.demBytes)
        }
        if (!isFirst) return RegionStatus.Waiting
        return when (work) {
            DownloadWork.RUNNING -> RegionStatus.Downloading(region.progress)
            DownloadWork.WAITING_FOR_NETWORK -> RegionStatus.WaitingForNetwork(region.progress)
            DownloadWork.WAITING_FOR_STORAGE -> RegionStatus.WaitingForStorage(region.progress)
            DownloadWork.IDLE -> RegionStatus.Waiting
        }
    }

    private companion object {
        // The overlay's threshold too (user decision).
        const val MIN_DOWNLOAD_ZOOM = 11.0
    }
}

/** E.g. `10.5 × 22.3 km, 7406 map tiles and 413 elevation tiles, about 25 min` (offline-regions spec). */
fun formatEstimate(estimate: DownloadEstimate): String =
    "${oneDecimal(estimate.widthKm)} × ${oneDecimal(estimate.heightKm)} km, ${estimate.mapTiles} map tiles and " +
        "${estimate.demTiles} elevation tiles, about ${estimate.minutes} min"

private fun oneDecimal(value: Double) = String.format(Locale.ROOT, "%.1f", value)
