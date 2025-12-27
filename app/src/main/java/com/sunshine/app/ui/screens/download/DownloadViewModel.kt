package com.sunshine.app.ui.screens.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.app.data.connectivity.ConnectivityObserver
import com.sunshine.app.domain.model.DownloadableRegion
import com.sunshine.app.domain.repository.DownloadProgress
import com.sunshine.app.domain.repository.DownloadState
import com.sunshine.app.domain.repository.RegionProvider
import com.sunshine.app.domain.repository.TileDownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the download screen.
 */
data class DownloadUiState(
    val availableRegions: List<RegionWithStatus> = emptyList(),
    val totalStorageUsed: Long = 0,
    val isOnline: Boolean = true,
)

/**
 * Combines a downloadable region with its download status.
 */
data class RegionWithStatus(
    val region: DownloadableRegion,
    val downloadProgress: DownloadProgress?,
) {
    val isDownloaded: Boolean
        get() = downloadProgress?.status == DownloadState.COMPLETED

    val isDownloading: Boolean
        get() = downloadProgress?.status == DownloadState.DOWNLOADING

    val progress: Int
        get() = downloadProgress?.progress ?: 0

    val statusText: String
        get() =
            when (downloadProgress?.status) {
                DownloadState.PENDING -> "Pending..."
                DownloadState.DOWNLOADING -> "Downloading $progress%"
                DownloadState.COMPLETED -> "Downloaded"
                DownloadState.FAILED -> "Failed"
                DownloadState.PAUSED -> "Paused"
                null -> "Not downloaded"
            }
}

/**
 * ViewModel for the download screen.
 */
class DownloadViewModel(
    private val regionProvider: RegionProvider,
    private val downloadRepository: TileDownloadRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {
    val uiState: StateFlow<DownloadUiState> =
        combine(
            downloadRepository.getDownloadProgress(),
            downloadRepository.getTotalStorageUsed(),
            connectivityObserver.isOnline,
        ) { downloadProgress, totalStorage, isOnline ->
            val availableRegions = regionProvider.getAvailableRegions()
            val regionsWithStatus =
                availableRegions.map { region ->
                    RegionWithStatus(
                        region = region,
                        downloadProgress = downloadProgress.find { it.regionId == region.id },
                    )
                }
            DownloadUiState(
                availableRegions = regionsWithStatus,
                totalStorageUsed = totalStorage,
                isOnline = isOnline,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS),
            initialValue =
                DownloadUiState(
                    availableRegions =
                        regionProvider.getAvailableRegions().map {
                            RegionWithStatus(region = it, downloadProgress = null)
                        },
                ),
        )

    fun startDownload(region: DownloadableRegion) {
        downloadRepository.startDownload(region)
    }

    fun cancelDownload(regionId: String) {
        downloadRepository.cancelDownload(regionId)
    }

    fun deleteDownload(regionId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(regionId)
        }
    }

    @Suppress("MagicNumber")
    fun formatStorageSize(bytes: Long): String =
        when {
            bytes < KB -> "$bytes B"
            bytes < MB -> "${bytes / KB} KB"
            bytes < GB -> "${bytes / MB} MB"
            else -> "${"%.1f".format(bytes.toDouble() / GB)} GB"
        }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5000L
        private const val KB = 1024L
        private const val MB = KB * 1024
        private const val GB = MB * 1024
    }
}
