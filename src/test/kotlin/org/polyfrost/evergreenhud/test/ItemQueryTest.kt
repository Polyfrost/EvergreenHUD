package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.potion.ItemEntry
import org.polyfrost.evergreenhud.client.hud.potion.ItemLocation
import org.polyfrost.evergreenhud.client.hud.potion.ItemQuery
import org.polyfrost.evergreenhud.client.hud.potion.ItemSnapshot
import org.polyfrost.evergreenhud.client.hud.potion.itemKey

class ItemQueryTest {

    private val potion = "minecraft:potion"
    private val healing = itemKey(potion, "minecraft:healing")

    private fun inventory(vararg stacks: List<String>) = ItemSnapshot.Builder().apply {
        for (keys in stacks) add(keys, 1, ItemLocation.INVENTORY)
    }.build()

    @Test
    fun oneStackSatisfiesOnlyOneEntry() {
        val query = ItemQuery(ItemLocation.INVENTORY, 2, listOf(ItemEntry(potion), ItemEntry(healing)))
        assertFalse(query.matches(inventory(listOf(potion, healing))))
        assertTrue(query.matches(inventory(listOf(potion, healing), listOf(potion, healing))))
        assertTrue(query.matches(inventory(listOf(potion, itemKey(potion, "minecraft:swiftness")), listOf(potion, healing))))
    }
}
