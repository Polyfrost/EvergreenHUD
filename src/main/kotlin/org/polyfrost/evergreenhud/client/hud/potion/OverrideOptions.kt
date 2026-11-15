@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import net.kyori.adventure.text.Component
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.config.v1.annotations.Option
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.MultiSelect
import org.polyfrost.oneconfig.api.ui.v1.components.currentOptionWidth

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

private fun labelOf(label: Any): String = when (label) {
    is String -> label
    is AnnotatedString -> label.text
    is Component -> Platform.compatibility().resolveComponent(label)
    else -> label.toString()
}

class OverrideOptionsVisualizer : Visualizer {
    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun visualize(prop: Property<*>) {
        val options = prop.getMetadata<Array<String>>("options").orEmpty().toList()
        val labels = prop.getMetadata<Map<String, Any>>("labels").orEmpty()

        var selected by remember(prop) {
            mutableStateOf((prop.get() as? Array<*>)?.filterIsInstance<String>().orEmpty().toSet())
        }

        MultiSelect(
            selected = selected,
            options = options,
            onChange = { next ->
                val ordered = options.filter { it in next }
                selected = ordered.toSet()
                (prop as Property<Any>).set(ordered.toTypedArray())
            },
            modifier = Modifier.width(currentOptionWidth),
            label = { labelOf(labels[it] ?: it) },
        )
    }
}
