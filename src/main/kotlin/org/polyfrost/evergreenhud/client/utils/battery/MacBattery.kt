package org.polyfrost.evergreenhud.client.utils.battery

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import kotlin.math.roundToInt

@Suppress("FunctionName")
object MacBattery {
    private const val UTF8 = 0x08000100
    private const val CF_NUMBER_INT = 9

    private val keyType by lazy { cfString("Type") }
    private val keyPresent by lazy { cfString("Is Present") }
    private val keyCurrent by lazy { cfString("Current Capacity") }
    private val keyMax by lazy { cfString("Max Capacity") }
    private val keyState by lazy { cfString("Power Source State") }
    private val internalBattery by lazy { cfString("InternalBattery") }
    private val acPower by lazy { cfString("AC Power") }

    fun obtain(): Battery = runCatching(::read).getOrNull() ?: Battery.UnknownBattery

    private fun read(): Battery? {
        val info = IOKit.INSTANCE.IOPSCopyPowerSourcesInfo() ?: return null
        try {
            val list = IOKit.INSTANCE.IOPSCopyPowerSourcesList(info) ?: return null
            try {
                for (i in 0 until CF.INSTANCE.CFArrayGetCount(list)) {
                    val source = CF.INSTANCE.CFArrayGetValueAtIndex(list, i) ?: continue
                    val desc = IOKit.INSTANCE.IOPSGetPowerSourceDescription(info, source) ?: continue
                    if (!desc.matches(keyType, internalBattery) || desc.bool(keyPresent) == false) continue

                    val current = desc.int(keyCurrent) ?: continue
                    val max = desc.int(keyMax)?.takeIf { it > 0 } ?: 100
                    val onAc = desc.matches(keyState, acPower)
                    val remaining = IOKit.INSTANCE.IOPSGetTimeRemainingEstimate()

                    return Battery.ManualBattery(
                        percentage = (current * 100f / max).roundToInt().coerceIn(0, 100),
                        isCharging = onAc,
                        lifetimeRemaining = if (!onAc && remaining > 0) remaining.toInt() else -1,
                        isBatterySaverEnabled = lowPowerMode(),
                    )
                }
                return null
            } finally {
                CF.INSTANCE.CFRelease(list)
            }
        } finally {
            CF.INSTANCE.CFRelease(info)
        }
    }

    private fun lowPowerMode(): Boolean = runCatching {
        val processInfo = ObjC.INSTANCE.objc_msgSend(
            ObjC.INSTANCE.objc_getClass("NSProcessInfo"),
            ObjC.INSTANCE.sel_registerName("processInfo"),
        )
        (ObjC.INSTANCE.objc_msgSend(Pointer(processInfo), ObjC.INSTANCE.sel_registerName("isLowPowerModeEnabled")) and 0xFF) != 0L
    }.getOrDefault(false)

    private fun cfString(value: String): Pointer = CF.INSTANCE.CFStringCreateWithCString(null, value, UTF8)

    private fun Pointer.value(key: Pointer): Pointer? = CF.INSTANCE.CFDictionaryGetValue(this, key)

    private fun Pointer.matches(key: Pointer, expected: Pointer): Boolean =
        value(key)?.let { CF.INSTANCE.CFEqual(it, expected).toInt() != 0 } == true

    private fun Pointer.bool(key: Pointer): Boolean? = value(key)?.let { CF.INSTANCE.CFBooleanGetValue(it).toInt() != 0 }

    private fun Pointer.int(key: Pointer): Int? {
        val number = value(key) ?: return null
        val out = IntByReference()
        return if (CF.INSTANCE.CFNumberGetValue(number, CF_NUMBER_INT, out).toInt() != 0) out.value else null
    }

    private interface IOKit : Library {
        companion object {
            val INSTANCE: IOKit = Native.loadLibrary("IOKit", IOKit::class.java) as IOKit
        }

        fun IOPSCopyPowerSourcesInfo(): Pointer?
        fun IOPSCopyPowerSourcesList(blob: Pointer): Pointer?
        fun IOPSGetPowerSourceDescription(blob: Pointer, source: Pointer): Pointer?
        fun IOPSGetTimeRemainingEstimate(): Double
    }

    private interface CF : Library {
        companion object {
            val INSTANCE: CF = Native.loadLibrary("CoreFoundation", CF::class.java) as CF
        }

        fun CFArrayGetCount(array: Pointer): Long
        fun CFArrayGetValueAtIndex(array: Pointer, index: Long): Pointer?
        fun CFDictionaryGetValue(dict: Pointer, key: Pointer): Pointer?
        fun CFStringCreateWithCString(allocator: Pointer?, value: String, encoding: Int): Pointer
        fun CFNumberGetValue(number: Pointer, type: Int, out: IntByReference): Byte
        fun CFBooleanGetValue(value: Pointer): Byte
        fun CFEqual(a: Pointer, b: Pointer): Byte
        fun CFRelease(ref: Pointer)
    }

    private interface ObjC : Library {
        companion object {
            val INSTANCE: ObjC = Native.loadLibrary("objc", ObjC::class.java) as ObjC
        }

        fun objc_getClass(name: String): Pointer
        fun sel_registerName(name: String): Pointer
        fun objc_msgSend(receiver: Pointer, selector: Pointer): Long
    }
}
