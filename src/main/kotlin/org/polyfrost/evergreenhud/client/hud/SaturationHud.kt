package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.SaturationChangedEvent
import org.polyfrost.evergreenhud.client.utils.GenericNumberHud
import org.polyfrost.evergreenhud.client.utils.SaturationTracker
import org.polyfrost.oneconfig.api.config.v1.annotations.RangeSlider
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager

class SaturationHud : GenericNumberHud(
    title = "Saturation",
    category = Category.INFO,
    defaultValue = 5f,
) {
    @RangeSlider(title = "Show HUD Within Saturation Range", description = "Show the Saturation HUD only within a specific range. 0-20 always shows.", min = 0F, max = 20F, step = 1F, subcategory = "Visibility")
    private var showRange = floatArrayOf(0f, 20f)

    override fun setup() {
        super.setup()
        if (isReal) {
            updateWithNumber(SaturationTracker.saturation)
            updateWhenChanged("showRange")
        }
        eventHandler { (saturation): SaturationChangedEvent ->
            updateWithNumber(saturation)
        }
    }

    override fun clone(): Hud = (super.clone() as SaturationHud).apply {
        showRange = this@SaturationHud.showRange.copyOf()
    }

    override fun getText(): String {
        val value = SaturationTracker.saturation

        val editing = HudManager.isEditing
        autoHidden = !editing && (value < showRange[0] || value > showRange[1])

        return format(value)
    }
}
