package org.polyfrost.evergreenhud.client.hud

import net.minecraft.world.entity.Entity
import org.polyfrost.evergreenhud.client.utils.GenericNumberHud
import org.polyfrost.evergreenhud.client.utils.calculateReachDistanceToEntity
import org.polyfrost.evergreenhud.client.utils.settledAttackHandler
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.events.AttackEntityEvent
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.time.Duration.Companion.seconds

class ReachHud : GenericNumberHud(
    title = "Reach",
    category = Category.COMBAT,
    suffix = " blocks"
) {
    @Slider(title = "Discard Time", min = 1000F, max = 10000F)
    var discardTime = 3000

    @Slider(
        title = "Hide After (s)",
        description = "Hide the HUD once you haven't hit anything for this many seconds. 0 keeps it always shown.",
        min = 0F, max = 30F, step = 1F
    )
    var hideAfter = 0f

    @Text(title = "No Hit Message")
    var noHitMessage = "0"

    private var lastTime = 0L

    private val hideAfterMillis get() = (hideAfter * 1000f).toLong()

    override fun setup() {
        super.setup()
        settledAttackHandler { event: AttackEntityEvent ->
            if (event.cancelled) return@settledAttackHandler
            val target = event.getTarget<Any>() as? Entity ?: return@settledAttackHandler
            val player = mc.player
            if (player != null && event.getPlayer<Any>() == player && !player.isSpectator) {
                val reach = calculateReachDistanceToEntity(target)
                if (reach == 0f) {
                    return@settledAttackHandler
                }

                this.value = reach
                this.lastTime = System.currentTimeMillis()
                updateWithNumber(reach)
            }
        }

        if (isReal) {
            updateWhenChanged("noHitMessage")
            addCallback("hideAfter") {
                lastTime = System.currentTimeMillis()
                updateAndRecalculate()
            }
        }
    }

    override fun getText(): String? {
        if (!isReal) return format(2.87f)
        if (value == 0f) {
            return noHitMessage
        }

        return super.getText()
    }

    override fun update(): Boolean {
        val elapsed = System.currentTimeMillis() - lastTime
        if (elapsed > discardTime) {
            value = 0f
        }

        return super.update()
    }

    override fun updateFrequency() = 1.seconds.inWholeNanoseconds

    override fun shouldShow(): Boolean =
        hideAfterMillis <= 0L || System.currentTimeMillis() - lastTime <= hideAfterMillis
}
