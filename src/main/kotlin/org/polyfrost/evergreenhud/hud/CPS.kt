package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.lwjgl.input.Mouse
import org.polyfrost.evergreenhud.config.HudConfig

class CPS: HudConfig("CPS", "evergreenhud/cps.json", false) {
    @HUD(name = "Main")
    var hud = CPSHud()

    init {
        initialize()
    }

    class CPSHud: SingleTextHud("CPS", true, 0, 50) {

        @Switch(
            name = "Update Fast"
        )
        var updateFast = false

        @Text(
            name = "CPS Button Divider"
        )
        var divider = "|"

        @Dropdown(
            name = "Button",
            options = ["Left", "Right", "Both"]
        )
        var button = 2

        private val left = ArrayDeque<Long>()
        private var leftPressed = false
        private val right = ArrayDeque<Long>()
        private var rightPressed = false

        init {
            EventManager.INSTANCE.register(this)
        }

        @Subscribe
        private fun onRenderTick(event: RenderEvent) {
            if (event.stage == Stage.END) {
                var pressed = Mouse.isButtonDown(mc.gameSettings.keyBindAttack.keyCode + 100)

                if (pressed != leftPressed) {
                    leftPressed = pressed
                    if (pressed) left.add(System.currentTimeMillis())
                }

                pressed = Mouse.isButtonDown(mc.gameSettings.keyBindUseItem.keyCode + 100)

                if (pressed != rightPressed) {
                    rightPressed = pressed
                    if (pressed) right.add(System.currentTimeMillis())
                }

                val currentTime = System.currentTimeMillis()
                if (!left.isEmpty()) {
                    while ((currentTime - left.first()) > 1000) {
                        left.removeFirst()
                        if (left.isEmpty()) break
                    }
                }
                if (!right.isEmpty()) {
                    while ((currentTime - right.first()) > 1000) {
                        right.removeFirst()
                        if (right.isEmpty()) break
                    }
                }
            }
        }

        override fun getText(example: Boolean): String {
            return when (button) {
                0 -> left.size.toString()
                1 -> right.size.toString()
                2 -> "${left.size} $divider ${right.size}"
                else -> throw IllegalStateException()
            }
        }

        override fun getTextFrequent(example: Boolean): String? {
            return if (updateFast) {
                getText(example)
            } else {
                null
            }
        }

    }
}