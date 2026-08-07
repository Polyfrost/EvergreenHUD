package org.polyfrost.evergreenhud.client

import net.fabricmc.api.ClientModInitializer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.entity.Entity
import org.polyfrost.evergreenhud.client.config.GlobalConfig
import org.polyfrost.evergreenhud.client.hooks.EnderChestTracker
import org.polyfrost.evergreenhud.client.hud.*
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
        FrameTimeHelper.initialize()
        EnderChestTracker.initialize()
        SaturationTracker.initialize()
        GlobalConfig.preload()

        val huds = arrayOf(
            ArmorHud(),
            BiomeHud(), BlockAboveHud(),
            ClockHud(), DigitalClockHud(), ComboHud(), CpsHud(),
            DayHud(), DirectionHud(), EntityCounterHud(), FacingHud(), FpsHud(),
            InGameTimeHud(), InventoryHud(),
            KeystrokesHud(),
            LoreHud(), MemoryHud(), MouseStrokesHud(),
            PingHud(), PlaceCountHud(), PlayerPreviewHud(),
            PlayTimeHud(), PositionHud(), PotionEffectsHud(), ReachHud(),
            ResourcePackHud(), SaturationHud(), ServerAddressHud(),
            ShapeHud(), SpeedHud(), TpsHud(), WeatherHud(),
        )

        huds.forEach(::register)

        registerIfSupported(Battery.isSupported(), ::BatteryHud)

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

        EventManager.INSTANCE.register(object : EventHandler<InitializationEvent>() {
            override fun handle(event: InitializationEvent): Boolean {
                HudManager.unregister(factory(), removeActiveInstances = true)
                return false
            }

            override fun getEventClass(): Class<InitializationEvent> = InitializationEvent::class.java

            override fun getPriority(): Int = Int.MIN_VALUE
        })
    }

    private val recentBlockChanges = ConcurrentLinkedQueue<BlockPos>()

    private fun BlockChangeEvent() {
        eventHandler { event: PacketEvent.Receive ->
            when (val packet = event.getPacket<Any>()) {
                is ClientboundBlockUpdatePacket -> recentBlockChanges.add(packet.pos)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, _ -> recentBlockChanges.add(pos) }

            }
        }
        eventHandler { _: TickEvent ->
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
            when (packet) {
                is ClientboundDamageEventPacket -> postServerDamage(packet.entityId, packet.sourceCauseId)
                is ClientboundHurtAnimationPacket -> postServerDamage(packet.id, causeId = -1)
            }
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
