package com.yuki.yingdao.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

fun interface CapturedMediaSource {
    fun readPhoto(localPath: String): CapturedMediaRequest?
}

class CapturedMediaReader(
    private val contentResolver: ContentResolver,
) : CapturedMediaSource {
    override fun readPhoto(localPath: String): CapturedMediaRequest? {
        val uri = Uri.parse(localPath)
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null

        return bitmap.useAndEncode()
    }

    private fun Bitmap.useAndEncode(): CapturedMediaRequest {
        try {
            val output = ByteArrayOutputStream()
            compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            val dataBase64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            return CapturedMediaRequest(
                mimeType = "image/jpeg",
                dataBase64 = dataBase64,
            )
        } finally {
            recycle()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var scaledWidth = width
        var scaledHeight = height
        while (scaledWidth > MAX_IMAGE_DIMENSION_PX || scaledHeight > MAX_IMAGE_DIMENSION_PX) {
            sampleSize *= 2
            scaledWidth /= 2
            scaledHeight /= 2
        }
        return sampleSize
    }

    private companion object {
        const val MAX_IMAGE_DIMENSION_PX = 768
        const val JPEG_QUALITY = 70
    }
}
