package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.SaturationChangedEvent
import org.polyfrost.evergreenhud.client.utils.GenericNumberHud
import org.polyfrost.evergreenhud.client.utils.SaturationTracker
import org.polyfrost.oneconfig.api.config.v1.annotations.RangeSlider
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import kotlin.time.Duration.Companion.milliseconds

class SaturationHud : GenericNumberHud(
    title = "Saturation",
    category = Category.INFO,
    defaultValue = 5f,
) {
    @RangeSlider(title = "Show HUD Within Saturation Range", description = "Show the Saturation HUD only within a specific range. 0-20 always shows.", min = 0F, max = 20F, step = 1F, subcategory = "Visibility")
    private var showRange = floatArrayOf(0f, 20f)

    private var handler: EventHandler<*>? = null

    override fun setup() {
        super.setup()
        if (isReal) {
            updateWithNumber(SaturationTracker.saturation)
            updateWhenChanged("showRange")
        }
        handler = eventHandler { (saturation): SaturationChangedEvent ->
            updateWithNumber(saturation)
        }
    }

    override fun remove() {
        super.remove()
        handler?.unregister()
        handler = null
    }

    override fun updateFrequency(): Long = 50.milliseconds.inWholeNanoseconds

    override fun getText(): String {
        return format(if (isReal) SaturationTracker.saturation else value)
    }

    override fun showsLive(): Boolean = SaturationTracker.saturation in showRange[0]..showRange[1]
}
