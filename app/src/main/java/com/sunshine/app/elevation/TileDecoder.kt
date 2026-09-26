package com.sunshine.app.elevation

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * ARGB pixels (row-major) of an encoded tile image, or `null` if it cannot be decoded. Mapterhorn
 * tiles are lossless WebP without alpha or colour profile, so the pixels are the encoded values
 * (design D5; checked on a device).
 */
fun decodeArgb(bytes: ByteArray): IntArray? {
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    bitmap.recycle()
    return pixels
}
