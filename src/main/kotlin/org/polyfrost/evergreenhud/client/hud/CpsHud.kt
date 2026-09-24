package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.evergreenhud.client.utils.fastRemoveIfReversed
import org.polyfrost.evergreenhud.client.utils.matchesKeyCode
import org.polyfrost.evergreenhud.client.utils.matchesMouseButton
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent
import org.polyfrost.oneconfig.api.event.v1.events.MouseInputEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.time.Duration.Companion.milliseconds

class CpsHud : CachedTextHud(
    title = "CPS",
    category = Category.INFO,
    defaultText = "0 | 0",
) {
    @Text(title = "CPS Button Divider")
    var divider = " | "

    @RadioButton(
        title = "Button",
        options = ["Left", "Right", "Both"]
    )
    var mode = 2

    @Slider(
        title = "Hide After (s)",
        description = "Hide the HUD once you haven't clicked for this many seconds. 0 keeps it always shown.",
        min = 0F, max = 30F, step = 1F
    )
    var hideAfter = 0f

    private var left: ArrayList<Long> = ArrayList(20)
    private var right: ArrayList<Long> = ArrayList(10)

    private var handlers: ArrayList<EventHandler<*>> = ArrayList(2)

    private var lastClick = System.nanoTime()

    private val hideAfterNanos get() = (hideAfter * 1_000_000_000.0).toLong()

    override fun setup() {
        super.setup()

        if (isReal) {
            updateWhenChanged("mode")
            updateWhenChanged("divider")
            addCallback("hideAfter") {
                lastClick = System.nanoTime()
                updateAndRecalculate()
            }
            handlers.add(eventHandler { (btn, state): MouseInputEvent ->
                if (state == 1) {
                    val options = mc.options ?: return@eventHandler
                    var counted = false
                    if (options.keyAttack.matchesMouseButton(btn)) { onLeftClick(); counted = true }
                    if (options.keyUse.matchesMouseButton(btn)) { onRightClick(); counted = true }
                    if (counted) updateAndRecalculate()
                }
            })
            handlers.add(eventHandler { (key, _, state): KeyInputEvent ->
                if (state == 1 && key != 0) {
                    val options = mc.options ?: return@eventHandler
                    var counted = false
                    if (options.keyAttack.matchesKeyCode(key)) { onLeftClick(); counted = true }
                    if (options.keyUse.matchesKeyCode(key)) { onRightClick(); counted = true }
                    if (counted) updateAndRecalculate()
                }
            })
        }
    }

    override fun clone(): Hud = (super.clone() as CpsHud).apply {
        left = ArrayList(20)
        right = ArrayList(10)
        handlers = ArrayList(2)
    }

    override fun remove() {
        for (handler in handlers) handler.unregister()
        handlers.clear()
        left.clear()
        right.clear()
    }

    private fun onLeftClick() {
        if (mode != 1) {
            left.add(System.nanoTime())
            registerClick()
        }
    }

    private fun onRightClick() {
        if (mode > 0) {
            right.add(System.nanoTime())
            registerClick()
        }
    }

    private fun registerClick() {
        lastClick = System.nanoTime()
    }

    override fun getText(): String {
        val time = System.nanoTime()
        left.fastRemoveIfReversed { time - it > 1_000_000_000 }
        right.fastRemoveIfReversed { time - it > 1_000_000_000 }
        val nleft = left.size
        val nright = right.size
        val sb = StringBuilder()
        when (mode) {
            0 -> sb.append(nleft)
            1 -> sb.append(nright)
            2 -> sb.append(nleft).append(divider).append(nright)
        }

        return sb.toString()
    }

    override fun updateFrequency() = 100.milliseconds.inWholeNanoseconds

    override fun shouldShow(): Boolean = hideAfterNanos <= 0L || System.nanoTime() - lastClick <= hideAfterNanos
}
