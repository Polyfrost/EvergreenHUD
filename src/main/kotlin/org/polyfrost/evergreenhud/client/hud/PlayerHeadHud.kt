@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHeads
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

    private var headImage = mutableStateOf<PolyBitmap?>(null)

    init {
        showBackground = false
    }

    override fun setup() {
        super.setup()
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", DEFAULT_SIDE)
        tree?.getProp("staticH")?.addMetadata("default", DEFAULT_SIDE)
    }

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun minimumSize(): Pair<Float, Float> = MIN_SIDE to MIN_SIDE

    override fun update(): Boolean {
        val id = mc.player?.uuid ?: PlayerHeads.localPlayerId()
        val next = PlayerHeads.bitmap(id, preferLoadedSkin = true) ?: id?.let(PlayerHeads::defaultBitmap)
        if (next === headImage.value) return false
        headImage.value = next
        return true
    }

    override fun updateFrequency(): Long = if (isReal) UPDATE_NANOS else -1L

    override fun onCloned() {
        headImage = mutableStateOf(null)
    }

    @Composable
    override fun Content() {
        if (!isReal) headImage.value
        PolyBox(modifier = hudBackground().size(staticW, staticH)) {
            PolyCanvas(PolyModifier.size(staticW, staticH)) { x, y, w, h ->
                val head = headImage.value ?: return@PolyCanvas
                val side = min(w, h)
                val left = x + (w - side) / 2f
                val top = y + (h - side) / 2f
                image(head, left, top, side, side, FilterQuality.None)
            }
        }
    }
}
