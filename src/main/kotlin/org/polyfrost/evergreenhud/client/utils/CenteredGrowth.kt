package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.config.v1.Properties.ktProperty
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor
import java.util.function.Supplier

/** Shared layout state; each HUD owns its own copy. */
internal class CenteredGrowth {
    var previousGrowthAnchor = HudAnchor.Auto
    var previousSelfAnchor = HudAnchor.TopLeft
    var previousStaticWidth = false
    var previousStaticW = 0f
    var previousStaticH = 0f

    fun apply(hud: Hud, enabled: Boolean, anchor: HudAnchor) {
        if (enabled) {
            previousGrowthAnchor = hud.growthAnchor
            previousSelfAnchor = hud.selfAnchorPoint
            previousStaticWidth = hud.staticWidth
            previousStaticW = hud.staticW
            previousStaticH = hud.staticH
            enforce(hud, anchor)
        } else {
            val left = hud.x
            val top = hud.y
            hud.staticWidth = previousStaticWidth
            hud.staticW = previousStaticW
            hud.staticH = previousStaticH
            hud.growthAnchor = previousGrowthAnchor
            hud.selfAnchorPoint = previousSelfAnchor
            hud.setAbsolutePosition(left, top)
        }
    }

    fun enforce(hud: Hud, anchor: HudAnchor) {
        if (!hud.staticWidth && hud.growthAnchor == anchor && hud.selfAnchorPoint == anchor) return
        val left = hud.x
        val top = hud.y
        hud.staticWidth = false
        hud.growthAnchor = anchor
        hud.selfAnchorPoint = anchor
        hud.setAbsolutePosition(left, top)
    }

    fun serialize(tree: Tree) {
        tree.set("previousGrowthAnchor", ktProperty(this::previousGrowthAnchor).addDisplayCondition(Supplier { Property.Display.HIDDEN }))
        tree.set("previousSelfAnchor", ktProperty(this::previousSelfAnchor).addDisplayCondition(Supplier { Property.Display.HIDDEN }))
        tree.set("previousStaticWidth", ktProperty(this::previousStaticWidth).addDisplayCondition(Supplier { Property.Display.HIDDEN }))
        tree.set("previousStaticW", ktProperty(this::previousStaticW).addDisplayCondition(Supplier { Property.Display.HIDDEN }))
        tree.set("previousStaticH", ktProperty(this::previousStaticH).addDisplayCondition(Supplier { Property.Display.HIDDEN }))
    }

    fun copy(): CenteredGrowth = CenteredGrowth().also {
        it.previousGrowthAnchor = previousGrowthAnchor
        it.previousSelfAnchor = previousSelfAnchor
        it.previousStaticWidth = previousStaticWidth
        it.previousStaticW = previousStaticW
        it.previousStaticH = previousStaticH
    }
}
