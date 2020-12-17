package com.lxh.rxarcfacelibrary

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * 提取图像中的BGR像素
 * @param image
 * @return
 */
fun getPixelsBGR(image: Bitmap): ByteArray? {
    // calculate how many bytes our image consists of
    val bytes = image.byteCount
    val buffer = ByteBuffer.allocate(bytes) // Create a new buffer
    image.copyPixelsToBuffer(buffer) // Move the byte data to the buffer
    val temp = buffer.array() // Get the underlying array containing the data.
    val pixels = ByteArray(temp.size / 4 * 3) // Allocate for BGR

    // Copy pixels into place
    for (i in 0 until temp.size / 4) {
        pixels[i * 3] = temp[i * 4 + 2] //B
        pixels[i * 3 + 1] = temp[i * 4 + 1] //G
        pixels[i * 3 + 2] = temp[i * 4] //R
    }
    return pixels
}