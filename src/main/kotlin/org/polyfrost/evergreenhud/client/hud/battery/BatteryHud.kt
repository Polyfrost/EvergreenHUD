package org.polyfrost.evergreenhud.client.hud.battery

import org.polyfrost.evergreenhud.client.utils.EvergreenHud
import androidx.compose.runtime.Composable
import org.polyfrost.oneconfig.api.hud.v1.Hud
import kotlin.time.Duration.Companion.seconds

class BatteryHud : EvergreenHud(
    id = "battery.json",
    title = "Battery",
    category = Category.INFO,
) {

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    @Composable
    override fun Content() {
        BatteryDrawable()
    }

    override fun update(): Boolean {
        return true
    }


    override fun updateFrequency(): Long {
        return 1.seconds.inWholeNanoseconds
    }

}