package com.parental.shared.core.files

actual class ImageCompressor actual constructor() {
    actual fun compress(bytes: ByteArray, mimeType: String): CompressedImage {
        return CompressedImage(bytes, mimeType)
    }
}
