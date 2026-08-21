// Derived from AppleSkin (https://github.com/squeek502/AppleSkin), made by squeek502.
// AppleSkin is licensed under the Unlicense (public domain).

package org.polyfrost.evergreenhud.client.utils

import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.world.food.FoodData
import net.minecraft.world.phys.Vec3
import org.polyfrost.evergreenhud.client.SaturationChangedEvent
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.utils.v1.dsl.mc

object SaturationTracker {
    @Volatile
    var saturation = 0.0f
        private set

    private var foodLevel = 20
    private var lastPosition: Vec3? = null
    private var lastPosted = Float.NaN

    fun initialize() {
        eventHandler { _: TickEvent.End -> tick() }

        eventHandler { (packet): PacketEvent.Receive ->
            if (packet !is ClientboundDamageEventPacket) return@eventHandler
            val player = mc.player ?: return@eventHandler
            if (packet.entityId() != player.id) return@eventHandler
        }
    }

    fun onServerSync(data: FoodData) {
        if (mc.player?.foodData !== data) return
        foodLevel = data.foodLevel
        saturation = data.saturationLevel
        post()
    }

    private fun tick() {
        val player = mc.player
        if (player == null) {
            reset()
            return
        }
    }

    private fun reset() {
        saturation = 0.0f
        foodLevel = 20
        lastPosition = null
        lastPosted = Float.NaN
    }

    private fun post() {
        if (saturation == lastPosted) return
        lastPosted = saturation
        EventManager.INSTANCE.post(SaturationChangedEvent(saturation))
    }
}
