package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.config.v1.Properties.ktProperty
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.evergreenhud.client.hooks.KeyframeSync
import org.polyfrost.oneconfig.api.hud.v1.TextHud
import java.util.function.Supplier

abstract class SpacedTextHud(
    id: String,
    title: String,
    category: Category,
    prefix: String,
    suffix: String = "",
) : TextHud(id, title, category, prefix, suffix), KeyframeSync.Synced {

    private val defaultPrefix = prefix
    private val defaultSuffix = suffix

    private var spacingMigrated = false

    protected open val legacySuffixes: Map<String, String> get() = emptyMap()

    override fun addToSerialized(tree: Tree) {
        super.addToSerialized(tree)
        tree.set(
            "spacingMigrated",
            ktProperty(this::spacingMigrated)
                .addDisplayCondition(Supplier { Property.Display.HIDDEN })
        )
    }

    @Transient
    private var liveValue: String? = null

    @Transient
    private var appliedPlayback: String? = null

    override fun setup() {
        super.setup()
        if (isReal) {
            migrateSpacing()
            KeyframeSync.register(this, KeyframeSync.channel(this) ?: return) { (if (it.showsLive()) "1" else "0") + it.liveValue.orEmpty() }
            KeyframeSync.track(this)
        }
    }

    private fun playbackState(): String? = KeyframeSync.replayed(KeyframeSync.channel(this))

    protected fun shownValue(value: String?): String? = playbackState()?.substring(1) ?: value

    override fun concat(prefix: String, value: String?, suffix: String): String {
        liveValue = value
        return super.concat(prefix, shownValue(value), suffix)
    }

    final override fun shouldShow(): Boolean = playbackState()?.startsWith('1') ?: showsLive()

    protected open fun showsLive(): Boolean = true

    override fun refreshFromPlayback() {
        val state = playbackState()
        if (state == appliedPlayback) return
        appliedPlayback = state
        updateAndRecalculate()
    }

    private fun migrateSpacing() {
        if (spacingMigrated) return
        spacingMigrated = true

        var changed = false

        if (defaultPrefix.endsWith(' ') && prefix == defaultPrefix.dropLast(1)) {
            prefix = defaultPrefix
            changed = true
        }

        val suffixes = buildMap {
            if (defaultSuffix.startsWith(' ')) put(defaultSuffix.substring(1), defaultSuffix)
            putAll(legacySuffixes)
        }
        suffixes[suffix]?.let {
            suffix = it
            changed = true
        }

        save()
        if (changed) updateAndRecalculate()
    }
}
