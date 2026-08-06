package org.polyfrost.evergreenhud.client.hud.keystrokes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import net.minecraft.client.KeyMapping
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.background
import org.polyfrost.compose.composables.margin
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.evergreenhud.client.utils.fastRemoveIfReversed
import org.polyfrost.evergreenhud.client.utils.matchesKeyCode
import org.polyfrost.evergreenhud.client.utils.matchesMouseButton
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.DraggableList
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent
import org.polyfrost.oneconfig.api.event.v1.events.MouseInputEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.utils.v1.dsl.mc

private const val KEY = 16f
private const val CPS_SCALE = 0.5f
private const val DEFAULT_GAP = 2f
private const val LINE_GAP = 1f
private const val DEFAULT_SPACE_H = 11f
private const val DEFAULT_SPACE_LINE_W = 11f
private const val DEFAULT_SPACE_LINE_H = 1f
private const val FONT = 8f

private const val CPS_NONE = 0
private const val CPS_SMALL = 1
private const val CPS_LARGE = 2

private const val ROW_MOVEMENT = "Movement"
private const val ROW_SPACEBAR = "Spacebar"
private const val ROW_MOUSE = "Mouse"
private const val ROW_SPRINT = "Sprint"
private const val ROW_SNEAK = "Sneak"

private const val UP = "▲"
private const val DOWN = "▼"
private const val LEFT = "◀"
private const val RIGHT = "▶"

class KeystrokesHud : Hud(
    id = "keystrokes.json",
    title = "Keystrokes",
    category = Category.INFO,
) {
    @DraggableList(
        title = "Rows",
        description = "Which rows to show, and in what order.",
        checkable = true,
        options = [ROW_MOVEMENT, ROW_SPACEBAR, ROW_MOUSE, ROW_SPRINT, ROW_SNEAK],
    )
    var rows = arrayOf(ROW_MOVEMENT, ROW_SPACEBAR, ROW_MOUSE)

    @Switch(title = "Arrows", description = "Use arrows instead of key names.")
    var arrows = false

    @RadioButton(
        title = "Click Counter",
        description = "Show CPS on the mouse keys. Small sits under the name, large replaces it.",
        options = ["None", "Small", "Large"],
    )
    var cpsMode = CPS_NONE

    @Slider(title = "Key Spacing", description = "Gap between keys.", min = 0F, max = 10F, step = 0.5F)
    var keyGap = DEFAULT_GAP

    @Slider(title = "Spacebar Height", description = "Height of the spacebar.", min = 4F, max = 24F, step = 0.5F)
    var spaceH = DEFAULT_SPACE_H

    @Slider(title = "Spacebar Line Width", description = "Length of the spacebar line.", min = 1F, max = 48F, step = 0.5F)
    var spaceLineW = DEFAULT_SPACE_LINE_W

    @Slider(title = "Spacebar Line Thickness", description = "Thickness of the spacebar line.", min = 0.5F, max = 6F, step = 0.5F)
    var spaceLineH = DEFAULT_SPACE_LINE_H

    @Slider(title = "Fade In Duration (ms)", description = "Time to fade to the pressed colors.", min = 1F, max = 250F, step = 1F)
    var fadeInMs = 150f

    @Slider(title = "Fade Out Duration (ms)", description = "Time to fade back to the unpressed colors.", min = 1F, max = 250F, step = 1F)
    var fadeOutMs = 150f

    @Color(title = "Unpressed Background Color", subcategory = "Colors")
    var unpressedBg = PolyColor(0x80000000.toInt())

    @Color(title = "Unpressed Text Color", subcategory = "Colors")
    var unpressedText = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Pressed Background Color", subcategory = "Colors")
    var pressedBg = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Pressed Text Color", subcategory = "Colors")
    var pressedText = PolyColor(0xFF000000.toInt())

    private val showJump: Boolean get() = ROW_SPACEBAR in rows

    private val showClicks: Boolean get() = ROW_MOUSE in rows

    @Transient
    private var keys = KeyState()

    @Transient
    private var handlers: ArrayList<EventHandler<*>> = ArrayList(2)

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun clone(): Hud = (super.clone() as KeystrokesHud).apply {
        unpressedBg = unpressedBg.copy()
        unpressedText = unpressedText.copy()
        pressedBg = pressedBg.copy()
        pressedText = pressedText.copy()
        rows = rows.copyOf()
        keys = KeyState()
        handlers = ArrayList(2)
    }

    override fun setup() {
        super.setup()
        if (isReal) {
            addDependency("arrows", null) {
                if (font != Font.Minecraft) Property.Display.DISABLED else Property.Display.SHOWN
            }
            addDependency("cpsMode", null) {
                if (showClicks) Property.Display.SHOWN else Property.Display.DISABLED
            }
            for (option in listOf("spaceH", "spaceLineW", "spaceLineH")) {
                addDependency(option, null) {
                    if (showJump) Property.Display.SHOWN else Property.Display.DISABLED
                }
            }
            for (option in listOf("rows", "arrows", "cpsMode", "keyGap", "spaceH", "spaceLineW", "spaceLineH")) {
                addCallback(option) { keys.rev.value++ }
            }
            addCallback("unpressedBg") { pushToDesigner() }
            addCallback("unpressedText") { pushToDesigner() }
            pushToDesigner()
            handlers.add(eventHandler { (btn, state): MouseInputEvent ->
                if (state == 1) onClick { it.matchesMouseButton(btn) }
            })
            handlers.add(eventHandler { (key, _, state): KeyInputEvent ->
                if (state == 1 && key != 0) onClick { it.matchesKeyCode(key) }
            })
        }
    }

    override fun remove() {
        for (handler in handlers) handler.unregister()
        handlers.clear()
        keys.attackClicks.clear()
        keys.useClicks.clear()
    }

    private fun pushToDesigner() {
        bgColor = unpressedBg.rawArgb
        bgChroma = unpressedBg.chroma
        bgChromaSpeed = unpressedBg.chromaSpeed
        textColor = unpressedText.rawArgb
        textChroma = unpressedText.chroma
        textChromaSpeed = unpressedText.chromaSpeed
    }

    private fun pullFromDesigner(): Boolean {
        var pulled = false
        if (bgColor != unpressedBg.rawArgb || bgChroma != unpressedBg.chroma || bgChromaSpeed != unpressedBg.chromaSpeed) {
            unpressedBg = PolyColor(bgColor, bgChroma, bgChromaSpeed)
            pulled = true
        }
            if (textColor != unpressedText.rawArgb || textChroma != unpressedText.chroma || textChromaSpeed != unpressedText.chromaSpeed) {
        unpressedText = PolyColor(textColor, textChroma, textChromaSpeed)
        pulled = true
        }
        if (pulled) keys.rev.value++
        return pulled
    }

    private inline fun onClick(matches: (KeyMapping) -> Boolean) {
        if (!showClicks || cpsMode == CPS_NONE) return
        val o = mc.options ?: return
        val now = System.nanoTime()
        if (matches(o.keyAttack)) keys.attackClicks.add(now)
        if (matches(o.keyUse)) keys.useClicks.add(now)
    }

    override fun update(): Boolean {
        val o = mc.options ?: return false
        val now = System.nanoTime()
        val dtMs = (now - keys.lastNanos).coerceAtLeast(0L) / 1_000_000f
        keys.lastNanos = now
        var changed = pullFromDesigner()
        val inStep = if (fadeInMs <= 0f) 1f else dtMs / fadeInMs
        val outStep = if (fadeOutMs <= 0f) 1f else dtMs / fadeOutMs
        fun poll(state: MutableState<Float>, key: KeyMapping) {
            val target = if (key.isDown) 1f else 0f
            val cur = state.value
            val next = when {
                cur < target -> (cur + inStep).coerceAtMost(target)
                cur > target -> (cur - outStep).coerceAtLeast(target)
                else -> cur
            }
            if (next != cur) {
                state.value = next
                changed = true
            }
        }
        poll(keys.forward, o.keyUp)
        poll(keys.left, o.keyLeft)
        poll(keys.back, o.keyDown)
        poll(keys.right, o.keyRight)
        poll(keys.jump, o.keyJump)
        poll(keys.attack, o.keyAttack)
        poll(keys.use, o.keyUse)
        poll(keys.sprint, o.keySprint)
        poll(keys.sneak, o.keyShift)
        if (showClicks && cpsMode != CPS_NONE) {
            fun pollCps(clicks: ArrayList<Long>, cps: MutableState<Int>) {
                clicks.fastRemoveIfReversed { now - it > 1_000_000_000 }
                if (cps.value != clicks.size) {
                    cps.value = clicks.size
                    changed = true
                }
            }
            pollCps(keys.attackClicks, keys.attackCps)
            pollCps(keys.useClicks, keys.useCps)
        }
        return changed
    }

    @Composable
    override fun Content() {
        keys.rev.value
        val o = mc.options ?: return
        val s = textScale.coerceAtLeast(0.01f)
        val keyW = KEY * s + padLeft + padRight
        val keyH = KEY * s + padTop + padBottom
        val gap = keyGap * s
        val rowW = keyW * 3 + gap * 2
        val clickW = (rowW - gap) / 2f
        val spaceKeyH = spaceH * s + padTop + padBottom
        val useArrows = arrows && font == Font.Minecraft

        PolyColumn(gap = gap) {
            for (row in rows) when (row) {
                ROW_MOVEMENT -> {
                    PolyBox(modifier = PolyModifier.size(rowW, keyH)) {
                        Key(if (useArrows) UP else o.keyUp.label(), keys.forward.value, keyW, keyH, PolyAlign.Center)
                    }
                    PolyRow(gap = gap) {
                        Key(if (useArrows) LEFT else o.keyLeft.label(), keys.left.value, keyW, keyH)
                        Key(if (useArrows) DOWN else o.keyDown.label(), keys.back.value, keyW, keyH)
                        Key(if (useArrows) RIGHT else o.keyRight.label(), keys.right.value, keyW, keyH)
                    }
                }

                ROW_SPACEBAR -> SpaceKey(keys.jump.value, rowW, spaceKeyH)

                ROW_MOUSE -> PolyRow(gap = gap) {
                    ClickKey("LMB", keys.attack.value, keys.attackCps.value, clickW, keyH)
                    ClickKey("RMB", keys.use.value, keys.useCps.value, clickW, keyH)
                }

                ROW_SPRINT -> Key(o.keySprint.label(), keys.sprint.value, rowW, keyH)

                ROW_SNEAK -> Key(o.keyShift.label(), keys.sneak.value, rowW, keyH)
            }
        }
    }

    private fun blend(unpressed: PolyColor, pressed: PolyColor, progress: Float): PolyColor = when {
        progress <= 0f -> unpressed
        progress >= 1f -> pressed
        else -> unpressed.lerp(pressed, progress)
    }

    private fun keyFg(progress: Float): PolyColor = blend(unpressedText, pressedText, progress)

    private fun keyBg(progress: Float): PolyColor = blend(unpressedBg, pressedBg, progress)

    private fun keyModifier(progress: Float, w: Float, h: Float, align: PolyAlign? = null): PolyModifier {
        var mod = PolyModifier.size(w, h)
        if (showBackground) mod = mod.background(keyBg(progress), bgRadius)
        if (align != null) mod = mod.align(align)
        return mod
    }

    @Composable
    private fun ClickKey(label: String, progress: Float, cps: Int, w: Float, h: Float) {
        when (cpsMode) {
            CPS_SMALL -> Key(label, progress, w, h, sub = "$cps CPS")
            CPS_LARGE -> Key(if (cps > 0) cps.toString() else label, progress, w, h)
            else -> Key(label, progress, w, h)
        }
    }

    @Composable
    private fun Key(label: String, progress: Float, w: Float, h: Float, align: PolyAlign? = null, sub: String? = null) {
        val fg = keyFg(progress)
        PolyBox(modifier = keyModifier(progress, w, h, align)) {
            val subScale = textScale * CPS_SCALE
            if (font == Font.Minecraft) {
                val lineH = McFontQueue.measureTextHeight(textScale)
                val lineGap = LINE_GAP * textScale
                val totalH = if (sub == null) lineH else lineH + lineGap + McFontQueue.measureTextHeight(subScale)
                val topPad = ((h - totalH) / 2f).coerceAtLeast(0f)
                McLine(label, fg, w, topPad, textScale)
                if (sub != null) McLine(sub, fg, w, topPad + lineH + lineGap, subScale)
            } else if (sub == null) {
                KeyText(label, fg, textScale)
            } else {
                PolyColumn(gap = LINE_GAP * textScale, modifier = PolyModifier.align(PolyAlign.Center)) {
                    KeyText(label, fg, textScale)
                    KeyText(sub, fg, subScale)
                }
            }
        }
    }

    @Composable
    private fun McLine(text: String, color: PolyColor, w: Float, topPad: Float, scale: Float) {
        val visibleW = (McFontQueue.measureTextWidth(text, scale) - scale).coerceAtLeast(0f)
        val leftPad = ((w - visibleW) / 2f).coerceAtLeast(0f)
        PolyMcText(
            text,
            color = color,
            shadow = showShadow,
            scale = scale,
            modifier = PolyModifier.align(PolyAlign.TopLeft).margin(leftPad, topPad, 0f, 0f),
        )
    }

    @Composable
    private fun KeyText(text: String, color: PolyColor, scale: Float) {
        PolyText(
            text,
            color = color,
            fontSize = FONT * scale,
            font = getPoppinsFontName(),
            modifier = PolyModifier.align(PolyAlign.Center),
        )
    }

    @Composable
    private fun SpaceKey(progress: Float, w: Float, h: Float) {
        val fg = keyFg(progress)
        PolyBox(modifier = keyModifier(progress, w, h)) {
            PolyCanvas(PolyModifier.size(w, h)) { x, y, cw, ch ->
                val s = textScale
                val half = spaceLineW * s / 2f
                val thickness = spaceLineH * s
                val cx = x + cw / 2f - 0.5f * s
                val cy = y + ch / 2f - 0.5f * s
                if (showShadow && font == Font.Minecraft) {
                    line(cx - half + s, cy + s, cx + half + s, cy + s, fg.darken(0.75f), thickness)
                }
                line(cx - half, cy, cx + half, cy, fg, thickness)
            }
        }
    }

    private fun KeyMapping.label(): String = translatedKeyMessage.string
}

/** Per-instance render state. Not serialized, and rebuilt from scratch on clone. */
private class KeyState {
    val forward = mutableStateOf(0f)
    val left = mutableStateOf(0f)
    val back = mutableStateOf(0f)
    val right = mutableStateOf(0f)
    val jump = mutableStateOf(0f)
    val attack = mutableStateOf(0f)
    val use = mutableStateOf(0f)
    val sprint = mutableStateOf(0f)
    val sneak = mutableStateOf(0f)
    val attackCps = mutableStateOf(0)
    val useCps = mutableStateOf(0)
    val attackClicks = ArrayList<Long>(20)
    val useClicks = ArrayList<Long>(20)

    /** Bumped to force a recompose when an option or the designer changes the layout. */
    val rev = mutableStateOf(0)

    var lastNanos = System.nanoTime()
}
