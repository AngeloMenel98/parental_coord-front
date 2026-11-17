package com.parental.shared.feature.admin.ui

import com.parental.shared.feature.admin.domain.model.AdminUser

/**
 * F6 · búsqueda de personas en el panel — una sola regla para la lista de usuarios y
 * para el combobox del wizard.
 *
 * Se compara contra nombre, apellido y email, sin distinguir mayúsculas ni acentos:
 * quien busca "maria" tiene que encontrar a "María", y quien tipea el apellido no
 * debería tener que acordarse del orden.
 */
internal fun AdminUser.matchesUserQuery(query: String): Boolean {
    val needle = query.foldAccents().trim()
    if (needle.isEmpty()) return true
    val haystack = "${firstName.orEmpty()} ${lastName.orEmpty()} $email".foldAccents()
    return haystack.contains(needle)
}

internal fun String.foldAccents(): String =
    lowercase()
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")
        .replace("ñ", "n")
