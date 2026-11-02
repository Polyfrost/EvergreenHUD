package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.EntityCounterEvent
import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler

class EntityCounterHud : CachedTextHud(
    title = "Entity Counter",
    category = Category.INFO,
    prefix = "Entities: ",
    defaultText = "0"
) {
    @Switch(title = "Show total entities")
    var showTotal = true

    override fun getText(): String? = when {
        isReal -> super.getText()
        showTotal -> "24/87"
        else -> "24"
    }

    override fun setup() {
        super.setup()
        eventHandler { ev: EntityCounterEvent ->
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
}
