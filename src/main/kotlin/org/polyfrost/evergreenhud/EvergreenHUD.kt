package org.polyfrost.evergreenhud

import org.polyfrost.evergreenhud.hud.*
import org.polyfrost.evergreenhud.utils.PinkuluAPIManager
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world.World
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import org.polyfrost.evergreenhud.config.ModConfig

@Mod(modid = EvergreenHUD.MODID, name = EvergreenHUD.NAME, version = EvergreenHUD.VERSION)
class EvergreenHUD {
    @Mod.EventHandler
    fun onFMLInitialization(event: FMLInitializationEvent) {
        ModConfig.initialize()
        PinkuluAPIManager.initialize()
        Armour()
        BedwarsResource()
        Biome()
        BlockAbove()
        CCounter()
        Combo()
        Coordinates()
        CPS()
        CustomImages()
        CustomTexts()
        Day()
        Direction()
        ECounter()
        FPS()
        GameMode()
        GameType()
        HeightLimit()
        HeldItemLore()
        InGameTime()
        Inventory()
        Map()
        MapType()
        Memory()
        //Ping()
        Pitch()
        PlaceCount()
        PlayerPreview()
        Playtime()
        Reach()
        RealLifeDate()
        RealLifeTime()
        Saturation()
        ServerIP()
        Speed()
        TPS()
        Yaw()
    }

    companion object {
        var mods: ArrayList<cc.polyfrost.oneconfig.config.data.Mod> = ArrayList()
        const val MODID = "@ID@"
        const val NAME = "@NAME@"
        const val VERSION = "@VER@"
    }
}

class ClientDamageEntityEvent(val attacker: Entity, val target: Entity)
class ClientPlaceBlockEvent(val player: EntityPlayer, val world: World)
