@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.composables.NodeBox
import org.polyfrost.compose.composables.NodeCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.minecraft.mc.McHeadStyle
import org.polyfrost.compose.minecraft.mc.McPlayerFaces
import org.polyfrost.compose.minecraft.mc.McSkinLayers
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.math.min

private const val DEFAULT_SIDE = 16f
private const val MIN_SIDE = 8f

private const val UPDATE_NANOS = 500_000_000L

class PlayerHeadHud : Hud(
    id = "player_head.json",
    title = "Player Head",
    category = Category.PLAYER,
) {
    private var _staticW = mutableStateOf(DEFAULT_SIDE)
    override var staticW: Float
        get() = _staticW.value
        set(v) { _staticW.value = v.coerceAtLeast(MIN_SIDE) }

    private var _staticH = mutableStateOf(DEFAULT_SIDE)
    override var staticH: Float
        get() = _staticH.value
        set(v) { _staticH.value = v.coerceAtLeast(MIN_SIDE) }

    @Dropdown(title = "Style", options = ["Face", "Flat", "3D"])
    var style = 1

    private var head = mutableStateOf<McSkinLayers?>(null)
    private var headStyle = mutableStateOf(McHeadStyle.FLAT)

    init {
        showBackground = false
    }

    override fun setup() {
        super.setup()
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", DEFAULT_SIDE)
        tree?.getProp("staticH")?.addMetadata("default", DEFAULT_SIDE)
        if (isReal) updateWhenChanged("style")
    }

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun minimumSize(): Pair<Float, Float> = MIN_SIDE to MIN_SIDE

    override fun update(): Boolean {
        val next = McPlayerFaces.layers(mc.player?.uuid)
        val nextStyle = McHeadStyle.entries.getOrElse(style) { McHeadStyle.FLAT }
        if (next === head.value && nextStyle == headStyle.value) return false
        head.value = next
        headStyle.value = nextStyle
        return true
    }

    override fun updateFrequency(): Long = if (isReal) UPDATE_NANOS else -1L

    override fun onCloned() {
        head = mutableStateOf(null)
        headStyle = mutableStateOf(McHeadStyle.FLAT)
    }

    @Composable
    override fun Content() {
        if (!isReal) {
            head.value
            headStyle.value
        }
        NodeBox(modifier = hudBackground().size(staticW, staticH)) {
            NodeCanvas(PolyModifier.size(staticW, staticH)) { x, y, w, h ->
                val layers = head.value ?: return@NodeCanvas
                val style = headStyle.value
                val side = min(w, h) * if (style == McHeadStyle.LAYERED && layers.hasHat) 8f / 9f else 1f
                McPlayerFaces.drawHead(this, layers, x + (w - side) / 2f, y + (h - side) / 2f, side, style)
            }
        }
    }
}
