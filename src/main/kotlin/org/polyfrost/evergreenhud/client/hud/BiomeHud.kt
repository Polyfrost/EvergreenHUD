package org.polyfrost.evergreenhud.client.hud

//? if > 1.8.9 {
import net.minecraft.client.renderer.BiomeColors
import net.minecraft.locale.Language
//?} else
//import net.minecraft.client.world.color.BiomeColors
import net.minecraft.network.chat.Component
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.BlockPositionChangedEvent
import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.utils.v1.dsl.mc
//? if > 1.8.9
import kotlin.jvm.optionals.getOrNull

private val BIOME_COLORS = mapOf(
    "plains" to 0x8DC85A,
    "sunflower_plains" to 0xF2D04B,
    "snowy_plains" to 0xE4F1FA,
    "ice_spikes" to 0xA9DCF5,
    "desert" to 0xEDD98F,
    "swamp" to 0x8FA65A,
    "mangrove_swamp" to 0x7FA55C,
    "forest" to 0x5FBF4A,
    "flower_forest" to 0xE58FC9,
    "birch_forest" to 0xB8D98A,
    "old_growth_birch_forest" to 0xA6CF7A,
    "dark_forest" to 0x5E9E46,
    "pale_garden" to 0xC9CFC4,
    "dappled_forest" to 0x9CCB62,
    "taiga" to 0x6FA88A,
    "snowy_taiga" to 0xC8E4EC,
    "old_growth_pine_taiga" to 0x8FA368,
    "old_growth_spruce_taiga" to 0x7E9F6B,
    "savanna" to 0xC9C26A,
    "savanna_plateau" to 0xC2B66A,
    "windswept_savanna" to 0xD3B56E,
    "windswept_hills" to 0xA3B5A6,
    "windswept_gravelly_hills" to 0xB0AFA8,
    "windswept_forest" to 0x7FAE7B,
    "jungle" to 0x4FD14A,
    "sparse_jungle" to 0x7CCB5A,
    "bamboo_jungle" to 0x8EDB5C,
    "badlands" to 0xE07B39,
    "eroded_badlands" to 0xEC8A4B,
    "wooded_badlands" to 0xC98A52,
    "meadow" to 0xA8DB7E,
    "cherry_grove" to 0xF7A8C8,
    "grove" to 0xC4E2D8,
    "snowy_slopes" to 0xDDEBF5,
    "frozen_peaks" to 0xB5D9F0,
    "jagged_peaks" to 0xD4E4F2,
    "stony_peaks" to 0xB8B4AE,
    "river" to 0x5AA9E6,
    "frozen_river" to 0xA6D3F0,
    "beach" to 0xF0DFA0,
    "snowy_beach" to 0xE6EEF0,
    "stony_shore" to 0xA9A9A9,
    "warm_ocean" to 0x43D5EE,
    "lukewarm_ocean" to 0x45B4E8,
    "deep_lukewarm_ocean" to 0x3C9EDB,
    "ocean" to 0x3F8EE8,
    "deep_ocean" to 0x4A7BDB,
    "cold_ocean" to 0x5C8FD9,
    "deep_cold_ocean" to 0x5A7FCC,
    "frozen_ocean" to 0x9CC8EE,
    "deep_frozen_ocean" to 0x8BB5E3,
    "mushroom_fields" to 0xD368D6,
    "dripstone_caves" to 0xC09A78,
    "lush_caves" to 0x7FD66B,
    "deep_dark" to 0x4FB8C4,
    "sulfur_caves" to 0xE8E05A,
    "nether_wastes" to 0xE0574A,
    "crimson_forest" to 0xE04A5E,
    "warped_forest" to 0x3FCFB4,
    "soul_sand_valley" to 0x7FC9D6,
    "basalt_deltas" to 0x9A96A0,
    "the_end" to 0xC9A6F2,
    "end_highlands" to 0xD3B0F5,
    "end_midlands" to 0xBFA0EB,
    "small_end_islands" to 0xB596E6,
    "end_barrens" to 0xAD8FD9,
    "the_void" to 0x9E86C9,
)

//? if = 1.8.9 {
/*private val LEGACY_BIOMES = mapOf(
    "extreme_hills" to "windswept_hills",
    "extreme_hills_edge" to "windswept_hills",
    "swampland" to "swamp",
    "hell" to "nether_wastes",
    "frozenocean" to "frozen_ocean",
    "frozenriver" to "frozen_river",
    "ice_plains" to "snowy_plains",
    "ice_mountains" to "snowy_plains",
    "ice_plains_spikes" to "ice_spikes",
    "mushroomisland" to "mushroom_fields",
    "mushroomislandshore" to "mushroom_fields",
    "deserthills" to "desert",
    "foresthills" to "forest",
    "taigahills" to "taiga",
    "junglehills" to "jungle",
    "jungleedge" to "sparse_jungle",
    "stone_beach" to "stony_shore",
    "cold_beach" to "snowy_beach",
    "birch_forest_hills" to "birch_forest",
    "roofed_forest" to "dark_forest",
    "cold_taiga" to "snowy_taiga",
    "cold_taiga_hills" to "snowy_taiga",
    "mega_taiga" to "old_growth_pine_taiga",
    "mega_taiga_hills" to "old_growth_pine_taiga",
    "mega_spruce_taiga" to "old_growth_spruce_taiga",
    "redwood_taiga_hills" to "old_growth_spruce_taiga",
    "mesa" to "badlands",
    "mesa_plateau" to "badlands",
    "mesa_(bryce)" to "eroded_badlands",
    "mesa_plateau_f" to "wooded_badlands",
)
*///?}

class BiomeHud : CachedTextHud(
    title = "Biome",
    category = Category.INFO,
    defaultText = "Unknown"
) {

    @Switch(title = "Color By Biome", description = "Colours the biome name to match the biome.", subcategory = "Colors")
    private var colorByBiome = false

    private var biomeColor = BIOME_COLORS.getValue("plains")

    override fun getText(): String? = if (isReal) super.getText() else "Plains"

    override fun valueColor(): PolyColor? = if (colorByBiome) PolyColor(biomeColor or 0xFF000000.toInt()) else null

    override fun setup() {
        super.setup()
        if (isReal) updateWhenChanged("colorByBiome")
        eventHandler { (pos): BlockPositionChangedEvent ->

            val level = mc.level ?: return@eventHandler

            //? if > 1.8.9 {
            //? if >= 1.21.11
            if (!level.isInValidBounds(pos)) {
            //? if < 1.21.11
            //if (!level.isInWorldBounds(pos)) {
                return@eventHandler
            }

            val id = level.getBiome(pos)
                .unwrapKey()
                ?.getOrNull()
                //$ if >= 1.21.11 '?.identifier()' else '?.location()'
                ?.identifier()

            val translationKey = id?.toLanguageKey("biome")

            val text = if (translationKey != null && Language.getInstance().has(translationKey)) {
                Component.translatable(translationKey).string
            } else id?.toString() ?: defaultText
            biomeColor = id?.takeIf { it.namespace == "minecraft" }?.let { BIOME_COLORS[it.path] }
                ?: BiomeColors.getAverageGrassColor(level, pos)
            //?} else {
            /*val text = level.getBiome(pos).name
            val key = text.removeSuffix(" M").removeSuffix("+").lowercase().replace(' ', '_')
            biomeColor = BIOME_COLORS[LEGACY_BIOMES[key] ?: key] ?: BiomeColors.getGrassColor(level, pos)
            *///?}

            updateWithText(text)
        }
    }
}
