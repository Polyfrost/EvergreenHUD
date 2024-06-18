package org.polyfrost.evergreenhud.utils

import net.minecraft.item.ItemStack

object ItemStackUtils {
    // you could add this directly to the list for the hud instead of creating and intermediate one
    fun ItemStack.getLore(): List<String> {
        val list: MutableList<String> = ArrayList()
        val theTagCompound = this.tagCompound ?: return list
        val theTagList = theTagCompound.getCompoundTag("display").getTagList("Lore", 8)
        for (i in 0..<theTagList.tagCount()) {
            list.add(theTagList.getStringTagAt(i))
        }
        return list
    }
}