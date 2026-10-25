package org.polyfrost.evergreenhud.client.utils.media

object LinuxMedia : MediaBackend {
    private const val PREFIX = "org.mpris.MediaPlayer2."
    private const val PATH = "/org/mpris/MediaPlayer2"
    private const val PLAYER = "org.mpris.MediaPlayer2.Player"
    private const val BUS = "org.freedesktop.DBus"
    private const val BUS_PATH = "/org/freedesktop/DBus"
    private const val PROPERTIES = "org.freedesktop.DBus.Properties"
    private const val REDISCOVER_MS = 30_000L
    private const val OWNER_MATCH =
        "type='signal',sender='$BUS',interface='$BUS',member='NameOwnerChanged',arg0namespace='org.mpris.MediaPlayer2'"

    private var connection: DBusConnection? = null
    private var players = emptyList<String>()
    private var discoveredAt = 0L
    private var ownersChanged = false
    private var lastActive: String? = null

    fun isAvailable(): Boolean =
        System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true) &&
            runCatching { DBusConnection.session().close() }.isSuccess

    @Synchronized
    override fun poll(): MediaInfo? {
        val bus = connect() ?: return null
        return try {
            bus.drain()
            discover(bus)
            val chosen = players.mapNotNull { name ->
                runCatching { properties(bus, name) }.getOrNull()?.let { parse(name, it) }
            }.pickActive(lastActive)
            if (chosen?.playing == true) lastActive = chosen.player
            chosen
        } catch (_: Exception) {
            close()
            null
        }
    }

    @Synchronized
    override fun send(player: String, command: MediaCommand) {
        val bus = connect() ?: return
        val method = when (command) {
            MediaCommand.Previous -> "Previous"
            MediaCommand.Toggle -> "PlayPause"
            MediaCommand.Next -> "Next"
        }
        runCatching { bus.call(player, PATH, PLAYER, method) }.onFailure { close() }
    }

    @Synchronized
    override fun close() {
        connection?.close()
        connection = null
        players = emptyList()
        discoveredAt = 0L
    }

    private fun connect(): DBusConnection? {
        connection?.let { return it }
        return runCatching {
            DBusConnection.session().also { bus ->
                bus.onSignal = { if (it.member == "NameOwnerChanged") ownersChanged = true }
                bus.call(BUS, BUS_PATH, BUS, "AddMatch", OWNER_MATCH)
                connection = bus
            }
        }.getOrNull()
    }

    private fun discover(bus: DBusConnection) {
        val now = System.currentTimeMillis()
        if (!ownersChanged && now - discoveredAt < REDISCOVER_MS) return
        ownersChanged = false
        discoveredAt = now
        @Suppress("UNCHECKED_CAST")
        players = (bus.call(BUS, BUS_PATH, BUS, "ListNames").firstOrNull() as? List<String>)
            .orEmpty()
            .filter { it.startsWith(PREFIX) }
    }

    private fun properties(bus: DBusConnection, player: String): Map<*, *>? =
        bus.call(player, PATH, PROPERTIES, "GetAll", PLAYER).firstOrNull() as? Map<*, *>

    fun parse(player: String, props: Map<*, *>): MediaInfo? {
        val meta = props["Metadata"] as? Map<*, *>
        val title = meta?.get("xesam:title") as? String ?: ""
        val artist = when (val value = meta?.get("xesam:artist")) {
            is List<*> -> value.joinToString(", ")
            is String -> value
            else -> ""
        }
        if (title.isEmpty() && artist.isEmpty()) return null

        return MediaInfo(
            player = player,
            title = title.ifEmpty { player.removePrefix(PREFIX) },
            artist = artist,
            artUrl = (meta?.get("mpris:artUrl") as? String)?.takeIf { it.isNotEmpty() },
            playing = props["PlaybackStatus"] == "Playing",
            positionMs = micros(props["Position"]) / 1000L,
            lengthMs = micros(meta?.get("mpris:length")) / 1000L,
            canPrevious = props["CanGoPrevious"] as? Boolean ?: false,
            canNext = props["CanGoNext"] as? Boolean ?: false,
            canToggle = props["CanControl"] as? Boolean ?: true,
        )
    }

    private fun micros(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
}
