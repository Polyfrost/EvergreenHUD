@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client

import net.fabricmc.api.ClientModInitializer
import net.minecraft.core.BlockPos
//? if > 1.8.9 {
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket
//?} else
//import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket
import net.minecraft.world.entity.Entity
import org.polyfrost.evergreenhud.client.config.GlobalConfig
import org.polyfrost.evergreenhud.client.hooks.EnderChestTracker
import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewOffscreen
import org.polyfrost.evergreenhud.client.hud.*
import org.polyfrost.evergreenhud.client.hud.armor.*
import org.polyfrost.evergreenhud.client.hud.item.VanillaTextures
import org.polyfrost.evergreenhud.client.hud.battery.BatteryHud
import org.polyfrost.evergreenhud.client.utils.battery.Battery
import org.polyfrost.evergreenhud.client.hud.clock.ClockHud
import org.polyfrost.evergreenhud.client.hud.clock.DigitalClockHud
import org.polyfrost.evergreenhud.client.hud.direction.DirectionHud
import org.polyfrost.evergreenhud.client.hud.keystrokes.KeystrokesHud
import org.polyfrost.evergreenhud.client.hud.media.LyricsHud
import org.polyfrost.evergreenhud.client.hud.media.MediaHud
import org.polyfrost.evergreenhud.client.utils.media.MediaService
import org.polyfrost.evergreenhud.client.hud.mouse.MouseStrokesHud
import org.polyfrost.evergreenhud.client.hud.mouse.MouseTurnTracker
import org.polyfrost.evergreenhud.client.hud.potion.PotionEffectsHud
import org.polyfrost.evergreenhud.client.hud.shape.ShapeHud
import org.polyfrost.evergreenhud.client.utils.FrameTimeHelper
import org.polyfrost.evergreenhud.client.utils.shaders.ShaderMod
import org.polyfrost.evergreenhud.client.utils.SaturationTracker
import org.polyfrost.evergreenhud.client.utils.settledAttackHandler
import org.polyfrost.evergreenhud.client.utils.uniqueEntityId
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.AttackEntityEvent
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudGroups
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.TextHud
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.utils.v1.dsl.mc

object EvergreenHudClient : ClientModInitializer {
    override fun onInitializeClient() {
        ArmorMigration.register()
        FrameTimeHelper.initialize()
        PlayerPreviewOffscreen.initialize()
        VanillaTextures.initialize()
        EnderChestTracker.initialize()
        SaturationTracker.initialize()
        MouseTurnTracker.initialize()
        GlobalConfig.preload()

        val huds = listOf<() -> Hud>(
            ::ArmorHelmetHud, ::ArmorChestplateHud, ::ArmorLeggingsHud, ::ArmorBootsHud, ::ArmorMainHandHud,
            //? if > 1.8.9
            ::ArmorOffHandHud,
            ::BiomeHud, ::BlockAboveHud, ::CCounterHud,
            ::ClockHud, ::DigitalClockHud, ::ComboHud, ::CpsHud,
            ::CustomImageHud, ::CustomTextHud,
            ::DayHud, ::DirectionHud, ::EntityCounterHud, ::FacingHud, ::FpsHud,
            ::InGameTimeHud, ::InventoryHud, ::ItemCounterHud, ::ItemTrackerHud,
            ::KeystrokesHud,
            ::LoreHud, ::MemoryHud, ::MouseStrokesHud,
            ::PingHud, ::PlaceCountHud, ::PlayerHeadHud, ::PlayerPreviewHud,
            ::PlayTimeHud, ::PositionHud, ::PotionEffectsHud, ::ReachHud,
            ::ResourcePackHud, ::SaturationHud, ::ServerAddressHud,
            ::ShapeHud, ::SocialStatsHud, ::SpeedHud, ::TpsHud, ::WeatherHud,
        )

        huds.forEach { register(it) }

        val armorHuds = listOf<Class<out Hud>>(
            ArmorHelmetHud::class.java, ArmorChestplateHud::class.java, ArmorLeggingsHud::class.java,
            ArmorBootsHud::class.java, ArmorMainHandHud::class.java,
            //? if > 1.8.9
            ArmorOffHandHud::class.java,
        )
        HudGroups.registerDefault("evergreenhud-armor", "Armor Status", *armorHuds.toTypedArray())
        HudGroups.registerPreset(
            "evergreenhud-armor",
            "Armor Status",
            armorHuds,
            actions = ArmorLayout.actions,
            defaultAction = "horizontal",
        )

        register(::BatteryHud) { Battery.isSupported() }
        register(::MediaHud) { MediaService.isSupported() }
        register(::LyricsHud) { MediaService.isSupported() }
        register(::ShaderHud) { ShaderMod.isSupported() }

        BlockPositionChangedEvent()
        ServerDamageEntityEvent()
    }

    private fun register(factory: () -> Hud, supported: () -> Boolean = { true }) {
        HudManager.registerLater(GlobalConfig.id, GlobalConfig.iconPath, supported) {
            factory().also { if (it is TextHud) it.staticWidth = false }
        }
    }

    @Suppress("FunctionName")
    private fun BlockPositionChangedEvent() {
        var lastPos = BlockPos.ZERO
        eventHandler { _: TickEvent.End ->
            val player = mc.player ?: return@eventHandler
            val pos = player.blockPosition()
            if (pos != lastPos) {
                lastPos = pos
                EventManager.INSTANCE.post(BlockPositionChangedEvent(pos))
            }
        }
    }

    private const val ATTACK_CORRELATION_WINDOW_MS = 1000L

    private const val DUPLICATE_HURT_WINDOW_MS = 50L

    @Volatile
    private var lastAttacker: Entity? = null

    @Volatile
    private var lastTargetId = -1

    @Volatile
    private var lastAttackTime = 0L

    @Suppress("FunctionName")
    private fun ServerDamageEntityEvent() {
        settledAttackHandler { event: AttackEntityEvent ->
            if (event.cancelled || mc.player?.isSpectator == true) return@settledAttackHandler
            val target = event.getTarget<Any>() as? Entity ?: return@settledAttackHandler
            lastAttacker = event.getPlayer<Any>() as? Entity
            lastTargetId = target.uniqueEntityId
            lastAttackTime = System.currentTimeMillis()
        }

        eventHandler { (packet): PacketEvent.Receive ->
            //? if > 1.8.9 {
            when (packet) {
                is ClientboundDamageEventPacket -> postServerDamage(packet.entityId, packet.sourceCauseId)
                is ClientboundHurtAnimationPacket -> postServerDamage(packet.id, causeId = -1)
            }
            //?} else {
            /*if (packet is EntityEventS2CPacket && packet.event.toInt() == 2) {
                val target = packet.getEntity(mc.level ?: return@eventHandler) ?: return@eventHandler
                postServerDamage(target.uniqueEntityId, causeId = -1)
            }
            *///?}
        }
    }

    @Volatile
    private var lastPostedTargetId = -1

    @Volatile
    private var lastPostedTime = 0L

    private fun postServerDamage(targetId: Int, causeId: Int) {
        val world = mc.level ?: return
        val target = world.getEntity(targetId) ?: return
        val now = System.currentTimeMillis()

        val attacker = world.getEntity(causeId)
            ?: lastAttacker?.takeIf {
                lastTargetId == targetId && now - lastAttackTime <= ATTACK_CORRELATION_WINDOW_MS
            }

        if (targetId == lastPostedTargetId && now - lastPostedTime <= DUPLICATE_HURT_WINDOW_MS) {
            return
        }
        lastPostedTargetId = targetId
        lastPostedTime = now

        if (lastTargetId == targetId) {
            lastAttacker = null
            lastTargetId = -1
        }

        EventManager.INSTANCE.post(ServerDamageEntityEvent(attacker, target))
    }
}
