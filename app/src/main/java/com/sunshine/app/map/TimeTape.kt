package com.sunshine.app.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.sunshine.app.R
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * The time tape (time-selection spec, "Choose the time of day", "Time tape strip"; design D4 of
 * polish-ui): the selected day's scale moving under a fixed needle, with the [strip]'s state per
 * step. Dragging, flinging and tapping report each step the needle reaches through [onMinutes]
 * (minutes since the start of the day); a [selectedTime] set elsewhere moves the scale to it.
 */
@Composable
fun TimeTape(
    selectedTime: ZonedDateTime,
    step: Int,
    strip: List<StripState>,
    onMinutes: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale =
        remember(selectedTime.toLocalDate(), selectedTime.zone, step) { TapeScale(selectedTime.toLocalDate(), selectedTime.zone, step) }
    val ticks = remember(scale) { scale.hourTicks() }
    val selectedMinutes = sliderMinutes(selectedTime)
    val position = remember { Animatable(selectedMinutes) }
    val coroutineScope = rememberCoroutineScope()
    val currentOnMinutes by rememberUpdatedState(onMinutes)
    // While a gesture or its fling runs, the scale leads and the selected time follows.
    var moving by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(selectedMinutes) }

    fun report(minutes: Float) {
        val snapped = scale.snap(minutes)
        if (snapped != reported) {
            reported = snapped
            currentOnMinutes(snapped)
        }
    }
    LaunchedEffect(selectedMinutes, scale) {
        reported = selectedMinutes
        if (!moving) position.animateTo(selectedMinutes.coerceIn(0f, scale.lastMinutes))
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface))
    val tickColour = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColour = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.sun_panel_time_of_day)
    val earlier = stringResource(R.string.tape_earlier)
    val later = stringResource(R.string.tape_later)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(TAPE_HEIGHT)
            .semantics {
                contentDescription = description
                stateDescription = selectedTime.format(TIME)
                customActions =
                    listOf(
                        CustomAccessibilityAction(earlier) {
                            currentOnMinutes(scale.snap(selectedMinutes - step))
                            true
                        },
                        CustomAccessibilityAction(later) {
                            currentOnMinutes(scale.snap(selectedMinutes + step))
                            true
                        },
                    )
            }.pointerInput(scale) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        moving = true
                        tracker.resetTracking()
                        coroutineScope.launch { position.stop() }
                    },
                    onHorizontalDrag = { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val minutes = scale.dragged(position.value, dx / density)
                        coroutineScope.launch { position.snapTo(minutes) }
                        report(minutes)
                    },
                    onDragEnd = {
                        // Pixels per second to the right are minutes per second towards earlier times.
                        val velocity = -tracker.calculateVelocity().x / density / TapeScale.DP_PER_MINUTE
                        val target = scale.snap(DECAY.calculateTargetValue(position.value, velocity))
                        coroutineScope.launch {
                            position.animateTo(target, initialVelocity = velocity) { report(value) }
                            moving = false
                        }
                    },
                    onDragCancel = { moving = false },
                )
            }.pointerInput(scale) {
                detectTapGestures { offset ->
                    val target = scale.snap(scale.minutesAt((offset.x - size.width / 2f) / density, position.value))
                    moving = true
                    coroutineScope.launch {
                        position.animateTo(target) { report(value) }
                        moving = false
                    }
                }
            },
    ) {
        val pxPerMinute = TapeScale.DP_PER_MINUTE * density
        val centre = size.width / 2f
        val needle = position.value
        val first = floor((needle - centre / pxPerMinute) / step).toInt().coerceAtLeast(0)
        val last = ceil((needle + centre / pxPerMinute) / step).toInt().coerceAtMost(scale.stepCount - 1)
        val stripTop = STRIP_TOP.toPx()
        val stripHeight = STRIP_HEIGHT.toPx()
        for (i in first..last) {
            val x = centre + (i * step - needle) * pxPerMinute
            val state = if (strip.size == scale.stepCount) strip[i] else StripState.NOT_COMPUTED
            drawStep(state, Offset(x, stripTop), Size(step * pxPerMinute, stripHeight))
        }
        for (tick in ticks) {
            val x = centre + (tick.minutes - needle) * pxPerMinute
            if (x < -LABEL_SLACK.toPx() || x > size.width + LABEL_SLACK.toPx()) continue
            drawLine(tickColour, Offset(x, stripTop + stripHeight), Offset(x, stripTop + stripHeight + TICK_LENGTH.toPx()), 1.dp.toPx())
            drawText(textMeasurer, tick.label.toString(), Offset(x + 2.dp.toPx(), stripTop + stripHeight + 2.dp.toPx()), labelStyle)
        }
        drawLine(needleColour, Offset(centre, 0f), Offset(centre, size.height), NEEDLE_WIDTH.toPx())
    }
}

// One step of the strip: its state's colour, unknown as the map's stripes over the neutral colour.
private fun DrawScope.drawStep(
    state: StripState,
    topLeft: Offset,
    size: Size,
) {
    when (state) {
        StripState.SUN -> drawRect(Color(SUN_ARGB), topLeft, size)
        StripState.SHADE -> drawRect(Color(SHADE_ARGB), topLeft, size)
        StripState.NIGHT -> drawRect(Color(NIGHT_ARGB), topLeft, size)
        StripState.NOT_COMPUTED -> drawRect(Color(NOT_COMPUTED_ARGB), topLeft, size)
        StripState.UNKNOWN -> {
            drawRect(Color(NOT_COMPUTED_ARGB), topLeft, size)
            val period = HATCH_PERIOD.dp.toPx()
            clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
                // Stripes anchored to the scale, so that neighbouring steps continue them.
                var x = floor((topLeft.x - size.height) / period) * period
                while (x < topLeft.x + size.width) {
                    drawLine(
                        Color(UNKNOWN_ARGB),
                        Offset(x, topLeft.y + size.height),
                        Offset(x + size.height, topLeft.y),
                        HATCH_WIDTH.dp.toPx() / sqrt(2f),
                    )
                    x += period
                }
            }
        }
    }
}

// The strip's colours (time-selection spec, "Time tape strip"); shade and unknown as on the map.
internal const val SUN_ARGB = 0xFFFFD54F.toInt()
internal const val NIGHT_ARGB = 0xFF263238.toInt()
internal const val NOT_COMPUTED_ARGB = 0xFFE0E0E0.toInt()

private val DECAY = exponentialDecay<Float>()
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val TAPE_HEIGHT = 48.dp
private val STRIP_TOP = 6.dp
private val STRIP_HEIGHT = 16.dp
private val TICK_LENGTH = 6.dp
private val NEEDLE_WIDTH = 2.dp
private val LABEL_SLACK = 24.dp
