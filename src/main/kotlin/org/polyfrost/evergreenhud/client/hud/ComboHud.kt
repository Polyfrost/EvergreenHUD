package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.ServerDamageEntityEvent
import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.evergreenhud.client.utils.uniqueEntityId
import org.polyfrost.oneconfig.api.config.v1.annotations.Number
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.utils.v1.dsl.mc

class ComboHud : CachedTextHud(
    title = "Combo",
    category = Category.COMBAT,
    suffix = " hits",
    defaultText = "0",
) {
    @Slider(title = "Discard Time", min = 1F, max = 10F, step = 1F)
    var discardTime = 2F

    @Text(title = "No Hit Message")
    var noHitMessage = "0"

    @Number(title = "Minimum Combo to Show", description = "Only show the HUD once your combo reaches this many hits. 0 always shows.", min = 0F, max = 100F, subcategory = "Visibility")
    var minCombo = 0F

    private var lastHitTime = 0L
    private var lastAttackId = -1

    private var currentCombo = 0
        set(value) {
            if (field == value) return
            field = value
            if (value == 0) updateWithText(noHitMessage)
            else updateWithText(value)
            updateVisibility()
        }

    override fun setup() {
        super.setup()

        eventHandler<TickEvent.Start> {
            if (currentCombo == 0) return@eventHandler
            if (System.currentTimeMillis() - lastHitTime >= discardTime * 1000L) {
                currentCombo = 0
                lastAttackId = -1
            }
        }

        eventHandler { (attacker, target): ServerDamageEntityEvent ->
            if (target == mc.player) {
                currentCombo = 0
                return@eventHandler
            }

            if (attacker != mc.player) {
                return@eventHandler
            }

            if (target.uniqueEntityId == lastAttackId) {
                currentCombo++
            } else {
                currentCombo = 1
            }

            lastHitTime = System.currentTimeMillis()
            lastAttackId = target.uniqueEntityId
        }

        if (isReal) {
            updateWhenChanged("noHitMessage")
            updateWhenChanged("minCombo")
        }
    }

    private fun updateVisibility() {
        if (!HudManager.isEditing) {
            autoHidden = minCombo > 0F && currentCombo < minCombo.toInt()
        }
    }
}