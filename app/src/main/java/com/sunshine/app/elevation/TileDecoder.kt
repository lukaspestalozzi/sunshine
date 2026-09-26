package com.sunshine.app.elevation

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * ARGB pixels (row-major) of an encoded [size] × [size] tile image, or `null` if it cannot be
 * decoded or has other dimensions. Mapterhorn tiles are lossless WebP without alpha or colour
 * profile, so the pixels are the encoded values (design D5; bit-exactness checked on a device,
 * task 5.2).
 */
fun decodeArgb(
    bytes: ByteArray,
    size: Int,
): IntArray? {
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val pixels =
        if (bitmap.width == size && bitmap.height == size) {
            IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }
        } else {
            null
        }
    bitmap.recycle()
    return pixels
}
