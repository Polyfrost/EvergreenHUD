package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.GenericNumberHud
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

class SpeedHud : GenericNumberHud(
    title = "Speed",
    category = Category.INFO,
    suffix = " m/s"
) {
    @Switch(title = "Use X")
    var useX = true

    @Switch(title = "Use Y")
    var useY = true

    @Switch(title = "Use Z")
    var useZ = true

    @Dropdown(
        title = "Speed Unit",
        options = ["Meters per tick", "Meters per second", "Kilometers per hour", "Miles per hour"],
    )
    var speedUnit = 1

    @Switch(title = "Smooth Speed")
    var smooth = true

    @Slider(title = "Smoothing Factor", min = 0.01F, max = 1F, step = 0.01F)
    var smoothing = 0.2F

    override val legacySuffixes = mapOf(
        "m/s" to " m/s",
        "kph" to " kph",
        "mph" to " mph",
        "m/t" to " m/t",
    )

    override fun setup() {
        super.setup()

        if (isReal) {
            addCallback("speedUnit") { value: Int ->
                suffix = when (value) {
                    1 -> " m/s"
                    2 -> " kph"
                    3 -> " mph"
                    else -> " m/t"
                }

                updateAndRecalculate()
                false
            }

            updateWhenChanged("useX")
            updateWhenChanged("useY")
            updateWhenChanged("useZ")
            updateWhenChanged("smooth")
            updateWhenChanged("smoothing")
        }
    }

    override fun getText(): String {
        val player = mc.player
        if (player == null) {
            value = 0f
            return format(value)
        }

        val dx = if (useX) (player.x - player.xo).toFloat() else 0f
        val dy = if (useY) (player.y - player.yo).toFloat() else 0f
        val dz = if (useZ) (player.z - player.zo).toFloat() else 0f
        val raw = convertSpeed(sqrt(dx * dx + dy * dy + dz * dz))
        value = if (smooth) value + (raw - value) * smoothing else raw

        return format(value)
    }

    override fun updateFrequency(): Long {
        return 50.milliseconds.inWholeNanoseconds
    }

    private fun convertSpeed(speed: Float): Float {
        return when (speedUnit) {
            1 -> speed * 20f
            2 -> speed * 3.6f * 20f
            3 -> speed * 2.237f * 20f
            else -> speed
        }
    }
}
