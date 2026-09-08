package org.polyfrost.evergreenhud.client.hooks

import com.mojang.blaze3d.pipeline.RenderTarget
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skiko.Library
import org.jetbrains.skiko.LibraryLoadException
import org.jetbrains.skiko.SkikoProperties
import org.jetbrains.skiko.hostId
import org.jetbrains.skiko.hostOs
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx
import org.polyfrost.oneconfig.internal.ui.services.VulkanService
import org.slf4j.LoggerFactory
import java.io.File

object SkiaOffscreen {
    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Skia Offscreen")

    private var cached: VulkanService? = null

    var isAvailable = false
        private set

    fun initialize() {
        isAvailable = try {
            Paint().close()
            true
        } catch (error: LinkageError) {
            reportUnavailable(error)
            false
        } catch (error: LibraryLoadException) {
            reportUnavailable(error)
            false
        }
    }

    private fun reportUnavailable(error: Throwable) {
        val root = generateSequence(error) { it.cause }.last()
        val path = runCatching { libraryPath() }.getOrElse { "an unresolved location" }
        LOGGER.error(
            "Skia native library could not be loaded from {}; offscreen HUD rendering is disabled: {}",
            path,
            root.toString(),
            error,
        )
    }

    private fun libraryPath(): String {
        val name = "skiko-$hostId"
        val fileName = System.mapLibraryName(name)
        SkikoProperties.libraryPath?.let { return File(it, fileName).absolutePath }
        val jvmLibrary = File(System.getProperty("java.home"), if (hostOs.isWindows) "bin" else "lib").resolve(fileName)
        if (jvmLibrary.exists()) return jvmLibrary.absolutePath
        val hash = Library::class.java.getResourceAsStream("/$fileName.sha256")?.use { it.bufferedReader().readLine() }
        val directory = if (hash == null) File(SkikoProperties.dataPath) else File(SkikoProperties.dataPath, "$name-$hash")
        return File(directory, fileName).absolutePath
    }

    private fun service(): VulkanService? {
        cached?.let { return it }
        if (!SkiaCtx.isReady) return null
        val resolved = runCatching {
            SkiaCtx.javaClass.getDeclaredField("vulkanService")
                .apply { isAccessible = true }
                .get(SkiaCtx) as? VulkanService
        }.getOrNull() ?: runCatching { VulkanService.detect() }.getOrNull()
        cached = resolved
        return resolved
    }

    val isVulkan: Boolean get() = SkiaCtx.isVulkanMode

    val needsPerFrameRewrap: Boolean get() = service()?.offscreenNeedsPerFrameRewrap == true

    fun makeSurface(target: RenderTarget, width: Int, height: Int): Pair<BackendRenderTarget, Surface>? {
        val service = service() ?: return null
        val (backend, colorFormat) = service.makeOffscreenBRT(target, width, height)
        val origin = if (SkiaCtx.isDeferredComposeBackend) SurfaceOrigin.TOP_LEFT else SurfaceOrigin.BOTTOM_LEFT
        val surface = Surface.makeFromBackendRenderTarget(
            SkiaCtx.directContext,
            backend,
            origin,
            colorFormat,
            ColorSpace.sRGB,
            null,
        )
        if (surface == null) {
            backend.close()
            return null
        }
        return backend to surface
    }

    fun beginRender(target: RenderTarget) {
        service()?.transitionOffscreenForRendering(target)
    }

    fun endRender(target: RenderTarget) {
        service()?.transitionOffscreenForSampling(target)
    }

    fun resetContext() {
        if (!SkiaCtx.isReady) return
        if (isVulkan) SkiaCtx.directContext.resetAll() else SkiaCtx.directContext.resetGLAll()
    }
}
