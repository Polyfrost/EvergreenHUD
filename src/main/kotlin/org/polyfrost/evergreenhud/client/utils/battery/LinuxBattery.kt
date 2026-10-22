package org.polyfrost.evergreenhud.client.utils.battery

import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

object LinuxBattery {
    private val POWER_SUPPLY = File("/sys/class/power_supply")

    fun obtain(root: File = POWER_SUPPLY): Battery {
        val supplies = root.listFiles() ?: return Battery.UnknownBattery
        val batteries = supplies.filter { it.read("type") == "Battery" && it.read("scope") != "Device" }
        if (batteries.isEmpty()) return Battery.UnknownBattery

        val acOnline = supplies.any { it.read("type") == "Mains" && it.read("online") == "1" }
        val statuses = batteries.map { it.read("status") }
        val charging = acOnline || "Charging" in statuses

        val energy = batteries.sumOf { it.energy("now") }
        val full = batteries.sumOf { it.energy("full") }
        val percentage = if (full > 0) {
            (energy * 100.0 / full).roundToInt()
        } else {
            batteries.mapNotNull { it.read("capacity")?.toIntOrNull() }.average().takeUnless { it.isNaN() }?.roundToInt()
                ?: return Battery.UnknownBattery
        }

        val draw = batteries.sumOf { it.draw() }
        val remaining = if (!charging && "Discharging" in statuses && draw > 0) (energy * 3600 / draw).toInt() else -1

        return Battery.ManualBattery(
            percentage = percentage.coerceIn(0, 100),
            isCharging = charging,
            lifetimeRemaining = remaining,
            isBatterySaverEnabled = File("/sys/firmware/acpi/platform_profile").readOrNull() == "low-power",
        )
    }

    private fun File.energy(suffix: String): Long =
        long("energy_$suffix") ?: long("charge_$suffix") ?: 0L

    private fun File.draw(): Long =
        abs(long("power_now") ?: long("current_now") ?: 0L)

    private fun File.long(name: String): Long? = read(name)?.toLongOrNull()

    private fun File.read(name: String): String? = File(this, name).readOrNull()

    private fun File.readOrNull(): String? = runCatching { readText().trim() }.getOrNull()
}
