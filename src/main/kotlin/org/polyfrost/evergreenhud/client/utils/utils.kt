package org.polyfrost.evergreenhud.client.utils

import androidx.compose.ui.graphics.Color
//? if >= 1.21.10 {
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
//?}
import net.minecraft.client.KeyMapping
//? if = 1.8.9
//import org.polyfrost.oneconfig.internal.legacy.KeyCodes
//? if = 1.8.9 {
/*import org.lwjgl.sdl.SDLKeyboard
import org.lwjgl.sdl.SDLMouse
import java.nio.FloatBuffer
*///?}
import net.minecraft.world.entity.Entity
//? if > 1.8.9
import net.minecraft.world.phys.EntityHitResult
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.utils.v1.dsl.mc

val Entity.uniqueEntityId: Int
    get() = id

val cameraYaw: Float?
    get() = (mc.cameraEntity ?: mc.player)?.yRot

fun StringBuilder.replace(string: String, value: String): StringBuilder {
    val index = indexOf(string)
    if (index != -1) {
        replace(index, index + string.length, value)
    }

    return this
}

fun calculateReachDistanceToEntity(entity: Entity): Float {
    //? if > 1.8.9 {
    val hit = mc.hitResult as? EntityHitResult ?: return 0f
    if (hit.entity != entity) return 0f
    val hitPos = hit.location
    //?} else {
    /*val hit = mc.crosshairTarget ?: return 0f
    if (hit.entity != entity) return 0f
    val hitPos = hit.facePos
    *///?}
    val camera = mc.cameraEntity ?: return 0f
    return camera.getEyePosition(1.0f).distanceTo(hitPos).toFloat()
}

inline fun <L, E> L.fastRemoveIfReversed(predicate: (E) -> Boolean) where L : MutableList<E>, L : RandomAccess {
    for (i in indices.reversed()) {
        if (i > this.size - 1) {
            continue
        }
        if (predicate(this[i])) {
            this.removeAt(i.coerceAtMost(size - 1))
        }
    }
}

fun KeyMapping.matchesMouseButton(button: Int): Boolean {
    //? if >= 1.21.10 {
    return matchesMouse(MouseButtonEvent(0.0, 0.0, MouseButtonInfo(button, 0)))
    //?} elif > 1.8.9 {
    /*return matchesMouse(button)
    *///?} else
    //return keyCode == KeyCodes.mouseToLegacy(button) - 100
}

fun KeyMapping.matchesKeyCode(keyCode: Int): Boolean {
    //? if >= 1.21.10 {
    return matches(KeyEvent(keyCode, 0, 0))
    //?} elif > 1.8.9 {
    /*return matches(keyCode, 0)
    *///?} else
    //return this.keyCode == KeyCodes.toLegacy(keyCode)
}

fun KeyMapping.isHeld(): Boolean {
    //? if = 1.8.9 {
    /*if (keyCode < 0) {
        val button = KeyCodes.mouseFromLegacy(keyCode + 100)
        return button > 0 && SDLMouse.SDL_GetMouseState(null as FloatBuffer?, null as FloatBuffer?) and (1 shl (button - 1)) != 0
    }
    val scancode = KeyCodes.fromLegacy(keyCode).value
    return scancode > 0 && (SDLKeyboard.SDL_GetKeyboardState()?.get(scancode)?.toInt() ?: 0) != 0
    *///?} else
    return isDown
}

fun PolyColor.copy(): PolyColor = PolyColor(rawArgb, chroma, chromaSpeed)

fun PolyColor.toComposeColor(): Color {
    return Color(red, green, blue, alpha)
}

fun Color.toPolyColor(): PolyColor {
    return PolyColor.hex(value.toInt())
}