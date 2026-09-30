package com.sunshine.app.offline

/**
 * Deletes regions (design D10 of add-offline-regions; offline-regions spec, "Delete a region"):
 * the region leaves the list at once, its download stops, its map region is deleted, and its DEM
 * tiles no other region claims become browsed tiles. [deleteMap] deletes a MapLibre region by id,
 * [deleteOrphanMaps] the MapLibre regions of regions not among the given ids, [cancelDownload]
 * stops the region's download, [onRegionBytes] sets the ambient limit.
 */
class RegionDeleter(
    private val dao: OfflineDao,
    private val store: DemTileStore,
    private val deleteMap: suspend (Long) -> Unit,
    private val deleteOrphanMaps: suspend (Set<Long>) -> Unit,
    private val cancelDownload: (Long) -> Unit,
    private val onRegionBytes: (Long, Boolean) -> Unit,
) {
    suspend fun delete(regionId: Long) {
        dao.markDeleted(regionId)
        cancelDownload(regionId)
        finish(regionId)
        // A map region created by the cancelled download before its id was recorded.
        deleteOrphanMaps(dao.regionIds().toSet())
    }

    /** At start: finishes deletions the end of the process interrupted, and drops map regions and claims without a region. */
    suspend fun finishPending() {
        dao.deletedRegions().forEach { finish(it.id) }
        deleteOrphanMaps(dao.regionIds().toSet())
        dao.deleteOrphanClaims()
        store.evict() // what the dropped claims left browsed
    }

    private suspend fun finish(regionId: Long) {
        dao.region(regionId)?.mapRegionId?.let { deleteMap(it) }
        store.deleteRegion(regionId)
        dao.deleteRegion(regionId)
        onRegionBytes(dao.regionMapBytes(), true)
    }
}
