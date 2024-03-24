package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.hud.hudlist.HudList
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.config.elements.OptionPage
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.renderer.TinyFD
import cc.polyfrost.oneconfig.renderer.asset.Image
import cc.polyfrost.oneconfig.utils.Notifications
import cc.polyfrost.oneconfig.utils.dsl.*
import org.polyfrost.evergreenhud.config.HudConfig
import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import javax.imageio.ImageIO
import kotlin.math.min

private fun notify(message: String) = Notifications.INSTANCE.send("EvergreenHUD", message)

class CustomImages : HudConfig(Mod("Custom Images", ModType.HUD), "evergreenhud/customimages.json", false) {
    @CustomOption
    var huds = ImageHudList()

    init {
        initialize()
    }

    override fun getCustomOption(
        field: Field, annotation: CustomOption, page: OptionPage, mod: Mod, migrate: Boolean,
    ) = huds.addOptionTo(this, page)

    class ImageHudList : HudList<CustomImageHud>() {
        override fun newHud() = CustomImageHud()
        override fun getHudName(hud: CustomImageHud) = hud.loadedImage?.fileName ?: "No Image Loaded"
    }

    class CustomImageHud : BasicHud(true, 180f, 30f) {
        @Button(name = "Image", text = "Browse")
        val browseButton = Runnable { runAsync { browse() } }

        private fun browse() {
            notify("A file dialogue has opened. You may need to tab out to see it.")

            val result = TinyFD.INSTANCE.openFileSelector(
                "Select an image",
                "",
                arrayOf("*.png", "*.jpg", "*.jpeg"),
                "Image Files"
            ) ?: run {
                notify("You must select an image.")
                return
            }

            imagePath = result.absolutePath.takeIf {
                it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg")
            } ?: run {
                notify("You must select a PNG or JPG image.")
                return
            }

            refreshed = false
            notify("You have selected a new image.")
        }

        @Button(name = "Refresh Image", text = "Refresh")
        val refreshButton = Runnable { refresh() }

        private fun refresh() {
            try {
                loadImage()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            refreshed = true
        }

        var imagePath = ""

        @Transient
        var loadedImage: ScaledImage? = null

        @Transient
        private var refreshed = false

        override fun draw(matrices: UMatrixStack, x: Float, y: Float, scale: Float, example: Boolean) {
            if (!refreshed) refresh()

            val image = loadedImage ?: return

            nanoVGHelper.setupAndDraw(true) { vgLong ->
                val scaledRadius = if (rounded) cornerRadius * scale else 0f
                nanoVGHelper.drawRoundImage(vgLong, image, x, y, image.width * scale, image.height * scale, scaledRadius)
            }
        }

        private fun loadImage() {
            loadedImage = null

            if (imagePath.isBlank()) return
            val file = File(imagePath)
            if (!file.exists() || !file.isFile) return

            val bufferedImage = ImageIO.read(file)
            loadedImage = ScaledImage(imagePath, file.name, bufferedImage.width, bufferedImage.height)
        }

        override fun getWidth(scale: Float, example: Boolean) = (loadedImage?.width ?: 0f) * scale
        override fun getHeight(scale: Float, example: Boolean) = (loadedImage?.height ?: 0f) * scale

        class ScaledImage(
            filePath: String,
            val fileName: String,
            imageWidth: Int,
            imageHeight: Int,
        ) : Image(filePath) {
            private val scale = 64f / min(imageWidth, imageHeight).toFloat()
            val width = imageWidth.toFloat() * scale
            val height = imageHeight.toFloat() * scale
        }
    }

}