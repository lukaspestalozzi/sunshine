package com.sunshine.app.search

import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.sunshine.app.map.CameraState
import com.sunshine.app.network.NetworkMonitor
import com.sunshine.app.sunshine.debugLog
import com.sunshine.core.MapArea

/**
 * The search page (place-search spec, "Search page"; design D13 of add-place-search), opened over
 * the map showing [area]: the back arrow ([onBack]) and the system back return unchanged; a chosen
 * entry returns with its camera ([onChosen]).
 */
@Composable
fun SearchScreen(
    area: MapArea,
    onBack: () -> Unit,
    onChosen: (CameraState) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = searchViewModelFactory),
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Its own back handler empties the field before returning, as a choice does.
    val close = {
        viewModel.onClosed()
        onBack()
    }
    BackHandler(onBack = close)
    LaunchedEffect(Unit) {
        // Once per opening, the field empty: after a rotation the view model is still open and keeps
        // the text; after the process was recreated it is new and is opened again.
        if (!viewModel.isOpened) viewModel.onOpened(area)
        focus.requestFocus()
        keyboard?.show()
    }
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = viewModel::onTextChanged,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    trailingIcon =
                        if (text.isEmpty()) {
                            null
                        } else {
                            {
                                IconButton(onClick = { viewModel.onTextChanged("") }) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                                }
                            }
                        },
                    modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus),
                )
            }
            results.notice?.let { notice ->
                val message =
                    when (notice) {
                        SearchNotice.OFFLINE -> R.string.search_offline
                        SearchNotice.UNAVAILABLE -> R.string.search_unavailable
                        SearchNotice.SEARCHING -> R.string.search_searching
                    }
                Notice(stringResource(message))
            }
            if (results.noneFound) Notice(stringResource(R.string.search_none_found))
            LazyColumn {
                items(results.entries) { entry ->
                    EntryRow(entry) {
                        val camera = viewModel.cameraFor(entry)
                        viewModel.onClosed()
                        onChosen(camera)
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** An entry: title and second line, its distance at the end; tapping chooses it (place-search spec, "Result entries"). */
@Composable
private fun EntryRow(
    entry: SearchEntry,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge)
            Text(entry.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(entry.distance, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

private val searchViewModelFactory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[APPLICATION_KEY]) { "SearchViewModel needs the Application" } as SunshineApp
            val connectivityManager =
                checkNotNull(application.getSystemService(ConnectivityManager::class.java)) { "ConnectivityManager is not available" }
            SearchViewModel(
                store = application.placeStore,
                fetch = application.photonClient::fetch,
                isOnline = NetworkMonitor(connectivityManager).isOnline,
                settings = application.settingsStore.settings,
                log = ::debugLog,
            )
        }
    }
