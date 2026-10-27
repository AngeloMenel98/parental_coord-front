package com.parental.shared.core.files

import androidx.compose.runtime.Composable

actual class FilePicker {
    actual fun launch(mimeTypes: Array<String>, onResult: (FileData?) -> Unit) {
        onResult(null)
    }
}

@Composable
actual fun rememberFilePicker(): FilePicker {
    return FilePicker()
}
