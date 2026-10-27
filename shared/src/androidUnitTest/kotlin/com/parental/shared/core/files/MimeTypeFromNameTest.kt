package com.parental.shared.core.files

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Algunos proveedores de `content://` (Descargas, Drive) devuelven `null` en
 * `ContentResolver.getType()`. Sin fallback, el `mimeType` viajaba como
 * `application/octet-stream` y el backend lo rechazaba con `400 Invalid mimeType`,
 * en silencio.
 */
class MimeTypeFromNameTest {

    @Test
    fun `resuelve los tres tipos que acepta el backend`() {
        assertEquals("application/pdf", mimeTypeFromName("comprobante.pdf"))
        assertEquals("image/png", mimeTypeFromName("foto.png"))
        assertEquals("image/jpeg", mimeTypeFromName("foto.jpg"))
        assertEquals("image/jpeg", mimeTypeFromName("foto.jpeg"))
    }

    @Test
    fun `no distingue mayusculas en la extension`() {
        assertEquals("image/png", mimeTypeFromName("FOTO.PNG"))
        assertEquals("application/pdf", mimeTypeFromName("Comprobante.Pdf"))
    }

    @Test
    fun `un tipo no soportado no se disfraza de valido`() {
        assertEquals("application/octet-stream", mimeTypeFromName("notas.txt"))
        assertEquals("application/octet-stream", mimeTypeFromName("sin-extension"))
    }
}
