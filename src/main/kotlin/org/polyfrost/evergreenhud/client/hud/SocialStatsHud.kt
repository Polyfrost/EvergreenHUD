package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.AutoHideTextHud
import org.polyfrost.evergreenhud.client.utils.social.SocialStats
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

private const val PLATFORM_YOUTUBE = 0

private const val NO_CHANNEL_TEXT = "No Channel"

private const val UNAVAILABLE_TEXT = "Unavailable"

private const val UNKNOWN_CHANNEL_TEXT = "Unknown Channel"

private val PREVIEW = SocialStats.Stats(
    count = 2670869,
    goal = 329131,
    views = 357228439,
    videos = 6086,
    viewers = 1204,
)

class SocialStatsHud : AutoHideTextHud(
    id = "social_stats.json",
    title = "Social Media Stats",
    category = Category.INFO,
    prefix = "",
) {
    @Dropdown(title = "Platform", options = ["YouTube", "Twitch"])
    var platform = PLATFORM_YOUTUBE

    @Text(title = "Channel", description = "@MrBeast on YouTube, ninja on Twitch. Ids and links work too.")
    var channel = ""

    @Text(
        title = "Format String",
        description = "#count for subscribers or followers, #goal until the next milestone, " +
            "#views and #videos on YouTube, #viewers on Twitch.",
    )
    var formatString = "Subscribers: #count"

    @Switch(title = "Compact Numbers", description = "Shows 2,670,869 as 2.6M.")
    var compact = false

    @Switch(title = "Hide When Unavailable", description = "Hides the HUD while the counts cannot be fetched.")
    var hideWhenUnavailable = false

    override fun setup() {
        super.setup()
        if (isReal) {
            updateWhenChanged("platform")
            updateWhenChanged("channel")
            updateWhenChanged("formatString")
            updateWhenChanged("compact")
            updateWhenChanged("hideWhenUnavailable")
        }
    }

    override fun updateFrequency(): Long = 1.seconds.inWholeNanoseconds

    override fun getText(): String {
        if (!isReal) {
            autoHidden = false
            return render(PREVIEW)
        }

        if (channel.isBlank()) {
            autoHidden = hideWhenUnavailable
            return NO_CHANNEL_TEXT
        }

        val site = if (platform == PLATFORM_YOUTUBE) SocialStats.Platform.YOUTUBE else SocialStats.Platform.TWITCH
        val status = SocialStats.get(site, channel, wantsViewers = formatString.contains("#viewers"))
        val stats = status.stats

        autoHidden = hideWhenUnavailable && stats == null
        if (stats == null) return if (status.unknownChannel) UNKNOWN_CHANNEL_TEXT else UNAVAILABLE_TEXT
        return render(stats)
    }

    private fun render(stats: SocialStats.Stats): String = formatString
        .replace("#count", format(stats.count))
        .replace("#goal", format(stats.goal))
        .replace("#views", format(stats.views))
        .replace("#videos", format(stats.videos))
        .replace("#viewers", format(stats.viewers))

    private fun format(count: Long): String {
        if (!compact) return NumberFormat.getIntegerInstance(Locale.US).format(count)

        val magnitude = abs(count)
        val (divisor, unit) = when {
            magnitude >= 1_000_000_000L -> 1_000_000_000.0 to "B"
            magnitude >= 1_000_000L -> 1_000_000.0 to "M"
            magnitude >= 1_000L -> 1_000.0 to "K"
            else -> return count.toString()
        }

        val scaled = count / divisor
        val places = if (abs(scaled) >= 100.0) 0 else 1
        return String.format(Locale.US, "%.${places}f%s", scaled, unit)
    }
}
