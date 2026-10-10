package org.polyfrost.evergreenhud.client.hud.battery

import androidx.compose.runtime.Composable
import org.polyfrost.compose.composables.*
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.battery.Battery

const val BORDER = 2f
const val RADIUS_OUTER = 5f
const val RADIUS_INNER = 3f
const val FONT_SIZE = 16f

val successColor = PolyColor.rgb(35, 154, 96) to PolyColor.rgb(26, 139, 82)
val warningColor = PolyColor.rgb(255, 171, 29) to PolyColor.rgb(233, 156, 27)

const val LOW_BATTERY_THRESHOLD = 20

@Composable
fun BatteryDrawable(
    battery: Battery,
) {
    val percent = battery.percentage.coerceIn(0, 100)
    val (backgroundColor, innerColor) = when {
        battery.isCharging -> successColor
        battery.isBatterySaverEnabled || percent <= LOW_BATTERY_THRESHOLD -> warningColor
        else -> successColor
    }

    PolyBox(
        PolyModifier.width(60f)
            .height(25f)
            .border(backgroundColor, BORDER, RADIUS_OUTER)
            .radius(RADIUS_OUTER)
            .padding(BORDER)
    ) {
        PolyBox(
            PolyModifier.fillHeight()
                .width((60f - BORDER + 2f) * (percent / 100f))
                .background(innerColor, RADIUS_INNER)
                .radius(RADIUS_INNER)
        )

        PolyText(
            text = "$percent%",
            fontSize = FONT_SIZE,
            color = backgroundColor.invert()
        )
    }
}