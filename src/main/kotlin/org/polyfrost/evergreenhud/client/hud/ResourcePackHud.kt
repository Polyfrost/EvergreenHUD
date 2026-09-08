package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
//? if < 1.21.11
//import net.minecraft.resources.ResourceLocation
//? if >= 1.21.11
import net.minecraft.resources.Identifier as ResourceLocation
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.BuiltInPackSource
import net.minecraft.server.packs.repository.Pack
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.ImageLoader
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private const val ICON = 32f
private const val GAP = 4f
private const val TITLE_GAP = 1f
private const val DESCRIPTION_WIDTH = 140f
private const val FONT_SIZE = 8f

class ResourcePackHud : Hud(
    id = "resource_pack.json",
    title = "Resource Pack",
    category = Category.INFO,
) {
    private companion object {
        private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Resource Pack")

        private const val DEFAULT_TITLE = "Default"
        private const val DEFAULT_DESCRIPTION = "The classic Minecraft experience."

        private const val FABRIC_SOURCE_KEY = "pack.source.fabricmod"

        private val DEFAULT_ICON = ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png")

        private val iconPaint by lazy { Paint() }
        private val iconCache = HashMap<String, Image>()
        private val texturedCache = HashMap<String, Boolean>()
        private var cachedIds: List<String>? = null
        private var defaultIcon: Image? = null
        private var defaultIconLoaded = false

        private object FoundTexture : RuntimeException(null, null, false, false)

        fun syncCaches(ids: List<String>) {
            if (ids == cachedIds) return
            cachedIds = ids
            iconCache.clear()
            texturedCache.clear()
        }

        fun isModProvided(pack: Pack): Boolean =
            mentionsKey(pack.packSource.decorate(Component.empty()), FABRIC_SOURCE_KEY)

        private fun mentionsKey(component: Component, key: String): Boolean {
            val contents = component.contents
            if (contents is TranslatableContents) {
                if (contents.key == key) return true
                if (contents.args.any { it is Component && mentionsKey(it, key) }) return true
            }
            return component.siblings.any { mentionsKey(it, key) }
        }

        fun hasTextures(pack: Pack): Boolean = texturedCache.getOrPut(pack.id) {
            try {
                pack.open().use { resources ->
                    for (namespace in resources.getNamespaces(PackType.CLIENT_RESOURCES)) {
                        try {
                            resources.listResources(PackType.CLIENT_RESOURCES, namespace, "textures") { _, _ -> throw FoundTexture }
                        } catch (_: FoundTexture) {
                            return@getOrPut true
                        }
                    }
                }
                false
            } catch (e: Exception) {
                LOGGER.warn("Failed to inspect pack {} for textures, assuming it has some", pack.id, e)
                true
            }
        }

        fun iconFor(id: String?): Image? {
            if (id == null) return defaultIcon()
            iconCache[id]?.let { return it }
            val icon = loadIcon(id) ?: defaultIcon() ?: return null
            iconCache[id] = icon
            return icon
        }

        private fun defaultIcon(): Image? {
            if (defaultIconLoaded) return defaultIcon
            defaultIconLoaded = true
            defaultIcon = try {
                mc.resourceManager.getResource(DEFAULT_ICON).orElse(null)
                    ?.open()?.use { ImageLoader.fromBytes(it.readBytes()) }
            } catch (e: Exception) {
                LOGGER.warn("Failed to load the fallback pack icon", e)
                null
            }
            return defaultIcon
        }

        private fun loadIcon(id: String): Image? {
            val pack = mc.resourcePackRepository.getPack(id) ?: return null
            return try {
                pack.open().use { resources ->
                    val supplier = resources.getRootResource("pack.png") ?: return null
                    supplier.get().use { ImageLoader.fromBytes(it.readBytes()) }
                }
            } catch (e: Exception) {
                LOGGER.warn("Failed to load icon from pack {}", id, e)
                null
            }
        }

        fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
            val lines = ArrayList<String>()
            for (paragraph in text.split('\n')) {
                var line = StringBuilder()
                for (word in paragraph.split(' ')) {
                    if (line.isEmpty()) {
                        line.append(word)
                        continue
                    }
                    val candidate = "$line $word"
                    if (measure(candidate) <= maxWidth) {
                        line = StringBuilder(candidate)
                    } else {
                        lines.add(line.toString())
                        line = StringBuilder(word)
                    }
                }
                lines.add(line.toString())
            }
            return lines
        }
    }

    @Switch(title = "Ignore Overlay", description = "Show the pack underneath rather than the one layered on top of it.")
    var ignoreOverlay = true

    private var lastPackId: String? = null
    private var lastIgnoreOverlay: Boolean? = null

    private var packTitle = mutableStateOf(DEFAULT_TITLE)
    private var packDescription = mutableStateOf(DEFAULT_DESCRIPTION)
    private var packIcon = mutableStateOf<Image?>(null)

    init {
        padLeft = 4f
        padRight = 4f
        padTop = 4f
        padBottom = 4f
    }

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun updateFrequency(): Long = 1.seconds.inWholeNanoseconds

    override fun setup() {
        super.setup()
        if (isReal) updateWhenChanged("ignoreOverlay")
        update()
    }

    override fun update(): Boolean {
        val selected = mc.resourcePackRepository.selectedPacks
        syncCaches(selected.map { it.id })

        val candidates = selected.filter {
            it.id != BuiltInPackSource.VANILLA_ID && !isModProvided(it) && hasTextures(it)
        }
        val pack = (if (ignoreOverlay) candidates.firstOrNull() else candidates.lastOrNull())
            ?: mc.resourcePackRepository.getPack(BuiltInPackSource.VANILLA_ID)
        if (pack?.id == lastPackId && ignoreOverlay == lastIgnoreOverlay) return false
        lastPackId = pack?.id
        lastIgnoreOverlay = ignoreOverlay

        packTitle.value = pack?.title?.string ?: DEFAULT_TITLE
        packDescription.value = pack?.description?.string ?: DEFAULT_DESCRIPTION
        packIcon.value = iconFor(pack?.id)
        return true
    }

    @Composable
    override fun Content() {
        val scale = textScale.coerceAtLeast(0.01f)
        val skiaFont = if (font == Font.Poppins) FontManager.getFont(FONT_SIZE * scale, getPoppinsFontName()) else null
        val measure: (String) -> Float =
            if (skiaFont != null) skiaFont::measureTextWidth
            else { text -> McFontQueue.measureTextWidth(text, scale) }

        val lines = wrap(packDescription.value, DESCRIPTION_WIDTH * scale, measure)

        val modifier = hudBackground().padding(padLeft, padTop, padRight, padBottom)

        PolyBox(modifier = modifier) {
            PolyRow(gap = GAP * scale) {
                Icon(ICON * scale, if (showBackground) bgRadius else 0f)
                PolyColumn(gap = TITLE_GAP * scale) {
                    Line(packTitle.value, scale)
                    for (line in lines) Line(line, scale)
                }
            }
        }
    }

    @Composable
    private fun Icon(size: Float, radius: Float) {
        val icon = packIcon.value ?: return
        PolyCanvas(PolyModifier.size(size, size)) { x, y, w, h ->
            val corner = radius.coerceIn(0f, minOf(w, h) / 2f)
            if (corner <= 0f) {
                image(icon, x, y, w, h, iconPaint)
                return@PolyCanvas
            }
            save()
            clipRRect(x, y, w, h, corner)
            image(icon, x, y, w, h, iconPaint)
            restore()
        }
    }

    @Composable
    private fun Line(text: String, scale: Float) {
        val color = PolyColor(textColor, textChroma, textChromaSpeed)
        if (font == Font.Minecraft) {
            PolyMcText(text, color = color, shadow = showShadow, scale = scale)
        } else {
            PolyText(
                text,
                color = color,
                fontSize = FONT_SIZE * scale,
                shadow = showShadow,
                shadowColor = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed),
                shadowOffset = shadowOffsetX,
                font = getPoppinsFontName(),
            )
        }
    }

    override fun clone(): Hud = (super.clone() as ResourcePackHud).also {
        it.packTitle = mutableStateOf(DEFAULT_TITLE)
        it.packDescription = mutableStateOf(DEFAULT_DESCRIPTION)
        it.packIcon = mutableStateOf(null)
        it.lastPackId = null
        it.lastIgnoreOverlay = null
    }
}
