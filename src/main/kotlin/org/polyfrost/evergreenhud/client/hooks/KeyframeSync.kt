package org.polyfrost.evergreenhud.client.hooks

import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.hud.v1.Hud
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.BiConsumer
import java.util.function.BooleanSupplier
import java.util.function.Function
import java.util.function.LongSupplier
import java.util.function.Supplier

object KeyframeSync {
    private val share = FabricLoader.getInstance().objectShare

    private val tracked: MutableSet<Synced> = Collections.newSetFromMap(WeakHashMap())

    private var wasReplaying = false

    val replaying: Boolean get() = (share.get("keyframe:replaying") as? BooleanSupplier)?.asBoolean == true

    fun initialize() {
        if (!FabricLoader.getInstance().isModLoaded("keyframe")) return
        register("evergreenhud:wall_clock") { System.currentTimeMillis().toString() }
        eventHandler { _: TickEvent.End ->
            val now = replaying
            if (!now && !wasReplaying) return@eventHandler
            wasReplaying = now
            tracked.toList().forEach(Synced::refreshFromPlayback)
        }
    }

    fun channel(hud: Hud, name: String = "text"): String? = hud.tree?.id?.let { "evergreenhud:$it:$name" }

    @Suppress("UNCHECKED_CAST")
    fun register(id: String, sampler: () -> String?) {
        share.whenAvailable("keyframe:register_channel") { _, register ->
            (register as? BiConsumer<String, Supplier<String?>>)?.accept(id, Supplier(sampler))
        }
    }

    fun <T : Any> register(owner: T, id: String, sampler: (T) -> String?) {
        val ref = WeakReference(owner)
        register(id) { ref.get()?.let(sampler) }
    }

    @Suppress("UNCHECKED_CAST")
    fun replayed(id: String?): String? = id?.let { (share.get("keyframe:channel") as? Function<String, String?>)?.apply(it) }

    private var wallBase = Long.MIN_VALUE

    private var wallBaseNanos = 0L

    fun replayedWallClock(): Long? = replayed("evergreenhud:wall_clock")?.toLongOrNull()

    fun wallMillis(): Long {
        val recorded = replayedWallClock() ?: return System.currentTimeMillis()
        val now = nanoTime()
        if (recorded != wallBase) {
            wallBase = recorded
            wallBaseNanos = now
        }
        return recorded + ((now - wallBaseNanos) / 1_000_000L).coerceIn(0L, 50L)
    }

    fun nanoTime(): Long = (share.get("keyframe:nanos") as? LongSupplier)?.asLong ?: System.nanoTime()

    fun track(synced: Synced) {
        tracked += synced
    }

    interface Synced {
        fun refreshFromPlayback()
    }
}
