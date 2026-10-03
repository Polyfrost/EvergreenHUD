package org.polyfrost.evergreenhud.client.hud.clock

import org.polyfrost.evergreenhud.client.utils.EvergreenHud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.MultiSelectDropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Hud
import java.time.LocalTime
import java.time.ZoneId

private const val DEFAULT_SIDE = 64f

private const val DETAIL_FIVE_MINUTE_MARKS = 0
private const val DETAIL_MINUTE_MARKS = 1
private const val DETAIL_NUMBERS = 2

private fun millisOfDay(zone: ZoneId): Long = LocalTime.now(zone).toNanoOfDay() / 1_000_000L

class ClockHud : EvergreenHud(
    id = "clock.json",
    title = "Clock",
    category = Category.INFO,
) {

    @Slider(title = "Hand Width", min = 1F, max = 10F, step = 1F)
    var handWidth = 2f

    @Switch(title = "Ticking")
    var ticking = false

    @MultiSelectDropdown(title = "Details", options = ["5-Minute Marks", "Minute Marks", "Numbers"])
    var details = booleanArrayOf(false, false, false)

    @Color(title = "Hour Hand Color")
    var hourHandColor = PolyColor(0xFF78B4FF.toInt())

    @Color(title = "Minute Hand Color")
    var minuteHandColor = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Second Hand Color")
    var secondHandColor = PolyColor(0xFFAAAAAA.toInt())

    private var rev = mutableStateOf(0)

    private var _staticW = mutableStateOf(DEFAULT_SIDE)
    override var staticW: Float get() = _staticW.value; set(v) { _staticW.value = v }

    private var _staticH = mutableStateOf(DEFAULT_SIDE)
    override var staticH: Float get() = _staticH.value; set(v) { _staticH.value = v }

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun clone(): Hud = (super.clone() as ClockHud).apply {
        details = this@ClockHud.details.copyOf()
        hourHandColor = this@ClockHud.hourHandColor.copy()
        minuteHandColor = this@ClockHud.minuteHandColor.copy()
        secondHandColor = this@ClockHud.secondHandColor.copy()
        rev = mutableStateOf(0)
        _staticW = mutableStateOf(this@ClockHud.staticW)
        _staticH = mutableStateOf(this@ClockHud.staticH)
    }

    fun applyGlobalColor(color: PolyColor) {
        hourHandColor = color.copy()
        minuteHandColor = color.copy()
        secondHandColor = color.copy()
        rev.value++
    }

    override fun setup() {
        super.setup()
        if (isReal) {
            for (option in listOf("details", "handWidth", "ticking", "hourHandColor", "minuteHandColor", "secondHandColor")) {
                addCallback(option) { rev.value++ }
            }
        }
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", DEFAULT_SIDE)
        tree?.getProp("staticH")?.addMetadata("default", DEFAULT_SIDE)
    }

    override fun minimumSize(): Pair<Float, Float> = 16f to 16f

    override fun canMergeBackground(): Boolean = true

    @Composable
    override fun Content() {
        rev.value
        val sizeModifier = PolyModifier.size(scaledWidth, scaledHeight)
        val zone = remember { ZoneId.systemDefault() }
        val timeState = remember { mutableLongStateOf(millisOfDay(zone)) }
        LaunchedEffect(zone) {
            while (true) {
                withFrameMillis { timeState.longValue = millisOfDay(zone) }
            }
        }
        val timeMillis by timeState
        val fiveMinuteMarks = detailEnabled(DETAIL_FIVE_MINUTE_MARKS)
        val minuteMarks = detailEnabled(DETAIL_MINUTE_MARKS)
        val numbers = detailEnabled(DETAIL_NUMBERS)
        val detailColor = PolyColor(textColor, textChroma, textChromaSpeed)
        val fontName = getPoppinsFontName()
        if (showBackground) {
            PolyBox(modifier = hudBackground(sizeModifier)) {
                Clock(timeMillis, handWidth, ticking, fiveMinuteMarks, minuteMarks, numbers, hourHandColor, minuteHandColor, secondHandColor, detailColor, fontName, bgRadius, sizeModifier)
            }
        } else {
            Clock(timeMillis, handWidth, ticking, fiveMinuteMarks, minuteMarks, numbers, hourHandColor, minuteHandColor, secondHandColor, detailColor, fontName, null, sizeModifier)
        }
    }

    private fun detailEnabled(index: Int): Boolean = details.getOrElse(index) { false }

    override fun update(): Boolean {
        return false
    }
}
