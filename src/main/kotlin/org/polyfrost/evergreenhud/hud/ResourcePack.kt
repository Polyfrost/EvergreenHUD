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

class ResourcePack: HudConfig("Resource Pack", "evergreenhud/resourcepack.json", true) {

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
                reloadPack()
            }
        }

        @SubscribeEvent
        fun onPackChange(e: TextureStitchEvent.Post) {
            reloadPack()
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
        var iconPadding = 2

        @Exclude
        var pack: ResourcePackRepository.Entry? = mc.resourcePackRepository.repositoryEntries.getOrNull(if (ignoreOverlay) 0 else mc.resourcePackRepository.repositoryEntries.size - 1)

        @Exclude
        val defaultIcon = mc.textureManager.getDynamicTextureLocation("texturepackicon", DynamicTexture(mc.resourcePackRepository.rprDefaultResourcePack.packImage))

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            GlStateManager.pushMatrix()
            GlStateManager.translate(x, y, 0f)
            GlStateManager.scale(scale, scale, 1f)
            GlStateManager.enableBlend()
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
            GL11.glColor4f(1f, 1f, 1f, 1f)
            pack?.bindTexturePackIcon(mc.textureManager) ?: mc.textureManager.bindTexture(defaultIcon)
            Gui.drawScaledCustomSizeModalRect(0, 0, 0f, 0f, 64, 64, iconSize, iconSize, 64f, 64f)
            TextRenderer.drawScaledString(pack?.resourcePackName ?: "Default", (iconSize + iconPadding).toFloat(), (iconSize - 8) / 2f, color.rgb, TextRenderer.TextType.toType(textType), 1f)
            GlStateManager.disableBlend()
            GlStateManager.popMatrix()
        }

        override fun getWidth(scale: Float, example: Boolean): Float {
            return (iconSize + (iconPadding / 2) + mc.fontRendererObj.getStringWidth(pack?.resourcePackName ?: "Default")) * scale
        }

        override fun getHeight(scale: Float, example: Boolean): Float = iconSize * scale

        fun reloadPack() {
            pack = mc.resourcePackRepository.repositoryEntries.getOrNull(if (ignoreOverlay) 0 else mc.resourcePackRepository.repositoryEntries.size - 1)
        }

    }

}