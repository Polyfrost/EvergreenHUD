package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.renderer.chunk.CompiledChunk
import org.polyfrost.evergreenhud.config.HudConfig

class CCounter: HudConfig("C Counter", "evergreenhud/ccounter.json", false) {
    @HUD(name = "Main")
    var hud = CCounterHud()

    init {
        initialize()
    }

    class CCounterHud: SingleTextHud("C", true, 400, 70) {

        @Switch(
                name = "Simplified"
        )
        var simplified = true

        override fun getText(example: Boolean): String {
            if (mc.thePlayer == null) return "Unknown"
            val i = mc.renderGlobal.viewFrustum.renderChunks.size
            var j = 0
            for (`renderglobal$containerlocalrenderinformation` in this.renderInfos) {
                val compiledchunk = `renderglobal$containerlocalrenderinformation`.renderChunk.compiledChunk
                if (compiledchunk === CompiledChunk.DUMMY || compiledchunk.isEmpty) continue
                ++j
            }
            // looking at source of this method
            // int i = this.viewFrustum.renderChunks.length; is what you want and is much faster
            // if it isnt that simple atleast use a substring instead of this splitting
            return if (simplified) mc.renderGlobal.debugInfoRenders.split("/")[0].replace("C: ", "")
                else mc.renderGlobal.debugInfoRenders.split(" ")[1]
        }
    }

}