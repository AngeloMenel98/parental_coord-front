package com.parental.shared.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val TAG = "FilePicker"

/** Los tres tipos que acepta el backend (`ALLOWED_EXT` en `activity-attachments.service.ts`). */
internal val SUPPORTED_MIME_TYPES = arrayOf("application/pdf", "image/jpeg", "image/png")

/**
 * Guarda el callback de la selección en curso fuera del estado de composición.
 *
 * `rememberLauncherForActivityResult` lo entrega en un callback que corre
 * después de varias recomposiciones; un `mutableStateOf` ahí sólo agregaría
 * invalidaciones sin aportar nada.
 */
private class PendingPick {
    var callback: ((FileData?) -> Unit)? = null
}

actual class FilePicker {
    internal var launchPicker: ((Array<String>, (FileData?) -> Unit) -> Unit)? = null

    actual fun launch(mimeTypes: Array<String>, onResult: (FileData?) -> Unit) {
        val start = launchPicker
        if (start == null) {
            Log.w(TAG, "FilePicker usado fuera de rememberFilePicker(): no hay launcher")
            onResult(null)
            return
        }
        start(mimeTypes, onResult)
    }
}

@Composable
actual fun rememberFilePicker(): FilePicker {
    val context = LocalContext.current
    val picker = remember { FilePicker() }
    val pending = remember { PendingPick() }

    // `OpenDocument` y no `GetContent`: acepta una LISTA de mime types, que es
    // exactamente la firma de `FilePicker.launch`. `GetContent` sólo admite uno.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pending.callback
        pending.callback = null
        callback?.invoke(uri?.let { readPickedFile(context, it) })
    }

    picker.launchPicker = { mimeTypes, onResult ->
        pending.callback = onResult
        launcher.launch(mimeTypes.ifEmpty { SUPPORTED_MIME_TYPES })
    }

    return picker
}

/**
 * Convierte el `content://` elegido en bytes.
 *
 * Devuelve `null` —y lo deja en logcat— cuando el proveedor no se puede leer,
 * en vez de fallar en silencio como hacía el stub anterior.
 */
private fun readPickedFile(context: Context, uri: Uri): FileData? {
    return try {
        val resolver = context.contentResolver
        val displayName = resolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
            ?: uri.lastPathSegment
            ?: "archivo"

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            Log.w(TAG, "openInputStream devolvió null para $uri")
            return null
        }

        FileData(
            name = displayName,
            // Algunos proveedores (Descargas, Drive) no declaran el tipo: sin
            // este fallback el backend responde `400 Invalid mimeType`.
            mimeType = resolver.getType(uri) ?: mimeTypeFromName(displayName),
            bytes = bytes,
        )
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer el archivo elegido: $uri", e)
        null
    }
}

/** Fallback por extensión cuando el proveedor no informa el `mimeType`. */
internal fun mimeTypeFromName(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }
