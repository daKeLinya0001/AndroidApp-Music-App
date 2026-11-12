package com.example.musicapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.musicapp.ui.CropView

/**
 * 壁纸取景：选好图片/视频后，让用户拖动、缩放决定背景显示哪一块。
 */
class CropActivity : AppCompatActivity() {

    private lateinit var cropView: CropView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_crop)

        cropView = findViewById(R.id.crop_view)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.crop_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        findViewById<TextView>(R.id.btn_crop_cancel).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        findViewById<TextView>(R.id.btn_crop_done).setOnClickListener {
            val rect = cropView.cropRect()
            val data = Intent().apply {
                putExtra(EXTRA_LEFT, rect.left)
                putExtra(EXTRA_TOP, rect.top)
                putExtra(EXTRA_RIGHT, rect.right)
                putExtra(EXTRA_BOTTOM, rect.bottom)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }

        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        loadPreview(path, type)?.let { cropView.setBitmap(it) }
    }

    /** 图片直接解码，视频取第一帧作为取景预览 */
    private fun loadPreview(path: String, type: String): Bitmap? {
        if (path.isBlank()) return null
        val source = if (type == "video") {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)
                val frame = retriever.getFrameAtTime(0)
                retriever.release()
                frame
            }.getOrNull()
        } else {
            decodeSampled(path)
        } ?: return null

        val longest = maxOf(source.width, source.height)
        val limit = 2048
        if (longest <= limit) return source
        val scale = limit.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled != source) source.recycle()
        return scaled
    }

    /** 先按 2 的幂降采样解码，避免超大图片在取景页直接 OOM */
    private fun decodeSampled(path: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val limit = 2048
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= limit || bounds.outHeight / (sample * 2) >= limit) {
            sample *= 2
        }
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    companion object {
        const val EXTRA_PATH = "path"
        const val EXTRA_TYPE = "type"
        const val EXTRA_LEFT = "cropLeft"
        const val EXTRA_TOP = "cropTop"
        const val EXTRA_RIGHT = "cropRight"
        const val EXTRA_BOTTOM = "cropBottom"
    }
}
