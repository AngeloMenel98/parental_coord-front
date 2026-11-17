package com.parental.shared.core.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.parental.shared.core.ui.theme.CategoryTokens
import com.parental.shared.core.ui.theme.StatusTokens
import com.parental.shared.core.ui.theme.TagTriple
import com.parental.shared.core.ui.theme.parseHexColor

/*
 * ÚNICA puerta de resolución de color de etiquetas (categoría/estado) —
 * `calendar-design-redesign`, T4 (paso 4). **La costura de A1.**
 *
 * No introduce mapeos, fusiones ni casos nuevos: reproduce exactamente la
 * precedencia vigente (R7-T4-7b / R7-T4-7c). No declara ningún color — `Color.kt`
 * sigue siendo el único propietario de literales hex y no se modifica (R5-T4-5).
 *
 * Devuelve el `TagTriple` COMPLETO (dot + container + content). El calendario
 * usa `.dot`; quien quiera surface o chip usa `.container` / `.content` sin
 * volver a resolver nada. Antes de este archivo, cuatro call-sites tiraban dos
 * tercios del triángulo y re-derivaban el resto a mano.
 *
 * `null` significa NEUTRAL. Cada caller conserva su propio comportamiento
 * neutro tal cual (`triple?.dot ?: MaterialTheme.colorScheme.outline`).
 * Fabricar aquí un container neutro sería decidir el alpha de contraste del
 * chip — y esa decisión es de A1 (`calendar-a11y-and-fit-fixes`), no de T4.
 */

/**
 * Tríada de color de la categoría de [info], o `null` si no hay color del
 * servidor y el nombre no es una categoría conocida.
 *
 * Precedencia (congelada): ① `info.colorHex` del servidor (gana siempre,
 * `CategoryResolver.kt:43`); ② `info.name` → `CategoryTokens.resolve`; ③ `null`.
 *
 * ⚠ RAMA DEL HEX DEL SERVIDOR — `container` y `content` NO están decididos.
 * Un color del servidor llega como un único `Color`: no tiene container ni
 * content derivados, y nadie los necesita hoy (los cuatro call-sites de T4 sólo
 * leen `.dot`). Para que la tríada sea completa sin inventar una decisión de
 * contraste, los tres slots llevan el mismo hex: `container` y `content` son
 * **placeholders sin dueño**, no una elección. Su dueño es A1, que además debe
 * tratar esta rama aparte de la de token — un hex del servidor no tiene tríada
 * de marca asociada. El calendario no lee estos slots → T4 queda en delta cero.
 */
@Composable
fun categoryTagTriple(info: CategoryInfo?): TagTriple? {
    val darkTheme = isSystemInDarkTheme()
    return info?.colorHex?.let { hex ->
        parseHexColor(hex)?.let { color ->
            TagTriple(dot = color, container = color, content = color)
        }
    } ?: info?.name?.let { name ->
        CategoryTokens.resolve(name)?.let { category ->
            CategoryTokens.triple(category, darkTheme)
        }
    }
}

/**
 * Tríada de color del [status] de una actividad, o `null` si el estado no está
 * en el mapa de alias. No hay fallback por nombre: sólo el mapa canónico.
 */
@Composable
fun statusTagTriple(status: String): TagTriple? {
    val darkTheme = isSystemInDarkTheme()
    return StatusTokens.aliasMap[status.uppercase()]
        ?.let { token -> StatusTokens.triple(token, darkTheme) }
}

/**
 * ¿Es [status] el estado terminal `Done`? (`R3-T3-3`)
 *
 * **Por qué vive aquí y no en la UI.** El calendario no puede nombrar
 * `StatusTokens`: RC-7 exige que `feature/activities/` no toque los objetos de
 * token, así que la costura es su único camino. Y `R3-T3-3` prohíbe explícitamente
 * una quinta copia del mapa de alias en la capa de UI. Esta función **lee** el
 * mapa canónico (`Color.kt:172-190`); no lo replica.
 *
 * No necesita `@Composable`: el mapa de alias es independiente del tema, así que
 * `DONE`, `COMPLETED` y `APROBADO` resuelven a `Done` sin tocar `isSystemInDarkTheme()`
 * y sin añadir un quinto `when` de etiquetas.
 */
fun isDoneStatus(status: String): Boolean =
    StatusTokens.aliasMap[status.uppercase()] == StatusTokens.Status.Done

/**
 * `true` cuando el estado resuelve a `Overdue` por el mapa canónico de alias.
 *
 * Existía duplicado como comparación de cadena en `RailCanvas.kt` y en `MonthDensity.kt`.
 * Comparar el alias resuelto da lo mismo para lo que manda el servidor hoy y sigue siendo
 * correcto si mañana aparece otro alias de `Overdue`, que es justo lo que `R3-T3-3` pide
 * evitar replicando. Queda una copia en `ActivityCard.kt`; desaparece con esa tarjeta en T8.
 */
fun isOverdueStatus(status: String): Boolean =
    StatusTokens.aliasMap[status.uppercase()] == StatusTokens.Status.Overdue
