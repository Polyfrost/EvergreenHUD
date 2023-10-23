package cc.polyfrost.evergreenhud.hud.customimages

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.Hud
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.renderer.NanoVGHelper
import cc.polyfrost.oneconfig.renderer.asset.Image
import cc.polyfrost.oneconfig.utils.Notifications
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.*
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.min

class CustomImageHud : Hud(true, 180f, 30f) {

    @Transient
    private var imageDimension: Dimension? = null
    @Transient
    private var currentImage: Image? = null
    @Transient
    private var changed = false
    @Transient
    private var scaleMod = 1f
    @Transient
    private var width = 0f
    @Transient
    private var height = 0f

    @Button(name = "Image", text = "Browse")
    val browser: Runnable = Runnable {
        Thread {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            val fileChooser = JFileChooser()
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
            fileChooser.fileFilter = FileNameExtensionFilter("Image File", "png", "jpg")
            Notifications.INSTANCE.send(
                "EvergreenHUD",
                "The file dialogue has just been opened. You may need to tab out to see it."
            )
            val result: Int = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val file: File = fileChooser.selectedFile
                imagePath = file.path
                changed = true
                Notifications.INSTANCE.send("EvergreenHUD", "You have selected a new image.")
            } else {
                Notifications.INSTANCE.send("EvergreenHUD", "You must select an image.")
            }
        }.start()
    }

    var imagePath = ""

    var text = ""

    @Checkbox(name = "Rounded Corners")
    var rounded = false

    @Checkbox(name = "Drop Shadow")
    var dropShadow = false

    @Slider(name = "Corner Radius", min = 0f, max = 10f, step = 1)
    var radius = 4f

    @Slider(name = "Blur", min = 0f, max = 100f, step = 1)
    var blur = 64f

    @Slider(name = "Spread", min = 0f, max = 100f, step = 1)
    var spread = 0f

    override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
        if (changed || currentImage == null) {
            if (currentImage != null) currentImage = null
            try {
                cacheResourceLocation()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            changed = false
        }
        scaleMod = 64f / min(imageDimension!!.getWidth().toFloat(), imageDimension!!.getHeight().toFloat())
        width = imageDimension!!.width.toFloat() * scaleMod * scale
        height = imageDimension!!.height.toFloat() * scaleMod * scale

        val nano = NanoVGHelper.INSTANCE
        nano.setupAndDraw(true) { vg ->

            val radius = if (rounded) radius * scale else 0f
            nano.drawRoundImage(vg, currentImage, x, y, width, height, radius)
            if (dropShadow)
                nano.drawDropShadow(vg, x, y, width, height, blur, spread, radius)
        }
    }

    @Throws(IOException::class)
    private fun cacheResourceLocation() {

        if (getImageFile() == null) {
            currentImage = null
            imageDimension = Dimension(50, 50)
            return
        }else{
            val imgFile: File? = getImageFile()
            text = imgFile!!.name
            val `in`: InputStream = FileInputStream(imgFile)
            val img: BufferedImage = ImageIO.read(`in`)
            currentImage = Image(imagePath)
            imageDimension = Dimension(img.width, img.height)
        }
    }

    private fun getImageFile(): File? {
        return if (imagePath == "" || !File(imagePath).exists()) {
            null
        } else File(imagePath)
    }

    override fun getWidth(scale: Float, example: Boolean): Float {
        return width
    }

    override fun getHeight(scale: Float, example: Boolean): Float {
        return height
    }
}
