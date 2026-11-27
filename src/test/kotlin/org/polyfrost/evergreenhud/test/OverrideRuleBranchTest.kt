package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.potion.AnyOf
import org.polyfrost.evergreenhud.client.hud.potion.OverrideCondition
import org.polyfrost.evergreenhud.client.hud.potion.OverrideCondition.Type
import org.polyfrost.evergreenhud.client.hud.potion.OverrideRule

class OverrideRuleBranchTest {

    private fun alternative(type: Type) = OverrideRule(listOf(OverrideCondition(type)))

    @Test
    fun conditionOnlyHasToFitOneBranch() {
        val rule = OverrideRule(anyOf = listOf(AnyOf(listOf(alternative(Type.EFFECT), alternative(Type.AMBIENT)))))
        assertTrue(rule.canAdd(Type.CATEGORY))
        assertFalse(rule.locksEffectNegation())

        val both = OverrideRule(anyOf = listOf(AnyOf(listOf(alternative(Type.EFFECT), alternative(Type.EFFECT)))))
        assertFalse(both.canAdd(Type.EFFECT))
        assertFalse(both.canAdd(Type.CATEGORY))
    }

    @Test
    fun locksNeedEveryBranch() {
        val some = OverrideRule(anyOf = listOf(AnyOf(listOf(alternative(Type.ELAPSED), alternative(Type.AMBIENT)))))
        assertFalse(some.locksPermanent())

        val every = OverrideRule(listOf(OverrideCondition(Type.CATEGORY)), listOf(AnyOf(listOf(alternative(Type.ELAPSED), alternative(Type.PERCENT_LEFT)))))
        assertTrue(every.locksPermanent())
        assertTrue(every.locksEffectNegation())
    }

    @Test
    fun alternativeSeesOuterConditionsAndOtherBlocks() {
        val block = AnyOf(listOf(alternative(Type.AMBIENT)))
        val rule = OverrideRule(listOf(OverrideCondition(Type.CATEGORY)), listOf(block, AnyOf(listOf(alternative(Type.EFFECT)))))
        val alongside = rule.alongside(block)
        assertFalse(OverrideRule().canAdd(Type.EFFECT, alongside))
        assertTrue(OverrideRule().locksEffectNegation(alongside))
    }
}
