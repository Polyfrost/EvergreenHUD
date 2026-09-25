package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.ServerChangedEvent
import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.utils.v1.dsl.mc

class ServerAddressHud : CachedTextHud(
    id = "server_ip.json",
    title = "Server Address",
    category = Category.INFO,
    prefix = "IP: ",
) {
    @Switch(title = "Show in Single Player")
    var showInSinglePlayer = true

    @Text(title = "No Server Text")
    var noServerText = "127.0.0.1"

    override val defaultText: String by ::noServerText

    override fun setup() {
        super.setup()
        if (!isReal) {
            updateWithText(EXAMPLE_ADDRESS)
            return
        }

        eventHandler { (ip, _, _): ServerChangedEvent ->
            applyServer(ip)
        }

        applyServer(mc.currentServer?.ip)

        updateWhenChanged("showInSinglePlayer")
        updateWhenChanged("noServerText")
    }

    private fun applyServer(ip: String?) {
        updateWithText(ip)
    }

    override fun shouldShow(): Boolean = showInSinglePlayer || !mc.hasSingleplayerServer()

    override fun getText(): String? = if (isReal) super.getText() else EXAMPLE_ADDRESS

    private companion object {
        private const val EXAMPLE_ADDRESS = "polyfrost.org"
    }
}
