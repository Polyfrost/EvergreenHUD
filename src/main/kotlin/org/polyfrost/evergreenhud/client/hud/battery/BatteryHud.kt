package org.polyfrost.evergreenhud.client.hud.battery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.height
import org.polyfrost.compose.composables.margin
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.battery.Battery
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import kotlin.time.Duration.Companion.seconds

private const val TEXT_GAP = 3f
private const val FONT_SIZE = 8f
private const val MC_INK_HEIGHT = 7f
private const val MC_TRAILING_SPACE = 1f
private const val BASE_PADDING = 1f
private const val OUTLINE_ALPHA = 0.5f

private val EXAMPLE = Battery.ManualBattery(
    percentage = 72,
    isCharging = false,
    lifetimeRemaining = 2 * 3600 + 14 * 60,
    isBatterySaverEnabled = false,
)

class BatteryHud : Hud(
    id = "battery.json",
    title = "Battery",
    category = Category.INFO,
) {
    @Switch(title = "Show Percentage")
    var showPercentage = true

    @Switch(title = "Show Time Remaining", description = "Estimated time left while running on battery.")
    var showTimeRemaining = false

    @Switch(title = "Hide When Plugged In")
    var hideWhenCharging = false

    @Slider(title = "Low Battery Threshold", min = 5f, max = 50f, step = 5f)
    var lowThreshold = 20

    @Color(title = "Charging Color")
    var chargingColor = PolyColor(0xFF34C759.toInt())

    @Color(title = "Battery Saver Color")
    var saverColor = PolyColor(0xFFFFCC00.toInt())

    @Color(title = "Low Battery Color")
    var lowColor = PolyColor(0xFFFF453A.toInt())

    private data class Display(
        val percent: Int,
        val charging: Boolean,
        val fill: PolyColor?,
        val text: String,
    )

    private var display = mutableStateOf<Display?>(null)

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun setup() {
        super.setup()
        if (!isReal) return
        for (option in listOf("showPercentage", "showTimeRemaining", "hideWhenCharging", "lowThreshold", "chargingColor", "saverColor", "lowColor")) {
            updateWhenChanged(option)
        }
    }

    override fun update(): Boolean {
        val next = read()
        if (next == display.value) return false
        display.value = next
        return true
    }

    override fun updateFrequency(): Long = 1.seconds.inWholeNanoseconds

    override fun shouldShow(): Boolean {
        val current = display.value ?: return false
        return !hideWhenCharging || !current.charging || HudManager.isEditing
    }

    private fun read(): Display? {
        val live = if (isReal) Battery.get() else EXAMPLE
        val battery = when {
            live !is Battery.UnknownBattery -> live
            HudManager.isEditing -> EXAMPLE
            else -> return null
        }

        val percent = battery.percentage.coerceIn(0, 100)
        val fill = when {
            battery.isCharging -> chargingColor
            percent <= lowThreshold -> lowColor
            battery.isBatterySaverEnabled -> saverColor
            else -> null
        }
        val text = buildString {
            if (showPercentage) append(percent).append('%')
            val remaining = battery.lifetimeRemaining
            if (showTimeRemaining && !battery.isCharging && remaining > 0) {
                if (isNotEmpty()) append(' ')
                append(formatRemaining(remaining))
            }
        }
        return Display(percent, battery.isCharging, fill, text)
    }

    private fun formatRemaining(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    @Composable
    override fun Content() {
        val current = display.value ?: return
        val scale = textScale.coerceAtLeast(0.01f)
        val hasText = current.text.isNotEmpty()
        val isStatic = staticWidth && staticW > 0f && staticH > 0f
        val background = if (isStatic) hudBackground().size(staticW, staticH) else hudBackground()
        val modifier = background.padding(
            padLeft + BASE_PADDING * scale,
            padTop + BASE_PADDING * scale,
            padRight + (if (hasText && font == Font.Minecraft) BASE_PADDING - MC_TRAILING_SPACE else BASE_PADDING) * scale,
            padBottom + BASE_PADDING * scale,
        )
        val color = PolyColor(textColor, textChroma, textChromaSpeed)
        val rowModifier = PolyModifier.height(CELL_HEIGHT * scale).align(if (isStatic) alignment else PolyAlign.Center)

        PolyBox(modifier = modifier) {
            PolyRow(gap = if (hasText) TEXT_GAP * scale else 0f, modifier = rowModifier) {
                BatteryCell(
                    percent = current.percent,
                    fill = current.fill ?: color,
                    outline = color.multiplyAlpha(OUTLINE_ALPHA),
                    bolt = if (current.charging) color else null,
                    scale = scale,
                )
                if (hasText) Label(current.text, color, scale)
            }
        }
    }

    @Composable
    private fun Label(text: String, color: PolyColor, scale: Float) {
        val rowHeight = CELL_HEIGHT * scale
        if (font == Font.Minecraft) {
            val modifier = topAlign(inkOffset(rowHeight, 0f, MC_INK_HEIGHT * scale))
            PolyMcText(text, color = color, shadow = showShadow, scale = scale, modifier = modifier)
        } else {
            val metrics = FontManager.getFont(FONT_SIZE * scale, getPoppinsFontName()).metrics
            val inkTop = -metrics.ascent - metrics.capHeight
            val modifier = topAlign(inkOffset(rowHeight, inkTop, metrics.capHeight))
            PolyText(
                text,
                color = color,
                fontSize = FONT_SIZE * scale,
                shadow = showShadow,
                shadowColor = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed),
                shadowOffset = shadowOffsetX,
                font = getPoppinsFontName(),
                modifier = modifier,
            )
        }
    }

    private fun topAlign(offset: Float): PolyModifier =
        PolyModifier.align(PolyAlign.Top).margin(top = offset)

    private fun inkOffset(rowHeight: Float, inkTop: Float, inkHeight: Float): Float =
        ((rowHeight - inkHeight) / 2f - inkTop).coerceAtLeast(0f)

    override fun clone(): Hud = (super.clone() as BatteryHud).also {
        it.chargingColor = chargingColor.copy()
        it.saverColor = saverColor.copy()
        it.lowColor = lowColor.copy()
        it.display = mutableStateOf(null)
    }
}
