package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.TextHud
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor

abstract class EvergreenTextHud(id: String, title: String, category: Category, prefix: String = "", suffix: String = "") : TextHud(id, title, category, prefix, suffix) {
    @Switch(title = "Centered Growth", description = "Keeps the middle of the HUD fixed as its content changes size.", subcategory = "Dimensions")
    var centeredGrowth = false

    /** Modules opt in when this control is useful for their layout. */
    protected open val supportsCenteredGrowth: Boolean get() = false

    private var centering = CenteredGrowth()
    protected val layoutAnchor get() = if (centeredGrowth) centering.previousSelfAnchor else selfAnchorPoint
    protected open fun centeredGrowthAnchor(): HudAnchor = HudAnchor.Center

    internal fun applyCenteredGrowth(enabled: Boolean) = centering.apply(this, enabled, centeredGrowthAnchor())
    protected fun enforceCenteredGrowth() = centering.enforce(this, centeredGrowthAnchor())

    override var renderedW: Float
        get() = super.renderedW
        set(value) {
            if (supportsCenteredGrowth && centeredGrowth) enforceCenteredGrowth()
            super.renderedW = value
        }
    override var renderedH: Float
        get() = super.renderedH
        set(value) {
            if (supportsCenteredGrowth && centeredGrowth) enforceCenteredGrowth()
            super.renderedH = value
        }

    override fun setup() {
        super.setup()
        if (isReal) {
            // Undo saved centering from versions that exposed it on every HUD.
            if (!supportsCenteredGrowth && centeredGrowth) {
                applyCenteredGrowth(false)
                centeredGrowth = false
            }
            hideIf("centeredGrowth") { !supportsCenteredGrowth }
            if (supportsCenteredGrowth && centeredGrowth) enforceCenteredGrowth()
            hideIf("staticWidth") { centeredGrowth }
            addCallback("centeredGrowth") { enabled: Boolean ->
                applyCenteredGrowth(enabled)
                false
            }
        }
    }

    override fun addToSerialized(tree: Tree) {
        super.addToSerialized(tree)
        centering.serialize(tree)
    }

    override fun clone(): Hud = (super.clone() as EvergreenTextHud).also { it.centering = centering.copy() }
}
