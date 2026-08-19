package org.polyfrost.evergreenhud.client.utils.social

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.utils.v1.JsonUtils
import org.polyfrost.oneconfig.utils.v1.NetworkUtils
import org.polyfrost.oneconfig.utils.v1.dsl.runAsync
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object SocialStats {
    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Social")

    private const val REFRESH_SECONDS = 5L

    private const val CHECK_INTERVAL_TICKS = 20

    private const val MIN_RETRY_SECONDS = 30L
    private const val MAX_RETRY_SECONDS = 900L

    private const val FORGET_AFTER_NANOS = 60_000_000_000L

    private val CHANNEL_ID = Regex("^UC[A-Za-z0-9_-]{22}$")
    private val CHANNEL_ID_IN_PAGE = Regex("\"externalId\":\"(UC[A-Za-z0-9_-]{22})\"")
    private val CANONICAL_IN_PAGE =
        Regex("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/channel/(UC[A-Za-z0-9_-]{22})\"")

    enum class Platform { YOUTUBE, TWITCH }

    data class Stats(

        val count: Long,

        val goal: Long,
        val views: Long,
        val videos: Long,

        val viewers: Long,
    )

    class Status internal constructor(
        val stats: Stats?,

        val unknownChannel: Boolean,
    )

    private data class Key(val platform: Platform, val channel: String)

    private class Entry {
        val fetching = AtomicBoolean()

        @Volatile
        var stats: Stats? = null

        @Volatile
        var unknownChannel = false

        @Volatile
        var channelId: String? = null

        @Volatile
        var wantsViewers = false

        @Volatile
        var lastRequested = 0L

        @Volatile
        var fetchedAt: Instant = Instant.MIN

        @Volatile
        var retryAfter: Instant = Instant.MIN

        @Volatile
        var retryDelaySeconds = MIN_RETRY_SECONDS
    }

    private val entries = ConcurrentHashMap<Key, Entry>()

    private val started = AtomicBoolean()

    private val EMPTY = Status(null, unknownChannel = false)

    fun get(platform: Platform, channel: String, wantsViewers: Boolean = false): Status {
        val name = channel.trim()
        if (name.isEmpty()) return EMPTY

        ensureStarted()

        val entry = entries.computeIfAbsent(Key(platform, name)) { Entry() }
        entry.lastRequested = System.nanoTime()
        entry.wantsViewers = wantsViewers
        return Status(entry.stats, entry.unknownChannel)
    }

    private fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return

        var tickCount = 0
        eventHandler { _: TickEvent.Start ->
            if (tickCount++ % CHECK_INTERVAL_TICKS != 0) return@eventHandler
            refreshStale()
        }
    }

    private fun refreshStale() {
        val now = Instant.now()
        val nanos = System.nanoTime()

        for ((key, entry) in entries) {
            if (nanos - entry.lastRequested > FORGET_AFTER_NANOS) {
                entries.remove(key, entry)
                continue
            }
            if (now.isBefore(entry.fetchedAt.plusSeconds(REFRESH_SECONDS))) continue
            if (now.isBefore(entry.retryAfter)) continue
            runAsync { fetch(key, entry) }
        }
    }

    private fun fetch(key: Key, entry: Entry) {
        if (Instant.now().isBefore(entry.retryAfter)) return
        if (!entry.fetching.compareAndSet(false, true)) return

        try {
            val stats = when (key.platform) {
                Platform.YOUTUBE -> youTubeStats(key.channel, entry)
                Platform.TWITCH -> twitchStats(key.channel, entry)
            }

            if (stats == null) {
                backOff(key, entry)
                return
            }

            entry.unknownChannel = false
            entry.stats = stats
            entry.fetchedAt = Instant.now()
            entry.retryDelaySeconds = MIN_RETRY_SECONDS
        } finally {
            entry.fetching.set(false)
        }
    }

    private fun backOff(key: Key, entry: Entry) {
        entry.retryAfter = Instant.now().plusSeconds(entry.retryDelaySeconds)
        LOGGER.warn(
            "Failed to fetch the {} counts for {}, retrying in {} seconds",
            key.platform.name.lowercase(), key.channel, entry.retryDelaySeconds,
        )
        entry.retryDelaySeconds = (entry.retryDelaySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
    }

    private fun youTubeStats(channel: String, entry: Entry): Stats? {
        val id = entry.channelId ?: resolveChannelId(channel)?.also { entry.channelId = it }
        if (id == null) {
            entry.unknownChannel = true
            return null
        }

        val json = try {
            // TODO: Look into different APIs? Nothing wrong with this one per se but I didn't proper consider other options.
            JsonUtils.parseFromUrl("https://mixerno.space/api/youtube-channel-counter/user/$id") as? JsonObject
        } catch (e: Exception) {
            LOGGER.error("Failed to fetch the statistics for the channel {}", id, e)
            null
        }

        if (json == null) {
            LOGGER.error("Failed to obtain JSON from mixerno.space")
            return null
        }

        val counts = json.getAsJsonArray("counts")
        if (counts == null) {
            LOGGER.error("mixerno.space did not return any counts for the channel {}", id)
            return null
        }

        return try {

            val subscribers = count(counts, "subscribers") ?: count(counts, "apisubscribers") ?: return null
            Stats(
                count = subscribers,
                goal = count(counts, "goal") ?: (nextMilestone(subscribers) - subscribers),
                views = count(counts, "views") ?: count(counts, "apiviews") ?: 0L,
                videos = count(counts, "videos") ?: count(counts, "apivideos") ?: 0L,
                viewers = 0L,
            )
        } catch (e: Exception) {
            LOGGER.error("Failed to parse the statistics from mixerno.space", e)
            null
        }
    }

    private fun resolveChannelId(input: String): String? {
        val cleaned = input.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .removePrefix("youtube.com/").removePrefix("m.youtube.com/").trimEnd('/')

        val path = when {
            cleaned.startsWith("channel/") -> cleaned.removePrefix("channel/")
            cleaned.startsWith("c/") -> cleaned.removePrefix("c/")
            else -> cleaned
        }

        if (CHANNEL_ID.matches(path)) return path

        val url = when {
            path.startsWith("user/") -> "https://www.youtube.com/$path"
            path.startsWith("@") -> "https://www.youtube.com/$path"
            else -> "https://www.youtube.com/@$path"
        }

        val page = try {
            NetworkUtils.getString(url)
        } catch (e: Exception) {
            LOGGER.error("Failed to open the YouTube channel page {}", url, e)
            null
        }

        if (page.isNullOrEmpty()) {
            LOGGER.error("Failed to read the YouTube channel page {}", url)
            return null
        }

        val id = CANONICAL_IN_PAGE.find(page)?.groupValues?.get(1)
            ?: CHANNEL_ID_IN_PAGE.find(page)?.groupValues?.get(1)

        if (id == null) LOGGER.error("Could not find a channel id on the YouTube channel page {}", url)
        return id
    }

    private fun twitchStats(channel: String, entry: Entry): Stats? {
        val name = channel.trim('@', '/').substringAfterLast('/').lowercase()
        if (name.isEmpty()) {
            entry.unknownChannel = true
            return null
        }

        // TODO: Look into different APIs? Nothing wrong with this one per se but I didn't proper consider other options.
        val followers = twitchNumber("https://decapi.me/twitch/followcount/$name", entry) ?: return null
        val viewers = if (entry.wantsViewers) {

            // TODO: Look into different APIs? Nothing wrong with this one per se but I didn't proper consider other options.
            twitchNumber("https://decapi.me/twitch/viewercount/$name", entry, offlineIsZero = true) ?: 0L
        } else {
            0L
        }

        return Stats(
            count = followers,
            goal = nextMilestone(followers) - followers,
            views = 0L,
            videos = 0L,
            viewers = viewers,
        )
    }

    private fun twitchNumber(url: String, entry: Entry, offlineIsZero: Boolean = false): Long? {
        val body = try {
            NetworkUtils.getString(url)?.trim()
        } catch (e: Exception) {
            LOGGER.error("Failed to fetch {}", url, e)
            null
        }

        if (body.isNullOrEmpty()) return null

        body.toLongOrNull()?.let { return it }

        if (offlineIsZero && body.contains("offline", ignoreCase = true)) return 0L

        if (body.contains("429") || body.contains("Too Many Requests", ignoreCase = true) ||
            body.contains("500") || body.contains("503")
        ) {
            LOGGER.warn("decapi.me refused the request: {}", body)
            return null
        }

        entry.unknownChannel = true
        return null
    }

    private fun nextMilestone(count: Long): Long {
        var step = 1L
        while (step * 10L <= count && step < 1_000_000L) step *= 10L
        return (count / step + 1L) * step
    }

    private fun count(counts: JsonArray, name: String): Long? {
        for (element in counts) {
            val counter = element as? JsonObject ?: continue
            if (counter.get("value")?.asString != name) continue
            val value = counter.get("count") ?: continue
            if (value.isJsonNull) continue
            return value.asLong
        }
        return null
    }
}
