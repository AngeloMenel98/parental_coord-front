package com.parental.shared.core.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.parental.shared.platform.Platform

/** Los tres estados del tema: el del sistema o uno elegido a mano. */
enum class ThemeMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromStorage(raw: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == raw } ?: System
    }
}

/**
 * El interruptor sol/luna del Inicio, con memoria.
 *
 * Arranca en [ThemeMode.System] (sigue a `isSystemInDarkTheme()`); el primer toque fija
 * un modo explícito y lo persiste en la plataforma, así que sobrevive al cierre de la
 * app. Un toque desde el modo sistema elige lo CONTRARIO de lo que se está viendo: es lo
 * que el ojo espera del interruptor, no "claro siempre".
 *
 * La resolución vive fuera del controlador ([resolve]) porque el tema del sistema es un
 * valor de composición: el controlador guarda la DECISIÓN, no el resultado.
 */
class ThemeController(
    private val load: (String) -> String? = { key -> Platform.loadString(key) },
    private val save: (String, String) -> Unit = { key, value -> Platform.saveString(key, value) },
) {
    var mode: ThemeMode by mutableStateOf(ThemeMode.fromStorage(load(STORAGE_KEY)))
        private set

    /** `true` si hay que pintar el esquema oscuro con el tema de sistema [systemDark]. */
    fun resolve(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    /** Alterna claro/oscuro tomando como referencia lo que el usuario está viendo. */
    fun toggle(systemDark: Boolean) {
        val next = if (resolve(systemDark)) ThemeMode.Light else ThemeMode.Dark
        mode = next
        save(STORAGE_KEY, next.storageValue)
    }

    companion object {
        internal const val STORAGE_KEY = "ui.theme"
    }
}
