package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.utils.*
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.TextHud
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.polyfrost.evergreenhud.config.HudConfig

class Coordinates: HudConfig("Coordinates", "evergreenhud/coordinates.json", true) {
    @HUD(name = "Main")
    var hud = CoordinatesHud()

    init {
        initialize()
    }

    class CoordinatesHud : TextHud(true, 0, 0) {
        @DualOption(
            name = "Mode",
            left = "Vertical",
            right = "Horizontal"
        )
        var displayMode = false

        @Switch(
            name = "Show Axis"
        )
        var showAxis = true

        @Switch(
            name = "Show Direction"
        )
        var showDirection = false

        @Switch(
            name = "Show X"
        )
        var showX = true

        @Switch(
            name = "Show Y"
        )
        var showY = true

        @Switch(
            name = "Show Z"
        )
        var showZ = true

        @Slider(
            name = "Accuracy",
            min = 0f,
            max = 16f,
            step = 1
        )
        var accuracy = 0

        @Switch(
            name = "Trailing Zeros"
        )
        var trailingZeros = false

        override fun getLines(lines: MutableList<String>, example: Boolean) {
            if (mc.thePlayer == null) {
                lines.add("Unknown")
                return
            }

            // store this
            val df = decimalFormat(accuracy, trailingZeros)

            // and this
            val sb = StringBuilder()
            val facing = Facing.parseExact(mc.thePlayer!!.rotationYaw)
            if (showX) {
                // if(showAxis) sb.append("X: ") ?
                sb.append(if (showAxis) "X: " else "")
                sb.append(df.format(mc.thePlayer.posX))
                if (showDirection) {
                    sb.append(" (")

                    // add char facing.directionX/Y property or something and use that instead
                    sb.append(when (facing) {
                        Facing.EAST, Facing.NORTH_EAST, Facing.SOUTH_EAST -> "+"
                        Facing.WEST, Facing.NORTH_WEST, Facing.SOUTH_WEST -> "-"
                        else -> " "
                    })

                    sb.append(")")
                }
                if (!displayMode) {
                    lines.add(sb.toString())
                    sb.setLength(0)
                } else if (showY || showZ) {
                    sb.append(", ")
                }
            }
            if (showY) {
                sb.append(if (showAxis) "Y: " else "")
                sb.append(df.format(mc.thePlayer.posY))
                if (!displayMode) {
                    lines.add(sb.toString())
                    sb.setLength(0)
                } else if (showZ) {
                    sb.append(", ")
                }
            }
            if (showZ) {
                sb.append(if (showAxis) "Z: " else "")
                sb.append(df.format(mc.thePlayer.posZ))
                if (showDirection) {
                    sb.append(" (")

                    sb.append(when (facing) {
                        Facing.NORTH, Facing.NORTH_EAST, Facing.NORTH_WEST -> "-"
                        Facing.SOUTH, Facing.SOUTH_WEST, Facing.SOUTH_EAST -> "+"
                        else -> " "
                    })

                    sb.append(")")
                }
                if (!displayMode) {
                    lines.add(sb.toString())
                    sb.setLength(0)
                }
            }

            if (displayMode) {
                lines.add(sb.toString())
            }
        }
    }
}