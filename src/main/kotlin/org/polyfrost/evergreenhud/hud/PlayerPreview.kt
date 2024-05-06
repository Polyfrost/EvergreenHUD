package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.renderer.*
import net.minecraft.entity.EntityLivingBase
import org.polyfrost.evergreenhud.config.HudConfig
import org.polyfrost.evergreenhud.utils.selfNameTagEnabled


class PlayerPreview: HudConfig("Player Preview", "evergreenhud/playerpreview.json", false) {
    @HUD(
        name = "Self Preview"
    )
    var selfPreview = SelfPreviewHud()

    init {
        initialize()
    }

    class SelfPreviewHud: BasicHud(true, 1920 - 80f, 1080 - 120f) {

        @Switch(
            name = "Show Nametag"
        )
        var showNametag = false

        @Slider(
            name = "Rotation",
            min = 0F,
            max = 360F,
        )
        var rotation = 0

        @Exclude
        private var nametagExtend = 0
            get() = if (showNametag && selfNameTagEnabled) 26 else 0

        @Transient private var drawBackground = false
        @Transient var renderingNametag = false
            private set

        override fun shouldDrawBackground() = drawBackground

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            if (drawBackground) return
            GlStateManager.pushMatrix()
            GlStateManager.enableDepth()
            drawBackground = true
            try {
                drawAll(matrices, example)
            } finally {
                drawBackground = false
            }
            if (mc.thePlayer == null) {
                GlStateManager.disableDepth()
                GlStateManager.popMatrix()
                return
            }

            GlStateManager.color(1f, 1f, 1f, 1f)
            renderLiving(mc.thePlayer, matrices, x, y, scale, rotation)
            RenderHelper.disableStandardItemLighting()
            GlStateManager.disableRescaleNormal()
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
            GlStateManager.disableTexture2D()
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
            GlStateManager.popMatrix()
        }

        private fun renderLiving(ent: EntityLivingBase, matrices: UMatrixStack?, x: Float, y: Float, scale: Float, rotation: Int) {
            GlStateManager.enableColorMaterial()
            GlStateManager.pushMatrix()
            GlStateManager.translate(x.toDouble() + (40 * scale), y.toDouble() + (107 + nametagExtend) * scale, 50.0)
            GlStateManager.scale(-(scale * 50), scale * 50, scale * 50)
            GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f)
            val f = ent.renderYawOffset
            val f1 = ent.rotationYaw
            val f2 = ent.rotationPitch
            val f3 = ent.prevRotationYawHead
            val f4 = ent.rotationYawHead
            GlStateManager.rotate(135.0f, 0.0f, 1.0f, 0.0f)
            RenderHelper.enableStandardItemLighting()
            GlStateManager.rotate(-135.0f, 0.0f, 1.0f, 0.0f)
            val actualRotation = 360F - rotation
            ent.renderYawOffset = actualRotation
            ent.rotationYaw = actualRotation
            ent.rotationYawHead = ent.rotationYaw
            ent.prevRotationYawHead = ent.rotationYaw
            GlStateManager.translate(0.0f, 0.0f, 0.0f)
            val rendermanager = mc.renderManager
            rendermanager.playerViewX = 0f
            rendermanager.setPlayerViewY(180.0f)
            rendermanager.isRenderShadow = false
            renderingNametag = true
            rendermanager.doRenderEntity(ent, 0.0, 0.0, 0.0, 0.0f, 1.0f, false)
            renderingNametag = false
            rendermanager.isRenderShadow = true
            ent.renderYawOffset = f
            ent.rotationYaw = f1
            ent.rotationPitch = f2
            ent.prevRotationYawHead = f3
            ent.rotationYawHead = f4
            GlStateManager.popMatrix()
        }

        override fun getWidth(scale: Float, example: Boolean): Float = 80 * scale

        override fun getHeight(scale: Float, example: Boolean): Float = (120 + nametagExtend) * scale
    }

}