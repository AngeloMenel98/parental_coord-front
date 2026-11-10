package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.parental.shared.core.ui.theme.Spacing

/*
 * La línea vertical del timeline — `calendar-design-redesign`, T5-6.
 *
 * UNA sola implementación para los DOS sitios que la pintan: la fila cargada
 * (`AgendaList.kt`) y el esqueleto (`CalendarChrome.kt`). Antes eran dos `Box` con
 * el mismo papel y colores distintos — `outline` 4.37:1 / 5.83:1 en la lista,
 * `outlineVariant` 1.67:1 / 1.98:1 en el esqueleto. El contraste degrada justo en
 * el instante en que llega el contenido; compartir el composable es lo que hace
 * esa regresión imposible, en vez de dejarla pendiente de que nadie se acuerde.
 *
 * Vive en su propio archivo, no dentro de `AgendaList.kt`, porque ese archivo está
 * a 2 líneas de su techo y `CalendarChrome.kt` a 4 del suyo: meterlo en cualquiera
 * de los dos rompe un cap. Es la misma válvula de escape que T4-8 aplicó a
 * `ActivityBody.kt`.
 *
 * ⚠ NEUTRALIDAD: el color es IDÉNTICO en toda fila, sea cual sea su estado y
 * categoría (`prd-v4:94`) — no se cablea a ningún token. La cronología visual clara
 * es de la LÍNEA (`prd-v4:54`), no del separador: por eso aquí va `outline` y el
 * separador de fila se queda en `outlineVariant`.
 */

/** Línea de 1dp del timeline, del alto de su contenido. Cromo decorativo. */
@Composable
internal fun AgendaTimelineLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(Spacing.hairline)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outline)
            .clearAndSetSemantics { },
    )
}
