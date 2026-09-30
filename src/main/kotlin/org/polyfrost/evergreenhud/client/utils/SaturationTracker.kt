// Derived from AppleSkin (https://github.com/squeek502/AppleSkin), made by squeek502.
// AppleSkin is licensed under the Unlicense (public domain).

package org.polyfrost.evergreenhud.client.utils

import net.minecraft.world.food.FoodData
import org.polyfrost.evergreenhud.client.SaturationChangedEvent
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.utils.v1.dsl.mc

object SaturationTracker {
    @Volatile
    var saturation = 0.0f
        private set

    private var lastPosted = Float.NaN

    fun initialize() {
        eventHandler { _: TickEvent.End -> tick() }
    }

    fun onServerSync(data: FoodData) {
        if (mc.player?.foodData !== data) return
        saturation = data.saturationLevel
        post()
    }

    private fun tick() {
        if (mc.player == null) reset()
    }

    private fun reset() {
        saturation = 0.0f
        lastPosted = Float.NaN
    }

    private fun post() {
        if (saturation == lastPosted) return
        lastPosted = saturation
        EventManager.INSTANCE.post(SaturationChangedEvent(saturation))
    }
}
