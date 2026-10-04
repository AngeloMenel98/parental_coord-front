package com.parental.shared.feature.activities.ui.create

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.CategoryTokens
import com.parental.shared.core.ui.theme.DetailGeometry
import com.parental.shared.core.ui.theme.ParentalShapes
import com.parental.shared.core.ui.theme.ProgressStroke
import com.parental.shared.core.ui.theme.SheetGeometry
import com.parental.shared.core.ui.theme.Spacing
import com.parental.shared.core.ui.theme.parseHexColor
import com.parental.shared.feature.activities.ui.CreateUiState
import com.parental.shared.feature.activities.ui.MemberCandidate
import com.parental.shared.feature.activities.ui.criticalityLabel
import com.parental.shared.feature.activities.ui.formatTime
import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlinx.datetime.LocalDate

/*
 * Modal "Crear actividad" — `create-activity`, T-FE-16. Subpaquete `create/`, hermano de `detail/`.
 *
 * Decisiones que NO se deducen del código:
 *
 * ⚠ SUB-CONJUNTO DE API ACOTADO. `compose.material3:1.7.1` sólo aplica a iOS: el target Android
 *   resuelve a `androidx.compose.material3` y `androidApp/build.gradle.kts:47` pincha
 *   `compose-bom:2024.12.01` → **1.3.1**. Este fichero compila contra la INTERSECCIÓN de ambas.
 *   Verificado en los sources de las dos: `DatePicker`, `DatePickerDialog`,
 *   `rememberDatePickerState(initialSelectedDateMillis, selectableDates)`, `SelectableDates`,
 *   `TimePicker`, `rememberTimePickerState`, `TimeInput`, `FilterChip`,
 *   `ExposedDropdownMenuBox`, `FlowRow`. Lo que NO se usa: **`TimePickerDialog`, que no existe en
 *   ninguna de las dos** (por eso la hora es un `AlertDialog` propio con `TimeInput`), y
 *   `DateInput`, que diverge (`internal` en 1.7.1, `public` en androidx) — por eso la fecha usa
 *   `DatePicker` con `DisplayMode.Picker`. Cero dependencias nuevas.
 *
 * ⚠ ESTADO DE LA VERIFICACIÓN iOS. En Linux NO hay compilación real de 1.7.1: los únicos
 *   targets no-Android del módulo son iOS y Kotlin/Native no los construye fuera de macOS. La
 *   intersección se verificó LEYENDO LOS SOURCES de las dos versiones (`DatePickerDialog`,
 *   `rememberDatePickerState`, `rememberTimePickerState`, `TimeInput`, `TimePickerDialog` —que no
 *   existe en ninguna—, `MenuAnchorType`, `FilterChip`/`Role.Checkbox`), no compilando. Y
 *   `./gradlew :shared:compileCommonMainKotlinMetadata`, que sería la puerta de 1.7.1, está
 *   ROTA DE ANTES por `HomeScreen.kt:355` (`when` sobre `DayOfWeek` sin `else`), fichero ajeno a
 *   este cambio. Arreglarlo aquí sería alcance Ajeno: quien toque esa puerta debe abrir un
 *   ticket aparte.
 *
 * ⚠ SIN `BackHandler`. El diseño (§4.6) lo pedía, pero `androidx.activity:activity-compose` no está
 *   en el `compileClasspath` de `:shared` (sólo llega como dependencia transitiva `runtime`), así
 *   que `androidx.activity.compose.BackHandler` no compila y añadirlo sería una dependencia
 *   nueva. No hace falta: `ModalBottomSheet` registra él mismo un callback de back que llama a
 *   `onDismissRequest` (`ModalBottomSheet.android.kt:548-553`, material3 1.3.1, verificado). Los
 *   TRES gestos de descarte —X, arrastre y botón físico/ESC— desembocan en `onDismissRequest`, y la
 *   guarda de "descartar con cambios" vive justo ahí.
 *
 * ⚠ `readOnly = true` Y NO `enabled = false` en fecha/hora. `enabled = false` SÍ deja el
 *   `IconButton` del `trailingIcon` pulsable (`TextFieldImpl.kt:203-211` sólo le pasa el color
 *   `trailingIconColor(enabled, …)`, no un flag de interacción) — es decir, un control ACCIONABLE
 *   que se DIBUJA como deshabilitado. `readOnly` mantiene el color de campo normal y sigue
 *   impidiendo escribir: el affordance dice la verdad.
 *
 * ⚠ R-13 (tope de altura): [SheetGeometry] es ahora un token compartido con la hoja de detalle.
 *   🔴 NO "arreglar" con `skipPartiallyExpanded = false` — da el 50 % por cero líneas y rompe el
 *   pie fijo. La explicación larga está en el token.
 *
 * ⚠ `todayUtcMillis()` se calcula UNA vez por vida de la hoja, NUNCA dentro de `isSelectableDate`:
 *   el picker consulta esa lambda por celda y por frame, así que un "hoy" recalculado en cada
 *   llamada movería la frontera DENTRO del mismo frame.
 *
 * ⚠ La semántica de interruptor de los chips sale GRATIS: `FilterChip` → `SelectableChip` aplica
 *   `Modifier.semantics { role = Role.Checkbox }` en AMBAS versiones (`Chip.kt:2017` en 1.3.1). El
 *   `leadingIcon` de check es lo que hace que la selección NO dependa sólo del color (R10): con un
 *   solo hijo auto-seleccionado el usuario no notaría que puede deseleccionarlo o elegir más.
 *
 * ⚠ `isActive` se filtra SÓLO aquí. `CategoryResolver.build` sigue mapeando todas para que la
 *   lista de la agenda no cambie; el filtro por activas es política del ALTA (una actividad nueva
 *   exige categoría activa — CC-4/AS-6).
 */

/**
 * Raíz del modal. La hoja se compone SIEMPRE que se llame (la pantalla decide con
 * `create != Closed`), nunca con un `if` en la pantalla: así la cabecera, el pie y el
 * `SnackbarHost` sobreviven a la transición `Content → Loading → Closed` sin parpadeos.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CreateActivitySheet(
    state: CreateUiState,
    form: CreateForm,
    isDirty: Boolean,
    categories: Map<String, CategoryInfo>,
    children: List<VinculoChild>,
    memberCandidates: List<MemberCandidate>,
    snackbarHostState: SnackbarHostState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onToggleChild: (String) -> Unit,
    onAssigneeChange: (String?) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (TimeOfDay) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    // ⚠ LA MISMA función que gobierna el botón "Crear" gobierna también las líneas de error: no
    //   hay dos reglas de validación que puedan discrepar.
    val errors = remember(form) { validateCreateForm(form) }
    fun errorFor(field: CreateField) = errors.firstOrNull { it.field == field }?.message

    // ⚠ El error en línea SÓLO aparece si el campo YA FUE TOCADO. Con el botón deshabilitado
    //   desde el principio, un formulario recién abierto se llenaría de rojos antes de que el
    //   usuario haya tocado nada.
    var touched by remember { mutableStateOf(emptySet<CreateField>()) }
    fun showError(field: CreateField) =
        if (field in touched) errorFor(field) else null

    // ⚠ "Hoy" se congela al abrir la hoja: ver la nota del pie del `NoPastDatesFrom`.
    val selectableDates = remember { NoPastDatesFrom(todayUtcMillis()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // ⚠ El descarte con cambios pasa por AQUÍ y no por [onDismissRequest] directamente, porque los
    //   tres gestos (X, arrastre, botón físico) llaman a `onDismissRequest` y la guarda debe estar
    //   en ese ÚNICO sitio. Con cambios ⇒ confirmar; sin cambios ⇒ cerrar.
    val requestDismiss: () -> Unit = {
        if (isDirty) confirmDiscard = true else onDismissRequest()
    }

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetMaxWidth = Spacing.maxContentWidth,
        dragHandle = null,
    ) {
        BoxWithConstraints {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = maxHeight * SheetGeometry.maxHeightRatio),
            ) {
                CreateHeader(onClose = requestDismiss)

                // Error de red POR ENCIMA del formulario y con los datos intactos (spec: "todos los
                // campos siguen rellenos"): el formulario vive en el ViewModel (D19) y el `Error`
                // no lo sustituye, así que aquí sólo hay que pintar la tarjeta y el reintento.
                if (state is CreateUiState.Error) {
                    CreateErrorCard(
                        message = state.message,
                        onRetry = onRetry,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    // ⚠ Foco inicial en Título — requisito explícito del spec ("el foco inicial
                    //   MUST caer en Título" + "el teclado ya puede escribir"). El `runCatching`
                    //   es necesario y NO es defensivo: pedir foco antes de que la ventana exista
                    //   lanza IllegalStateException en iOS, y en un modal recién compuesto la
                    //   ventana puede no estar adjunta todavía.
                    val titleFocus = remember { FocusRequester() }
                    OutlinedTextField(
                        value = form.title,
                        onValueChange = {
                            touched = touched + CreateField.TITLE
                            onTitleChange(it)
                        },
                        label = { Text("Título") },
                        singleLine = true,
                        isError = errorFor(CreateField.TITLE) != null,
                        supportingText = showError(CreateField.TITLE)?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                    )
                    LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }

                    OutlinedTextField(
                        value = form.description,
                        onValueChange = {
                            touched = touched + CreateField.DESCRIPTION
                            onDescriptionChange(it)
                        },
                        label = { Text("Descripción") },
                        minLines = 2,
                        isError = errorFor(CreateField.DESCRIPTION) != null,
                        supportingText = showError(CreateField.DESCRIPTION)?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ChildrenSection(
                        children = children,
                        selected = form.childrenIds,
                        error = showError(CreateField.CHILDREN),
                        onToggle = { id ->
                            touched = touched + CreateField.CHILDREN
                            onToggleChild(id)
                        },
                    )

                    CategoriesSection(
                        categories = categories,
                        selectedId = form.categoryId,
                        error = showError(CreateField.CATEGORY),
                        onSelect = { id ->
                            touched = touched + CreateField.CATEGORY
                            onCategoryChange(id)
                        },
                    )

                    // Criticidad READ-ONLY POR CONSTRUCCIÓN: no es un input, es una fila
                    // clave/valor como la de la hoja de detalle. Así no existe el problema de
                    // contraste de un campo "disabled" y el valor no puede editarse ni viajar.
                    // Se deriva de `categories` YA CARGADAS ⇒ "al instante, sin ida y vuelta al
                    // servidor", que es literalmente lo que pide el escenario del spec.
                    CriticalityRow(
                        criticality = form.categoryId?.let { categories[it]?.criticality },
                    )

                    OutlinedTextField(
                        value = form.startDate?.let(::formatDdMmAaaa).orEmpty(),
                        onValueChange = {},
                        label = { Text("Fecha") },
                        placeholder = { Text("dd/mm/aaaa") },
                        readOnly = true,
                        isError = errorFor(CreateField.DATE) != null,
                        supportingText = showError(CreateField.DATE)?.let { { Text(it) } },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    touched = touched + CreateField.DATE
                                    showDatePicker = true
                                },
                            ) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "Elegir fecha")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = formatTime(form.startTime.hour, form.startTime.minute),
                        onValueChange = {},
                        label = { Text("Hora") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showTimePicker = true }) {
                                Icon(Icons.Filled.Schedule, contentDescription = "Elegir hora")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    AssigneeSection(
                        candidates = memberCandidates,
                        selectedId = form.assignedTo,
                        onSelect = onAssigneeChange,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }

                // Pie FIJO, fuera del scroll (misma decisión que el `DetailFooter`): la CTA no
                // baja con el contenido y el doble envío es imposible aunque se llegue a él.
                CreateFooter(
                    enabled = errors.isEmpty() && state !is CreateUiState.Loading,
                    loading = state is CreateUiState.Loading,
                    onSubmit = onSubmit,
                )

                // Un ÚNICO `SnackbarHost` y SIEMPRE el de la hoja abierta (R-8): el de la
                // pantalla queda tapado por el modal, así que "Actividad creada" se anunciaría en
                // un host invisible. Va fuera del `Column` con scroll para nocomer altura útil.
                SnackbarHost(snackbarHostState)
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.startDate?.toPickerMillis() ?: todayUtcMillis(),
            selectableDates = selectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        // ⚠ Se convierte por el INVERSO EXACTO (`pickerMillisToDate` → UTC),
                        //   nunca con `.date` sobre el instante: con la zona del dispositivo el
                        //   día se corre en husos negativos.
                        pickerState.selectedDateMillis?.let(::pickerMillisToDate)?.let(onDateChange)
                        showDatePicker = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = form.startTime.hour,
            initialMinute = form.startTime.minute,
        )
        // ⚠ `TimeInput` dentro de un `AlertDialog` propio porque `TimePickerDialog` NO EXISTE en
        //   material3 (ni 1.3.1 ni 1.7.1). El `TimeInput` mantiene el teclado NUMÉRICO del
        //   dispositivo, que es justo lo que se necesita aquí.
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Hora") },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(TimeOfDay(timeState.hour, timeState.minute))
                        showTimePicker = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar los cambios?") },
            text = { Text("Los cambios sin guardar se perderán.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onDismissRequest()
                    },
                ) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Cancelar") }
            },
        )
    }
}

/** Cabecera: eyebrow + título + "X". ⚠ El eyebrow es `labelLarge` en `primary` — la misma
 *  anatomía que el rótulo de la hoja de detalle (`ActividadDetailSheet.kt:216-222`). */
@Composable
private fun CreateHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nuevo evento",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Crear actividad",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onClose) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Cerrar")
        }
    }
}

/** Hijos: `FlowRow` de `FilterChip` con MULTI-SELECCIÓN (AS-2, D20). El toggle es `Set ± id`, así
 *  que la UI no puede fabricar `[a, a]` ni el backend ver un duplicado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildrenSection(
    children: List<VinculoChild>,
    selected: Set<String>,
    error: String?,
    onToggle: (String) -> Unit,
) {
    CreateFieldBlock(label = "Hijo", error = error) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            children.forEach { child ->
                val isSelected = child.id in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(child.id) },
                    label = { Text("${child.firstName} ${child.lastName}") },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesSection(
    categories: Map<String, CategoryInfo>,
    selectedId: String?,
    error: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeCategories = categories.filterValues { it.isActive }
    val selected = activeCategories[selectedId]

    CreateFieldBlock(label = "Categoría", error = error) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.name ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Seleccionar categoría") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                activeCategories.entries.forEach { (id, info) ->
                    DropdownMenuItem(
                        text = { Text(info.name) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** La MISMA cadena de color que `CategoryBadge(categoryId, resolve)`
 *  (`CategoryBadge.kt:70-74`) — extraída aquí para que las dos superficies no diverjan. */
@Composable
private fun categoryTint(info: CategoryInfo, darkTheme: Boolean): Color =
    info.colorHex?.let(::parseHexColor)
        ?: info.name?.let { name ->
            CategoryTokens.resolve(name)?.let { cat ->
                CategoryTokens.triple(cat, darkTheme).dot
            }
        }
        ?: MaterialTheme.colorScheme.outline

/** Criticidad derivada de la CATEGORÍA, no del cliente (CC-4/CC-5): fila clave/valor read-only.
 *  Sin categoría no se inventa "Media" — el backend la derivaría de la categoría, así que
 *  mostrarla sería mentir. */
@Composable
private fun CriticalityRow(criticality: String?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = ParentalShapes.medium) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = "Criticidad",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = criticality?.let(::criticalityLabel) ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** "Asignar a": "Sin asignar" (`null`) + los miembros del vínculo. ⚠ AS-9 — el desplegable NO
 *  inventa un estado `pending`: `bonds.repository.ts:27-30` ya filtra en el servidor a quienes se
 *  fueron (`leftAt IS NULL`), y lo único que NO se puede filtrar es "inactivo/pendiente"
 *  (`VinculoMember` no trae `leftAt` ni `user.isActive`). Degradación declarada, tal cual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssigneeSection(
    candidates: List<MemberCandidate>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = candidates.firstOrNull { it.id == selectedId }?.name ?: "Sin asignar"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            label = { Text("Asignar a") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // ⚠ Forma CON `MenuAnchorType`, no el `menuAnchor()` sin argumentos: ese overload está
            //   `@Deprecated(WARNING)` en material3 1.3.1 (androidx, línea 397-401) Y en 1.7.1
            //   (línea 188-192), y en ambos delega exactamente aquí. `MenuAnchorType` existe en las
            //   dos versiones, así que esta forma no genera aviso en ninguna — y el campo es
            //   `readOnly`, luego `PrimaryNotEditable` es el tipo que corresponde.
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sin asignar") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            candidates.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.name) },
                    onClick = {
                        onSelect(candidate.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Bloque con rótulo, contenido y error en línea DEBAJO del campo. */
@Composable
private fun CreateFieldBlock(
    label: String,
    error: String?,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        content()
        if (error != null) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Tarjeta de error de red CON reintento — el spec lo pide literalmente ("se muestra un mensaje
 *  de error con opción de reintentar"). Los campos NO se tocan: el formulario vive en el
 *  ViewModel (D19), así que el reintento reutiliza exactamente lo que el usuario escribió. */
@Composable
private fun CreateErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.md),
        shape = ParentalShapes.medium,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "No pudimos crear la actividad",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.md))
            OutlinedButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

/** Pie fijo con la CTA. En `Loading` el botón se deshabilita y gira: el doble envío es imposible
 *  por UI, y además el ViewModel corta con su propio guarda (dos barreras independientes). */
@Composable
private fun CreateFooter(enabled: Boolean, loading: Boolean, onSubmit: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(DetailGeometry.progressSize),
                    strokeWidth = ProgressStroke.thin,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Crear")
            }
        }
    }
}