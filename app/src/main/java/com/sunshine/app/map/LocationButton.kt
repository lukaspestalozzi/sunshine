package com.sunshine.app.map

/** The location button's state (gps-location spec, "Location button"). */
enum class LocationButtonState {
    /** No fresh position, and the user is not waiting for one. */
    IDLE,

    /** The user tapped without a fresh position; the icon is animated until one arrives. */
    WAITING,

    /** A fresh position is known; a tap centres the map on it. */
    READY,
}

/** The location access the user allowed. */
enum class LocationAccess {
    NONE,
    APPROXIMATE,
    PRECISE,
}

/** A notice answering a tap of the location button, with a button to the settings that fix it. */
enum class LocationNotice {
    /** Access refused, and Android no longer shows the dialog. */
    ACCESS_OFF,

    /** Location switched off on the device. */
    SWITCHED_OFF,

    /** Only approximate access allowed. */
    APPROXIMATE_ONLY,
}

/** What the screen does after an event of the location button. */
sealed interface LocationAction {
    data object AskPermission : LocationAction

    /** Move the map centre to the fresh position, keeping the zoom. */
    data object Centre : LocationAction

    data class Notice(
        val notice: LocationNotice,
    ) : LocationAction
}

/** The button's state after an event, and the actions to take in order. */
data class LocationStep(
    val state: LocationButtonState,
    val actions: List<LocationAction>,
)

/**
 * A tap of the location button with the current [access] and whether location is switched on
 * (design D3 of add-gps-location). Without access it asks; the answer comes as [onPermissionAnswer].
 */
fun LocationButtonState.onTap(
    access: LocationAccess,
    locationOn: Boolean,
): LocationStep {
    if (access == LocationAccess.NONE) return LocationStep(this, listOf(LocationAction.AskPermission))
    // No waiting while location is off: no position could ever end it (spec "Location switched off").
    if (!locationOn) return LocationStep(LocationButtonState.IDLE, listOf(LocationAction.Notice(LocationNotice.SWITCHED_OFF)))
    val approximate =
        if (access == LocationAccess.APPROXIMATE) listOf(LocationAction.Notice(LocationNotice.APPROXIMATE_ONLY)) else emptyList()
    return when (this) {
        LocationButtonState.READY -> LocationStep(this, listOf(LocationAction.Centre) + approximate)
        LocationButtonState.IDLE -> LocationStep(LocationButtonState.WAITING, approximate)
        LocationButtonState.WAITING -> LocationStep(LocationButtonState.IDLE, approximate)
    }
}

/**
 * The user's answer to the permission dialog asked by a tap. Android tells only after asking whether
 * it still shows the dialog ([dialogAvailable]); when it does not, the refusal is for good.
 */
fun LocationButtonState.onPermissionAnswer(
    access: LocationAccess,
    locationOn: Boolean,
    dialogAvailable: Boolean,
): LocationStep =
    when {
        access != LocationAccess.NONE -> onTap(access, locationOn)
        dialogAvailable -> LocationStep(this, emptyList())
        else -> LocationStep(LocationButtonState.IDLE, listOf(LocationAction.Notice(LocationNotice.ACCESS_OFF)))
    }

/** The position turned [stale] (old) or fresh (design D2 of add-gps-location). Waiting outlasts an old position. */
fun LocationButtonState.onStale(stale: Boolean): LocationButtonState =
    when {
        !stale -> LocationButtonState.READY
        this == LocationButtonState.WAITING -> this
        else -> LocationButtonState.IDLE
    }
