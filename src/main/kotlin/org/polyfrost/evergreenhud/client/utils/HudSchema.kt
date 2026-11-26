package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.config.v1.Properties.simple
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.hud.v1.Hud

const val SCHEMA_CONTENT_SCALE = 1
const val HUD_SCHEMA = SCHEMA_CONTENT_SCALE

private const val KEY = "evergreenSchema"

fun Tree.addHudSchema() {
    set(KEY, simple(HUD_SCHEMA).apply { addDisplayCondition { Property.Display.HIDDEN } })
}

fun Hud.migrateHudSchema(saved: Tree, steps: (from: Int) -> Unit): Boolean {
    val from = saved.getProp(KEY)?.getAs<Number?>()?.toInt() ?: 0
    if (from >= HUD_SCHEMA) return false
    steps(from)
    tree?.getProp(KEY)?.setAs(HUD_SCHEMA)
    return true
}
