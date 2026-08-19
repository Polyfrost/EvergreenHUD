//? if >= 1.21.10 {
package org.polyfrost.evergreenhud.client.hooks

import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
//? if >= 26.1 {
import net.minecraft.client.renderer.ProjectionMatrixBuffer
//? } else {
/*import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
*///? }
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//? if >= 26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState
//? } else {
/*import net.minecraft.client.renderer.state.CameraRenderState
*///? }
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.polyfrost.oneconfig.internal.ui.RenderTargetFbo
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.slf4j.LoggerFactory

private const val MAX_DIM = 1024
private const val FULL_BRIGHT = 0xF000F0

private const val OFFSET_Y = 0.0625f

private const val HEIGHT_CATCHUP = 12f

private const val STALE_NANOS = 1_000_000_000L

object PlayerPreviewOffscreen {
    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Player Preview")

    private val client: Minecraft get() = mc
    private val blitPaint = Paint()

    //? if >= 26.1 {
    private val projection by lazy { ProjectionMatrixBuffer("evergreenhud_player_preview") }
    //? } else {
    /*private val projection by lazy {
        CachedOrthoProjectionMatrixBuffer("evergreenhud_player_preview", -1000f, 1000f, true)
    }
    *///? }

    class Request(
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
        var target: TextureTarget? = null
        var brt: BackendRenderTarget? = null
        var surface: Surface? = null
        var lastWidth = -1
        var lastHeight = -1

        var anchorHeight = -1f
        var lastAnchorNanos = 0L

        var hasContent = false
        var lastUsedNanos = 0L
        var layoutIsGeneral = false

        fun releaseSurface() {
            surface?.close()
            surface = null
            brt?.close()
            brt = null
            layoutIsGeneral = false
        }

        fun invalidate() {
            releaseSurface()
            target?.destroyBuffers()
            target = null
            lastWidth = -1
            lastHeight = -1
            hasContent = false
        }
    }

    private val slots = LinkedHashMap<Any, Slot>()
    private val pending = LinkedHashMap<Any, Request>()
    private var failed = false
    private var loggedFirstFrame = false

    fun submit(owner: Any, request: Request) {
        pending[owner] = request
    }

    @JvmStatic
    fun render() {
        val requests = if (pending.isEmpty()) emptyList() else pending.entries.map { it.key to it.value }
        pending.clear()
        for (slot in slots.values) slot.hasContent = false
        if (failed || !SkiaCtx.isReady) return

        val player = client.player ?: return
        val now = System.nanoTime()
        for ((owner, request) in requests) {
            val slot = slots.getOrPut(owner) { Slot() }
            slot.lastUsedNanos = now
            renderSlot(slot, request, player)
            if (failed) break
        }
        evictStale(now)
    }

    private fun renderSlot(slot: Slot, request: Request, player: Player) {
        val requestedWidth = request.widthPx.coerceAtLeast(1)
        val requestedHeight = request.heightPx.coerceAtLeast(1)
        val fit = minOf(1f, MAX_DIM.toFloat() / requestedWidth, MAX_DIM.toFloat() / requestedHeight)
        val width = (requestedWidth * fit).toInt().coerceIn(1, MAX_DIM)
        val height = (requestedHeight * fit).toInt().coerceIn(1, MAX_DIM)

        try {
            if (!resolveTarget(slot, width, height)) return
            val rt = slot.target ?: return
            if (slot.layoutIsGeneral) SkiaOffscreen.beginRender(rt)
            renderInto(slot, rt, request, player, width, height, fit)
            SkiaOffscreen.endRender(rt)
            slot.layoutIsGeneral = true
            SkiaOffscreen.resetContext()
            slot.hasContent = true
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

    fun drawInto(owner: Any, canvas: Canvas, width: Float, height: Float) {
        val slot = slots[owner] ?: return
        if (!slot.hasContent) return
        val s = slot.surface ?: return
        val w = slot.lastWidth
        val h = slot.lastHeight
        if (w <= 0 || h <= 0) return
        try {
            s.notifyContentWillChange(ContentChangeMode.RETAIN)
            canvas.save()
            canvas.clipRect(Rect.makeXYWH(0f, 0f, width, height))
            canvas.scale(width / w, height / h)
            s.draw(canvas, 0, 0, blitPaint)
            canvas.restore()
        } catch (throwable: Throwable) {
            LOGGER.warn("Player preview blit failed", throwable)
        }
    }

    private fun renderInto(slot: Slot, rt: TextureTarget, request: Request, player: Player, width: Int, height: Int, fit: Float) {
        val colorTexture = rt.colorTexture ?: return
        val depthTexture = rt.depthTexture ?: return
        val colorView = rt.colorTextureView ?: return
        val depthView = rt.depthTextureView ?: return

        val savedLights = RenderSystem.getShaderLights()
        val savedFog = RenderSystem.getShaderFog()

        //? if >= 26.2 {
        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTexture, org.joml.Vector4f(0f, 0f, 0f, 0f), depthTexture, 0.0)
        //? } else {
        /*RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTexture, 0x00000000, depthTexture, 1.0)
        *///? }

        RenderSystem.backupProjectionMatrix()
        //? if >= 26.2 {
        RenderSystem.setProjectionMatrix(projection.getBuffer(orthoProjection(width, height)), ProjectionType.ORTHOGRAPHIC)
        //? } else if >= 26.1 {
        /*RenderSystem.setProjectionMatrix(projection.getBuffer(orthoMatrix(width, height)), ProjectionType.ORTHOGRAPHIC)
        *///? } else {
        /*RenderSystem.setProjectionMatrix(projection.getBuffer(width.toFloat(), height.toFloat()), ProjectionType.ORTHOGRAPHIC)
        *///? }
        RenderSystem.outputColorTextureOverride = colorView
        RenderSystem.outputDepthTextureOverride = depthView

        //? if >= 26.1 {
        val scissor = RenderSystem.getScissorStateForRenderTypeDraws()
        val hadScissor = scissor.enabled()
        val scissorX = scissor.x()
        val scissorY = scissor.y()
        val scissorW = scissor.width()
        val scissorH = scissor.height()
        //? }
        RenderSystem.disableScissorForRenderTypeDraws()

        //? if >= 26.2 {
        val modelView = RenderSystem.getModelViewStack()
        modelView.pushMatrix()
        modelView.identity()
        //? }
        try {
            renderPlayer(slot, request, player, width, height, fit)
        } finally {
            //? if >= 26.2 {
            RenderSystem.getModelViewStack().popMatrix()
            //? }
            RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
            RenderSystem.restoreProjectionMatrix()
            savedLights?.let { RenderSystem.setShaderLights(it) }
            savedFog?.let { RenderSystem.setShaderFog(it) }
            //? if >= 26.1 {
            if (hadScissor) RenderSystem.enableScissorForRenderTypeDraws(scissorX, scissorY, scissorW, scissorH)
            //? }
        }
    }

    private fun renderPlayer(slot: Slot, request: Request, player: Player, width: Int, height: Int, fit: Float) {
        playerPreviewPartialTick = request.partialTick
        playerPreviewNameTag = request.nametag
        val state = try {
            client.entityRenderDispatcher.extractEntity(player, request.partialTick) as? AvatarRenderState ?: return
        } finally {
            playerPreviewPartialTick = -1f
            playerPreviewNameTag = false
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
        pose.mulPose(rotation)

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

        //? if >= 26.2 {
        val storage = net.minecraft.client.renderer.SubmitNodeStorage()
        client.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, storage)
        client.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage)
        //? } else {
        /*val features = client.gameRenderer.featureRenderDispatcher
        client.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.submitNodeStorage)
        features.renderAllFeatures()
        client.renderBuffers().bufferSource().endBatch()
        *///? }
    }

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

    //? if >= 26.2 {
    private fun orthoProjection(width: Int, height: Int): net.minecraft.client.renderer.Projection =
        net.minecraft.client.renderer.Projection().apply {
            setupOrtho(-1000f, 1000f, width.toFloat(), height.toFloat(), true)
        }
    //? } else if >= 26.1 {
    /*private fun orthoMatrix(width: Int, height: Int): Matrix4f =
        Matrix4f().setOrtho(0f, width.toFloat(), height.toFloat(), 0f, -1000f, 1000f)
    *///? }

    private fun resolveTarget(slot: Slot, width: Int, height: Int): Boolean {
        if (slot.target == null || slot.lastWidth != width || slot.lastHeight != height) {
            slot.invalidate()

            //? if >= 26.2 {
            val rt = TextureTarget("evergreenhud_player_preview", width, height, true, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)
            //? } else {
            /*val rt = TextureTarget("evergreenhud_player_preview", width, height, true)
            *///? }
            slot.target = rt

            if (!SkiaOffscreen.isVulkan) {
                val fboId = RenderTargetFbo.getFboId(rt)
                if (fboId <= 0) {
                    LOGGER.warn("Player preview needs an OpenGL render target; disabling")
                    failed = true
                    invalidate()
                    return false
                }
            }
            slot.lastWidth = width
            slot.lastHeight = height
        }

        val rt = slot.target ?: return false
        if (slot.surface == null || SkiaOffscreen.needsPerFrameRewrap) {
            slot.releaseSurface()
            val (backend, made) = SkiaOffscreen.makeSurface(rt, width, height) ?: return false
            slot.brt = backend
            slot.surface = made
        }
        return true
    }

    private fun invalidate() {
        slots.values.forEach(Slot::invalidate)
        slots.clear()
        pending.clear()
    }
}
//? }
