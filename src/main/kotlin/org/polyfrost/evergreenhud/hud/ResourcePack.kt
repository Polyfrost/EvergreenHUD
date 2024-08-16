package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.renderer.TextRenderer
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.ResourcePackRepository
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import org.polyfrost.evergreenhud.config.HudConfig

class ResourcePack: HudConfig("Resource Pack", "evergreenhud/resourcepack.json", false) {

    @HUD(name = "Main")
    var hud = ResourcePackHUD()

    init {
        initialize()
    }

    class ResourcePackHUD: BasicHud(true, 100f, 0f){

        init {
            MinecraftForge.EVENT_BUS.register(this)
        }

        @SubscribeEvent
        fun onEntityJoin(e: EntityJoinWorldEvent) {
            if (e.entity.equals(mc.thePlayer)) {
                pack = getResourcePack()
            }
        }

        @SubscribeEvent
        fun onPackChange(e: TextureStitchEvent.Post) {
            pack = getResourcePack()
        }

        @Switch(name = "Ignore Overlay Pack", description = "Use only the first pack applied in the resource pack list.")
        var ignoreOverlay = true

        @Color(name = "Text Color")
        var color = OneColor(255, 255, 255)

        @Dropdown(name = "Text Type", options = ["No Shadow", "Shadow", "Full Shadow"])
        var textType = 0

        @Slider(
            name = "Icon Size",
            min = 10f, max = 40f
        )
        var iconSize = 24

        @Slider(
            name = "Icon Padding",
            min = 0f, max = 10f
        )
        var iconPadding = 5

        @Switch(
            name = "Remove \"!    \" from beginning of pack name"
        )
        var trimFront = false

        @Switch(
            name = "Remove \".zip\" from end of pack name"
        )
        var trimZip = false

        @Switch(
            name = "Hide pack icon"
        )
        var hideIcon = false;

        @Exclude
        var pack: ResourcePackRepository.Entry? = getResourcePack()

        @Exclude
        val defaultIcon = mc.textureManager.getDynamicTextureLocation("texturepackicon", DynamicTexture(mc.resourcePackRepository.rprDefaultResourcePack.packImage))

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            GlStateManager.pushMatrix()
            GlStateManager.translate(x, y, 0f)
            GlStateManager.scale(scale, scale, 1f)
            GlStateManager.enableBlend()
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
            GL11.glColor4f(1f, 1f, 1f, 1f)
            if(!hideIcon) {
                pack?.bindTexturePackIcon(mc.textureManager) ?: mc.textureManager.bindTexture(defaultIcon)
                Gui.drawScaledCustomSizeModalRect(0, 0, 0f, 0f, 64, 64, iconSize, iconSize, 64f, 64f)
            }
            TextRenderer.drawScaledString(getResourcePackName(), if (!hideIcon) (iconSize + iconPadding).toFloat() else 0f, if(!hideIcon) (iconSize - 8) / 2f else 0f, color.rgb, TextRenderer.TextType.toType(textType), 1f)
            GlStateManager.disableBlend()
            GlStateManager.popMatrix()
        }

        override fun getWidth(scale: Float, example: Boolean): Float {
            if(hideIcon) return mc.fontRendererObj.getStringWidth(getResourcePackName()) * scale
            return (iconSize + iconPadding + mc.fontRendererObj.getStringWidth(getResourcePackName())) * scale
        }

        override fun getHeight(scale: Float, example: Boolean): Float {
            // https://github.com/Polyfrost/OneConfig/blob/f9a98fef234ed9c31e9be51344447d2ab87fcee7/src/main/java/cc/polyfrost/oneconfig/hud/TextHud.java#L137
            // Height of 1 line TextHud
            // return lines == null ? 0 : (lines.size() * 12 - 4) * scale;
            if(hideIcon) return 8f * scale
            return iconSize * scale
        }

        fun getResourcePack(): ResourcePackRepository.Entry? {
            return mc.resourcePackRepository.repositoryEntries.getOrNull(if (ignoreOverlay) 0 else mc.resourcePackRepository.repositoryEntries.size - 1)
        }

        private fun getResourcePackName(): String {
            var resourcePackName = pack?.resourcePackName ?: "Default";
            if(trimFront && resourcePackName.startsWith("!")) resourcePackName = resourcePackName.substring(1).trimStart()
            if(trimZip && resourcePackName.endsWith(".zip")) resourcePackName = resourcePackName.substring(0, resourcePackName.length - 4)
            return resourcePackName;
        }

    }

}