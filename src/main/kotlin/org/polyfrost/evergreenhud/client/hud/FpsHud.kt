package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.FrameTimeHelper
import org.polyfrost.evergreenhud.client.utils.GenericNumberHud
import org.polyfrost.evergreenhud.client.utils.quality
import org.polyfrost.evergreenhud.client.utils.qualityColor
import org.polyfrost.evergreenhud.client.utils.replace
import org.polyfrost.oneconfig.api.config.v1.annotations.Number
import org.polyfrost.oneconfig.api.config.v1.annotations.RangeSlider
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager

private const val WORST_RATIO = 0.5

class FpsHud : GenericNumberHud(
    title = "FPS",
    category = Category.INFO,
) {
    @Text(title = "Format String", description = "Use #avg for average, #med for median, #fps for fps, #p95 for 95th percentile, #p99 for 99th percentile, #cst for consistency")
    private var formatString = "#fps"

    @Slider(title = "Update Rate", description = "Seconds between display updates. 0 updates as fast as possible.", min = 0F, max = 5F, step = 0.5F)
    private var updateRate = 1F

    @Switch(title = "Color By Value", description = "Colours the value green when FPS is near your usual, fading to red at half of it.", subcategory = "Colors")
    private var colorByValue = false

    @RangeSlider(title = "Show HUD Within Quality Range", description = "Show the FPS HUD only in a specific quality range, in %. 0-100% always shows.", min = 0F, max = 100F, step = 1F, subcategory = "Visibility")
    private var showRange = floatArrayOf(0f, 100f)

    @Number(title = "Hide Above FPS Number", description = "Hide the FPS HUD when above a specific number. 0 will always show.", min = 0F, max = 100000F, subcategory = "Visibility")
    private var hideAboveNumber = 0

    init {
        accuracy = 0
    }

    override fun setup() {
        super.setup()

        if (isReal) {
            updateWhenChanged("formatString")
            updateWhenChanged("colorByValue")
            updateWhenChanged("updateRate")
            updateWhenChanged("showRange")
            updateWhenChanged("hideAboveNumber")
        }
    }

    override fun clone(): Hud = (super.clone() as FpsHud).apply {
        showRange = this@FpsHud.showRange.copyOf()
    }

    override fun getText(): String {
        val data = FrameTimeHelper.latest
        val baseline = FrameTimeHelper.baselineFps

        val editing = HudManager.isEditing
        val hiddenByRange = !editing && baseline > 0.0 && qualityPercent(data.currentFps.toFloat(), baseline).let { it < showRange[0] || it > showRange[1] }
        val hiddenByNumber = !editing && hideAboveNumber > 0 && data.currentFps > hideAboveNumber

        autoHidden = hiddenByRange || hiddenByNumber

        return StringBuilder().append(formatString)
            .replace("#fps", format(data.currentFps))
            .replace("#avg", format(data.averageFps))
            .replace("#med", format(data.medianFps))
            .replace("#p95", format(data.p95Millis))
            .replace("#p99", format(data.p99Millis))
            .replace("#cst", format(data.consistencyPercent))
            .toString()
    }

    override fun updateFrequency(): Long =
        if (updateRate <= 0F) -1L else (updateRate * 1_000_000_000.0).toLong()

    override fun valueColor(): PolyColor? {
        val baseline = FrameTimeHelper.baselineFps
        if (!colorByValue || baseline <= 0.0) return null
        val fps = FrameTimeHelper.latest.currentFps
        return qualityColor(quality(fps.toFloat(), (baseline * WORST_RATIO).toFloat(), baseline.toFloat()))
    }

    private fun qualityPercent(fps: Float, baseline: Double): Float {
        val worst = (baseline * WORST_RATIO).toFloat()
        return quality(fps, worst, baseline.toFloat()) * 100F
    }
}
