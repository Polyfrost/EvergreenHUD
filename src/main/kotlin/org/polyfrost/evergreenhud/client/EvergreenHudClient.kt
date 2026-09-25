package org.polyfrost.evergreenhud.client

import net.fabricmc.api.ClientModInitializer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
//? if > 1.8.9 {
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
//?} else {
/*import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket
*///?}
import net.minecraft.world.entity.Entity
import org.polyfrost.evergreenhud.client.config.GlobalConfig
import org.polyfrost.evergreenhud.client.hooks.EnderChestTracker
import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewOffscreen
import org.polyfrost.evergreenhud.client.hud.*
import org.polyfrost.evergreenhud.client.hud.item.VanillaTextures
import org.polyfrost.evergreenhud.client.hud.battery.BatteryHud
import org.polyfrost.evergreenhud.client.utils.battery.Battery
import org.polyfrost.evergreenhud.client.hud.clock.ClockHud
import org.polyfrost.evergreenhud.client.hud.clock.DigitalClockHud
import org.polyfrost.evergreenhud.client.hud.direction.DirectionHud
import org.polyfrost.evergreenhud.client.hud.keystrokes.KeystrokesHud
import org.polyfrost.evergreenhud.client.hud.mouse.MouseStrokesHud
import org.polyfrost.evergreenhud.client.hud.potion.PotionEffectsHud
import org.polyfrost.evergreenhud.client.hud.shape.ShapeHud
import org.polyfrost.evergreenhud.client.utils.FrameTimeHelper
import org.polyfrost.evergreenhud.client.utils.shaders.ShaderMod
import org.polyfrost.evergreenhud.client.utils.SaturationTracker
import org.polyfrost.evergreenhud.client.utils.uniqueEntityId
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.InitializationEvent
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.TextHud
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import java.util.concurrent.ConcurrentLinkedQueue

object EvergreenHudClient : ClientModInitializer {
    override fun onInitializeClient() {
        // 1.8.9 runs entrypoints before Minecraft.init, so HUDs can't be built yet (I18n isn't loaded).
        // Wait for InitializationEvent at the end of init, at the lowest priority so OneConfig's own setup runs first
        //? if = 1.8.9 {
        /*EventManager.INSTANCE.register(object : EventHandler<InitializationEvent>() {
            override fun handle(event: InitializationEvent): Boolean {
                initializeClient()
                return true
            }

            override fun getEventClass(): Class<InitializationEvent> = InitializationEvent::class.java

            override fun getPriority(): Int = Int.MIN_VALUE
        })
        *///?} else
        initializeClient()
    }

    private fun initializeClient() {
        FrameTimeHelper.initialize()
        PlayerPreviewOffscreen.initialize()
        VanillaTextures.initialize()
        EnderChestTracker.initialize()
        SaturationTracker.initialize()
        GlobalConfig.preload()

        val huds = arrayOf(
            ArmorHud(),
            BiomeHud(), BlockAboveHud(),
            ClockHud(), DigitalClockHud(), ComboHud(), CpsHud(),
            CustomImageHud(), CustomTextHud(),
            DayHud(), DirectionHud(), EntityCounterHud(), FacingHud(), FpsHud(),
            InGameTimeHud(), InventoryHud(), ItemCounterHud(), ItemTrackerHud(),
            KeystrokesHud(),
            LoreHud(), MemoryHud(), MouseStrokesHud(),
            PingHud(), PlaceCountHud(), PlayerHeadHud(), PlayerPreviewHud(),
            PlayTimeHud(), PositionHud(), PotionEffectsHud(), ReachHud(),
            ResourcePackHud(), SaturationHud(), ServerAddressHud(),
            ShapeHud(), SocialStatsHud(), SpeedHud(), TpsHud(), WeatherHud(),
        )

        huds.forEach(::register)

        registerIfSupported(Battery.isSupported(), ::BatteryHud)
        registerIfSupported(ShaderMod.isSupported(), ::ShaderHud)

        BlockChangeEvent()
        BlockPositionChangedEvent()
        ServerDamageEntityEvent()
    }

    private fun register(hud: Hud) {
        if (hud is TextHud) hud.staticWidth = false
        HudManager.register(hud, GlobalConfig.id, GlobalConfig.iconPath)
    }

    private fun registerIfSupported(supported: Boolean, factory: () -> Hud) {
        if (supported) {
            register(factory())
            return
        }

        //? if = 1.8.9 {
        /*// already inside InitializationEvent on 1.8.9, so a handler registered now would never run
        HudManager.unregister(factory(), removeActiveInstances = true)
        *///?} else {
        EventManager.INSTANCE.register(object : EventHandler<InitializationEvent>() {
            override fun handle(event: InitializationEvent): Boolean {
                HudManager.unregister(factory(), removeActiveInstances = true)
                return false
            }

            override fun getEventClass(): Class<InitializationEvent> = InitializationEvent::class.java

            override fun getPriority(): Int = Int.MIN_VALUE
        })
        //?}
    }

    private val recentBlockChanges = ConcurrentLinkedQueue<BlockPos>()

    private fun BlockChangeEvent() {
        eventHandler { event: PacketEvent.Receive ->
            when (val packet = event.getPacket<Any>()) {
                is ClientboundBlockUpdatePacket -> recentBlockChanges.add(packet.pos)
                //? if > 1.8.9 {
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, _ -> recentBlockChanges.add(pos) }
                //?} else
                //is BlocksUpdateS2CPacket -> packet.updates.forEach { update -> recentBlockChanges.add(update.blockPos) }

            }
        }
        eventHandler { _: TickEvent.End ->
            while (true) {
                val pos = recentBlockChanges.poll() ?: break
                EventManager.INSTANCE.post(BlockChangeEvent(pos))
            }
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
        eventHandler { (attacker, target): ClientDamageEntityEvent ->
            lastAttacker = attacker
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
