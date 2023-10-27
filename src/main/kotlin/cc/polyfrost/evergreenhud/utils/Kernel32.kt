package cc.polyfrost.evergreenhud.utils

import com.sun.jna.Native
import com.sun.jna.Structure

object Kernel32 {
    init {
        Native.register("Kernel32")
    }

    private external fun GetSystemPowerStatus(result: SYSTEM_POWER_STATUS): Int

    fun getPowerStatus() = SYSTEM_POWER_STATUS().also { GetSystemPowerStatus(it) }

    // https://learn.microsoft.com/en-us/windows/win32/api/winbase/ns-winbase-system_power_status
    class SYSTEM_POWER_STATUS : Structure(), BatteryHelper.Battery {
        @JvmField
        var ACLineStatus: Byte = 0

        @JvmField
        var BatteryFlag: Byte = 0

        @JvmField
        var BatteryLifePercent: Byte = 0

        @JvmField
        var SystemStatusFlag: Byte = 0

        @JvmField
        var BatteryLifeTime = 0

        @JvmField
        var BatteryFullLifeTime = 0

        override val isCharging get() = ACLineStatus != 0.toByte()
        override val percentage get() = BatteryLifePercent.toInt().coerceIn(0, 100)
        override val lifetimeSeconds get() = BatteryLifeTime
    }
}