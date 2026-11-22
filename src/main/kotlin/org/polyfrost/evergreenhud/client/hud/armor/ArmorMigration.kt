package org.polyfrost.evergreenhud.client.hud.armor

import org.polyfrost.oneconfig.api.config.v1.migrate.ConfigMigrations
import org.polyfrost.oneconfig.api.config.v1.migrate.MigrationScope
import org.polyfrost.oneconfig.api.hud.v1.HudGroups

internal object ArmorMigration {
    private const val MOD_ID = "evergreenhud"
    private const val OLD_CLASS = "org.polyfrost.evergreenhud.client.hud.ArmorHud"
    private const val GROUP_PREFIX = "evergreenhud-armor${HudGroups.PRESET_SEPARATOR}"
    private const val VERTICAL_BIT = 1
    private const val REVERSED_BIT = 2
    private const val VANILLA_STYLE = 1

    private val OLD_FILE = Regex("huds/(\\d+-)?armor\\.json")

    private val REMOVED_KEYS = listOf(
        "showHelmet", "showChestplate", "showLeggings", "showBoots", "showMainHand", "showOffhand",
        "padding", "direction", "reversed", "staticWidth", "staticW", "staticH",
    )

    fun register() {
        ConfigMigrations.register(MOD_ID, 1) { splitAll() }
    }

    private fun MigrationScope.splitAll() {
        val olds = listFiles("huds", "*armor.json")
            .filter { OLD_FILE.matches(it) }
            .mapNotNull { file -> readJson(file)?.takeIf { it["hudClass"] == OLD_CLASS }?.let { file to it } }
        val loose = olds.filter { (_, old) -> keptPieces(old).size == 1 && (old["groupPath"] as? String).isNullOrEmpty() }
        val wouldFormDefaultGroup = loose.flatMap { (_, old) -> keptPieces(old) }.toSet().containsAll(ArmorPiece.entries)
        for (entry in olds) {
            val file = entry.first
            val old = readJson(file) ?: continue
            split(file, old, ownGroup = wouldFormDefaultGroup && entry in loose)
        }
    }

    private fun groupSuffix(file: String): String =
        OLD_FILE.matchEntire(file)?.groupValues?.get(1)?.removeSuffix("-")?.takeIf { it.isNotEmpty() }?.let { "x$it" } ?: "1"

    private fun keptPieces(old: Map<String, Any?>): List<ArmorPiece> = ArmorPiece.entries.filter { old[it.legacyToggle] != false }

    private fun vanillaTextPosition(position: Int, vertical: Boolean): Int = when {
        vertical && position == TEXT_ABOVE -> TEXT_LEFT
        vertical && position == TEXT_BELOW -> TEXT_RIGHT
        !vertical && position == TEXT_LEFT -> TEXT_ABOVE
        !vertical && position == TEXT_RIGHT -> TEXT_BELOW
        else -> position
    }

    private fun MigrationScope.split(file: String, old: Map<String, Any?>, ownGroup: Boolean) {
        val prefix = file.removeSuffix("armor.json")
        val kept = keptPieces(old)
        val vertical = (old["direction"] as? Number)?.toInt() == 1
        val reversed = old["reversed"] == true

        val base = LinkedHashMap(old)
        REMOVED_KEYS.forEach(base::remove)
        if ((old["style"] as? Number)?.toInt() == VANILLA_STYLE && ((old["extraInfo"] as? Number)?.toInt() ?: 0) != 0) {
            base["textPosition"] = vanillaTextPosition((old["textPosition"] as? Number)?.toInt() ?: TEXT_RIGHT, vertical)
        }
        if (kept.size > 1) {
            base["pendingLayout"] = (if (vertical) VERTICAL_BIT else 0) or (if (reversed) REVERSED_BIT else 0)
            base["pendingGap"] = (old["padding"] as? Number)?.toFloat() ?: ArmorLayout.DEFAULT_GAP
            base["migrationBatch"] = file
        }
        val presetGroup = "$GROUP_PREFIX${groupSuffix(file)}"
        val parentGroup = old["groupPath"] as? String ?: ""
        if (kept.size > 1) {
            base["groupPath"] = if (parentGroup.isEmpty()) presetGroup else "$parentGroup${HudGroups.SEPARATOR}$presetGroup"
        } else if (ownGroup) {
            base["groupPath"] = presetGroup
        }

        val written = kept.map { piece ->
            val target = "${prefix}armor-${piece.key}.json"
            if (!exists(target)) writeJson(target, base + ("hudClass" to hudClassName(piece)))
            target
        }

        if (written.isNotEmpty()) retargetAnchors(file, written, vertical, reversed)
        deleteFile(file)
    }

    private fun hudClassName(piece: ArmorPiece): String =
        "${ArmorPiece::class.java.packageName}.Armor${piece.title.replace(" ", "")}Hud"

    private fun MigrationScope.retargetAnchors(from: String, pieces: List<String>, vertical: Boolean, reversed: Boolean) {
        val ordered = if (reversed) pieces.asReversed() else pieces
        for (file in listFiles("huds", "*.json")) {
            val anchored = readJson(file)?.takeIf { it["anchorTargetId"] == from } ?: continue
            val point = anchored["anchorPoint"] as? String ?: ""
            editJson(file) { this["anchorTargetId"] = ordered[pieceIndex(point, vertical, ordered.size)] }
        }
    }

    private fun pieceIndex(point: String, vertical: Boolean, count: Int): Int {
        val atStart = if (vertical) point.startsWith("Top") else point.endsWith("Left")
        val atEnd = if (vertical) point.startsWith("Bottom") else point.endsWith("Right")
        return when {
            atStart -> 0
            atEnd -> count - 1
            else -> count / 2
        }
    }
}
