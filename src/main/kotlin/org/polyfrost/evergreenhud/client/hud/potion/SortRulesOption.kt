@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.config.v1.annotations.Option
import org.polyfrost.oneconfig.api.ui.v1.components.currentOptionWidth
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.internal.ui.api.settings.EffectListOptionData
import org.polyfrost.oneconfig.internal.ui.components.settings.EffectListOption
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth
import org.polyfrost.oneconfig.internal.ui.components.settings.SwitchControl
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.concentric
import java.util.function.Consumer
import java.util.function.Supplier

const val SORT_RULES_MODEL = "sortRulesModel"

@Option(display = SortRulesVisualizer::class)
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class SortRulesOption(
    val title: String = "",
    val description: String = "",
    val category: String = "General",
    val subcategory: String = "General",
    val fullWidth: Boolean = true,
)

class SortRulesVisualizer : Visualizer {
    @Composable
    override fun visualize(prop: Property<*>) {
        val model = prop.getMetadata<SortRulesModel>(SORT_RULES_MODEL) ?: return
        (prop.get() as? String)?.let(model::sync)

        val theme = OneConfigTheme.current
        val containerShape = theme.modCardShape
        val rules = remember(model.revision) { model.rules.toList() }

        Column(
            modifier = Modifier
                .width(currentOptionWidth)
                .clip(containerShape)
                .background(theme.componentBackground, containerShape)
                .border(1.dp, theme.borderColor, containerShape)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Reverse all", modifier = Modifier.weight(1f), color = theme.textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                SwitchControl(model.reverseAll) {
                    model.reverseAll = it
                    model.changed()
                }
            }

            ReorderableColumn(
                items = rules,
                keyOf = { it.uid },
                onMove = { from, to ->
                    model.rules.add(to, model.rules.removeAt(from))
                    model.changed()
                },
            ) { rule, handle ->
                SortRuleCard(rule, model, handle) {
                    model.rules.remove(rule)
                    model.changed()
                }
            }

            GameOrderCard(model)

            val addable = SortKey.entries.filter { key -> key.tiered || rules.none { it.key == key && it.only == null } }
            val sections = listOf(
                "Order by" to addable.filterNot { it.tiered }.map { it to it.title },
                "Put first or last" to addable.filter { it.tiered }.map { it to it.title },
            ).filter { it.second.isNotEmpty() }
            MenuButton("plus", "Add sort rule", containerShape.concentric(8.dp), sections) { key ->
                model.rules += SortRule(key)
                model.changed()
            }
        }
    }
}

@Composable
private fun GameOrderCard(model: SortRulesModel) {
    val theme = OneConfigTheme.current
    val shape = theme.modCardShape.concentric(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.modCardBackground, shape)
            .border(1.dp, theme.borderColor, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Game order", color = theme.textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Segmented(listOf(false to "As is", true to "Reversed"), model.reverseTies, ControlShape) {
            model.reverseTies = it
            model.changed()
        }
    }
}

@Composable
private fun SortRuleCard(rule: SortRule, model: SortRulesModel, handle: Modifier, onRemove: () -> Unit) {
    val theme = OneConfigTheme.current
    val shape = theme.modCardShape.concentric(8.dp)
    val innerShape = ControlShape

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.modCardBackground, shape)
            .border(1.dp, theme.borderColor, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon("dots-grid", modifier = handle.size(16.dp).pointerHoverIcon(PointerIcon.Hand), color = theme.textColorSecondary)
            Text(rule.key.title, modifier = Modifier.weight(1f), color = theme.textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            HoverIcon("trash", onClick = onRemove)
        }

        Segmented(listOf(false to rule.key.first, true to rule.key.second), rule.flipped, innerShape) {
            rule.flipped = it
            model.changed()
        }

        when (rule.key) {
            SortKey.CATEGORY -> CategoryOrder(rule, model, innerShape)
            SortKey.EFFECTS -> EffectOrder(rule, model)
            SortKey.TIME_RANGES, SortKey.ELAPSED_RANGES, SortKey.STARTED_RANGES -> RangeList(rule, rule.ranges, MAX_DURATION_SECONDS, "Add time range", reorderable = true) {
                rule.ranges = it
                model.changed()
            }
            SortKey.AMPLIFIER_RANGES -> RangeList(rule, rule.ranges, MAX_AMPLIFIER, "Add amplifier range", reorderable = true) {
                rule.ranges = it
                model.changed()
            }
            SortKey.PERCENT_RANGES -> RangeList(rule, rule.ranges, 100f, "Add percent range", reorderable = true) {
                rule.ranges = it
                model.changed()
            }
            else -> {}
        }

        OnlyFor(rule, model, innerShape)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryOrder(rule: SortRule, model: SortRulesModel, shape: Shape) {
    val theme = OneConfigTheme.current
    val labels = CATEGORY_CHOICES.toMap()

    ReorderableColumn(
        items = rule.values,
        keyOf = { it },
        spacing = 4.dp,
        onMove = { from, to ->
            rule.values = rule.values.toMutableList().apply { add(to, removeAt(from)) }
            model.changed()
        },
    ) { category, handle ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(shape)
                .background(theme.componentBackground, shape)
                .border(1.dp, theme.borderColor, shape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon("dots-grid", modifier = handle.size(14.dp).pointerHoverIcon(PointerIcon.Hand), color = theme.textColorSecondary)
            Text(labels[category] ?: category, modifier = Modifier.weight(1f), color = theme.textColor, fontSize = 12.sp)
            if (rule.values.size > 1) {
                HoverIcon("close") {
                    rule.values = rule.values - category
                    model.changed()
                }
            }
        }
    }

    val missing = CATEGORY_ORDER.filter { it !in rule.values }
    if (missing.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (category in missing) {
                Pill("+ ${labels[category] ?: category}", selected = false) {
                    rule.values = rule.values + category
                    model.changed()
                }
            }
        }
    }
}

@Composable
private fun EffectOrder(rule: SortRule, model: SortRulesModel) {
    val prop = remember(rule.uid) {
        Properties.functional(
            Supplier { rule.values.toTypedArray() },
            Consumer<Array<String>> {
                rule.values = it.toList()
                model.changed()
            },
            type = Array<String>::class.java,
        ).apply { addMetadata("addText", "Add effect") }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalOptionWidth provides maxWidth) {
            EffectListOption(EffectListOptionData(prop))
        }
    }
}

@Composable
private fun OnlyFor(rule: SortRule, model: SortRulesModel, shape: Shape) {
    val only = rule.only
    if (only == null) {
        MenuButton("plus", "Only for…", shape, listOf(null to CONDITION_TYPES), compact = true) { type ->
            rule.only = OverrideRule(listOf(OverrideCondition(type)))
            model.changed()
        }
        return
    }

    Joiner("only for")
    only.conditions.forEachIndexed { i, condition ->
        key(System.identityHashCode(condition)) {
            if (i > 0) Joiner("and")
            ConditionSection(
                condition = condition,
                shape = shape,
                negationLocked = condition.type == OverrideCondition.Type.EFFECT && only.locksEffectNegation(),
                onChange = model::changed,
                onRemove = {
                    only.conditions.remove(condition)
                    if (only.conditions.isEmpty()) rule.only = null
                    model.changed()
                },
            )
        }
    }

    val addable = CONDITION_TYPES.filter { only.canAdd(it.first) }
    if (addable.isNotEmpty()) {
        MenuButton("plus", "+ Condition", shape, listOf(null to addable), compact = true) { type ->
            only.add(type)
            model.changed()
        }
    }
}
