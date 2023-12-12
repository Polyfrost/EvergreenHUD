package org.polyfrost.evergreenhud.utils

import net.minecraft.item.ItemStack

object ItemStackUtils {
    fun ItemStack.getLore(): List<String> {
        var list: MutableList<String> = ArrayList<String>()
        val theTagCompound = this.tagCompound ?: return list
        val theTagList = theTagCompound.getCompoundTag("display").getTagList("Lore", 8)
        for (i in 0..(theTagList.tagCount() - 1)) {
            list.add(theTagList.getStringTagAt(i))
        }
        return list
    }
}