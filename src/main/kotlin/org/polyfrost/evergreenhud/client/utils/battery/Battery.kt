package org.polyfrost.evergreenhud.client.utils.battery

import org.apache.commons.lang3.SystemUtils

sealed interface Battery {
    companion object {
        @JvmStatic
        fun get(): Battery {
            return when {
                SystemUtils.IS_OS_WINDOWS -> WindowsBattery.obtain()
                SystemUtils.IS_OS_MAC -> MacBattery.obtain()
                SystemUtils.IS_OS_LINUX -> LinuxBattery.obtain()
                else -> UnknownBattery
            }
        }

        @JvmStatic
        fun isSupported(): Boolean = get() !is UnknownBattery
    }

    data object UnknownBattery : Battery {
        override val percentage: Int = -1
        override val isCharging: Boolean = false
        override val lifetimeRemaining: Int = -1
        override val isBatterySaverEnabled: Boolean = false
    }

    data class ManualBattery(
        override val percentage: Int,
        override val isCharging: Boolean,
        override val lifetimeRemaining: Int,
        override val isBatterySaverEnabled: Boolean
    ) : Battery

    val percentage: Int
    val isCharging: Boolean
    val lifetimeRemaining: Int
    val isBatterySaverEnabled: Boolean
}
