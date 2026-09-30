package com.sunshine.app.offline

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sunshine.app.SunshineApp
import com.sunshine.app.sunshine.debugLog
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads the queued regions in the background (design D7 of add-offline-regions): one unique
 * work for all regions, run while a network is connected and storage is not low. WorkManager
 * starts it again after the constraints return, the process ends or the device restarts.
 */
class RegionDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (refused: IllegalStateException) {
            // Android 12+ refuses to start a foreground service from the background, e.g. when the
            // network returns while the app is not shown. The download then runs as ordinary work.
            debugLog("Region download without a foreground service: ${refused.message}")
        }
        return try {
            (applicationContext as SunshineApp).regionDownloader.downloadAll()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            // E.g. no space left or a MapLibre error: tried again later, with WorkManager's backoff.
            debugLog("Region download failed: $failed")
            Result.retry()
        } finally {
            // Without a foreground service nothing else removes the progress notification.
            DownloadNotification.cancel(applicationContext)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            DownloadNotification.ID,
            DownloadNotification.build(applicationContext, 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    companion object {
        const val WORK_NAME = "offline-regions"

        /**
         * Starts the downloads; any network will do (user decision). Appended rather than kept, so
         * that a region queued while a finishing worker has already seen an empty queue still gets
         * a worker after it.
         */
        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<RegionDownloadWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresStorageNotLow(true)
                            .build(),
                    ).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/**
 * The region list's view of the download work (design D7 of add-offline-regions), from the states
 * and stop reasons of its entries. Without validated internet it waits for the network, even while
 * WorkManager, which only needs a connection, still runs it.
 */
fun downloadWork(
    entries: List<Pair<WorkInfo.State, Int>>,
    online: Boolean,
): DownloadWork {
    val unfinished = entries.filter { (state, _) -> !state.isFinished }
    return when {
        unfinished.isEmpty() -> DownloadWork.IDLE
        !online -> DownloadWork.WAITING_FOR_NETWORK
        unfinished.any { (state, _) -> state == WorkInfo.State.RUNNING } -> DownloadWork.RUNNING
        unfinished.any { (_, stopReason) ->
            stopReason == WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW
        } -> DownloadWork.WAITING_FOR_STORAGE
        else -> DownloadWork.IDLE
    }
}
