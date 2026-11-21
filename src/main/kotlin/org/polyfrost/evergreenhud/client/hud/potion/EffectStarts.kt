package org.polyfrost.evergreenhud.client.hud.potion

/**
 * The game only knows how long an effect has left, so its start is the time left when first seen, moved
 * forward whenever the time left goes up or the amplifier changes as both mean it was applied again
 */
class EffectStarts {
    class Seen(val id: String, val ticks: Int, val amplifier: Int)

    private class Start(val ticks: Int, val amplifier: Int, var lastTicks: Int)

    private val starts = HashMap<String, Start>()

    fun startTicks(id: String): Int? = starts[id]?.ticks

    fun clear() = starts.clear()

    fun update(effects: List<Seen>) {
        for (effect in effects) {
            val start = starts[effect.id]
            if (start == null || start.amplifier != effect.amplifier || effect.ticks > start.lastTicks) {
                starts[effect.id] = Start(effect.ticks, effect.amplifier, effect.ticks)
            } else {
                start.lastTicks = effect.ticks
            }
        }
        starts.keys.retainAll(effects.map { it.id }.toSet())
    }
}
