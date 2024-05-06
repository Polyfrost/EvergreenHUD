package org.polyfrost.evergreenhud

import org.polyfrost.evergreenhud.utils.PinkuluAPIManager
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world.World
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import org.polyfrost.evergreenhud.config.ModConfig

@Mod(modid = EvergreenHUD.MODID, name = EvergreenHUD.NAME, version = EvergreenHUD.VERSION)
class EvergreenHUD {
    @Mod.EventHandler
    fun onFMLInitialization(event: FMLInitializationEvent) {
        ModConfig.initialize()
        PinkuluAPIManager.initialize()
    }

    @Mod.EventHandler
    fun onPostInit(event: FMLPostInitializationEvent) {
        isPolyNametag = Loader.isModLoaded("polynametag")
    }

    companion object {
        var isPolyNametag = false
        const val MODID = "@ID@"
        const val NAME = "@NAME@"
        const val VERSION = "@VER@"
    }
}

class ClientDamageEntityEvent(val attacker: Entity, val target: Entity)
class ClientPlaceBlockEvent(val player: EntityPlayer, val world: World)
