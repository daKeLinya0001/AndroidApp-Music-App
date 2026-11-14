package app.endlessmusic.player.play

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** 一行同步歌词；[timeMs] 为该行开始时间 */
data class LyricLine(val timeMs: Long, val text: String)

/** LRC 文本解析：兼容 [mm:ss]、[mm:ss.SSS]、[mm:ss:SS] 以及一行多个时间标签 */
object LrcParser {

    private val tagRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = ArrayList<LyricLine>()
        for (line in raw.lineSequence()) {
            val matches = tagRegex.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (match in matches) {
                val minute = match.groupValues[1].toLongOrNull() ?: continue
                val second = match.groupValues[2].toLongOrNull() ?: continue
                val fraction = match.groupValues[3]
                val millis = if (fraction.isEmpty()) {
                    0L
                } else {
                    fraction.padEnd(3, '0').substring(0, 3).toLong()
                }
                lines.add(LyricLine(minute * 60_000 + second * 1000 + millis, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}

/**
 * 歌词抓取：优先读本地缓存，其次依次尝试网易云、QQ 音乐、酷狗的公开接口。
 *
 * 同一首歌往往有多个版本（原版 / 翻唱 / remix），它们的歌词时间轴并不一样，
 * 所以每家都要在搜索结果里按「歌名匹配度 + 时长接近度」打分，挑最像的那一条，
 * 而不是无脑取第一条，否则就会出现「歌词对不上、越放越偏」。
 */
class LyricsRepository(context: Context) {

    private val appContext = context.applicationContext

    /** 阻塞方法，必须在子线程调用 */
    fun load(name: String, durationMs: Long): List<LyricLine>? {
        val keyword = normalize(name)
        if (keyword.isBlank()) return null

        val cache = cacheFile(name, durationMs)
        if (cache.exists()) {
            val cached = LrcParser.parse(runCatching { cache.readText() }.getOrNull())
            if (cached.isNotEmpty()) return cached
            runCatching { cache.delete() }
        }

        val raw = fromNetease(keyword, durationMs)
            ?: fromQQMusic(keyword, durationMs)
            ?: fromKugou(keyword, durationMs)
            ?: return null

        val parsed = LrcParser.parse(raw)
        if (parsed.isEmpty()) return null
        runCatching { cache.writeText(raw) }
        return parsed
    }

    /**
     * 候选打分：歌名越接近、时长越接近，得分越高。
     * 时长完全对不上（超过 20 秒）基本可以判定不是同一个版本。
     */
    private fun score(title: String, candidateMs: Long, keyword: String, targetMs: Long): Float {
        val candidate = normalize(title).lowercase()
        val query = keyword.lowercase()
        val nameScore = when {
            candidate == query -> 1f
            candidate.contains(query) || query.contains(candidate) -> 0.85f
            else -> 0.25f
        }
        val durationScore = if (candidateMs > 0 && targetMs > 0) {
            (1f - abs(candidateMs - targetMs) / 20_000f).coerceIn(0f, 1f)
        } else {
            0.4f
        }
        return nameScore * 0.55f + durationScore * 0.45f
    }

    // ------------------------------------------------------------ 缓存

    private fun cacheFile(name: String, durationMs: Long): File {
        val dir = File(appContext.filesDir, "lyrics").apply { mkdirs() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
        return File(dir, "${safe}_${durationMs / 1000}.lrc")
    }

    /** 去掉括号里的版本信息与常见后缀，提高搜索命中率 */
    private fun normalize(name: String): String {
        var text = name.replace('_', ' ').trim()
        text = text.replace(Regex("[（(\\[].*?[)）\\]]"), " ")
        text = text.replace(Regex("(?i)\\b(official|lyric|video|audio|mv|hd|hq)\\b.*$"), " ")
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    // ------------------------------------------------------------ 网易云

    private fun fromNetease(keyword: String, targetMs: Long): String? = runCatching {
        val searchUrl =
            "https://music.163.com/api/search/get/web?csrf_token=&s=${encode(keyword)}&type=1&offset=0&total=true&limit=10"
        val search = JSONObject(get(searchUrl, REF_NETEASE))
        val songs = search.optJSONObject("result")?.optJSONArray("songs") ?: return null

        var bestId = 0L
        var bestScore = 0f
        for (index in 0 until songs.length()) {
            val song = songs.optJSONObject(index) ?: continue
            val id = song.optLong("id")
            if (id == 0L) continue
            val score = score(song.optString("name"), song.optLong("duration", 0), keyword, targetMs)
            if (score > bestScore) {
                bestScore = score
                bestId = id
            }
        }
        if (bestId == 0L) return null

        val lyricUrl = "https://music.163.com/api/song/lyric?id=$bestId&lv=-1&kv=-1&tv=-1"
        val lyric = JSONObject(get(lyricUrl, REF_NETEASE))
        lyric.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // ------------------------------------------------------------ QQ 音乐

    private fun fromQQMusic(keyword: String, targetMs: Long): String? = runCatching {
        val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
            "?ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song&searchid=0&t=0" +
            "&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0&p=1&n=10&w=${encode(keyword)}&format=json"
        val search = JSONObject(get(searchUrl, REF_QQ))
        val list = search.optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list") ?: return null

        var bestMid = ""
        var bestScore = 0f
        for (index in 0 until list.length()) {
            val song = list.optJSONObject(index) ?: continue
            val mid = song.optString("mid").ifBlank { song.optString("songmid") }
            if (mid.isBlank()) continue
            val title = song.optString("title").ifBlank { song.optString("songname") }
            val durationMs = song.optLong("interval", 0) * 1000
            val score = score(title, durationMs, keyword, targetMs)
            if (score > bestScore) {
                bestScore = score
                bestMid = mid
            }
        }
        if (bestMid.isBlank()) return null

        val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
            "?songmid=$bestMid&format=json&nobase64=1"
        val body = get(lyricUrl, REF_QQ)
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val lyric = JSONObject(body.substring(start, end + 1)).optString("lyric")
        if (lyric.contains('[')) return lyric
        // 部分情况仍返回 base64
        decodeBase64(lyric)?.takeIf { it.contains('[') }
    }.getOrNull()

    // ------------------------------------------------------------ 酷狗

    private fun fromKugou(keyword: String, targetMs: Long): String? = runCatching {
        val searchUrl = "https://mobilecdn.kugou.com/api/v3/search/song" +
            "?format=json&keyword=${encode(keyword)}&page=1&pagesize=10&showtype=1"
        val search = JSONObject(get(searchUrl, REF_KUGOU))
        val info = search.optJSONObject("data")?.optJSONArray("info") ?: return null

        var bestHash = ""
        var bestDuration = 0
        var bestScore = 0f
        for (index in 0 until info.length()) {
            val song = info.optJSONObject(index) ?: continue
            val hash = song.optString("hash")
            if (hash.isBlank()) continue
            val durationSeconds = song.optInt("duration", 0)
            val score = score(
                song.optString("songname"),
                durationSeconds * 1000L,
                keyword,
                targetMs
            )
            if (score > bestScore) {
                bestScore = score
                bestHash = hash
                bestDuration = durationSeconds
            }
        }
        if (bestHash.isBlank()) return null

        val krcUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi" +
            "&keyword=&duration=$bestDuration&hash=$bestHash"
        val candidates = JSONObject(get(krcUrl, REF_KUGOU))
            .optJSONArray("candidates") ?: return null
        val candidate = candidates.optJSONObject(0) ?: return null
        val id = candidate.optString("id")
        val accessKey = candidate.optString("accesskey")
        if (id.isBlank() || accessKey.isBlank()) return null

        val downloadUrl = "https://lyrics.kugou.com/download" +
            "?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
        val body = JSONObject(get(downloadUrl, REF_KUGOU))
        decodeBase64(body.optString("content"))?.takeIf { it.contains('[') }
    }.getOrNull()

    // ------------------------------------------------------------ 工具

    private fun decodeBase64(value: String): String? {
        if (value.isBlank()) return null
        return runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun get(url: String, referer: String?): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            setRequestProperty("Accept", "*/*")
            if (referer != null) setRequestProperty("Referer", referer)
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private companion object {
        const val REF_NETEASE = "https://music.163.com/"
        const val REF_QQ = "https://y.qq.com/"
        const val REF_KUGOU = "https://www.kugou.com/"
    }
}
