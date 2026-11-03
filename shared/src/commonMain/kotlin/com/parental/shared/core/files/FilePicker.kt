package com.parental.shared.core.files

import androidx.compose.runtime.Composable

expect class FilePicker {
    fun launch(mimeTypes: Array<String> = arrayOf("application/pdf", "image/jpeg", "image/png"), onResult: (FileData?) -> Unit)
}

@Composable
expect fun rememberFilePicker(): FilePicker

data class FileData(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)
