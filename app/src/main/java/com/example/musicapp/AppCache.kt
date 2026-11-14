package com.example.musicapp

import android.content.Context
import java.io.File

/**
 * 应用缓存清理。
 *
 * 只清理「可以再生成」的数据：
 * - 内部/外部 cacheDir（裁剪临时图、系统缓存等）
 * - filesDir/lyrics（歌词缓存，重新播放会重新下载）
 *
 * 不动用户数据：音乐库（filesDir/tracks）、壁纸（filesDir/wallpapers）、
 * 唱片内圈图片与全部设置。
 */
object AppCache {

    fun clear(context: Context): Boolean {
        var ok = true
        val dirs = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            File(context.filesDir, "lyrics")
        )
        for (dir in dirs) {
            dir.listFiles()?.forEach { child ->
                if (!child.deleteRecursively()) ok = false
            }
        }
        // 清理后重建歌词目录，避免后续写入时还要判空
        runCatching { File(context.filesDir, "lyrics").mkdirs() }
        return ok
    }
}
