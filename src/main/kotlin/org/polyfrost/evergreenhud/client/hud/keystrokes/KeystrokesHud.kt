package org.polyfrost.evergreenhud.client.hud.keystrokes

import androidx.compose.runtime.Composable
import com.mojang.blaze3d.platform.InputConstants
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import net.minecraft.client.KeyMapping
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
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
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent
import org.polyfrost.oneconfig.api.event.v1.events.MouseInputEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.experimental.or
import kotlin.math.roundToInt

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

private const val MODE_NORMAL = 0
private const val MODE_CUSTOM = 1

private const val UNKNOWN_KEY = "?"

private const val UP = "▲"
private const val DOWN = "▼"
private const val LEFT = "◀"
private const val RIGHT = "▶"

private val KEY_PAINT = Paint().apply { isAntiAlias = true }
private val LINE_PAINT = Paint().apply {
    isAntiAlias = false
    mode = PaintMode.STROKE
}

private fun snapToPixels(canvas: Canvas, x: Float, y: Float, w: Float, h: Float): Rect {
    val m = canvas.localToDeviceAsMatrix33.mat
    val scaleX = m[0]
    val scaleY = m[4]
    if (m[1] != 0f || m[3] != 0f || scaleX == 0f || scaleY == 0f) return Rect.makeXYWH(x, y, w, h)
    val transX = m[2]
    val transY = m[5]
    fun snapX(v: Float) = ((v * scaleX + transX).roundToInt() - transX) / scaleX
    fun snapY(v: Float) = ((v * scaleY + transY).roundToInt() - transY) / scaleY
    return Rect.makeLTRB(snapX(x), snapY(y), snapX(x + w), snapY(y + h))
}

private val ARROW_CODES = intArrayOf(InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT)

private const val UP_SHORT = "Up"
private const val DOWN_SHORT = "Down"
private const val LEFT_SHORT = "Left"
private const val RIGHT_SHORT = "Right"

class KeystrokesHud : Hud(
    id = "keystrokes.json",
    title = "Keystrokes",
    category = Category.INFO,
) {
    @RadioButton(
        title = "Mode",
        description = "Normal draws the movement layout. Custom draws one key of your choice.",
        options = ["Normal", "Custom"],
    )
    var mode = MODE_NORMAL

    @DraggableList(
        title = "Rows",
        description = "Rows to show, and their order.",
        checkable = true,
        options = [ROW_MOVEMENT, ROW_SPACEBAR, ROW_MOUSE, ROW_SPRINT, ROW_SNEAK],
    )
    var rows = arrayOf(ROW_MOVEMENT, ROW_SPACEBAR, ROW_MOUSE)

    @Keybind(title = "Key", description = "Key this HUD watches.")
    var customBind: OneConfigKeybind = KeybindHelper.builder().key(InputConstants.KEY_G).build()

    @Text(title = "Key Label", description = "Text on the key. Empty uses the key's name.")
    var customLabel = ""

    @Slider(title = "Key Width", description = "Width of the key.", min = 8F, max = 96F, step = 1F)
    var customWidth = KEY

    @Slider(title = "Key Height", description = "Height of the key.", min = 8F, max = 96F, step = 1F)
    var customHeight = KEY

    @Switch(title = "Arrows", description = "Use arrows instead of key names.")
    var arrows = false

    @RadioButton(
        title = "Click Counter",
        description = "Show CPS on the mouse keys.",
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

    @Slider(title = "Fade In Duration (ms)", description = "Fade time when pressed.", min = 1F, max = 250F, step = 1F)
    var fadeInMs = 150f

    @Slider(title = "Fade Out Duration (ms)", description = "Fade time when released.", min = 1F, max = 250F, step = 1F)
    var fadeOutMs = 150f

    @Slider(title = "Key Corner Radius", description = "Corner radius of each key.", min = 0F, max = 16F, step = 0.5F, subcategory = "Colors")
    var keyRadius = 4f

    @Color(title = "Unpressed Key Color", description = "Key fill while not held.", subcategory = "Colors")
    var unpressedBg = PolyColor(0x80000000.toInt())

    @Color(title = "Unpressed Text Color", subcategory = "Colors")
    var unpressedText = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Pressed Key Color", description = "Key fill while held.", subcategory = "Colors")
    var pressedBg = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Pressed Text Color", subcategory = "Colors")
    var pressedText = PolyColor(0xFF000000.toInt())

    private val isCustom: Boolean get() = mode == MODE_CUSTOM

    private val showJump: Boolean get() = !isCustom && ROW_SPACEBAR in rows

    private val showClicks: Boolean get() = !isCustom && ROW_MOUSE in rows

    @Transient
    private var keys = KeyState()

    @Transient
    private var handlers: ArrayList<EventHandler<*>> = ArrayList(2)

    init {
        showBackground = false
    }

    override fun hasBackground(): Boolean = false

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun clone(): Hud = (super.clone() as KeystrokesHud).apply {
        unpressedBg = unpressedBg.copy()
        unpressedText = unpressedText.copy()
        pressedBg = pressedBg.copy()
        pressedText = pressedText.copy()
        rows = rows.copyOf()
        customBind = customBind.copyWith(customBind.keyCodes?.copyOf(), customBind.mouseBtns?.copyOf(), customBind.mods)
        keys = KeyState()
        handlers = ArrayList(2)
    }

    override fun setup() {
        super.setup()
        if (isReal) {
            addDependency("arrows", null) {
                if (isCustom || font != Font.Minecraft) Property.Display.DISABLED else Property.Display.SHOWN
            }
            addDependency("cpsMode", null) {
                if (showClicks) Property.Display.SHOWN else Property.Display.DISABLED
            }
            for (option in listOf("spaceH", "spaceLineW", "spaceLineH")) {
                addDependency(option, null) {
                    if (showJump) Property.Display.SHOWN else Property.Display.DISABLED
                }
            }
            addDependency("rows", null) {
                if (isCustom) Property.Display.HIDDEN else Property.Display.SHOWN
            }
            addDependency("keyGap", null) {
                if (isCustom) Property.Display.DISABLED else Property.Display.SHOWN
            }
            for (option in listOf("customBind", "customLabel", "customWidth", "customHeight")) {
                addDependency(option, null) {
                    if (isCustom) Property.Display.SHOWN else Property.Display.DISABLED
                }
            }
            for (option in listOf(
                "mode", "rows", "arrows", "cpsMode", "keyGap", "spaceH", "spaceLineW", "spaceLineH", "keyRadius",
                "customBind", "customLabel", "customWidth", "customHeight",
            )) {
                addCallback(option) { keys.rev.value++ }
            }
            addCallback("unpressedText") { pushTextToDesigner() }
            pushTextToDesigner()
            handlers.add(eventHandler { (btn, state): MouseInputEvent ->
                when (state) {
                    1 -> {
                        keys.downMouse.add(btn)
                        onClick { it.matchesMouseButton(btn) }
                    }

                    0 -> keys.downMouse.remove(btn)
                }
            })
            handlers.add(eventHandler { (key, _, state): KeyInputEvent ->
                when (state) {
                    1 -> {
                        keys.downKeys.add(key)
                        if (key != 0) onClick { it.matchesKeyCode(key) }
                    }

                    0 -> keys.downKeys.remove(key)
                }
            })
        }
    }

    override fun remove() {
        for (handler in handlers) handler.unregister()
        handlers.clear()
        keys.attackClicks.clear()
        keys.useClicks.clear()
    }

    private fun pushTextToDesigner() {
        textColor = unpressedText.rawArgb
        textChroma = unpressedText.chroma
        textChromaSpeed = unpressedText.chromaSpeed
    }

    private fun pullTextFromDesigner(): Boolean {
        if (textColor == unpressedText.rawArgb && textChroma == unpressedText.chroma && textChromaSpeed == unpressedText.chromaSpeed) return false
        unpressedText = PolyColor(textColor, textChroma, textChromaSpeed)
        keys.rev.value++
        return true
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
        var changed = pullTextFromDesigner()
        val inStep = if (fadeInMs <= 0f) 1f else dtMs / fadeInMs
        val outStep = if (fadeOutMs <= 0f) 1f else dtMs / fadeOutMs
        fun pollTo(state: MutableState<Float>, target: Float) {
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

        if (isCustom) {
            if (screenOpen()) {
                keys.downKeys.clear()
                keys.downMouse.clear()
            }
            pollTo(keys.custom, if (customBind.test(keys.downKeys, keys.downMouse, heldMods())) 1f else 0f)
            return changed
        }

        fun poll(state: MutableState<Float>, key: KeyMapping) = pollTo(state, if (key.isDown) 1f else 0f)
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

    private fun screenOpen(): Boolean = Platform.screen().current<Any?>() != null

    private fun heldMods(): Byte {
        val k = Platform.compatibility().keys()
        var mods = KeyModifiers.NONE
        fun add(a: Int, b: Int, flag: Byte) {
            if (a in keys.downKeys || b in keys.downKeys) mods = (mods or flag)
        }
        add(k.keyLeftShift, k.keyRightShift, KeyModifiers.SHIFT)
        add(k.keyLeftControl, k.keyRightControl, KeyModifiers.CTRL)
        add(k.keyLeftAlt, k.keyRightAlt, KeyModifiers.ALT)
        add(k.keyLeftSuper, k.keyRightSuper, KeyModifiers.META)
        return mods
    }

    private fun customBindLabel(): String {
        if (!customBind.isBound) return UNKNOWN_KEY
        val single = customBind.mods == KeyModifiers.NONE &&
            customBind.mouseBtns?.isNotEmpty() != true &&
            customBind.keyCodes?.size == 1
        if (single) arrowLabel(customBind.keyCodes!![0])?.let { return it }
        return customBind.displayName()
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

        if (isCustom) {
            Key(
                customLabel.ifBlank { customBindLabel() },
                keys.custom.value,
                customWidth * s + padLeft + padRight,
                customHeight * s + padTop + padBottom,
            )
            return
        }

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
        if (align != null) mod = mod.align(align)
        return mod
    }

    @Composable
    private fun KeyBackground(progress: Float, w: Float, h: Float) {
        val color = keyBg(progress)
        val radius = keyRadius
        if (color.alpha == 0) return
        PolyCanvas(PolyModifier.size(w, h)) { x, y, cw, ch ->
            KEY_PAINT.color = color.argb
            val rect = snapToPixels(canvas, x, y, cw, ch)
            if (radius > 0f) {
                canvas.drawRRect(RRect.makeLTRB(rect.left, rect.top, rect.right, rect.bottom, radius), KEY_PAINT)
            } else {
                canvas.drawRect(rect, KEY_PAINT)
            }
        }
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
            KeyBackground(progress, w, h)
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
            KeyBackground(progress, w, h)
            PolyCanvas(PolyModifier.size(w, h)) { x, y, cw, ch ->
                val s = textScale
                val half = spaceLineW * s / 2f
                val thickness = spaceLineH * s
                val cx = x + cw / 2f - 0.5f * s
                val cy = y + ch / 2f - 0.5f * s
                LINE_PAINT.strokeWidth = thickness
                if (showShadow && font == Font.Minecraft) {
                    LINE_PAINT.color = fg.darken(0.75f).argb
                    canvas.drawLine(cx - half + s, cy + s, cx + half + s, cy + s, LINE_PAINT)
                }
                LINE_PAINT.color = fg.argb
                canvas.drawLine(cx - half, cy, cx + half, cy, LINE_PAINT)
            }
        }
    }

    private fun KeyMapping.label(): String {
        for (code in ARROW_CODES) {
            if (matchesKeyCode(code)) return arrowLabel(code) ?: break
        }
        return translatedKeyMessage.string
    }

    private fun arrowLabel(code: Int): String? {
        val glyphs = font == Font.Minecraft
        return when (code) {
            InputConstants.KEY_UP -> if (glyphs) UP else UP_SHORT
            InputConstants.KEY_DOWN -> if (glyphs) DOWN else DOWN_SHORT
            InputConstants.KEY_LEFT -> if (glyphs) LEFT else LEFT_SHORT
            InputConstants.KEY_RIGHT -> if (glyphs) RIGHT else RIGHT_SHORT
            else -> null
        }
    }
}

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

    val downKeys = HashSet<Int>()
    val downMouse = HashSet<Int>()

    val custom = mutableStateOf(0f)

    val rev = mutableStateOf(0)

    var lastNanos = System.nanoTime()
}
