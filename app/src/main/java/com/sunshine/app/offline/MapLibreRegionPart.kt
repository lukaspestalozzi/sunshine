package com.sunshine.app.offline

import android.content.Context
import com.sunshine.app.map.REGION_STYLE_URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * The map tiles of a region as a MapLibre offline region (design D7 of add-offline-regions): map
 * zooms 5 to 16 of the region's area with the locally served style (design D3). MapLibre retries
 * failed tiles itself and skips tiles it already has. [log] receives its errors.
 */
class MapLibreRegionPart(
    private val context: Context,
    private val dao: OfflineDao,
    private val log: (String) -> Unit,
) : MapRegionPart {
    override suspend fun download(
        region: RegionRow,
        onStatus: suspend (MapStatus) -> Unit,
    ): Long {
        // A map region created but not yet recorded (the download was cancelled in between) is
        // found by its metadata, so a resumed download never creates a second one.
        val offlineRegion = region.mapRegionId?.let { find(it) } ?: list().firstOrNull { regionIdOf(it) == region.id } ?: create(region)
        if (offlineRegion.id != region.mapRegionId) dao.updateMapRegionId(region.id, offlineRegion.id)
        return statuses(offlineRegion)
            .onEach { onStatus(MapStatus(it.completedResourceCount, it.requiredResourceCount, it.completedResourceSize)) }
            .first { it.isComplete }
            .completedResourceSize
    }

    /** Deletes the map region; its tiles no other region uses become browsed tiles (design D10). */
    suspend fun delete(mapRegionId: Long) {
        find(mapRegionId)?.let { delete(it) }
    }

    private suspend fun delete(offlineRegion: OfflineRegion) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                offlineRegion.delete(
                    object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() = continuation.resume(Unit)

                        override fun onError(error: String) = continuation.resumeWithException(IllegalStateException(error))
                    },
                )
            }
        }
    }

    /**
     * Deletes the map regions of regions that no longer exist: e.g. one created while its region was
     * being deleted, before its id was recorded. [regionIds] are the regions that exist.
     */
    suspend fun deleteOrphans(regionIds: Set<Long>) {
        for (offlineRegion in list()) {
            if (regionIdOf(offlineRegion) !in regionIds) delete(offlineRegion)
        }
    }

    /** The region's statuses while it downloads; it goes inactive when collection stops (MapLibre requires it). */
    private fun statuses(offlineRegion: OfflineRegion) =
        callbackFlow {
            offlineRegion.setObserver(
                object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        trySend(status)
                    }

                    override fun onError(error: OfflineRegionError) =
                        log("Map region ${offlineRegion.id}: ${error.reason} ${error.message}")

                    override fun mapboxTileCountLimitExceeded(limit: Long) = log("Map region ${offlineRegion.id}: tile limit $limit")
                },
            )
            offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            awaitClose {
                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                offlineRegion.setObserver(null)
            }
        }.flowOn(Dispatchers.Main)

    private suspend fun create(region: RegionRow): OfflineRegion =
        withContext(Dispatchers.Main) {
            val definition =
                OfflineTilePyramidRegionDefinition(
                    REGION_STYLE_URL,
                    LatLngBounds.from(region.north, region.east, region.south, region.west),
                    MIN_MAP_ZOOM,
                    MAX_MAP_ZOOM,
                    PIXEL_RATIO,
                )
            suspendCancellableCoroutine { continuation ->
                OfflineManager.getInstance(context).createOfflineRegion(
                    definition,
                    region.id.toString().toByteArray(),
                    object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(offlineRegion: OfflineRegion) = continuation.resume(offlineRegion)

                        override fun onError(error: String) = continuation.resumeWithException(IllegalStateException(error))
                    },
                )
            }
        }

    private suspend fun list(): List<OfflineRegion> =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                OfflineManager.getInstance(context).listOfflineRegions(
                    object : OfflineManager.ListOfflineRegionsCallback {
                        override fun onList(offlineRegions: Array<OfflineRegion>?) = continuation.resume(offlineRegions.orEmpty().toList())

                        override fun onError(error: String) = continuation.resumeWithException(IllegalStateException(error))
                    },
                )
            }
        }

    /** Our region's id, which [create] stores as the map region's metadata. */
    private fun regionIdOf(offlineRegion: OfflineRegion): Long? = String(offlineRegion.metadata).toLongOrNull()

    private suspend fun find(mapRegionId: Long): OfflineRegion? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                OfflineManager.getInstance(context).getOfflineRegion(
                    mapRegionId,
                    object : OfflineManager.GetOfflineRegionCallback {
                        override fun onRegion(offlineRegion: OfflineRegion) = continuation.resume(offlineRegion)

                        override fun onRegionNotFound() = continuation.resume(null)

                        override fun onError(error: String) = continuation.resumeWithException(IllegalStateException(error))
                    },
                )
            }
        }

    private companion object {
        // Map zooms 5 to 16, i.e. OpenTopoMap tiles z6 to z17 (offline-regions spec, "Region contents").
        const val MIN_MAP_ZOOM = 5.0
        const val MAX_MAP_ZOOM = 16.0

        // The style's tile URL has no {ratio}, so the ratio does not change which tiles are fetched.
        const val PIXEL_RATIO = 1f
    }
}
