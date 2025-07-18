package org.polyfrost.evergreenhud.config

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.Exclude
import cc.polyfrost.oneconfig.config.annotations.SubConfig
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.gui.pages.ModsPage
import org.polyfrost.evergreenhud.EvergreenHUD
import org.polyfrost.evergreenhud.hud.*

object ModConfig : Config(Mod(EvergreenHUD.NAME, ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "${EvergreenHUD.MODID}.json") {

    @SubConfig var armour = Armour()
    @SubConfig var bedwarsResource = BedwarsResource()
    @SubConfig var biome = Biome()
    @SubConfig var blockAbove = BlockAbove()
    @SubConfig var cCounter = CCounter()
    @SubConfig var clock = Clock()
    @SubConfig var combo = Combo()
    @SubConfig var coordinates = Coordinates()
    @SubConfig var cps = CPS()
    @SubConfig var customImages = CustomImages()
    @SubConfig var customTexts = CustomTexts()
    @SubConfig var day = Day()
    @SubConfig var direction = Direction()
    @SubConfig var eCounter = ECounter()
    @SubConfig var fps = FPS()
    @SubConfig var gameMode = GameMode()
    @SubConfig var gameType = GameType()
    @SubConfig var heightLimit = HeightLimit()
    @SubConfig var heldItemLore = HeldItemLore()
    @SubConfig var inGameTime = InGameTime()
    @SubConfig var inventory = Inventory()
    @SubConfig var map = Map()
    @SubConfig var mapType = MapType()
    @SubConfig var memory = Memory()
    @SubConfig var ping = Ping()
    @SubConfig var pitch = Pitch()
    @SubConfig var placeCount = PlaceCount()
    @SubConfig var playerPreview = PlayerPreview()
    @SubConfig var playTime = Playtime()
    @SubConfig var reach = Reach()
    @SubConfig var realLifeDate = RealLifeDate()
    @SubConfig var realLifeTime = RealLifeTime()
    @SubConfig var resourcePack = ResourcePack()
    @SubConfig var saturation = Saturation()
    @SubConfig var serverIP = ServerIP()
    @SubConfig var speed = Speed()
    @SubConfig var tps = TPS()
    @SubConfig var yaw = Yaw()

}
