package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.config.v1.annotations.Option
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.fadingEdges
import org.polyfrost.oneconfig.internal.ui.components.onClick
import org.polyfrost.oneconfig.internal.ui.components.rememberInteractionSource
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.concentric

@Option(display = OverrideOptionsVisualizer::class)
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class OverrideOptions(
    val title: String = "",
    val description: String = "",
    val category: String = "General",
    val subcategory: String = "General",
)

private val EntryHeight = 34.dp
private val ContainerPadding = 8.dp

class OverrideOptionsVisualizer : Visualizer {
    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun visualize(prop: Property<*>) {
        val theme = LocalTheme.current
        val options = prop.getMetadata<Array<String>>("options").orEmpty()
        val labels = prop.getMetadata<Map<String, Any>>("labels").orEmpty()
        val containerShape = theme.modCardShape
        val entryShape = containerShape.concentric(ContainerPadding)

        var selected by remember(prop) {
            mutableStateOf((prop.get() as? Array<*>)?.filterIsInstance<String>().orEmpty())
        }
        val remaining = options.filter { it !in selected }

        fun commit(next: Collection<String>) {
            selected = options.filter { it in next }
            (prop as Property<Any>).set(selected.toTypedArray())
        }

        Column(
            modifier = Modifier
                .width(LocalOptionWidth.current)
                .clip(containerShape)
                .background(theme.componentBackground, containerShape)
                .border(1.dp, theme.borderColor, containerShape)
                .padding(ContainerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (field in selected) {
                val rowInteraction = rememberInteractionSource()
                val hovered by rowInteraction.collectIsHoveredAsState()
                val contentColor by animateColorAsState(if (hovered) theme.textColor else theme.textColorSecondary)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EntryHeight)
                        .clip(entryShape)
                        .background(theme.modCardBackground, entryShape)
                        .border(1.dp, theme.borderColor, entryShape)
                        .hoverable(rowInteraction)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        labels[field] ?: field,
                        modifier = Modifier.weight(1f),
                        color = contentColor,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        "trash",
                        modifier = Modifier
                            .size(16.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .onClick(rememberInteractionSource()) { commit(selected - field) },
                        color = contentColor,
                    )
                }
            }

            if (remaining.isNotEmpty()) {
                AddOptionButton(remaining, labels, entryShape) { commit(selected + it) }
            }
        }
    }

    @Composable
    private fun AddOptionButton(
        remaining: List<String>,
        labels: Map<String, Any>,
        entryShape: Shape,
        onAdd: (String) -> Unit,
    ) {
        val theme = LocalTheme.current
        var expanded by remember { mutableStateOf(false) }
        var buttonHeightPx by remember { mutableStateOf(0) }
        val interaction = rememberInteractionSource()
        val hovered by interaction.collectIsHoveredAsState()
        val contentColor by animateColorAsState(if (hovered || expanded) theme.textColor else theme.textColorSecondary)

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EntryHeight)
                    .onSizeChanged { buttonHeightPx = it.height }
                    .clip(entryShape)
                    .border(1.dp, theme.borderColor, entryShape)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .hoverable(interaction)
                    .onClick(interaction) { expanded = !expanded }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon("plus", modifier = Modifier.size(16.dp), color = contentColor)
                Text("Add option", color = contentColor, fontSize = 13.sp, maxLines = 1)
            }

            if (expanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, buttonHeightPx + 10),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    val scrollState = rememberScrollState()
                    val menuShape = theme.sideBarNavigationEntryShape
                    val itemShape = menuShape.concentric(4.dp)
                    Box(
                        modifier = Modifier
                            .width(LocalOptionWidth.current)
                            .clip(menuShape)
                            .background(theme.componentBackground, menuShape)
                            .border(1.dp, theme.borderColor, menuShape)
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 280.dp)
                                .fadingEdges(scrollState, theme.componentBackground)
                                .verticalScroll(scrollState)
                                .padding(4.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (field in remaining) {
                                val itemInteraction = rememberInteractionSource()
                                val itemHovered by itemInteraction.collectIsHoveredAsState()
                                val background by animateColorAsState(
                                    if (itemHovered) theme.modCardBackground else theme.modCardBackground.copy(0f)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(background, itemShape)
                                        .onClick(itemInteraction) {
                                            onAdd(field)
                                            expanded = false
                                        }
                                        .hoverable(itemInteraction)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(labels[field] ?: field, color = theme.textColor, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
