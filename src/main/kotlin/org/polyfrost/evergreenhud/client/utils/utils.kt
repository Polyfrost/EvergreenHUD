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
//? if > 1.21.1
import net.minecraft.util.profiling.Profiler
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.utils.v1.dsl.mc

private const val MAX_REACH_DISTANCE = 6.0f

private val Entity.accurateCollisionBox: AABB
    get() = boundingBox.expandTowards(pickRadius.toDouble(), pickRadius.toDouble(), pickRadius.toDouble())

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
    //? if > 1.21.1
    val profiler = Profiler.get()
    //? if <= 1.21.1
    //val profiler = mc.profiler
    val player = mc.player ?: return 0f
    if (!player.isAlive) return 0f
    profiler.push("evergreenhud_reach_distance_calculation")

    var result = 0f
    val collisionBox = entity.accurateCollisionBox
    val eyePos = player.getEyePosition(1.0f)
    val lookPos = player.getViewVector(1.0f)
    val adjustedPos = eyePos.add(lookPos.x * MAX_REACH_DISTANCE, lookPos.y * MAX_REACH_DISTANCE, lookPos.z * MAX_REACH_DISTANCE)
    //~ if = 1.8.9 '.orElse(null)' -> '?.facePos'
    val hit = collisionBox.clip(eyePos, adjustedPos).orElse(null)
    if (hit != null) {
        result = eyePos.distanceTo(hit).toFloat()
    }
    profiler.pop()
    return result
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

fun PolyColor.copy(): PolyColor = PolyColor(rawArgb, chroma, chromaSpeed)

fun PolyColor.toComposeColor(): Color {
    return Color(red, green, blue, alpha)
}

fun Color.toPolyColor(): PolyColor {
    return PolyColor.hex(value.toInt())
}