package com.example.musicapp.play

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
 * 三个接口都是各家网页端在用的搜索 + 歌词下载接口，不保证长期可用；
 * 任何一步失败都会继续尝试下一家，全部失败则返回 null（界面显示「暂无歌词」）。
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

        val raw = fromNetease(keyword)
            ?: fromQQMusic(keyword)
            ?: fromKugou(keyword)
            ?: return null

        val parsed = LrcParser.parse(raw)
        if (parsed.isEmpty()) return null
        runCatching { cache.writeText(raw) }
        return parsed
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

    private fun fromNetease(keyword: String): String? = runCatching {
        val searchUrl =
            "https://music.163.com/api/search/get/web?csrf_token=&s=${encode(keyword)}&type=1&offset=0&total=true&limit=5"
        val search = JSONObject(get(searchUrl, "https://music.163.com/"))
        val songs = search.optJSONObject("result")?.optJSONArray("songs") ?: return null
        if (songs.length() == 0) return null
        val id = songs.optJSONObject(0)?.optLong("id") ?: return null

        val lyricUrl = "https://music.163.com/api/song/lyric?id=$id&lv=-1&kv=-1&tv=-1"
        val lyric = JSONObject(get(lyricUrl, "https://music.163.com/"))
        lyric.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // ------------------------------------------------------------ QQ 音乐

    private fun fromQQMusic(keyword: String): String? = runCatching {
        val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
            "?ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song&searchid=0&t=0" +
            "&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0&p=1&n=5&w=${encode(keyword)}&format=json"
        val search = JSONObject(get(searchUrl, "https://y.qq.com/"))
        val list = search.optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list") ?: return null
        val song = list.optJSONObject(0) ?: return null
        val mid = song.optString("mid").ifBlank { song.optString("songmid") }
        if (mid.isBlank()) return null

        val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
            "?songmid=$mid&format=json&nobase64=1"
        val body = get(lyricUrl, "https://y.qq.com/")
        val json = JSONObject(body.substring(body.indexOf('{'), body.lastIndexOf('}') + 1))
        val lyric = json.optString("lyric")
        if (lyric.isNotBlank() && lyric.contains('[')) return lyric
        // 部分情况仍返回 base64
        decodeBase64(lyric)?.takeIf { it.contains('[') }
    }.getOrNull()

    // ------------------------------------------------------------ 酷狗

    private fun fromKugou(keyword: String): String? = runCatching {
        val searchUrl = "https://mobilecdn.kugou.com/api/v3/search/song" +
            "?format=json&keyword=${encode(keyword)}&page=1&pagesize=5&showtype=1"
        val search = JSONObject(get(searchUrl, "https://www.kugou.com/"))
        val info = search.optJSONObject("data")?.optJSONArray("info") ?: return null
        val song = info.optJSONObject(0) ?: return null
        val hash = song.optString("hash")
        val duration = song.optInt("duration", 0)
        if (hash.isBlank()) return null

        val krcUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi" +
            "&keyword=&duration=$duration&hash=$hash"
        val candidates = JSONObject(get(krcUrl, "https://www.kugou.com/"))
            .optJSONArray("candidates") ?: return null
        val candidate = candidates.optJSONObject(0) ?: return null
        val id = candidate.optString("id")
        val accessKey = candidate.optString("accesskey")
        if (id.isBlank() || accessKey.isBlank()) return null

        val downloadUrl = "https://lyrics.kugou.com/download" +
            "?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
        val body = JSONObject(get(downloadUrl, "https://www.kugou.com/"))
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
}
