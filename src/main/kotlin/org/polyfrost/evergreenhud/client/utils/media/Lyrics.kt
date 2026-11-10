package org.polyfrost.evergreenhud.client.utils.media

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.polyfrost.oneconfig.utils.v1.dsl.runAsync
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.InflaterInputStream
import kotlin.math.abs

data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

data class LyricLine(val startMs: Long, val endMs: Long, val words: List<LyricWord>) {
    val text: String get() = words.joinToString("") { it.text }
}

class Lyrics(val lines: List<LyricLine>, val wordSynced: Boolean, val source: String = "") {
    fun from(source: String): Lyrics = Lyrics(lines, wordSynced, source)

    fun lineAt(positionMs: Long): Int = lines.indexOfLast { it.startMs <= positionMs }

    val explicit: Boolean by lazy { lines.any { PROFANITY.containsMatchIn(it.text) } }
}

private val PROFANITY = Regex("""(?i)fuck|shit|bitch|nigg|pussy|cunt|whore""")
private val CLEAN_TITLE = Regex("""(?i)\b(?:clean|edited|radio edit)\b""")
private const val NETWORK_PICKS = 3

fun wantsClean(title: String): Boolean = CLEAN_TITLE.containsMatchIn(title)

fun Sequence<Lyrics?>.pick(prefer: (Lyrics) -> Boolean, limit: Int = Int.MAX_VALUE): Lyrics? {
    var first: Lyrics? = null
    for (lyrics in take(limit).filterNotNull()) {
        if (prefer(lyrics)) return lyrics
        if (first == null) first = lyrics
    }
    return first
}

const val LRC_RED = "lrc.red"
const val LRCLIB = "lrclib.net"
const val KUGOU = "KuGou"
val PROVIDERS = arrayOf(LRC_RED, LRCLIB, KUGOU)

object LyricsService {
    private const val API = "https://lrclib.net/api/search"
    private const val MAX_DURATION_DIFF_S = 8.0
    private const val NO_MATCH = Int.MIN_VALUE
    private const val SYNCED = 1000
    private const val WORD_SYNCED = 2000
    private const val ARTIST_MATCH = 300
    private const val EXACT_TITLE = 100
    private const val DURATION_PENALTY = 20
    private const val UNKNOWN_DURATION_PENALTY_S = 4.0
    private val TOPIC_SUFFIX = Regex("""(?i)\s*(?:-\s*topic|vevo)$""")
    private val DASHED_TITLE = Regex("""(.+?)\s+[-–—]\s+(.+)""")
    private const val RETRY_MS = 30_000L

    @Volatile
    private var key: String? = null

    @Volatile
    private var retryAt = Long.MAX_VALUE

    @Volatile
    var current: Lyrics? = null
        private set

    fun lyricsFor(info: MediaInfo?): Lyrics? {
        val next = info?.let { "${it.title}\u0000${it.artist}" }
        synchronized(LyricsService) {
            if (next == key && (current != null || System.currentTimeMillis() < retryAt)) return current
            key = next
            current = null
            retryAt = Long.MAX_VALUE
        }
        if (info != null) runAsync {
            val (found, failed) = runCatching { search(info) { key == next } }.getOrDefault(null to true)
            synchronized(LyricsService) {
                if (key != next) return@runAsync
                current = found
                if (found == null && failed) retryAt = System.currentTimeMillis() + RETRY_MS
            }
        }
        return null
    }

    @Volatile
    var providers: List<String> = PROVIDERS.toList()
        set(value) {
            if (value == field) return
            field = value
            key = null
        }

    private fun search(info: MediaInfo, stillWanted: () -> Boolean): Pair<Lyrics?, Boolean> {
        val wanted = wantedTracks(info)
        if (wanted.isEmpty()) return null to false
        val (title, artist) = wanted.firstOrNull { it.second != wanted.first().second } ?: wanted.first()
        val clean = wantsClean(info.title)
        val prefer = { lyrics: Lyrics -> if (clean) !lyrics.explicit else lyrics.explicit || lyrics.wordSynced }
        fun rank(lyrics: Lyrics) = (if (prefer(lyrics)) 2 else 0) + (if (lyrics.wordSynced) 1 else 0)
        var best: Lyrics? = null
        var failed = false
        for (source in providers) {
            if (!stillWanted()) return null to false
            val found = runCatching {
                when (source) {
                    LRC_RED -> LrcRed.search(info, title, artist, prefer)
                    LRCLIB -> searchLrclib(info, wanted, prefer)
                    KUGOU -> KuGou.search(info, title, artist, prefer)
                    else -> null
                }
            }.onFailure { failed = true }.getOrNull()?.from(source) ?: continue
            if (rank(found) == 3) return found to failed
            if (best == null || rank(found) > rank(best)) best = found
        }
        return best to failed
    }

    private fun searchLrclib(info: MediaInfo, wanted: List<Pair<String, String>>, prefer: (Lyrics) -> Boolean): Lyrics? {
        val found = LinkedHashMap<String, JsonObject>()
        fun add(results: List<JsonObject>) {
            for (result in results) found.putIfAbsent(result.get("id")?.toString() ?: continue, result)
        }
        fun bestScore(): Int = found.values.maxOfOrNull { score(it, wanted, info) } ?: Int.MIN_VALUE
        for ((title, artist) in wanted) add(query("track_name" to title, "artist_name" to artist))
        if (bestScore() < WORD_SYNCED) add(query("q" to wanted.first().let { "${it.second} ${it.first}" }))
        if (bestScore() < SYNCED) add(query("q" to wanted.first().first))
        return found.values
            .map { it to score(it, wanted, info) }
            .filter { it.second > NO_MATCH }
            .sortedByDescending { it.second }
            .asSequence()
            .map { (result, _) ->
                result.string("lyricsfile")?.let(::parseLyricsfile)?.takeIf { it.wordSynced || result.string("syncedLyrics") == null }
                    ?: result.string("syncedLyrics")?.let(::parseLrc)
                    ?: result.string("lyricsfile")?.let(::parseLyricsfile)
            }
            .pick(prefer)
    }

    private fun score(result: JsonObject, wanted: List<Pair<String, String>>, info: MediaInfo): Int {
        val title = normalize(result.string("trackName") ?: return NO_MATCH)
        val artist = normalize(result.string("artistName").orEmpty())
        val match = wanted.firstOrNull { (t, _) -> normalize(t).let { it.isNotEmpty() && (it == title || it in title || title in it) } } ?: return NO_MATCH
        if (result.string("syncedLyrics") == null && result.string("lyricsfile") == null) return NO_MATCH
        val duration = result.double("duration")
        val diff = if (duration != null && info.lengthMs > 0) abs(duration - info.lengthMs / 1000.0) else null
        if (diff != null && diff > MAX_DURATION_DIFF_S) return NO_MATCH
        var score = SYNCED
        if (result.bool("hasWordSync")) score += WORD_SYNCED
        val wantedArtist = normalize(match.second)
        if (wantedArtist.isNotEmpty() && (wantedArtist in artist || artist in wantedArtist) && artist.isNotEmpty()) score += ARTIST_MATCH
        if (normalize(match.first) == title) score += EXACT_TITLE
        score -= ((diff ?: UNKNOWN_DURATION_PENALTY_S) * DURATION_PENALTY).toInt()
        return score
    }

    private fun wantedTracks(info: MediaInfo): List<Pair<String, String>> {
        val artist = info.artist.split(", ", " & ", " x ", " feat. ", " ft. ").first()
            .replace(TOPIC_SUFFIX, "").trim()
        val clean = cleanTitle(info.title)
        val out = linkedSetOf(clean to artist, info.title to artist)
        DASHED_TITLE.matchEntire(clean)?.let { out.add(it.groupValues[2].trim() to it.groupValues[1].trim()) }
        return out.filter { it.first.isNotBlank() }
    }

    private fun query(vararg params: Pair<String, String>): List<JsonObject> {
        if (params.any { it.second.isBlank() }) return emptyList()
        val search = params.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return fetchJson("$API?$search").asJsonArray.map { it.asJsonObject }
    }

    private fun JsonObject.double(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

    private fun JsonObject.bool(name: String): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true

}

private const val TIMEOUT_MS = 5000
private const val USER_AGENT = "EvergreenHUD (https://github.com/Polyfrost/EvergreenHUD)"

private fun open(url: String) = URI(url).toURL().openConnection().apply {
    connectTimeout = TIMEOUT_MS
    readTimeout = TIMEOUT_MS
    setRequestProperty("User-Agent", USER_AGENT)
}.getInputStream()

private fun fetchJson(url: String): JsonElement = open(url).reader().use { JsonParser.parseReader(it) }

private fun fetchText(url: String): String = open(url).use { it.readBytes().toString(Charsets.UTF_8) }

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

private object LrcRed {
    private const val API = "https://lrc.red"
    private const val MAX_DURATION_DIFF_S = 8.0

    fun search(info: MediaInfo, title: String, artist: String, prefer: (Lyrics) -> Boolean): Lyrics? {
        val hits = fetchJson("$API/search.json?q=${encode("$artist $title".trim())}").asJsonObject.getAsJsonArray("hits") ?: return null
        val wanted = normalize(title)
        return hits.map { it.asJsonObject }
            .filter { hit -> normalize(hit.string("title").orEmpty()).let { it.isNotEmpty() && (wanted in it || it in wanted) } }
            .filter { hit ->
                val length = hit.string("duration")?.toDoubleOrNull()
                info.lengthMs <= 0 || length == null || abs(length - info.lengthMs / 1000.0) <= MAX_DURATION_DIFF_S
            }
            .asSequence()
            .map { hit ->
                val isrc = hit.string("isrc")?.takeIf { it.all(Char::isLetterOrDigit) } ?: return@map null
                runCatching { parseLyricsfile(fetchText("$API/s/$isrc.lyricsfile.yaml")) }.getOrNull()
            }
            .pick(prefer, NETWORK_PICKS)
    }
}

private object KuGou {
    private const val SEARCH = "https://lyrics.kugou.com/search"
    private const val DOWNLOAD = "https://lyrics.kugou.com/download"
    private const val MAX_DURATION_DIFF_MS = 8000L

    fun search(info: MediaInfo, title: String, artist: String, prefer: (Lyrics) -> Boolean): Lyrics? {
        val keyword = if (artist.isBlank()) title else "$artist - $title"
        val duration = if (info.lengthMs > 0) "&duration=${info.lengthMs}" else ""
        val candidates = fetchJson("$SEARCH?ver=1&man=yes&client=pc&keyword=${encode(keyword)}$duration")
            .asJsonObject.getAsJsonArray("candidates") ?: return null
        val wanted = normalize(title)
        return candidates.map { it.asJsonObject }
            .filter { candidate -> normalize(candidate.string("song").orEmpty()).let { it.isNotEmpty() && (wanted in it || it in wanted) } }
            .filter { candidate ->
                val length = candidate.string("duration")?.toLongOrNull()
                info.lengthMs <= 0 || length == null || abs(length - info.lengthMs) <= MAX_DURATION_DIFF_MS
            }
            .asSequence()
            .map { candidate -> runCatching { download(candidate, title, artist) }.getOrNull() }
            .pick(prefer, NETWORK_PICKS)
    }

    private fun download(candidate: JsonObject, title: String, artist: String): Lyrics? {
        val id = candidate.string("id") ?: return null
        val key = candidate.string("accesskey") ?: return null
        val content = fetchJson("$DOWNLOAD?ver=1&client=pc&id=${encode(id)}&accesskey=${encode(key)}&fmt=krc&charset=utf8")
            .asJsonObject.string("content") ?: return null
        return parseKrc(decryptKrc(Base64.getDecoder().decode(content)), title, artist)
    }
}

private val KRC_KEY = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)

fun decryptKrc(raw: ByteArray): String {
    require(raw.size > 4 && String(raw, 0, 4, Charsets.US_ASCII) == "krc1") { "not a KRC file" }
    val data = ByteArray(raw.size - 4) { (raw[it + 4].toInt() xor KRC_KEY[it % KRC_KEY.size]).toByte() }
    return InflaterInputStream(data.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
}

private val KRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
private val KRC_WORD = Regex("""<(\d+),(\d+),\d+>([^<]*)""")
private val CREDIT = Regex(
    """^\s*(?:作?词|作?曲|编曲|制作人?|混音|母带|和声|录音|监制|出品|发行|lyrics|lyricist|composer|composed|written|produced|producer|arranged|arranger|mixed|mastered)(?:\s*by)?\s*[:：]""",
    RegexOption.IGNORE_CASE,
)
private const val KRC_HEADER_LINES = 2

fun parseKrc(krc: String, title: String = "", artist: String = ""): Lyrics? {
    val wantedTitle = normalize(cleanTitle(title))
    val wantedArtist = normalize(artist)
    var index = 0
    val lines = krc.lineSequence().mapNotNull { line ->
        val match = KRC_LINE.matchEntire(line.trim()) ?: return@mapNotNull null
        val start = match.groupValues[1].toLong()
        val words = KRC_WORD.findAll(match.groupValues[3]).map {
            val from = start + it.groupValues[1].toLong()
            RawWord(it.groupValues[3], from, from + it.groupValues[2].toLong())
        }.filter { it.text.isNotEmpty() }.toList()
        val text = words.joinToString("") { it.text }.trim()
        val flat = normalize(text)
        val header = index++ < KRC_HEADER_LINES && wantedTitle.isNotEmpty() && wantedTitle in flat && (wantedArtist.isEmpty() || wantedArtist in flat || " - " in text)
        if (text.isEmpty() || header || CREDIT.containsMatchIn(text)) null
        else RawLine(start, start + match.groupValues[2].toLong(), text, words)
    }.toList()
    return build(lines)
}

private const val TITLE_NOISE =
    """remaster(?:ed)?|official|music|video|audio|lyrics?|visuali[sz]er|live|mono|stereo|version|radio|edit(?:ed)?|deluxe|bonus|explicit|clean|hd|hq|4k|mv|anniversary|edition|\d{4}"""
private const val ALL_NOISE = """(?:$TITLE_NOISE)(?:[\s,&/]+(?:$TITLE_NOISE))*"""
private const val FEAT = """(?:feat\.?|ft\.?|featuring)\s"""
private val BRACKETED_NOISE = Regex("""\s*[(\[]\s*(?:$ALL_NOISE|$FEAT[^)\]]*)\s*[)\]]""", RegexOption.IGNORE_CASE)
private val DASHED_NOISE = Regex("""\s+[-–—]\s+(?:$ALL_NOISE|$FEAT[^-–—]*)\s*$""", RegexOption.IGNORE_CASE)

fun cleanTitle(title: String): String =
    title.replace(BRACKETED_NOISE, "").replace(DASHED_NOISE, "").trim().ifEmpty { title }

private const val LAST_LINE_MS = 5000L
private const val DEFAULT_BEAT_MS = 260.0
private const val MIN_BEAT_MS = 110.0
private const val MAX_BEAT_MS = 650.0
private const val PACE_PERCENTILE = 0.3
private const val SLACK_SUNG = 0.35
private const val MAX_SUNG_FRACTION = 0.92
private const val COMMA_PAUSE = 0.8
private const val STOP_PAUSE = 1.3
private const val FINAL_HOLD = 1.0

private class RawWord(val text: String, val startMs: Long, val endMs: Long? = null)

private class RawLine(val startMs: Long, val endMs: Long?, val text: String, val words: List<RawWord>)

private val WORD_SPLIT = Regex("""\S+\s*""")

private fun build(raw: List<RawLine>): Lyrics? {
    val sorted = raw.sortedBy { it.startMs }
    val pace = songPace(sorted)
    val out = ArrayList<LyricLine>()
    for ((i, line) in sorted.withIndex()) {
        if (line.text.isBlank()) continue
        val next = sorted.getOrNull(i + 1)?.startMs ?: (line.startMs + LAST_LINE_MS)
        val end = line.endMs?.takeIf { it > line.startMs }?.coerceAtMost(next) ?: next
        val words = if (line.words.isNotEmpty()) timedWords(line.words, end) else estimatedWords(line.text, line.startMs, end, pace)
        out.add(LyricLine(line.startMs, end, words))
    }
    if (out.isEmpty()) return null
    return Lyrics(out, wordSynced = raw.any { it.words.isNotEmpty() })
}

private fun timedWords(words: List<RawWord>, lineEnd: Long): List<LyricWord> =
    words.mapIndexed { i, word ->
        val next = words.getOrNull(i + 1)?.startMs ?: lineEnd
        val end = word.endMs?.coerceAtMost(next) ?: next
        LyricWord(word.text, word.startMs, end.coerceAtLeast(word.startMs + 1))
    }

private class Beat(val text: String, val length: Double, val pauseAfter: Double)

private val VOWEL_GROUP = Regex("""[aeiouyàáâãäåæèéêëìíîïòóôõöøœùúûüý]+""")

fun syllables(word: String): Int {
    val letters = word.filter { it.isLetterOrDigit() }.lowercase()
    if (letters.isEmpty()) return 1
    val latin = letters.filter { it.isLetter() && it.code < 0x250 }
    if (latin.isEmpty()) return letters.length
    var count = VOWEL_GROUP.findAll(latin).count()
    if (latin.length > 2 && latin.endsWith('e') && !latin.endsWith("le") && count > 1) count--
    return count.coerceAtLeast(1) + letters.count { it.isDigit() }
}

private fun beats(text: String): List<Beat> {
    val pieces = WORD_SPLIT.findAll(text).map { it.value }.toList()
    return pieces.mapIndexed { i, piece ->
        val bare = piece.trimEnd()
        val last = i == pieces.lastIndex
        val pause = when {
            last -> 0.0
            bare.last() in ",;:-–—" -> COMMA_PAUSE
            bare.last() in ".!?…" -> STOP_PAUSE
            else -> 0.0
        }
        Beat(piece, syllables(bare) + if (last) FINAL_HOLD else 0.0, pause)
    }
}

private fun List<Beat>.units(): Double = sumOf { it.length + it.pauseAfter }.coerceAtLeast(1.0)

private fun songPace(lines: List<RawLine>): Double {
    val rates = lines.zipWithNext { line, next ->
        if (line.words.isNotEmpty() || line.text.isBlank()) null else (next.startMs - line.startMs) / beats(line.text).units()
    }.filterNotNull().filter { it > 0.0 }.sorted()
    if (rates.isEmpty()) return DEFAULT_BEAT_MS
    return rates[(rates.size * PACE_PERCENTILE).toInt().coerceAtMost(rates.lastIndex)].coerceIn(MIN_BEAT_MS, MAX_BEAT_MS)
}

private fun estimatedWords(text: String, start: Long, end: Long, pace: Double): List<LyricWord> {
    val beats = beats(text)
    val units = beats.units()
    val span = (end - start).toDouble().coerceAtLeast(1.0)
    val natural = units * pace
    val sung = minOf(span * MAX_SUNG_FRACTION, natural + (span - natural).coerceAtLeast(0.0) * SLACK_SUNG).coerceAtLeast(1.0)
    val unit = sung / units
    var at = start.toDouble()
    return beats.map { beat ->
        val from = at.toLong()
        at += beat.length * unit
        val word = LyricWord(beat.text, from, at.toLong().coerceAtLeast(from + 1))
        at += beat.pauseAfter * unit
        word
    }
}

private val LRC_TIME = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
private val LRC_WORD_TIME = Regex("""<(\d+):(\d+(?:\.\d+)?)>""")
private val YAML_ENTRY = Regex("""^( *)(- )?([a-z_]+):(?: (.*))?$""")

private fun MatchResult.ms(): Long = groupValues[1].toLong() * 60_000L + (groupValues[2].toDouble() * 1000).toLong()

fun parseLrc(lrc: String): Lyrics? = build(lrc.lineSequence().flatMap { line ->
    val times = LRC_TIME.findAll(line).toList()
    val body = line.substring(times.lastOrNull()?.range?.last?.plus(1) ?: return@flatMap emptySequence())
    val tags = LRC_WORD_TIME.findAll(body).toList()
    val words = tags.mapIndexedNotNull { i, tag ->
        val text = body.substring(tag.range.last + 1, tags.getOrNull(i + 1)?.range?.first ?: body.length)
        if (text.isBlank()) null else RawWord(text.trimStart(), tag.ms())
    }
    val text = if (tags.isEmpty()) body.trim() else words.joinToString("") { it.text }.trim()
    val end = tags.lastOrNull()?.takeIf { body.substring(it.range.last + 1).isBlank() }?.ms()
    times.asSequence().map { RawLine(it.ms(), end, text, words) }
}.toList())

fun parseLyricsfile(yaml: String): Lyrics? {
    val lines = ArrayList<RawLine>()
    var inLines = false
    var lineText = ""
    var lineStart = -1L
    var lineEnd: Long? = null
    var words = ArrayList<RawWord>()
    var wordText: String? = null

    fun flushLine() {
        if (lineStart >= 0) lines.add(RawLine(lineStart, lineEnd, lineText, words))
        lineText = ""
        lineStart = -1L
        lineEnd = null
        words = ArrayList()
    }

    var lineDepth = -1
    for (raw in yaml.lineSequence()) {
        val match = YAML_ENTRY.matchEntire(raw) ?: continue
        val (indent, dash, key, value) = match.destructured
        val depth = indent.length + dash.length
        if (depth == 0) {
            if (inLines) break
            inLines = key == "lines"
            continue
        }
        if (!inLines) continue
        if (lineDepth < 0 && key == "text") lineDepth = depth
        val word = depth > lineDepth
        when {
            key == "text" && !word -> {
                flushLine()
                lineText = unquote(value)
            }
            key == "start_ms" && !word -> lineStart = value.toLongOrNull() ?: continue
            key == "end_ms" && !word -> lineEnd = value.toLongOrNull()
            key == "text" -> wordText = unquote(value)
            key == "start_ms" -> {
                val start = value.toLongOrNull() ?: continue
                words.add(RawWord(wordText ?: continue, start))
                wordText = null
            }
            key == "end_ms" -> {
                val last = words.lastOrNull() ?: continue
                words[words.lastIndex] = RawWord(last.text, last.startMs, value.toLongOrNull())
            }
        }
    }
    flushLine()
    return build(lines)
}

private fun unquote(value: String): String = when {
    value.length >= 2 && value.startsWith('\'') && value.endsWith('\'') -> value.substring(1, value.length - 1).replace("''", "'")
    value.length >= 2 && value.startsWith('"') && value.endsWith('"') -> runCatching { JsonParser.parseString(value).asString }.getOrDefault(value)
    else -> value
}
