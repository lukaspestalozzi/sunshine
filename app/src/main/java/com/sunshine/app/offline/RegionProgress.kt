package com.sunshine.app.offline

import java.time.LocalDate

// Texts of the region list, fixed by the offline-regions spec ("Background download", "Region
// list", "Interrupted download"). All output is independent of the device locale.

/** What the region list shows for a region. */
sealed interface RegionStatus {
    data class Downloading(
        val percent: Int,
    ) : RegionStatus

    /** Queued behind the region being downloaded. */
    data object Waiting : RegionStatus

    data class WaitingForNetwork(
        val percent: Int,
    ) : RegionStatus

    data class WaitingForStorage(
        val percent: Int,
    ) : RegionStatus

    data class Complete(
        val date: LocalDate,
        val bytes: Long,
    ) : RegionStatus
}

/** The share of the region's tiles [obtained] (stored or known missing), rounded down; 100 only when [complete]. */
fun regionProgress(
    obtained: Long,
    total: Long,
    complete: Boolean,
): Int {
    if (complete) return FULL
    if (total <= 0) return 0
    return (obtained * FULL / total).toInt().coerceAtMost(FULL - 1)
}

fun formatRegionStatus(status: RegionStatus): String =
    when (status) {
        is RegionStatus.Downloading -> "Downloading ${status.percent} %"
        RegionStatus.Waiting -> "Waiting"
        is RegionStatus.WaitingForNetwork -> "Incomplete (${status.percent} %) · waiting for network"
        is RegionStatus.WaitingForStorage -> "Incomplete (${status.percent} %) · waiting for storage"
        is RegionStatus.Complete -> "${status.date} · ${formatMebibytes(status.bytes)}"
    }

/** Whole MiB, rounded half up, e.g. `183 MiB`. */
fun formatMebibytes(bytes: Long): String = "${(bytes + MEBIBYTE / 2) / MEBIBYTE} MiB"

private const val FULL = 100
internal const val MEBIBYTE = 1024L * 1024
