package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.libs.universal.USound
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.block.*
import net.minecraft.init.Blocks
import net.minecraft.util.BlockPos
import org.polyfrost.evergreenhud.config.HudConfig

class BlockAbove: HudConfig("Block Above", "evergreenhud/blockabove.json", false) {
    @HUD(name = "Main")
    var hud = BlockAboveHud()

    init {
        initialize()
    }

    class BlockAboveHud: SingleTextHud("Above", true, 120, 50) {
        @Switch(
            name = "Notify With Sound"
        )
        var notify = false

        @Slider(
            name = "Notify Height",
            min = 1F,
            max = 10F,
            step = 1
        )
        var notifyHeight = 3

        @Slider(
            name = "Check Height",
            min = 1F,
            max = 30F,
            step = 1
        )
        var checkHeight = 10

        @Exclude
        private var notified = false

        override fun getText(example: Boolean): String {
            if (mc.theWorld == null || mc.thePlayer == null) return "0"

            var above = 0
            // you could like probably change this to do the calculation somewhere else instead of on every frame
            for (i in 1..checkHeight) {
                val pos = BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1 + i, mc.thePlayer.posZ)
                if (pos.y > mc.theWorld!!.height) break

                val state = mc.theWorld!!.getBlockState(pos) ?: continue
                if (state.block == Blocks.air
                    || state.block == Blocks.water
                    || state.block is BlockSign
                    || state.block is BlockVine
                    || state.block is BlockBanner)
                    continue

                above = i

                if (above <= notifyHeight && notify) {
                    if (!notified) {
                        USound.playExpSound()
                        notified = true
                    }
                } else {
                    notified = false
                }

                break
            }

            return above.toString()
        }
    }
}