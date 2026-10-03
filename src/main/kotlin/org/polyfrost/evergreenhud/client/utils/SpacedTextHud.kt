package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.evergreenhud.client.utils.EvergreenTextHud
import org.polyfrost.oneconfig.api.config.v1.Properties.ktProperty
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.hud.v1.TextHud
import java.util.function.Supplier

abstract class SpacedTextHud(
    id: String,
    title: String,
    category: Category,
    prefix: String,
    suffix: String = "",
) : EvergreenTextHud(id, title, category, prefix, suffix) {

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

    override fun setup() {
        super.setup()
        if (isReal) migrateSpacing()
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
