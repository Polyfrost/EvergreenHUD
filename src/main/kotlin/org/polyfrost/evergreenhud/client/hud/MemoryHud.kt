package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.evergreenhud.utils.decimalFormat
import org.polyfrost.oneconfig.api.config.v1.annotations.Number
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.seconds

class MemoryHud : CachedTextHud(
    title = "Memory",
    category = Category.INFO,
    suffix = " GB"
) {
    @RadioButton(
        title = "Display Type",
        options = ["Absolute", "Percentage"]
    )
    var displayType = 0

    @Switch(title = "Trailing Zeros")
    var trailingZeros = false

    @Number(title = "Update Frequency (requires restart)", min = 0.5F, max = 5F)
    var updateFrequency = 0.5F

    private val df: DecimalFormat get() = decimalFormat(1, trailingZeros, displayType == 1)

    override val legacySuffixes = mapOf("GB" to " GB")

    override fun setup() {
        super.setup()
        if (isReal) {
            addCallback("trailingZeros") { _: Boolean ->
                updateAndRecalculate()
                false
            }

            addCallback("displayType") { value: Int ->
                suffix = when (value) {
                    1 -> ""
                    else -> " GB"
                }

                updateAndRecalculate()
                false
            }
        }
    }

    override fun updateFrequency(): Long {
        return updateFrequency.toDouble().seconds.inWholeNanoseconds
    }

    override fun getText(): String {
        if (!isReal) return df.format(if (displayType == 1) 0.42 else 3.4).trim()
        val r = Runtime.getRuntime()
        val usedBytes = bytesToMb(r.totalMemory() - r.freeMemory())
        return df.format(when (displayType) {
            1 -> usedBytes.toDouble() / bytesToMb(r.maxMemory()).toDouble()
            else -> usedBytes / 1024.0
        }).trim()
    }

    private fun bytesToMb(bytes: Long): Long {
        return bytes / 1024L / 1024L
    }
}
