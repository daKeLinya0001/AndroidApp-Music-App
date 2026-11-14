package app.endlessmusic.player.data

import org.json.JSONObject

/**
 * 一首已导入本地的曲目。
 *
 * @param name       展示名（去掉扩展名）
 * @param ext        小写扩展名，例如 mp3
 * @param size       文件字节数
 * @param durationMs 时长（毫秒），读取失败时为 0
 * @param fileName   复制到应用私有目录后的文件名
 */
data class Track(
    val id: String,
    val name: String,
    val ext: String,
    val size: Long,
    val durationMs: Long,
    val fileName: String,
    val addedAt: Long
) {
    val extLabel: String
        get() = if (ext == "m4a") "M4A" else ext.uppercase()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ext", ext)
        put("size", size)
        put("durationMs", durationMs)
        put("fileName", fileName)
        put("addedAt", addedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Track = Track(
            id = json.optString("id"),
            name = json.optString("name"),
            ext = json.optString("ext"),
            size = json.optLong("size"),
            durationMs = json.optLong("durationMs"),
            fileName = json.optString("fileName"),
            addedAt = json.optLong("addedAt")
        )
    }
}
