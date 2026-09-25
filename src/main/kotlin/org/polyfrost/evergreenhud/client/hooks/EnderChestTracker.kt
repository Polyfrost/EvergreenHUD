package org.polyfrost.evergreenhud.client.hooks

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
//? if = 1.8.9
//import net.minecraft.client.resource.language.I18n
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.utils.v1.dsl.mc

const val ENDER_CHEST_SLOTS = 27

object EnderChestTracker {
    //? if > 1.8.9 {
    private val enderChestTitle = Component.translatable("container.enderchest").contents
    //?} else {
    /*private val enderChestTitle
        get() = I18n.translate("container.enderchest")
    *///?}

    //~ if = 1.8.9 'List<ItemStack>' -> 'List<ItemStack?>'
    private var contents: List<ItemStack> = emptyList()

    fun initialize() {
        eventHandler { _: TickEvent.End ->
            if (mc.player == null) {
                contents = emptyList()
            } else {
                capture()
            }
        }
    }

    //~ if = 1.8.9 'List<ItemStack>?' -> 'List<ItemStack?>?'
    fun contents(): List<ItemStack>? = contents.takeIf { it.isNotEmpty() }

    private fun capture() {
        //? if = 1.8.9
        //val screen = mc.screen as? AbstractContainerScreen ?: return
        //? if < 26.2 && > 1.8.9
        //val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        //? if >= 26.2
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return
        val menu = screen.menu as? ChestMenu ?: return
        //? if > 1.8.9 {
        if (menu.rowCount != 3 || screen.title.contents != enderChestTitle) return
        //?} else
        //if (menu.container.containerSize / 9 != 3 || menu.container.displayName.string != enderChestTitle) return

        val container = menu.container
        if (container.containerSize != ENDER_CHEST_SLOTS) return
        //~ if = 1.8.9 'container.getItem(it).copy()' -> 'container.getItem(it)?.copy()'
        contents = List(ENDER_CHEST_SLOTS) { container.getItem(it).copy() }
    }
}
