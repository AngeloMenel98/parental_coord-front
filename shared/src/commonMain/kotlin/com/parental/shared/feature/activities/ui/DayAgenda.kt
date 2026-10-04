package com.parental.shared.feature.activities.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
 * ⚠ Techo ≈230. Antes de separar `DayAgendaHeader` a su propio fichero, F7 cerraba en
 * 351 y NO llegaba: el exceso era TODO prosa de estos comentarios. Comprimir es lo
 * permitido; suprimir el porqué no (precedente B, desv. 5).
 *
 * 🪓 2026-10-03 — `DayAgendaHeader` se movió a `DayAgendaHeader.kt`. El antetítulo y el
 * titular son otro trabajo que la lista de actividades, y este fichero ya sostenía las
 * cuatro piezas de `RR-4`. 🔴 `RR-4` NO SE TOCA: las cuatro siguen siendo DECLARACIONES
 * separadas, sólo que repartidas en dos ficheros. La reversibilidad de C9 (`OD-4`) depende
 * de la declaración, no del fichero.
 */

/**
 * Fila del agenda del día — `R4-T2-4`. Cuatro columnas hermanas: **columna de tiempo +
 * riel + nodo + contenido**.
 *
 * `AgendaGeometry.timeColumn` ya valía 48dp desde T1 (declarado con propietario
 * `T2 / R4-T2-4`), así que T2-4 no crea token: consume el suyo. Es la única dimensión de
 * 48dp que NO es un objetivo táctil — no "corregirla" a `Layout.touchMin`, igual que
 * `dateColumn` (`Spacing.kt`).
 *
 * SEMÁNTICA — EXACTAMENTE DOS nodos por hueco, y ni uno decorativo más: (1) el **nodo de
 * tiempo**, un `Text` real que se lee tal cual; y (2) el nodo fusionado de [ActivityBody],
 * que anuncia fecha, hora, título, categoría, tipo, estado y límite
 * ([activitySemanticLabel]). Sólo la fila es pulsable. El [ConfirmarChip], cuando existe, es
 * un TERCER nodo hermano (T2-8), nunca anidado dentro del rótulo de la fila.
 *
 * ⚠ La hora se anuncia DOS veces — y `R4-T2-4` lo pide así a propósito. No se oculta porque
 * es información real, y no se funde en el rótulo porque `activitySemanticLabel` ya la dice:
 * fundirla repetiría la hora DENTRO de un nodo, mientras que su propio nodo la produce ENTRE
 * dos, que es la estructura que el requisito especifica y la que el árbol de semántica puede
 * contar — y además es lo que permite barrer la columna de horas. `AgendaNode` y el riel SÍ
 * siguen decorativos (`clearAndSetSemantics { }`): el rótulo ya nombra la categoría y la
 * cronología la llevan la hora y el contador de `DayAgendaHeader`.
 *
 * 🔴 CONSECUENCIA DE `R7-T2-8` QUE `R4-T2-4` NO REFLEJA. Ese requisito dice "exactamente DOS
 * nodos por hueco"; con la hora rotulada **una vez por grupo** un hueco de N tiene **DOS**
 * nodos en la PRIMERA fila (hora + rótulo fusionado) y **UNO** en cada CONTINUACIÓN, donde no
 * hay `Text` que contar — el `Spacer` no es nodo. Mejora, no regresión: el rótulo fusionado
 * sigue anunciando hora, título, categoría y estado en todas las filas, así que ninguna es
 * sorda; lo que cambia es el recuento. 🔴 `R4-T2-4` **NO se ha editado para que cuadre**: su
 * texto es el rastro de auditoría y el desacuerdo queda declarado aquí, no borrado.
 *
 * ⚠ `height(IntrinsicSize.Min)` es el MISMO patrón que ya envía `AgendaList.kt`: sin él el
 * riel de `fillMaxHeight()` no tiene altura que llenar. Desviación registrada contra §7.2
 * de diseño, confirmada por T2-7: el riel del día es un segmento de altura de fila, y esa es
 * la única forma de dibujarlo sin un contenedor medidor extra.
 */
@Composable
internal fun DayAgendaRow(
    timeLabel: String?,
    row: TimelineRow,
    info: CategoryInfo?,
    zone: TimeZone,
    showConnector: Boolean = true,
    canConfirm: Boolean,
    onSelectActividad: (String) -> Unit,
    onConfirm: (String) -> Unit,
    isConfirming: Boolean,
    focusRequesters: MutableMap<String, FocusRequester>,
    /**
     * R2-T2-7 · Hueco de swipe. Llega como
     * `@Composable (content: @Composable () -> Unit) -> Unit` y NO como componente
     * concreto a propósito: si [DayAgendaRow] importara `ActivitySwipeBox`, esta fila
     * sabría qué gestos existen y habría que cambiar su firma cada vez que se añada uno.
     * Con el hueco, la fila sólo garantiza "esto es lo que se puede deslizar".
     *
     * Por defecto `{}` (pinta el contenido tal cual): [AgendaList] y cualquier otro
     * llamante existente siguen compilando sin tocar una línea, y una fila sin swipe no
     * añade ni superficie ni nodo de semántica.
     */
    swipe: @Composable (content: @Composable () -> Unit) -> Unit = { c -> c() },
    modifier: Modifier = Modifier,
) {
    // R4-T1-2 · Mismo registro que `AgendaTimelineRow`; el mapa es compartido para
    // que T2-2 pueda devolver el foco sea cual sea la pestaña que abrió la hoja.
    val focusRequester = remember { FocusRequester() }
    SideEffect { focusRequesters[row.actividad.id] = focusRequester }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
// 🔴 `R7-T2-8` · LA HORA SE ROTULA UNA VEZ POR GRUPO. `timeLabel` es `null` en las filas
        // de CONTINUACIÓN de un grupo de misma hora. El pie está RESERVADO igual (48dp) para
        // que nodo, riel y cuerpo no se desplacen: el riel es lo que `RR-4` y `AgendaList.kt`
        // tienen alineado. ⚠ Un `Spacer` y no un texto vacío porque un `Text` vacío SÍ crea
        // nodo de semántica, y `R4-T2-4` cuenta nodos con precisión.
        if (timeLabel != null) {
            Text(
                text = timeLabel,
                style = TypeTokens.time,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 🔴 `R4-T2-4` · ARRIBA, NO CENTRADA. `align(Alignment.Top)` es el
                // `Alignment.Vertical` del `RowScope`: sube SÓLO este hijo, así que el nodo y
                // el cuerpo siguen centrados. Con título de dos líneas + límite la fila
                // crece, y una hora centrada deja de leerse como la hora DE ESA fila.
                // ⚠ Y NO SE ESCONDE (ver el KDoc): sin el `clearAndSetSemantics` de antes,
                // este `Text` es un nodo real y la fila tiene su nodo de hora.
                modifier = Modifier
                    .align(Alignment.Top)
                    .width(AgendaGeometry.timeColumn)
                    .padding(end = Spacing.sm),
            )
        } else {
            Spacer(modifier = Modifier.width(AgendaGeometry.timeColumn))
        }
        // Riel = hermano de la fila, NO hijo de [AgendaNode] (RR-4).
        if (showConnector) {
            TimelineConnector()
        }
        AgendaNode(
            tag = categoryTagTriple(info),
            categoryName = info?.name,
            status = row.actividad.status,
        )
        // R2-T2-7 · El `weight`, el `padding` y el `focusRequester` se QUITAN de
        // `ActivityBody` y se quedan en el `Box` que lo envuelve. No es un
        // reposicionamiento: es el mismo nodo visual de antes —el cuerpo de la fila—, así
        // que la geometría de R2 no se mueve; lo que cambia es que el gesto de deslizar
        // pasa a ser hijo suyo. Devolver el foco al envoltorio además es lo correcto: el
        // destino del foco es la tarjeta completa, no su contenido.
        //
        // ⚠ `Box` SIN `semantics`: `R4-T2-4` cuenta nodos de semántica con precisión y
        // un `Box` pelado no crea ninguno, así que el conteo no cambia. Un
        // `Modifier.semantics {}` aquí sí lo movería.
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.md)
                .focusRequester(focusRequester),
        ) {
            swipe {
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
                )
            }
        }
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
        // 🔴 `R6-T2-6` · EL PUNTO VA EN LA ESQUINA SUPERIOR DERECHA. El requisito lo pide
        // "centred on the node's top-right corner"; abajo chocaba con el borde inferior del
        // propio disco y con el `outline` de la fila siguiente. Arriba, con la fila ya
        // alineada ARRIBA (T2-4), el solape lee como "este nodo lleva este estado".
        //
        // ⚠ TAMAÑOS: collar `statusBadge` 8dp con punto 6dp, el token tal cual. Su VALOR no
        // es de T2 — `ActivityBody.kt:160` lo comparte el punto de estado de la fila de
        // Agenda, así que moverlo es un cambio de dos pantallas. El contraste que hace el
        // collar es punto-contra-`surface`, no collar-contra-disco.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
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

