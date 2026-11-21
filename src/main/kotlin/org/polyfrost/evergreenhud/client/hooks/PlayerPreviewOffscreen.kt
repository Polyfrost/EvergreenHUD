@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hooks

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
//?} else {
/*import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.platform.Lighting
import net.minecraft.util.math.MathHelper
import org.lwjgl.opengl.GL11
*///?}
import net.minecraft.client.Minecraft
//? if >= 26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState
//?} elif >= 1.21.10
//import net.minecraft.client.renderer.state.CameraRenderState
//? if >= 1.21.10
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//? if >= 26.2 {
import net.minecraft.client.renderer.SubmitNodeStorage
//?}
//? if >= 26.3 {
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import java.util.Optional
import java.util.OptionalDouble
//? }
//? if > 1.8.9 && < 1.21.10
//import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
//? if >= 1.21.10
import net.minecraft.world.phys.Vec3
//? if > 1.8.9 {
import org.joml.Quaternionf
//?}
import org.polyfrost.compose.minecraft.canvas.PolyOffscreen
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.slf4j.LoggerFactory

private const val MAX_DIM = 1024
private const val FULL_BRIGHT = 0xF000F0

private const val OFFSET_Y = 0.0625f

private const val HEIGHT_CATCHUP = 12f

private const val STALE_NANOS = 1_000_000_000L

private const val MIN_RENDER_NANOS = 1_000_000_000L / 60

object PlayerPreviewOffscreen {
    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Player Preview")

    private val client: Minecraft get() = mc

    data class Request(
        val widthPx: Int,
        val heightPx: Int,
        val sizePx: Float,
        val bodyRot: Float,
        val headRot: Float?,
        val headPitch: Float?,
        val modelTilt: Float,
        val partialTick: Float,
        val verticalAnchor: Float,
        val nametag: Boolean,
    )

    private class Slot {
        var offscreen: PolyOffscreen? = null

        var anchorHeight = -1f
        var lastAnchorNanos = 0L

        var lastUsedNanos = 0L

        var lastRequest: Request? = null
        var lastRenderNanos = 0L

        fun invalidate() {
            offscreen?.close()
            offscreen = null
        }
    }

    private val slots = LinkedHashMap<Any, Slot>()
    private val pending = LinkedHashMap<Any, Request>()
    private var failed = false
    private var loggedFirstFrame = false
    private var loggedResolveFailure = false

    @JvmField var rendering = false
    @JvmField var nameTag = false

    fun initialize() {
        eventHandler { _: WorldEvent.Unload -> invalidate() }
        //? if >= 1.21.10
        PolyOffscreen.onBeforePresent(::render)
    }

    fun submit(owner: Any, request: Request) {
        pending[owner] = request
    }

    @JvmStatic
    fun render() {
        val requests = if (pending.isEmpty()) emptyList() else pending.entries.map { it.key to it.value }
        pending.clear()
        if (failed) return

        val player = client.player ?: return
        val now = System.nanoTime()
        for ((owner, request) in requests) {
            val slot = slots.getOrPut(owner) { Slot() }
            slot.lastUsedNanos = now
            if (!renderDue(slot, request, now)) continue
            slot.lastRequest = request
            val behind = now - slot.lastRenderNanos
            slot.lastRenderNanos = if (behind in MIN_RENDER_NANOS until 2 * MIN_RENDER_NANOS) slot.lastRenderNanos + MIN_RENDER_NANOS else now
            renderSlot(slot, request, player)
            if (failed) break
        }
        evictStale(now)
    }

    private fun renderDue(slot: Slot, request: Request, now: Long): Boolean {
        val last = slot.lastRequest ?: return true
        if (slot.offscreen?.hasContent != true) return true
        if (last.widthPx != request.widthPx || last.heightPx != request.heightPx) return true
        return request != last && now - slot.lastRenderNanos >= MIN_RENDER_NANOS
    }

    private fun renderSlot(slot: Slot, request: Request, player: Player) {
        val requestedWidth = request.widthPx.coerceAtLeast(1)
        val requestedHeight = request.heightPx.coerceAtLeast(1)
        val fit = minOf(1f, MAX_DIM.toFloat() / requestedWidth, MAX_DIM.toFloat() / requestedHeight)
        val width = (requestedWidth * fit).toInt().coerceIn(1, MAX_DIM)
        val height = (requestedHeight * fit).toInt().coerceIn(1, MAX_DIM)

        try {
            val offscreen = slot.offscreen ?: PolyOffscreen(width, height).also { slot.offscreen = it }
            offscreen.resize(width, height)
            offscreen.render {
                //? if >= 1.21.10 {
                val rt = offscreen.renderTarget ?: return@render
                renderPlayer(slot, rt, request, player, width, height, fit)
                //?} else
                //renderInto(slot, request, player, width, height, fit)
            }
            if (!offscreen.hasContent) {
                if (!loggedResolveFailure) {
                    loggedResolveFailure = true
                    LOGGER.warn("Player preview could not acquire an offscreen target")
                }
                return
            }
            if (!loggedFirstFrame) {
                loggedFirstFrame = true
                LOGGER.info("Player preview target ready ({}x{} px)", width, height)
            }
        } catch (throwable: Throwable) {
            LOGGER.warn("Player preview render failed; disabling", throwable)
            failed = true
            invalidate()
        }
    }

    private fun evictStale(now: Long) {
        val iterator = slots.entries.iterator()
        while (iterator.hasNext()) {
            val slot = iterator.next().value
            if (now - slot.lastUsedNanos < STALE_NANOS) continue
            slot.invalidate()
            iterator.remove()
        }
    }

    fun drawInto(owner: Any, ctx: RenderContext, width: Float, height: Float) {
        if (client.player == null) return
        val offscreen = slots[owner]?.offscreen ?: return
        try {
            with(offscreen) { ctx.blit(0f, 0f, width, height) }
        } catch (throwable: Throwable) {
            LOGGER.warn("Player preview blit failed", throwable)
        }
    }

    //? if > 1.8.9 && < 1.21.10 {
    /*private fun renderInto(slot: Slot, request: Request, player: Player, width: Int, height: Int, fit: Float) {
        renderPlayer(slot, request, player, width, height, fit)
    }
    *///?} else if <= 1.8.9 {
    /*private fun renderInto(slot: Slot, request: Request, player: Player, width: Int, height: Int, fit: Float) {
        // the held item layer disables GL_RESCALE_NORMAL, which darkens every layer drawn after it at this scale
        GL11.glEnable(GL11.GL_NORMALIZE)
        try {
            renderPlayer(slot, request, player, width, height, fit)
        } finally {
            GL11.glDisable(GL11.GL_NORMALIZE)
        }
    }
    *///?}

    //? if >= 1.21.10 {
    private fun renderPlayer(slot: Slot, rt: RenderTarget, request: Request, player: Player, width: Int, height: Int, fit: Float) {
        rendering = true
        nameTag = request.nametag
        val state = try {
            client.entityRenderDispatcher.extractEntity(player, request.partialTick) as? AvatarRenderState ?: return
        } finally {
            rendering = false
            nameTag = false
        }

        state.lightCoords = FULL_BRIGHT
        if (!request.nametag) state.nameTag = null
        state.bodyRot = request.bodyRot
        request.headRot?.let { state.yRot = it }
        request.headPitch?.let { state.xRot = it }
        val boxHeight = state.boundingBoxHeight / state.scale
        state.boundingBoxWidth /= state.scale
        state.boundingBoxHeight = boxHeight
        state.scale = 1f

        val anchor = smoothAnchorHeight(slot, boxHeight)

        val size = request.sizePx * fit
        val tilt = Math.toRadians(request.modelTilt.toDouble()).toFloat()
        val rotation = Quaternionf().rotateZ(Math.PI.toFloat())
        if (tilt != 0f) rotation.mul(Quaternionf().rotateX(tilt))

        val pose = PoseStack()
        pose.translate(width / 2f, height * request.verticalAnchor, 0f)
        pose.scale(size, size, -size)
        pose.translate(0f, anchor / 2f + OFFSET_Y, 0f)
        //? if >= 26.3 {
        pose.rotate(rotation)
        //? } else {
        /*pose.mulPose(rotation)
        *///? }

        //? if >= 26.2 {
        client.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
        //? } else {
        /*client.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        *///? }

        val camera = CameraRenderState().apply {
            orientation = Quaternionf().rotateY(Math.PI.toFloat()).apply { if (tilt != 0f) mul(Quaternionf().rotateX(tilt)) }
            pos = Vec3.ZERO
            //? if < 26.1 {
            /*entityPos = Vec3.ZERO
            *///? }
        }

        //? if >= 26.3 {
        val colorView = rt.colorTextureView ?: return
        val depthView = rt.depthTextureView ?: return
        val storage = SubmitNodeStorage()
        client.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, storage)
        client.gameRenderer.featureRenderDispatcher().prepareFrame(storage).use { frame ->
            RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                { "evergreenhud_player_preview" },
                colorView,
                Optional.empty(),
                depthView,
                OptionalDouble.empty(),
            ).use { pass ->
                RenderSystem.bindDefaultUniforms(pass)
                FeatureRenderDispatcher.renderAllFeatures(pass, frame)
            }
        }
        //? } else if >= 26.2 {
        /*val storage = SubmitNodeStorage()
        client.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, storage)
        client.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage)
        *///? } else {
        /*val features = client.gameRenderer.featureRenderDispatcher
        client.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.submitNodeStorage)
        features.renderAllFeatures()
        client.renderBuffers().bufferSource().endBatch()
        *///? }
    }
    //?} else if > 1.8.9 {
    /*private fun renderPlayer(slot: Slot, request: Request, player: Player, width: Int, height: Int, fit: Float) {
        val partialTick = request.partialTick
        val entityScale = player.scale.coerceAtLeast(0.0001f)
        val liveBodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)
        val liveHeadRot = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot)
        val headRot = request.bodyRot + (request.headRot ?: Mth.wrapDegrees(liveHeadRot - liveBodyRot))
        val headPitch = request.headPitch ?: Mth.lerp(partialTick, player.xRotO, player.xRot)

        val anchor = smoothAnchorHeight(slot, player.bbHeight / entityScale)
        val size = request.sizePx * fit
        val modelScale = size / entityScale
        val tilt = Math.toRadians(request.modelTilt.toDouble()).toFloat()
        val cameraRotation = Quaternionf().rotateX(tilt)
        val rotation = Quaternionf().rotateZ(Math.PI.toFloat()).mul(cameraRotation)

        val pose = PoseStack()
        pose.translate(width / 2f, height * request.verticalAnchor, 0f)
        pose.scale(modelScale, modelScale, -modelScale)
        pose.translate(0f, entityScale * (anchor / 2f + OFFSET_Y), 0f)
        pose.mulPose(rotation)

        val savedBodyRot = player.yBodyRot
        val savedBodyRotO = player.yBodyRotO
        val savedHeadRot = player.yHeadRot
        val savedHeadRotO = player.yHeadRotO
        val savedYRot = player.yRot
        val savedXRot = player.xRot
        val savedXRotO = player.xRotO

        val dispatcher = client.entityRenderDispatcher
        val buffers = client.renderBuffers().bufferSource()

        rendering = true
        nameTag = request.nametag
        player.yBodyRot = request.bodyRot
        player.yBodyRotO = request.bodyRot
        player.yHeadRot = headRot
        player.yHeadRotO = headRot
        player.yRot = headRot
        player.xRot = headPitch
        player.xRotO = headPitch
        //? if >= 1.21.8 {
        client.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        //?} else
        //Lighting.setupForEntityInInventory()
        dispatcher.overrideCameraOrientation(cameraRotation.conjugate(Quaternionf()).rotateY(Math.PI.toFloat()))
        dispatcher.setRenderShadow(false)

        //? if = 1.21.8 {
        val savedHitboxes = dispatcher.shouldRenderHitBoxes()
        dispatcher.setRenderHitBoxes(false)
        //?}

        try {
            //? if >= 1.21.4 {
            dispatcher.render(player, 0.0, 0.0, 0.0, partialTick, pose, buffers, FULL_BRIGHT)
            //?} else
            //RenderSystem.runAsFancy { dispatcher.render(player, 0.0, 0.0, 0.0, 0f, partialTick, pose, buffers, FULL_BRIGHT) }
            buffers.endBatch()
        } finally {
            dispatcher.setRenderShadow(true)
            //? if = 1.21.8
            dispatcher.setRenderHitBoxes(savedHitboxes)
            //? if < 1.21.8
            //Lighting.setupFor3DItems()
            player.yBodyRot = savedBodyRot
            player.yBodyRotO = savedBodyRotO
            player.yHeadRot = savedHeadRot
            player.yHeadRotO = savedHeadRotO
            player.yRot = savedYRot
            player.xRot = savedXRot
            player.xRotO = savedXRotO
            rendering = false
            nameTag = false
        }
    }
    *///?} else {
    /*private fun renderPlayer(slot: Slot, request: Request, player: Player, width: Int, height: Int, fit: Float) {
        val partialTick = request.partialTick
        val liveBodyRot = player.lastBodyYaw + partialTick * MathHelper.wrapDegrees(player.bodyYaw - player.lastBodyYaw)
        val liveHeadRot = player.lastHeadYaw + partialTick * MathHelper.wrapDegrees(player.headYaw - player.lastHeadYaw)
        // requests use modern's yaw (180 faces the viewer), 1.8.9 faces the viewer at 0
        val bodyRot = request.bodyRot - 180f
        val headRot = bodyRot + (request.headRot ?: MathHelper.wrapDegrees(liveHeadRot - liveBodyRot))
        val headPitch = request.headPitch ?: (player.lastPitch + partialTick * (player.xRot - player.lastPitch))

        val anchor = smoothAnchorHeight(slot, player.height)
        val size = request.sizePx * fit

        val savedBodyRot = player.bodyYaw
        val savedBodyRotO = player.lastBodyYaw
        val savedHeadRot = player.headYaw
        val savedHeadRotO = player.lastHeadYaw
        val savedYRot = player.yRot
        val savedYRotO = player.lastYaw
        val savedXRot = player.xRot
        val savedXRotO = player.lastPitch

        val dispatcher = client.entityRenderDispatcher

        rendering = true
        nameTag = request.nametag
        player.bodyYaw = bodyRot
        player.lastBodyYaw = bodyRot
        player.headYaw = headRot
        player.lastHeadYaw = headRot
        player.yRot = headRot
        player.lastYaw = headRot
        player.xRot = headPitch
        player.lastPitch = headPitch

        GlStateManager.enableColorMaterial()
        GlStateManager.pushMatrix()
        try {
            GlStateManager.translatef(width / 2f, height * request.verticalAnchor, 50f)
            GlStateManager.scalef(-size, size, size)
            GlStateManager.translatef(0f, anchor / 2f + OFFSET_Y, 0f)
            GlStateManager.rotatef(180f, 0f, 0f, 1f)
            if (request.modelTilt != 0f) GlStateManager.rotatef(request.modelTilt, 1f, 0f, 0f)
            GlStateManager.rotatef(135f, 0f, 1f, 0f)
            Lighting.turnOn()
            GlStateManager.rotatef(-135f, 0f, 1f, 0f)
            dispatcher.setCameraYaw(180f)
            dispatcher.setRenderShadow(false)
            // last argument skips the F3+B hitbox
            dispatcher.render(player, 0.0, 0.0, 0.0, 0f, partialTick, true)
        } finally {
            dispatcher.setRenderShadow(true)
            GlStateManager.popMatrix()
            Lighting.turnOff()
            GlStateManager.disableRescaleNormal()
            GlStateManager.activeTexture(GLX.GL_TEXTURE1)
            GlStateManager.disableTexture()
            GlStateManager.activeTexture(GLX.GL_TEXTURE0)
            player.bodyYaw = savedBodyRot
            player.lastBodyYaw = savedBodyRotO
            player.headYaw = savedHeadRot
            player.lastHeadYaw = savedHeadRotO
            player.yRot = savedYRot
            player.lastYaw = savedYRotO
            player.xRot = savedXRot
            player.lastPitch = savedXRotO
            rendering = false
            nameTag = false
        }
    }
    *///?}

    private fun smoothAnchorHeight(slot: Slot, target: Float): Float {
        val now = System.nanoTime()
        val previous = slot.anchorHeight
        val elapsed = if (slot.lastAnchorNanos == 0L) 0.0 else (now - slot.lastAnchorNanos) / 1_000_000_000.0
        slot.lastAnchorNanos = now
        if (previous <= 0f || elapsed <= 0.0 || elapsed > 0.5) {
            slot.anchorHeight = target
            return target
        }
        val step = (1.0 - Math.exp(-HEIGHT_CATCHUP * elapsed)).toFloat()
        slot.anchorHeight = previous + (target - previous) * step
        return slot.anchorHeight
    }

    private fun invalidate() {
        slots.values.forEach(Slot::invalidate)
        slots.clear()
        pending.clear()
    }
}
