package cc.polyfrost.evergreenhud.utils

import jdk.internal.org.jline.utils.OSUtils

object BatteryHelper {
    fun getBattery(): Battery {
        if (!OSUtils.IS_WINDOWS) return UnknownBattery
        return runCatching {
            Kernel32.getPowerStatus()
        }.getOrDefault(UnknownBattery)
    }

    interface Battery {
        val percentage: Int
        val lifetimeSeconds: Int
        val isCharging: Boolean
    }

    object UnknownBattery : Battery {
        override val percentage = 100
        override val lifetimeSeconds = -1
        override val isCharging = true
    }
}