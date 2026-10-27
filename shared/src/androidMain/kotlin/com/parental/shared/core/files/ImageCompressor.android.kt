package com.parental.shared.core.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

actual class ImageCompressor actual constructor() {
    actual fun compress(bytes: ByteArray, mimeType: String): CompressedImage {
        if (mimeType == "application/pdf") {
            return CompressedImage(bytes, mimeType)
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        var sample = 1
        val max = 2048
        while (options.outWidth / sample > max || options.outHeight / sample > max) {
            sample *= 2
        }
        val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts2) ?: return CompressedImage(bytes, mimeType)
        val out = ByteArrayOutputStream()
        if (mimeType == "image/png") {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            return CompressedImage(out.toByteArray(), "image/png", bmp.width, bmp.height)
        }
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return CompressedImage(out.toByteArray(), "image/jpeg", bmp.width, bmp.height)
    }
}
