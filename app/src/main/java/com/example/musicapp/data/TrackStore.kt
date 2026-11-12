package com.example.musicapp.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * 音乐库仓库：把选中的音频复制到应用私有目录，并把元数据持久化到 SharedPreferences。
 * 对应插件里 IndexedDB 的 tracks object store。
 */
class TrackStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "tracks").apply { mkdirs() }

    fun load(): MutableList<Track> {
        val raw = prefs.getString(KEY_TRACKS, null) ?: return mutableListOf()
        val result = mutableListOf<Track>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                result.add(Track.fromJson(array.getJSONObject(i)))
            }
        }
        return result
    }

    fun save(tracks: List<Track>) {
        val array = JSONArray()
        tracks.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_TRACKS, array.toString()).commit()
    }

    fun fileFor(track: Track): File = File(dir, track.fileName)

    fun delete(track: Track) {
        runCatching { fileFor(track).delete() }
    }

    /** 把选中的音频复制进私有目录，返回成功导入的曲目与被跳过的数量。 */
    fun import(uris: List<Uri>): ImportResult {
        val added = mutableListOf<Track>()
        var skipped = 0
        for (uri in uris) {
            try {
                val meta = queryMeta(uri)
                if (meta == null) {
                    skipped++
                    continue
                }
                val ext = resolveExtension(meta.first, uri)
                if (ext.isBlank() || ext !in SUPPORTED_EXTENSIONS) {
                    skipped++
                    continue
                }
                val id = UUID.randomUUID().toString()
                val fileName = "$id.$ext"
                val target = File(dir, fileName)
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                    true
                } ?: false
                if (!copied) {
                    runCatching { target.delete() }
                    skipped++
                    continue
                }
                val displayName = meta.first.substringBeforeLast('.').ifBlank { meta.first }
                added.add(
                    Track(
                        id = id,
                        name = displayName,
                        ext = ext,
                        size = target.length(),
                        durationMs = readDuration(target),
                        fileName = fileName,
                        addedAt = System.currentTimeMillis()
                    )
                )
            } catch (_: Exception) {
                skipped++
            }
        }
        return ImportResult(added, skipped)
    }

    /**
     * 优先用文件名后缀；有些文件选择器返回的名字没有后缀，
     * 这时退回用 MIME 类型推断，否则明明是可播放音频也会被当成不支持。
     */
    private fun resolveExtension(displayName: String, uri: Uri): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName.isNotBlank() && fromName in SUPPORTED_EXTENSIONS) return fromName
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        return when (mime.lowercase()) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/aac", "audio/x-aac" -> "aac"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/webm" -> "webm"
            "audio/aiff", "audio/x-aiff" -> "aiff"
            else -> fromName
        }
    }

    private fun queryMeta(uri: Uri): Pair<String, Long>? {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                if (!name.isNullOrBlank()) return name to size
            }
        }
        return null
    }

    private fun readDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    data class ImportResult(val tracks: MutableList<Track>, val skipped: Int)

    private companion object {
        const val PREFS = "music_library"
        const val KEY_TRACKS = "tracks"
        val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "oga",
            "opus", "weba", "webm", "aif", "aiff", "alac", "wma"
        )
    }
}
