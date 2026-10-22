package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.evergreenhud.client.utils.battery.Battery
import org.polyfrost.evergreenhud.client.utils.battery.LinuxBattery
import java.io.File

class LinuxBatteryTest {

    @TempDir
    lateinit var root: File

    private fun supply(name: String, vararg values: Pair<String, String>) {
        val dir = File(root, name).apply { mkdirs() }
        for ((key, value) in values) File(dir, key).writeText("$value\n")
    }

    @Test
    fun noBatteryIsUnknown() {
        supply("AC", "type" to "Mains", "online" to "1")
        supply("hidpp_battery_0", "type" to "Battery", "scope" to "Device", "capacity" to "80")
        assertEquals(Battery.UnknownBattery, LinuxBattery.obtain(root))
    }

    @Test
    fun dischargingWithEnergy() {
        supply("AC", "type" to "Mains", "online" to "0")
        supply(
            "BAT0",
            "type" to "Battery", "status" to "Discharging", "capacity" to "49",
            "energy_now" to "25000000", "energy_full" to "50000000", "power_now" to "10000000",
        )
        val battery = LinuxBattery.obtain(root)
        assertEquals(50, battery.percentage)
        assertFalse(battery.isCharging)
        assertEquals(9000, battery.lifetimeRemaining)
    }

    @Test
    fun twoBatteriesWithChargeAreCombined() {
        supply(
            "BAT0",
            "type" to "Battery", "status" to "Discharging",
            "charge_now" to "1000000", "charge_full" to "4000000", "current_now" to "-1000000",
        )
        supply(
            "BAT1",
            "type" to "Battery", "status" to "Discharging",
            "charge_now" to "3000000", "charge_full" to "4000000", "current_now" to "1000000",
        )
        val battery = LinuxBattery.obtain(root)
        assertEquals(50, battery.percentage)
        assertEquals(7200, battery.lifetimeRemaining)
    }

    @Test
    fun pluggedInCountsAsChargingWithoutEstimate() {
        supply("ADP1", "type" to "Mains", "online" to "1")
        supply("BAT0", "type" to "Battery", "status" to "Not charging", "capacity" to "80")
        val battery = LinuxBattery.obtain(root)
        assertEquals(80, battery.percentage)
        assertTrue(battery.isCharging)
        assertEquals(-1, battery.lifetimeRemaining)
    }
}
