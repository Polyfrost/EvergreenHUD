package org.polyfrost.evergreenhud.client.hud.battery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.evergreenhud.client.utils.battery.Battery
import org.polyfrost.oneconfig.api.hud.v1.Hud
import kotlin.time.Duration.Companion.seconds

class BatteryHud : Hud(
    id = "battery.json",
    title = "Battery",
    category = Category.INFO,
) {

    @Transient
    private val battery = mutableStateOf(snapshot())

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    @Composable
    override fun Content() {
        BatteryDrawable(battery.value)
    }

    override fun update(): Boolean {
        battery.value = snapshot()
        return true
    }


    override fun updateFrequency(): Long {
        return 1.seconds.inWholeNanoseconds
    }

    private fun snapshot(): Battery = Battery.get().let {
        Battery.ManualBattery(it.percentage, it.isCharging, -1, it.isBatterySaverEnabled)
    }

}