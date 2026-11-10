package com.parental.shared.core.files

data class CompressedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
)

expect class ImageCompressor() {
    fun compress(bytes: ByteArray, mimeType: String): CompressedImage
}
