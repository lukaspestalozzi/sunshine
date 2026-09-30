package com.sunshine.app.offline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sunshine.app.R
import com.sunshine.app.SunshineApp
import com.sunshine.core.MapArea
import java.time.ZoneId

/**
 * The Offline page for the map [area] visible when it was opened (offline-regions spec; design D9
 * of add-offline-regions): the download of that area, the regions, and the storage used. The system
 * back returns to the map.
 */
@Composable
fun OfflineScreen(
    area: MapArea,
    modifier: Modifier = Modifier,
    viewModel: OfflineViewModel = viewModel(key = area.toString(), factory = offlineViewModelFactory(area)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // On Android 13+ the download asks for notifications first; it starts whatever the answer.
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onDownload() }
    val onDownload = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onDownload()
        }
    }
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.offline_title), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onDownload, enabled = state.canDownload) { Text(stringResource(R.string.offline_download)) }
            Text(state.estimate ?: stringResource(R.string.offline_zoom_in), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.offline_regions), style = MaterialTheme.typography.titleMedium)
            if (state.regions.isEmpty()) {
                Text(stringResource(R.string.offline_no_regions), style = MaterialTheme.typography.bodyLarge)
            }
            for (region in state.regions) {
                RegionEntry(region, onDelete = { viewModel.onDeleteRequested(region.id) })
            }
            Column {
                Text(stringResource(R.string.offline_storage_map, state.mapStorage), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.offline_storage_elevation, state.demStorage), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.offline_storage_limit), style = MaterialTheme.typography.bodySmall)
        }
    }
    state.confirmDelete?.let { region ->
        AlertDialog(
            onDismissRequest = viewModel::onDeleteCancelled,
            text = { Text(stringResource(R.string.offline_delete_question, region.name)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::onDeleteConfirmed,
                ) { Text(stringResource(R.string.offline_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = viewModel::onDeleteCancelled) { Text(stringResource(R.string.offline_delete_cancel)) } },
        )
    }
}

@Composable
private fun RegionEntry(
    region: RegionItem,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(region.name, style = MaterialTheme.typography.bodyLarge)
            Text(region.status, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.offline_delete))
        }
    }
}

private fun offlineViewModelFactory(area: MapArea) =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[APPLICATION_KEY]) { "OfflineViewModel needs the Application" } as SunshineApp
            OfflineViewModel(
                area = area,
                regions = application.offlineDatabase.dao().regions(),
                work = application.downloadWork(),
                storage = application::storageUse,
                download = application::downloadArea,
                delete = application.regionDeleter::delete,
                zone = ZoneId.systemDefault(),
            )
        }
    }
