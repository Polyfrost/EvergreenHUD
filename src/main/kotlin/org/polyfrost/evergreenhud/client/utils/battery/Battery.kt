package org.polyfrost.evergreenhud.client.utils.battery

import org.apache.commons.lang3.SystemUtils
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val POLL_SECONDS = 5L

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

        private val executor by lazy {
            Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "EvergreenHUD Battery").apply { isDaemon = true }
            }
        }

        private var task: ScheduledFuture<*>? = null
        private var users = 0

        @Volatile
        var latest: Battery? = null
            private set

        @Synchronized
        fun acquire() {
            if (users++ > 0) return
            task = executor.scheduleWithFixedDelay({
                latest = runCatching { get() }.getOrDefault(UnknownBattery)
            }, 0L, POLL_SECONDS, TimeUnit.SECONDS)
        }

        @Synchronized
        fun release() {
            if (users == 0 || --users > 0) return
            task?.cancel(false)
            task = null
        }
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
