@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon as UiIcon
import org.polyfrost.oneconfig.api.ui.v1.components.Text as UiText
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = UiText(
    text,
    modifier,
    color,
    OneConfigTheme.typography.body.copy(fontSize = fontSize, fontWeight = fontWeight),
    maxLines,
    overflow,
    textAlign = textAlign,
)

@Composable
internal fun Icon(iconName: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) = UiIcon(iconName, null, modifier, color)
