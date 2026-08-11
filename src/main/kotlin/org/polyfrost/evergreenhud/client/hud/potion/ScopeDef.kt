package org.polyfrost.evergreenhud.client.hud.potion

class ScopeDef(
    val key: String,
    val title: String,
    val settings: EffectComponentSettings,
    val strippedFields: Set<String> = emptySet(),
    val isOverride: Boolean = true,
) {
    val description: String
        get() = if (isOverride) "Overrides for $key effects." else "Default settings, used when no override applies."
}