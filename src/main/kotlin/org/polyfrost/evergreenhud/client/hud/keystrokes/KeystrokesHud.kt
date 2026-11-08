package org.polyfrost.evergreenhud.client.hud.keystrokes

import androidx.compose.runtime.Composable
import com.mojang.blaze3d.platform.InputConstants
//? if = 1.8.9
//import org.polyfrost.oneconfig.internal.legacy.KeyCodes
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
import org.polyfrost.compose.composables.margin
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.applyCase
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.evergreenhud.client.utils.fastRemoveIfReversed
import org.polyfrost.evergreenhud.client.utils.isHeld
import org.polyfrost.evergreenhud.client.utils.matchesKeyCode
import org.polyfrost.evergreenhud.client.utils.matchesMouseButton
import org.polyfrost.evergreenhud.client.utils.snapToPixels
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
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.internal.ui.keybind.MinecraftKeybindRegistrar
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.experimental.or
import kotlin.math.abs
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
private const val CPS_ONLY = 3

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
        options = ["None", "Small", "Large", "Only"],
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

    @Switch(title = "Per-Tick Updates", description = "Update keys once per game tick instead of in real time.")
    var perTick = false

    @Slider(title = "Background Fade In (ms)", description = "Background fade time when pressed.", min = 0F, max = 250F, step = 1F)
    var bgFadeInMs = 75f

    @Slider(title = "Background Fade Out (ms)", description = "Background fade time when released.", min = 0F, max = 250F, step = 1F)
    var bgFadeOutMs = 75f

    @Slider(title = "Text Fade In (ms)", description = "Text fade time when pressed.", min = 0F, max = 250F, step = 1F)
    var textFadeInMs = 0f

    @Slider(title = "Text Fade Out (ms)", description = "Text fade time when released.", min = 0F, max = 250F, step = 1F)
    var textFadeOutMs = 0f

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
            tree?.getProp("customBind")?.addMetadata(MinecraftKeybindRegistrar.NO_MIRROR_METADATA, true)
            KeybindManager.unregister(customBind)
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
                addCallback(option) {
                    keys.rev.value++
                    keys.dirty = true
                }
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
        var changed = pullTextFromDesigner() || keys.dirty
        keys.dirty = false
        fun step(cur: Float, target: Float, inMs: Float, outMs: Float): Float = when {
            cur < target -> (cur + if (inMs <= 0f) 1f else dtMs / inMs).coerceAtMost(target)
            cur > target -> (cur - if (outMs <= 0f) 1f else dtMs / outMs).coerceAtLeast(target)
            else -> cur
        }
        fun pollTo(fade: KeyFade, target: Float) {
            val bg = step(fade.bg.value, target, bgFadeInMs, bgFadeOutMs)
            val text = step(fade.text.value, target, textFadeInMs, textFadeOutMs)
            if (bg != fade.bg.value || text != fade.text.value) {
                fade.bg.value = bg
                fade.text.value = text
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

        fun poll(state: KeyFade, key: KeyMapping) = pollTo(state, if (isReal && if (perTick) key.isDown else key.isHeld()) 1f else 0f)
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
        val px = keys.px.value
        fun q(v: Float) = if (px > 0f) (v * px).roundToInt() / px else v
        val keyW = q(KEY * s + padLeft + padRight)
        val keyH = q(KEY * s + padTop + padBottom)
        val gap = q(keyGap * s)
        val rowW = keyW * 3 + gap * 2
        val clickW = q((rowW - gap) / 2f)
        val clickW2 = rowW - gap - clickW
        val spaceKeyH = q(spaceH * s + padTop + padBottom)
        val useArrows = arrows && font == Font.Minecraft

        if (isCustom) {
            Key(
                customLabel.ifBlank { customBindLabel() },
                keys.custom,
                q(customWidth * s + padLeft + padRight),
                q(customHeight * s + padTop + padBottom),
            )
            return
        }

        PolyColumn(gap = gap) {
            for (row in rows) when (row) {
                ROW_MOVEMENT -> {
                    PolyBox(modifier = PolyModifier.size(rowW, keyH)) {
                        Key(if (useArrows) UP else o.keyUp.label(), keys.forward, keyW, keyH, PolyAlign.Center)
                    }
                    PolyRow(gap = gap) {
                        Key(if (useArrows) LEFT else o.keyLeft.label(), keys.left, keyW, keyH)
                        Key(if (useArrows) DOWN else o.keyDown.label(), keys.back, keyW, keyH)
                        Key(if (useArrows) RIGHT else o.keyRight.label(), keys.right, keyW, keyH)
                    }
                }

                ROW_SPACEBAR -> SpaceKey(keys.jump, rowW, spaceKeyH)

                ROW_MOUSE -> PolyRow(gap = gap) {
                    ClickKey(o.keyAttack.label(), keys.attack, keys.attackCps.value, clickW, keyH)
                    ClickKey(o.keyUse.label(), keys.use, keys.useCps.value, clickW2, keyH)
                }

                ROW_SPRINT -> Key(o.keySprint.label(), keys.sprint, rowW, keyH)

                ROW_SNEAK -> Key(o.keyShift.label(), keys.sneak, rowW, keyH)
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

    private fun keyModifier(w: Float, h: Float, align: PolyAlign? = null): PolyModifier {
        var mod = PolyModifier.size(w, h)
        if (align != null) mod = mod.align(align)
        return mod
    }

    @Composable
    private fun KeyBackground(progress: Float, w: Float, h: Float) {
        keys.rev.value
        val color = keyBg(progress)
        val radius = keyRadius
        if (color.alpha == 0) return
        PolyCanvas(PolyModifier.size(w, h)) { x, y, cw, ch ->
            deviceTransform()?.get(0)?.let { if (it > 0f && abs(it - keys.px.value) > 1e-3f) keys.px.value = it }
            val r = snapToPixels(this, x, y, cw, ch)
            rect(r[0], r[1], r[2] - r[0], r[3] - r[1], color, radius.coerceAtLeast(0f))
        }
    }

    @Composable
    private fun ClickKey(label: String, progress: KeyFade, cps: Int, w: Float, h: Float) {
        keys.rev.value
        when (cpsMode) {
            CPS_SMALL -> Key(label, progress, w, h, sub = "$cps CPS")
            CPS_LARGE -> Key(if (cps > 0) cps.toString() else label, progress, w, h)
	    CPS_ONLY -> Key(cps.toString(), progress, w, h)
            else -> Key(label, progress, w, h)
        }
    }

    @Composable
    private fun Key(label: String, progress: KeyFade, w: Float, h: Float, align: PolyAlign? = null, sub: String? = null) {
        keys.rev.value
        val fg = keyFg(progress.text.value)
        PolyBox(modifier = keyModifier(w, h, align)) {
            KeyBackground(progress.bg.value, w, h)
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
    private fun McLine(raw: String, color: PolyColor, w: Float, topPad: Float, scale: Float) {
        val text = applyCase(raw)
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
    private fun KeyText(raw: String, color: PolyColor, scale: Float) {
        val text = applyCase(raw)
        PolyText(
            text,
            color = color,
            fontSize = FONT * scale,
            font = getPoppinsFontName(),
            modifier = PolyModifier.align(PolyAlign.Center),
        )
    }

    @Composable
    private fun SpaceKey(progress: KeyFade, w: Float, h: Float) {
        keys.rev.value
        val fg = keyFg(progress.text.value)
        PolyBox(modifier = keyModifier(w, h)) {
            KeyBackground(progress.bg.value, w, h)
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

    private fun KeyMapping.label(): String {
        when {
            matchesMouseButton(InputConstants.MOUSE_BUTTON_LEFT) -> return "LMB"
            matchesMouseButton(InputConstants.MOUSE_BUTTON_RIGHT) -> return "RMB"
            matchesMouseButton(InputConstants.MOUSE_BUTTON_MIDDLE) -> return "MMB"
        }
        for (code in ARROW_CODES) {
            if (matchesKeyCode(code)) return arrowLabel(code) ?: break
        }
        //~ if = 1.8.9 'translatedKeyMessage.string' -> 'KeyCodes.fromLegacy(keyCode).displayName.string'
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

private class KeyFade {
    val bg = mutableStateOf(0f)
    val text = mutableStateOf(0f)
}

private class KeyState {
    val forward = KeyFade()
    val left = KeyFade()
    val back = KeyFade()
    val right = KeyFade()
    val jump = KeyFade()
    val attack = KeyFade()
    val use = KeyFade()
    val sprint = KeyFade()
    val sneak = KeyFade()
    val attackCps = mutableStateOf(0)
    val useCps = mutableStateOf(0)
    val attackClicks = ArrayList<Long>(20)
    val useClicks = ArrayList<Long>(20)

    val downKeys = HashSet<Int>()
    val downMouse = HashSet<Int>()

    val custom = KeyFade()

    val rev = mutableStateOf(0)

    val px = mutableStateOf(0f)

    var dirty = false

    var lastNanos = System.nanoTime()
}
