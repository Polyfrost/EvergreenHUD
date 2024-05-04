package org.polyfrost.evergreenhud.utils

import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.RenderEvent
import cc.polyfrost.oneconfig.events.event.Stage
import cc.polyfrost.oneconfig.events.event.TickEvent
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe

object FrameTimeHelper {
    private var lastTime = System.currentTimeMillis().toDouble()
    val frameTimes = mutableListOf<Double>()

    init {
        EventManager.INSTANCE.register(this)
    }

    @Subscribe
    private fun onRenderTick(event: RenderEvent) {
        if (event.stage == Stage.END) {
            frameTimes += System.currentTimeMillis() - lastTime
            lastTime = System.currentTimeMillis().toDouble()
        }
    }

    @Subscribe
    private fun onTick(event: TickEvent) {
        if (event.stage == Stage.END) {
            frameTimes.clear()
        }
    }

}