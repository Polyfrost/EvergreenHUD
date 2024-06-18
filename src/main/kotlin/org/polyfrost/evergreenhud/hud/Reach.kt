package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.ClientDamageEntityEvent
import org.polyfrost.evergreenhud.utils.decimalFormat
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.entity.Entity
import org.polyfrost.evergreenhud.config.HudConfig

class Reach: HudConfig("Reach", "evergreenhud/reach.json", false) {
    @HUD(name = "Main")
    var hud = ReachHud()

    init {
        initialize()
    }

    class ReachHud: SingleTextHud("Reach", true, 0, 90) {
        @Switch(name = "Trailing Zeros")
        var trailingZeros = false

        @Slider(
            name = "Accuracy",
            min = 0F,
            max = 15F
        )
        var accuracy = 1

        @Slider(
            name = "Discard Time",
            min = 0F,
            max = 10000F
        )
        var discardTime = 3000

        @Text(
            name = "No Hit Message"
        )
        var noHitMessage = "0"

        @Transient private var reach: String? = null
        @Transient private var lastHit = System.currentTimeMillis()

        init {
            EventManager.INSTANCE.register(this)
        }

        @Subscribe
        private fun onDamage(event: ClientDamageEntityEvent) {
            if (event.attacker == mc.thePlayer) {
                val hitResult = getReachDistanceFromEntity(event.target) ?: return
                lastHit = System.currentTimeMillis()
                // see comments in dfCache
                reach = decimalFormat(accuracy, trailingZeros).format(hitResult)
            }
        }

        @Subscribe
        private fun onTick(event: TickEvent) {
            if (event.stage == Stage.START) {
                if (System.currentTimeMillis() - lastHit > discardTime) {
                    lastHit = System.currentTimeMillis()
                    reach = null
                }
            }
        }

        override fun getText(example: Boolean): String = reach ?: noHitMessage

        private fun getReachDistanceFromEntity(entity: Entity): Double? {
            mc.mcProfiler.startSection("Calculate Reach Dist")

            // How far will ray travel before ending
            val maxSize = 6.0 // use 6 because creative mode is 6 and any more is literally reach
            // Bounding box of entity
            val otherBB = entity.entityBoundingBox
            // This is where people found out that F3+B is not accurate for hitboxes,
            // it makes hitboxes bigger by certain amount
            val collisionBorderSize: Float = entity.collisionBorderSize
            val otherHitbox = otherBB.expand(
                collisionBorderSize.toDouble(),
                collisionBorderSize.toDouble(),
                collisionBorderSize.toDouble()
            )
            // Not quite sure what the difference is between these two vectors
            // In actual code where this is taken from, partialTicks is always 1.0
            // So this won't decrease accuracy
            val eyePos = mc.thePlayer.getPositionEyes(1.0f)
            val lookPos = mc.thePlayer.getLook(1.0f)
            // Get vector for raycast
            val adjustedPos = eyePos.addVector(lookPos.xCoord * maxSize, lookPos.yCoord * maxSize, lookPos.zCoord * maxSize)
            val movingObjectPosition = otherHitbox.calculateIntercept(eyePos, adjustedPos) ?: return null
            // This will trigger if hit distance is more than maxSize
            val otherEntityVec = movingObjectPosition.hitVec
            // finally calculate distance between both vectors
            val dist = eyePos.distanceTo(otherEntityVec)
            mc.mcProfiler.endSection()
            return dist
        }
    }
}