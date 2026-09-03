package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.evergreenhud.client.utils.Facing
import org.polyfrost.evergreenhud.client.utils.GenericNumberHud
import org.polyfrost.evergreenhud.client.utils.HudStyledCells
import org.polyfrost.evergreenhud.client.utils.StyledCell
import org.polyfrost.evergreenhud.client.utils.StyledRun
import org.polyfrost.evergreenhud.client.utils.cameraYaw
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.config.v1.annotations.Checkbox
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.utils.v1.dsl.mc

private const val NO_SIGN = ' '

class PositionHud : GenericNumberHud(
    title = "Position",
    category = Category.INFO,
    prefix = ""
) {
    init {
        textAlign = 0
    }

    @RadioButton(
        title = "Mode",
        options = ["Vertical", "Horizontal"]
    )
    var displayMode = 0

    @Switch(title = "Show Axis")
    var showAxis = true

    @Switch(title = "Show Direction")
    var showDirection = false

    @Switch(title = "Align Direction")
    var alignDirection = true

    @Checkbox(title = "Show X")
    var showX = true

    @Checkbox(title = "Show Y")
    var showY = true

    @Checkbox(title = "Show Z")
    var showZ = true

    @Checkbox(title = "Show Yaw")
    var showYaw = false

    @Checkbox(title = "Show Pitch")
    var showPitch = false

    @Switch(title = "Per-Axis Label Colors", description = "Colours the X/Y/Z labels; values keep the HUD text colour.", subcategory = "Colors")
    var perAxisColors = false

    @Color(title = "X Color", subcategory = "Colors")
    var xColor = PolyColor(0xFFFF5555.toInt())

    @Color(title = "Y Color", subcategory = "Colors")
    var yColor = PolyColor(0xFF55FF55.toInt())

    @Color(title = "Z Color", subcategory = "Colors")
    var zColor = PolyColor(0xFF5555FF.toInt())

    @Color(title = "Yaw Color", subcategory = "Colors")
    var yawColor = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Pitch Color", subcategory = "Colors")
    var pitchColor = PolyColor(0xFFFFFFFF.toInt())

    private val facing get() = Facing.parseExact(cameraYaw ?: 0f)
    private var px = 0.0
    private var py = 0.0
    private var pz = 0.0
    private var yaw = 0.0
    private var pitch = 0.0

    private var linesState: MutableState<List<List<StyledCell>>> = mutableStateOf(emptyList())
    private var alignState: MutableState<Boolean> = mutableStateOf(false)

    override fun setup() {
        super.setup()
        if (isReal) {
            updateWhenChanged("showDirection")
            updateWhenChanged("alignDirection")
            updateWhenChanged("showX")
            updateWhenChanged("showY")
            updateWhenChanged("showZ")
            updateWhenChanged("showYaw")
            updateWhenChanged("showPitch")
            updateWhenChanged("showAxis")
            updateWhenChanged("displayMode")
            updateWhenChanged("perAxisColors")

            hideIf("alignDirection") { !showDirection || displayMode != 0 }
            hideIf("perAxisColors") { !showAxis }
            hideIf("xColor") { !perAxisColors || !showAxis || !showX }
            hideIf("yColor") { !perAxisColors || !showAxis || !showY }
            hideIf("zColor") { !perAxisColors || !showAxis || !showZ }
            hideIf("yawColor") { !perAxisColors || !showAxis || !showYaw }
            hideIf("pitchColor") { !perAxisColors || !showAxis || !showPitch }
        }
    }

    @Composable
    override fun Content() = HudStyledCells(linesState.value, alignColumns = alignState.value)

    override fun update(): Boolean {
        val entries = createEntries()
        currentText = entries.joinToString(separator) { it.plain }
        val result = super.update()

        val player = mc.player ?: return result
        val camera = mc.cameraEntity ?: player
        this.px = player.x
        this.py = player.y
        this.pz = player.z
        this.yaw = Facing.wrapDegrees(camera.yRot).toDouble()
        this.pitch = camera.xRot.toDouble()

        linesState.value = buildLines(entries)
        alignState.value = alignDirection && showDirection && displayMode == 0
        return result
    }

    private val separator get() = if (displayMode == 0) "\n" else ", "

    private class Entry(val label: String, val value: String, val direction: String?, val color: PolyColor?) {
        val plain get() = label + value + (direction ?: "")
    }

    private fun run(text: String, color: PolyColor? = null) = StyledRun(text, color, false, false)

    private fun cells(entry: Entry): List<MutableList<StyledRun>> {
        val main = mutableListOf<StyledRun>()
        if (entry.label.isNotEmpty()) main.add(run(entry.label, entry.color))
        main.add(run(entry.value))

        return if (entry.direction != null) listOf(main, mutableListOf(run(entry.direction)))
        else listOf(main)
    }

    private fun buildLines(entries: List<Entry>): List<List<StyledCell>> {
        val lines: MutableList<MutableList<MutableList<StyledRun>>> = when {
            entries.isEmpty() -> mutableListOf(mutableListOf(mutableListOf(run(""))))

            displayMode == 0 -> entries.mapTo(ArrayList()) { cells(it).toMutableList() }

            else -> {
                val line = ArrayList<MutableList<StyledRun>>(entries.size * 2)
                for ((i, entry) in entries.withIndex()) {
                    if (i > 0) line.add(mutableListOf(run(", ")))
                    line.addAll(cells(entry))
                }
                mutableListOf(line)
            }
        }

        if (prefix.isNotEmpty()) lines.first().first().add(0, run("$prefix$concatString"))
        if (suffix.isNotEmpty()) lines.last().last().add(run("$concatString$suffix"))

        if (brackets) {
            for (line in lines) {
                if (line.all { cell -> cell.all { it.text.isEmpty() } }) continue
                line.first().add(0, run("["))
                line.last().add(run("]"))
            }
        }
        return lines.map { line -> line.map { StyledCell(it) } }
    }

    private fun entry(axis: Char, value: Double, sign: Char, color: PolyColor): Entry = Entry(
        label = if (showAxis) "$axis: " else "",
        value = format(value),
        direction = if (showDirection && sign != NO_SIGN) "  ($sign)" else null,
        color = color.takeIf { perAxisColors },
    )

    private fun angleEntry(label: String, value: Double, color: PolyColor): Entry = Entry(
        label = if (showAxis) "$label: " else "",
        value = format(value),
        direction = null,
        color = color.takeIf { perAxisColors },
    )

    private fun createEntries(): List<Entry> {
        val facing = this.facing
        val entries = ArrayList<Entry>(5)

        if (showX) {
            entries.add(entry('X', px, if (facing.isEast) '+' else if (facing.isWest) '-' else NO_SIGN, xColor))
        }

        if (showY) {
            entries.add(entry('Y', py, NO_SIGN, yColor))
        }

        if (showZ) {
            entries.add(entry('Z', pz, if (facing.isSouth) '+' else if (facing.isNorth) '-' else NO_SIGN, zColor))
        }

        if (showYaw) {
            entries.add(angleEntry("Yaw", yaw, yawColor))
        }

        if (showPitch) {
            entries.add(angleEntry("Pitch", pitch, pitchColor))
        }

        return entries
    }

    override fun clone(): Hud = (super.clone() as PositionHud).also {
        it.linesState = mutableStateOf(emptyList())
        it.alignState = mutableStateOf(false)
    }

    override fun defaultPosition(): Pair<Float, Float> = 10f to 30f

    override fun updateFrequency(): Long = 50L
}
