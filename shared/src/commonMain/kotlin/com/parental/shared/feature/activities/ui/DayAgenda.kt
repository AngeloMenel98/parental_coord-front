package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.TagDot
import com.parental.shared.core.ui.components.categoryTagTriple
import com.parental.shared.core.ui.components.statusTagTriple
import com.parental.shared.core.ui.theme.AgendaGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.TagTriple
import com.parental.shared.core.ui.theme.TypeTokens
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/*
 * Agenda del día — `calendar-design-redesign`, tema T2. F7. **Archivo nuevo.**
 *
 * RR-4 — POR QUÉ SON CUATRO DECLARACIONES Y NO UNA. `RC-4` exige que `DayAgendaHeader`,
 * `DayAgendaRow`, `AgendaNode` y `TimelineConnector` se declaren **por separado**, que
 * `DayAgendaRow` dibuje el riel **independientemente** de `AgendaNode` (riel hermano en el
 * layout de la fila, no hijo del nodo) y que `AgendaNode` reciba un `TagTriple?` y **no** un
 * `Color`. No es estilo: es el MECANISMO de reversibilidad de C9 («¿el nodo lleva título?»,
 * `OD-4`, **ASUMIDO — NO CONFIRMADO**). F7 cabe en su presupuesto **sólo porque** las cuatro
 * piezas están separadas: fusionarlas también cabría, y destruiría en silencio esa
 * reversibilidad. T2-9 la verifica. `TagTriple?` — no `Color` — para que un nodo tipo chip
 * (container + content) sea alcanzable más adelante **sin re-resolver** el color.
 *
 * RR-2 — EL `@OptIn` DE ESTE ARCHIVO ES NUEVO, NO HEREDADO. `WeekAgenda.kt` llevaba
 * `@OptIn(ExperimentalLayoutApi::class)` por su `FlowRow`, y **ese marcador no viaja con el
 * código**: un `@OptIn` ausente NO es un error que el compilador marque, porque la copia de
 * la que se copió sí está anotada. T2-7 retiró el `FlowRow`; el que se reintroduzca uno aquí
 * debe añadir el marcador él mismo, y eso se comprueba con grep. Riesgo R4, ALTA.
 *
 * ⚠ Techo ≈230 al que F7 NO llega: cierra en ~296 y el exceso es TODO prosa de estos
 * comentarios. Comprimir es lo permitido; suprimir el porqué no (precedente B, desv. 5), así
 * que el techo registrado en T2-9 es el real, no el ≈230.
 */

/**
 * Antetítulo + titular del día — `R1-T2-1` (antetítulo) y `R2-T2-2` (titular). El
 * antetítulo se escribe **`Agenda del día`** y se ve en versalitas gracias al
 * `fontFeatureSettings = "smcp"` de `labelSmall` (`Type.kt:103-109`): **NO se escribe en
 * mayúsculas a mano**, la fuente de verdad es la cadena con su capitalización natural y la
 * forma versal es un efecto de tipografía. Nodo de texto real, se anuncia una sola vez, **no**
 * lleva `contentDescription` y **no** se oculta de la tecnología asistiva: duplica nada,
 * porque las filas de abajo anuncian sus propios títulos.
 */
@Composable
internal fun DayAgendaHeader(date: LocalDate, activityCount: Int, zone: TimeZone) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Agenda del día",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
        )
        // R2-T2-2. Titular `weight(1f)` y conteo de ancho intrínseco: a 320dp el TITULAR se
        // elide, nunca el conteo. Éste no lleva `maxLines` porque el requisito es que no se
        // trunque a NINGÚN ancho. `TextAlign.End` + singular/plural: "1 actividad" / "N
        // actividades". Contar es `FR-13` — hasta T2-2 el número sólo existía en el
        // `contentDescription` fuera de pantalla de la celda de día, nunca en la UI visible.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatLongDate(date, currentDateIn(zone).year),
                style = TypeTokens.date,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = Spacing.sm),
            )
            Text(
                text = if (activityCount == 1) "1 actividad" else "$activityCount actividades",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        // R3-T2-3 · LA COPIA SIGUE AL TRATAMIENTO DE FILA QUE SE ENVÍA. Aquí nombra la
        // ACTIVIDAD porque la fila CON título (A4 provisional) es la que se renderiza; la
        // variante que nombra el icono sólo sería cierta para un nodo sin título
        // (`prd-v4:166` guioniza esa OTRA variante).
        //
        // ⚠ `S3-T2-3a` es un grep LITERAL sobre `shared/src/` y exige **0** aciertos
        // mientras la fila con título siga en pantalla. Citar la otra variante—even
        // entre comillas, incluso en un comentario— lo convierte en 1 y hace fallar el
        // guard por una razón que no es un defecto del producto. Por eso aquí se
        // DESCRIBE y no se transcribe.
        //
        // ⚠ LA COSTA DE C9 ASOMA AQUÍ: las DOS variantes están fijadas para que el futuro
        // flip sea un cambio de UNA cadena. Si C9 se confirma "sin título", cambia esta
        // línea y sólo esta. Es la costura que verifica T2-9.
        Text(
            text = "Toca una actividad para ver los detalles",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Fila del agenda del día — `R4-T2-4`. Cuatro columnas hermanas: **columna de tiempo +
 * riel + nodo + contenido**.
 *
 * `AgendaGeometry.timeColumn` ya valía 48dp desde T1 (declarado con propietario
 * `T2 / R4-T2-4`), así que T2-4 no crea token: consume el suyo. Es la única dimensión de
 * 48dp que NO es un objetivo táctil — no "corregirla" a `Layout.touchMin`, igual que
 * `dateColumn` (`Spacing.kt`).
 *
 * SEMÁNTICA — como máximo DOS nodos, y nunca un tercero decorativo: (1) el nodo fusionado de
 * [ActivityBody], que ya anuncia fecha, hora, título, categoría, tipo, estado y límite
 * ([activitySemanticLabel]); y (2) el [ConfirmarChip], hermano aparte con su propio nodo
 * (T2-8). La columna de tiempo y el nodo son **decorativos para la tecnología asistiva**:
 * `activitySemanticLabel` ya dice la hora de `row.localStart` — que es exactamente la de esta
 * fila — y la categoría en palabras, así que un nodo para cualquiera de los dos sería
 * duplicación, el mismo argumento que `AgendaRowSeparator` da en `AgendaList.kt`.
 *
 * ⚠ `height(IntrinsicSize.Min)` es el MISMO patrón que ya envía `AgendaList.kt`: sin él el
 * riel de `fillMaxHeight()` no tiene altura que llenar. Desviación registrada contra §7.2
 * de diseño, confirmada por T2-7: el riel del día es un segmento de altura de fila, y esa es
 * la única forma de dibujarlo sin un contenedor medidor extra.
 */
@Composable
internal fun DayAgendaRow(
    timeLabel: String,
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    showConnector: Boolean = true,
    canConfirm: Boolean,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeLabel,
            style = TypeTokens.time,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(AgendaGeometry.timeColumn)
                .padding(end = Spacing.sm)
                .clearAndSetSemantics { },
        )
        // Riel = hermano de la fila, NO hijo de [AgendaNode] (RR-4).
        if (showConnector) {
            TimelineConnector()
        }
        AgendaNode(
            tag = categoryTagTriple(info),
            categoryName = info?.name,
            status = row.actividad.status,
        )
        ActivityBody(
            row = row,
            info = info,
            zone = zone,
            semanticLabel = activitySemanticLabel(row, info, zone),
            showTime = false,
            showCategoryIcon = false,
            showMetaChips = false,
            showChevron = false,
            // R2-T2-8 · La fila y el chip son DOS nodos hermanos, no uno anidado: el
            // chip se pinta dentro de este `Row` pero FUERA del `clickable` del cuerpo,
            // de modo que tocar cerca de "Confirmar" confirma y no abre el detalle.
            showConfirmChip = true,
            canConfirm = canConfirm,
            onClick = { onSelectActividad(row.actividad.id) },
            onConfirm = { onConfirm(row.actividad.id) },
            isConfirming = isConfirming,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.md),
        )
    }
}

/**
 * Segmento de riel del día — `R2-T2-7`, y la CUARTA declaración que `RC-4` obliga a
 * mantener separada (`TimelineConnector`, nieto de `DayAgendaRow`).
 *
 * **N FILAS → N−1 SEGMENTOS.** El riel se emite en toda fila salvo en la **última** de
 * cada grupo de misma hora. De ahí la regla mecánica: ningún segmento cuelga debajo
 * del último nodo (cola huérfana) y ninguno asoma encima del primero (tapa suelta).
 * Nótese que `AgendaTimelineLine.kt` —el riel de F4— **no** se sustituye: aquel paints
 * una línea continua por fila y su geometría/color son de T5-6, no de T2. Aquí sólo
 * se declara el segmento propio de F7, por RR-4.
 *
 * Color `outline` (4.37:1 claro / 5.83:1 oscuro), 1dp de `[Spacing.hairline]`.
 * `outlineVariant` (1.67:1 / 1.98:1) es del SEPARADOR de fila de Agenda y aquí sería
 * ilegible: el portador de la cronología visual clara es la línea (`prd-v4:54`).
 * Cromo decorativo: `clearAndSetSemantics { }`, como el riel de F4.
 */
@Composable
internal fun TimelineConnector(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(Spacing.hairline)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outline)
            .clearAndSetSemantics { },
    )
}

/**
 * Nodo del timeline del día — `R2-T2-5`: disco de [AgendaGeometry.nodeCard] (40dp) con el
 * icono de categoría y el punto de estado. **No** contiene título, deadline ni chips — esa
 * separación es lo que hace reversible C9 (`R2-T2-5c`).
 *
 * COLOR. El disco toma `tag.dot` (el ancla de identidad de la tríada, la misma elección que
 * el `TagDot` de la fila de Agenda) y el fallback es `outline` — 4.37:1 / 5.83:1 — NUNCA el
 * `dot` de otra categoría: un nodo desconocido no debe insinuar una categoría que no tiene
 * (`R2-T2-5b`).
 *
 * EL PUNTO DE ESTADO va sobre un **collar de [Spacing.collar] (2dp) de `surface`** entre él y
 * el disco. Sin collar, `Dispute` y `Familiar` se leerían como el MISMO color (1.00:1 entre
 * sí) por familia de tono; con collar el ojo compara el estado contra `surface` — cota peor
 * 3.23:1 claro / 3.85:1 oscuro — y el contraste del color nunca es la única señal: la palabra
 * de estado sigue en el rótulo accesible.
 *
 * OI-1 — EL ÚNICO `Color(0xFFFFFFFF)` CRUDO DEL TEMA T2, y va en esta llamada al `Icon` y en
 * ningún otro sitio. El icono es blanco SIEMPRE, sobre los seis discos de categoría y sobre
 * el `outline` del fallback: la cota peor es 3.48:1 (`Recreación`), siempre ≥3:1. Por eso es
 * un literal y no un token — y por eso puede haber exactamente uno.
 */
@Composable
internal fun AgendaNode(
    tag: TagTriple?,
    categoryName: String?,
    status: String,
) {
    Box(modifier = Modifier.size(AgendaGeometry.nodeCard)) {
        Box(
            modifier = Modifier
                .size(AgendaGeometry.nodeCard)
                .clip(CircleShape)
                .background(tag?.dot ?: MaterialTheme.colorScheme.outline)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryVector(categoryName) ?: Icons.AutoMirrored.Filled.Label,
                contentDescription = null,
                // ÍCONO BLANCO FIJO: contraste ≥3:1 sobre los seis `dot` de categoría y
                // sobre el `outline` del fallback. Único `0xFFFFFFFF` de T2 (OI-1).
                tint = Color(0xFFFFFFFF),
                modifier = Modifier.size(AgendaGeometry.nodeCard / 2),
            )
        }
        // Collar de 2dp de `surface` entre el punto de estado y el disco de categoría.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(AgendaGeometry.statusBadge)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            TagDot(
                color = statusTagTriple(status)?.dot ?: MaterialTheme.colorScheme.outline,
                size = AgendaGeometry.statusBadge - Spacing.collar,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
