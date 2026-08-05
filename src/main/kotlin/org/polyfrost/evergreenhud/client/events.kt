package org.polyfrost.evergreenhud.client

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.polyfrost.oneconfig.api.event.v1.events.Event

data class ClientDamageEntityEvent(
    val attacker: Entity,
    val target: Entity
) : Event

data class ServerDamageEntityEvent(
    val attacker: Entity?,
    val target: Entity
) : Event

data class ClientPlaceBlockEvent(
    val player: Player,
    val world: Level
) : Event

data class ServerChangedEvent(
    val ip: String?,
    val name: String?,
    //~ if = 1.8.9 'Component' -> 'String'
    val motd: Component?
) : Event

data class SaturationChangedEvent(
    val saturation: Float
) : Event

data class BlockChangeEvent(
    val pos: BlockPos
) : Event

data class BlockPositionChangedEvent(
    val pos: BlockPos,
) : Event

data object ResourceReloadEvent : Event

data object EntityCounterEvent : Event {

    @JvmStatic
    var total = 0

    @JvmStatic
    var rendered = 0

}
