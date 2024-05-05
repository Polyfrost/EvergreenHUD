package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.ReceivePacketEvent
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe
import org.polyfrost.evergreenhud.config.HudConfig

class TPS : HudConfig("TPS", "evergreenhud/tps.json", false) {
    @HUD(name = "Main")
    var hud = TPSHud()

    init {
        initialize()
    }

    class TPSHud : SingleTextHud("TPS", true, 60, 70) {
        @Transient
        private var lastUpdated = 0L

        @Transient
        private var tpsText = "Nan"

        init {
            EventManager.INSTANCE.register(this)
        }

        @Subscribe
        fun onTimeUpdate(event: ReceivePacketEvent) {
            if (!isEnabled) return
            if (event.packet !is
                    //#if MC>=11202
                    //$$ net.minecraft.network.play.server.SPacketTimeUpdate
                    //#else
                    net.minecraft.network.play.server.S03PacketTimeUpdate
                    //#endif
                ) return

            val now = System.currentTimeMillis()
            val timeTaken = now - lastUpdated
            lastUpdated = now
            val tps = (20000.0 / timeTaken).coerceIn(0.0, 20.0)
            tpsText = "%.2f".format(tps)
        }

        override fun getText(example: Boolean) = tpsText

    }
}