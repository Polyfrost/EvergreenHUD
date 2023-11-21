package org.polyfrost.evergreenhud.utils

import org.apache.commons.lang3.SystemUtils

object BatteryHelper {
    fun getBattery(): Battery {
        if (SystemUtils.IS_OS_WINDOWS) return UnknownBattery
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