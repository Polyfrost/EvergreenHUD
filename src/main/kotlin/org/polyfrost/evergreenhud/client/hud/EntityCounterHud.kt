package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.EntityCounterEvent
import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler

class EntityCounterHud : CachedTextHud(
    title = "Entity Counter",
    category = Category.INFO,
    prefix = "Entities: ",
    defaultText = "0"
) {
    @Switch(title = "Show total entities")
    var showTotal = true

    private var handler: EventHandler<*>? = null
    private var lastRendered = -1
    private var lastTotal = -1
    private var lastShowTotal = false

    override fun getText(): String? = when {
        isReal -> super.getText()
        showTotal -> "24/87"
        else -> "24"
    }

    override fun setup() {
        super.setup()
        handler = eventHandler { ev: EntityCounterEvent ->
            if (ev.rendered == lastRendered && ev.total == lastTotal && showTotal == lastShowTotal) return@eventHandler
            lastRendered = ev.rendered
            lastTotal = ev.total
            lastShowTotal = showTotal
            val text = buildString {
                append(ev.rendered)
                if (showTotal) {
                    append('/').append(ev.total)
                }
            }

            updateWithText(text)
        }

        if (isReal) {
            updateWhenChanged("showTotal")
        }
    }

    override fun remove() {
        super.remove()
        handler?.unregister()
        handler = null
    }
}
