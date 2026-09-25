package org.polyfrost.evergreenhud.client.hud

//? if > 1.8.9
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import org.polyfrost.evergreenhud.client.BlockPositionChangedEvent
import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.utils.v1.dsl.mc
//? if > 1.8.9
import kotlin.jvm.optionals.getOrNull

class BiomeHud : CachedTextHud(
    title = "Biome",
    category = Category.INFO,
    defaultText = "Unknown"
) {

    override fun setup() {
        super.setup()
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
            //?} else
            //val text = level.getBiome(pos).name

            updateWithText(text)
        }
    }
}
