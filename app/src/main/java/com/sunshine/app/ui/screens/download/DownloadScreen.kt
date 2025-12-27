package com.sunshine.app.ui.screens.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunshine.app.R
import com.sunshine.app.domain.repository.DownloadState
import org.koin.androidx.compose.koinViewModel

private val CARD_PADDING = 16.dp
private val ITEM_SPACING = 12.dp
private val SMALL_SPACING = 8.dp
private val ICON_SPACING = 12.dp
private val SMALL_ICON_SIZE = 18.dp
private val PROGRESS_ICON_SIZE = 24.dp
private val PROGRESS_STROKE_WIDTH = 2.dp
private const val PROGRESS_DIVISOR = 100f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onNavigateBack: () -> Unit,
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            DownloadTopBar(onNavigateBack = onNavigateBack)
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (!uiState.isOnline) {
                OfflineBanner()
            }

            StorageInfo(
                totalUsed = uiState.totalStorageUsed,
                formatSize = viewModel::formatStorageSize,
            )

            RegionList(
                regions = uiState.availableRegions,
                isOnline = uiState.isOnline,
                onDownload = { viewModel.startDownload(it.region) },
                onCancel = { viewModel.cancelDownload(it.region.id) },
                onDelete = { viewModel.deleteDownload(it.region.id) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.download_title)) },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    )
}

@Composable
private fun RegionList(
    regions: List<RegionWithStatus>,
    isOnline: Boolean,
    onDownload: (RegionWithStatus) -> Unit,
    onCancel: (RegionWithStatus) -> Unit,
    onDelete: (RegionWithStatus) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = CARD_PADDING),
        verticalArrangement = Arrangement.spacedBy(ITEM_SPACING),
    ) {
        item {
            Text(
                text = stringResource(R.string.download_select_region),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = SMALL_SPACING),
            )
        }

        items(
            items = regions,
            key = { it.region.id },
        ) { regionWithStatus ->
            RegionCard(
                regionWithStatus = regionWithStatus,
                isOnline = isOnline,
                onDownload = { onDownload(regionWithStatus) },
                onCancel = { onCancel(regionWithStatus) },
                onDelete = { onDelete(regionWithStatus) },
            )
        }

        item {
            Spacer(modifier = Modifier.height(CARD_PADDING))
        }
    }
}

@Composable
private fun OfflineBanner() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(CARD_PADDING),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(ICON_SPACING))
            Text(
                text = stringResource(R.string.error_network),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun StorageInfo(
    totalUsed: Long,
    formatSize: (Long) -> String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(CARD_PADDING),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(ICON_SPACING))
            Text(
                text = "Storage used: ${formatSize(totalUsed)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun RegionCard(
    regionWithStatus: RegionWithStatus,
    isOnline: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val region = regionWithStatus.region
    val progress = regionWithStatus.downloadProgress

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (regionWithStatus.isDownloaded) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING),
        ) {
            RegionHeader(region = region, regionWithStatus = regionWithStatus)

            Spacer(modifier = Modifier.height(SMALL_SPACING))

            RegionDetails(region = region, regionWithStatus = regionWithStatus, progress = progress)

            if (regionWithStatus.isDownloading) {
                Spacer(modifier = Modifier.height(SMALL_SPACING))
                LinearProgressIndicator(
                    progress = { regionWithStatus.progress / PROGRESS_DIVISOR },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(SMALL_SPACING))

            RegionActions(
                regionWithStatus = regionWithStatus,
                isOnline = isOnline,
                onDownload = onDownload,
                onCancel = onCancel,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun RegionHeader(
    region: com.sunshine.app.domain.model.DownloadableRegion,
    regionWithStatus: RegionWithStatus,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = region.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = region.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        RegionStatusIcon(regionWithStatus)
    }
}

@Composable
private fun RegionDetails(
    region: com.sunshine.app.domain.model.DownloadableRegion,
    regionWithStatus: RegionWithStatus,
    progress: com.sunshine.app.domain.repository.DownloadProgress?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Est. size: ${region.formatEstimatedSize()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = regionWithStatus.statusText,
            style = MaterialTheme.typography.bodySmall,
            color =
                when (progress?.status) {
                    DownloadState.COMPLETED -> MaterialTheme.colorScheme.primary
                    DownloadState.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun RegionActions(
    regionWithStatus: RegionWithStatus,
    isOnline: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        when {
            regionWithStatus.isDownloaded -> {
                DeleteButton(onClick = onDelete)
            }
            regionWithStatus.isDownloading -> {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
            else -> {
                DownloadButton(onClick = onDownload, enabled = isOnline)
            }
        }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.size(SMALL_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Delete")
    }
}

@Composable
private fun DownloadButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(SMALL_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Download")
    }
}

@Composable
private fun RegionStatusIcon(regionWithStatus: RegionWithStatus) {
    when {
        regionWithStatus.isDownloaded -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        regionWithStatus.isDownloading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(PROGRESS_ICON_SIZE),
                strokeWidth = PROGRESS_STROKE_WIDTH,
            )
        }
        regionWithStatus.downloadProgress?.status == DownloadState.FAILED -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Failed",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        regionWithStatus.downloadProgress?.status == DownloadState.PAUSED -> {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Paused",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
