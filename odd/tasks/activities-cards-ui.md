# Activities cards UI — agenda redesign (direction 03) + calendar day-jump

Objective: rebuild the AGENDA mode of the Activities screen as one card per
activity (design direction 03, `design/activities-ui/03-tarjetas-a-la-vista.html`)
and add a calendar sheet that opens "with the days that has activities on there"
to jump the feed to a day.
Branch: `sdd/activity-swipe-actions` (repo's working branch; all prior ODD work
landed here). Delivery: reviewable work-unit commits (~400 authored lines), tests
and docs in the same commit as the behavior.
Test runner: `./gradlew :shared:testDebugUnitTest`; compile gate
`./gradlew :shared:compileDebugKotlinAndroid` (0 lines matching `e:`); final gate
`./gradlew :androidApp:assembleDebug`.
Visual spec: the artboard + `design/system.md` tokens. Frozen: `core/ui/theme/Color.kt`,
no new tokens; app copy in Spanish.

## Specs

- S1 (chosen direction, user verbatim): "if 03 could open a calendar with the days
  that has activities on there, implement that, otherwise use 02". 03 qualifies:
  its feed is grouped by day, so a calendar sheet is a day-jump affordance and the
  two compose. Artboards 01 and 02 stay as alternatives; only 03 is implemented.
- S2 (card anatomy, per artboard): one card per activity in AGENDA mode, replacing
  the date-column + timeline-line rows. 4dp rail in the category `dot` color on the
  leading edge; head = title + status pill top-right (the `StatusTag` recipe: dot
  color text on the alpha-0.15 container); meta = monospace time (`TypeTokens.time`)
  + optional deadline line ("Límite vencido: …" when overdue); foot = category
  label (dot + name in `content` color) + inline outlined "Confirmar" button iff
  `canConfirm`. Done cards: title dimmed to `onSurfaceVariant` and struck through.
  Feed intro line (artboard verbatim): "Deslizá una tarjeta para completar o
  posponer". Cards keep the existing swipe gesture (mounted at the card render
  point, `ActivitySwipeBox` — omission must not compile silently).
- S3 (calendar sheet): the top-bar calendar icon opens a modal bottom sheet with a
  month grid (weeks Monday-first, weekday header L M X J V S D). One dot per
  category present in the day, design note verbatim: "Un punto por categoría
  presente en el día (máximo 3, en orden de hora de inicio). Al tocar un día, la
  hoja se cierra y la lista salta a ese día — o al grupo con actividades más
  cercano hacia adelante si no tiene. «Hoy» vuelve al día actual." If no following
  group exists, jump to the latest preceding group. Today = filled `primary` disc.
  Month nav chevrons; "Cerrar" and scrim dismiss. In SEMANA mode the same sheet
  selects the date (and re-anchors the week) instead of scrolling.
- S4 (group headers): day groups get small-caps headers, artboard verbatim:
  "lunes 12 de mayo" with a relative marker "· hoy" on today's group (weekday full
  lowercase + day + month full lowercase). Undated group keeps "Sin fecha de inicio".
- S5 (chrome): filters become one horizontal line — Todos / Eventos / Obligaciones
  chips with counts derived from the loaded list (Todos = all, Eventos = `event`,
  Obligaciones = `obligation`). The "Vista" segmented control leaves the controls
  row: view switching becomes a top-bar icon (list ↔ week). Top bar per artboard:
  `surface` container, hairline `outlineVariant` bottom border, actions = calendar
  icon (opens sheet), view-toggle icon, "+" (unchanged `openCreate`).
- S6 (preserved behavior): card tap opens the detail sheet exactly like the row
  did (originId + focus return via `focusRequesters`, list fallback); Confirm keeps
  calling `confirmAssignment` under the same `canConfirm` rule; undo snackbar flow
  untouched; `SwipeMountingTest` stays green (the mount point moves, the mounting
  contract does not).
- S7 (untouched): SEMANA mode rendering (`WeekTimeline`, `DayAgenda`, `WeekAgenda`,
  `WeekOverview`, `AgendaTimelineRow` for undated rows), detail sheet, create flow,
  attachments, realtime wiring. No new color tokens; Spanish copy only.
- S8 (accessibility): card = one merged semantics node + sibling Confirm button
  (same rule as today); calendar day cells spoken as e.g. "lunes 12 de mayo, hoy,
  3 actividades: Salud, Educación, Recreación" / "…, sin actividades"; decorative
  icons and dots cleared from semantics; new touch targets ≥ 48dp; sheet is a real
  modal (library scrim covers the feed).

## Tasks

| ID | Spec | Route | Status | Commit |
|----|------|-------|--------|--------|
| T1 | S3, S4, S5 | inline (test-first) | done | c086ac2 |
| T2 | S2, S4 | inline | done | 1908266 |
| T3 | S5 | inline | done | 8821e18 |
| T4 | S3 | inline | done | 0b41805 |
| T5 | S8 | inline | done | 639b6c2 |
| T6 | S6 | inline | done | — |
| T7 | all | inline | done | — |
| T8 | S2, S6 | inline (test-first) | done | — |
| T9 | S3, S6 | inline | pending | — |

- T1 — pure derivation + formatters, RED→GREEN: `buildMonthGrid(year, month)`
  (Monday-start cells incl. outside days), `buildDayDots(rows, resolve)` (date →
  distinct categories by first start, cap 3), `jumpTargetDate(date, groups)`
  (exact → nearest following → latest preceding → null), `tabCounts(actividades)`,
  `formatDayTitle` / `formatGroupTitle` ("martes 12 de mayo" para 2026-05-12),
  `dayCellSemanticLabel(...)`.
- T2 — `ActivityCard.kt` + `AgendaList.kt` rewrite: group headers, cards, feed
  hint, swipe mounted per card, focusRequesters re-registration per id.
- T3 — chrome: top bar (calendar + view-toggle icons, surface + hairline), filters
  single line with counts, remove the "Vista" segmented control.
- T4 — `CalendarJumpSheet.kt`: month grid sheet, dots, day cells, Hoy/Cerrar,
  month nav; jump wiring (hoisted `LazyListState`, pending-jump state, scroll to
  group key); SEMANA mode selects date instead.
- T5 — a11y pass per S8.
- T6 — regression: `SwipeMountingTest` green, full unit suite green.
- T7 — final gates (compile + unit tests + assembleDebug), doc log, close report.

## Log

### L1 — user request (verbatim)

> if 03 could open a calendar with the days that has activities on there, implement that, otherwise use 02

### L2 — resume context

Resumed from Engram `design/activities-ui/status` (#1636) + session summary (#1635,
2026-10-29): design work was paused awaiting the user's pick among three artboards.
Canvas comments: none. User picked 03 conditionally on the calendar affordance;
feasible, so artboard 03 was iterated in place (added the calendar-sheet state,
repurposed nothing — the view toggle moved to its own icon) and `design.json`
tradeoff updated. Canvas: http://127.0.0.1:33499/…/d/activities-ui/ (dies with the
session). Handoff recorded 03 as the design's chosen artboard. 01/02 remain as
alternatives per the gentle-design skill.

### L3 — implementation notes

- `AgendaTimelineRow.kt` is NOT deleted: SEMANA mode renders undated rows through
  it (see its file header). Only `AgendaList.kt` (AGENDA mode) switches to cards.
- `StatusTag`/`categoryTagTriple`/`TypeTokens.time` are reused as-is — the card is
  a new composition of existing tokens, no new color values.
- The sheet reuses the repo's `ModalBottomSheet` pattern (detail/create sheets).

### L4 — T1 done (commit c086ac2)

- **RED observed**: `CalendarJumpTest.kt` written first; first run failed to compile with
  `Unresolved reference 'buildDayDots' / 'jumpTargetDate' / 'tabCounts' / 'formatGroupTitle' /
  'dayCellSemanticLabel'` (17 `e:` lines). Then the test file itself missed
  `import kotlinx.datetime.plus` — fixed, still RED.
- **GREEN**: `./gradlew :shared:testDebugUnitTest --tests "...CalendarJumpTest"` →
  `tests="24" skipped="0" failures="0" errors="0"` in
  `shared/build/test-results/testDebugUnitTest/TEST-...CalendarJumpTest.xml`.
- **Full suite**: `./gradlew :shared:testDebugUnitTest --rerun-tasks` → 22 suites / **186
  tests / 0 failures / 0 errors** (162 pre-existing + 24 new).
- **Three test expectations were WRONG, not the implementation** (recorded because the
  fix is the evidence): 2026-05-12 is a **martes**, not a lunes (Jan 1 2026 = jueves →
  May 1 = viernes → May 12 = martes), and the "following group" assertion contradicted its
  own name — the artboard says "más cercano **hacia adelante**", so 2026-05-15 jumps to
  2026-05-20, not to 2026-05-13. The weekday is now pinned with BOTH a Monday and a
  Tuesday case so the mapping cannot drift again.
- **`kotlinx-datetime` 0.6.1 has NO `LocalDate.daysInMonth`**: the month length comes from
  `firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth`.
  `LocalDate.plus(value, DateTimeUnit)` DOES exist (`DateTimeUnit.MONTH` is `MonthBased`,
  a `DateBased`) — verified in the library sources, not assumed.
- **Delegation unavailable**: `subagent_run(gentle-ai-worker)` was rejected 3× with
  "Writer bootstrap requires the original safe project root" (also with an explicit
  `workspace_root`). T1 was therefore done inline — bounded (2 new files), no ceremony.
- **Slice boundary**: T1 = 473 authored lines (142 source + 331 test). The branch-level
  warning (21567 lines / 46 commits since `origin/main`) is the ACCUMULATED branch, not
  this feature; this feature lands in slices of ~400–500 lines, one per task.

### L5 — T2 done (commit 1908266)

- **New**: `ui/ActivityCard.kt` (333 lines) — card with a 4dp category rail (`Spacing.xs`),
  title + status pill, mono time (`TypeTokens.time`) + deadline line, category label and
  an OUTLINED "Confirmar" button. Reuses `TagDot`, `categoryTagTriple`, `statusTagTriple`,
  `ParentalShapes.medium` — **zero new colors, zero new tokens**.
- **Rewritten**: `ui/AgendaList.kt` — day headers (`formatGroupTitle`/`formatDayTitle`,
  with "· hoy" in `primary`) + one card per activity; the list background becomes
  `surfaceVariant` so the `surface` cards read (without it the card is invisible against
  the screen). The "Sin fecha de inicio" group keeps its label.
- **The swipe contract was NOT weakened**: the card declares the `swipe` slot WITHOUT a
  default and `AgendaList.kt` mounts `ActivitySwipeBox` at the render point. The guard
  `SwipeMountingTest` now watches the NEW piece (`ActivityCard`) instead of
  `AgendaTimelineRow` — same intent, three more cases (slot declared, no default, every
  call mounts the gesture). It went 12 → 15 tests.
- **The Confirmar button is a SIBLING of the clickable body** (`R2-T2-8`): the clickable
  region is title + time + deadline; the foot row (category label + button) is outside it.
  A tap next to the button must NOT open the detail — that was the bug the swipe change
  found in `WeekActivityNode`.
- **`OutlinedButton`, not `Surface` + `clickable`**: Material applies
  `minimumInteractiveComponentSize()`, so the real touch target is 48dp even though the
  artboard drew 36dp. S8 demands 48dp, so the artboard's height loses.
- **Evidence**: `:shared:compileDebugKotlinAndroid` → 0 `e:`; `:shared:testDebugUnitTest
  --rerun-tasks` → 22 suites / **189 tests / 0 failures / 0 errors** (186 + 3 guard cases);
  `TEST-...SwipeMountingTest.xml` → `tests="15" failures="0"`.
- **No device attached** (`adb devices` → empty): the visual check of the new card is
  still OUTSTANDING. It needs a human (or an emulator) — it is listed as open proof at
  close, not as a passed check.
- **Slice**: T2 = 381 authored lines (333 new card + 145/−101 in the list + 48 in the
  guard).

### L6 — T3 done (commit 8821e18)

- **Top bar**: `surface` + `HorizontalDivider` de 1dp `outlineVariant` debajo. Acciones:
  **ícono de calendario** (lo abre T4), **ícono de vista** (`CalendarViewWeek` ⇄
  `AutoMirrored.Filled.List`) y "+". El control segmentado de "Vista" y los rótulos "Vista"
  y "Filtrar" se fueron: ≈64dp verticales para las tarjetas.
- **Filtros**: una fila de `FilterChip` con contador (`tabCounts` sobre la lista YA
  cargada), scrolleable en horizontal como la del arteboard.
- **Íconos verificados en el AAR, no de memoria**: `DateRange` y `AutoMirrored.Filled.List`
  están en material-icons-**core**; `CalendarViewWeek` en **extended**. (Los artefactos
  multiplatform de JetBrains son klib: no se pueden grepear, hay que abrir el AAR.)
- **`design/system.md` actualizado**: describía una barra `primaryContainer` y un control
  segmentado que ya no existen. Un doc que describe cromo muerto es un doc que miente.
- **Desviación documentada**: el título del mes de la hoja usa `formatMonthYear`
  ("Mayo De 2026"), la convención de copy de la app, en vez del "mayo 2026" en minúsculas
  del arteboard. Inventar un segundo formato de mes en la misma app es peor que no
  seguir el mock al pie de la letra.
- **Evidencia**: compile 0 `e:`; suite 22/189/0 fallos.

### L7 — T4 done (commit 0b41805)

- **La hoja** (`ui/CalendarJumpSheet.kt`): mes, rejilla de lunes a domingo, un punto POR
  CATEGORÍA por día (máximo 3), días de otro mes atenuados e inertes, "hoy" con disco
  `primary`, flechas de mes y pie con "Hoy" / "Cerrar".
- **El salto**: en modo Agenda la lista se desplaza al encabezado del día elegido (o al
  grupo más cercano hacia adelante, si ese día no tiene actividades); en modo Semana el día
  se SELELECCIONA y la semana se reancla.
- **La selección expresa gana a la regla automática**: al cambiar de semana, la pantalla
  re-resuelve el día seleccionado; la elección de la hoja se aplica DESPUÉS (dos
  `LaunchedEffect` en orden de declaración), así que la última palabra es del usuario.
- **`agendaHeaderIndex` + `AGENDA_HINT_ITEMS`**: el índice del encabezado se cuenta en las
  dos capas (la lista y el salto), y la constante de la pista vive en `CalendarJump.kt` para
  que no se separen. 3 pruebas nuevas fijan el índice ABSOLUTO.
- **Los puntos se memorizan por lista Y por catálogo** (`remember(filteredRows,
  uiState.categories)`): si las actividades llegan antes que las categorías, un `remember`
  con sólo la lista dejaría los puntos en `outline` para siempre.
- **Dos bugs propios, corregidos antes de commitear**: (1) el rótulo accesible de la celda
  pasaba `today = date` y marcaba "hoy" en TODOS los días; ahora recibe el `today` real.
  (2) el círculo de "hoy" medía `statusBadge * 3.5f` (una medida fuera de la escala): se
  redondeó a `Spacing.xl` (24dp).
- **Evidencia**: compile 0 `e:`; suite 22/**192**/0 fallos (189 + 3 del índice del
  encabezado).

### L8 — T5 done (commit 639b6c2)

- **Un fix real**: la celda del calendario NO fusionaba sus descendientes — TalkBack
  anunciaba la celda ("martes 12 de mayo, 3 actividades…") y ADEMÁS el número del día
  suelto, en dos nodos. Ahora `mergeDescendants = true`.
- **Guard de accesibilidad de fuente** (`ActivityCardAccessibilityTest`, 10 casos): este repo
  no tiene infraestructura de Compose UI, así que el contrato se defiende leyendo el
  código, igual que hace `SwipeMountingTest`. Defiende: la tarjeta es UN nodo de
  semántica; el cuerpo tiene UN SOLO `clickable`; el botón "Confirmar" se pinta como
  HERMANO (después del cuerpo clickeable) y es un `OutlinedButton` de Material; la celda
  fusiona y usa `dayCellSemanticLabel`; las celdas de otro mes son inertes; los puntos
  siguen decorativos; y los objetivos táctiles nuevos usan el token de 48dp.
- **Mutación comprobada**: quitar `mergeDescendants` del cuerpo de la tarjeta pone el
  guard en ROJO (1 de 10 falla). Un guard que no puede fallar no es un guard.
- **Dos fallos propios del test, no del código**: las rutas a `core/ui/...` están TRES
  niveles arriba de `…/feature/activities/ui`, no cuatro. Corregido con un helper
  `sharedRoot()`.

### L9 — T6 done (regresión)

- `SwipeMountingTest` — **15 casos, 0 fallos**. El guard de montaje sigue en pie sobre la
  tarjeta: la lista desliza, la semana desliza y ninguna superficie puede perder el gesto
  en silencio.
- Suite completa `--rerun-tasks` — **23 suites / 202 tests / 0 fallos / 0 errores / 0
  omitidos**. El baseline de este repo antes del cambio era 162; las 40 pruebas nuevas son
  24 de `CalendarJumpTest` + 3 de `agendaHeaderIndex` + 3 de montaje de swipe + 10 de
  accesibilidad.
- No hay regresión de comportamiento: `AgendaTimeline` conserva su firma, el caller de
  `ActivitiesScreen.kt` sigue siendo único y `onSwipeAction` sigue sin valor por defecto.

### L10 — T7 done (cierre)

- `:shared:compileDebugKotlinAndroid` → 0 líneas `e:`.
- `:shared:testDebugUnitTest --rerun-tasks` → 23 suites / 202 tests / 0 fallos.
- `:androidApp:assembleDebug` → **BUILD SUCCESSFUL** (exit 0), APK de 18.8 MB en
  `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- **Lo que NO está verificado**: el aspecto visual. `adb devices` no lista ningún
  dispositivo, así que la comparación con el arteboard (tarjetas, hoja de calendario, salto
  de día) NO se hizo en pantalla. Queda como prueba pendiente para una persona (o un
  emulador), no como un control pasado.
- **Risk: item 3** — el cambio toca código que otras superficies ya consumen: la firma de
  `AgendaTimeline` (la usa la pantalla), el cromo de la barra superior y el guard de
  montaje del gesto. La mitigación fue la regresión explícita de T6 y el guard de
  accesibilidad; el control que falta es el visual en dispositivo.
- **Evaluación nativa (RDD)**: `gentle-ai review mode status` → *on* (global). `assess`
  sobre el rango comprometido `6a9120f..HEAD` (18 rutas, 3246 líneas — las 2000+ son
  los HTML del diseño) → riesgo **medium**, `reviewDue: false` ("under_budget"),
  `writerProfile: large` (runtime), plan: `writerSelfVerification: true`,
  `independentVerifier: false`, `structuralReadbackOnly: false`. El plan no pide revisor
  externo ni revisión nativa: el propio cierre con compilación + suite + APK es la
  verificación. **No se abrió ninguna revisión nativa** (`reviewDue` false ⇒ no hay
  `inspect`/`START` que hacer).
- **Fuera de alcance, sin tocar**: el modo SEMANA (pinta igual que antes), la hoja de
  detalle, el alta de actividades, los adjuntos, el realtime y el backend.

### L11 — desviaciones registradas
1. El título del mes de la hoja usa `formatMonthYear` ("Mayo De 2026"), la convención de
   copy de la app, en vez del "mayo 2026" en minúsculas del arteboard.
2. El círculo de "hoy" mide 24dp (`Spacing.xl`), no los 28dp del arteboard: se redondeó al
   valor de la escala en vez de inventar una medida.
3. El botón "Confirmar" es un `OutlinedButton` de Material (objetivo táctil real de 48dp),
   no el botón de 36dp del arteboard.
4. La etiqueta de categoría del pie NO es pulsable (queda fuera del cuerpo clickeable):
   el cuerpo es título + hora + límite, como en la fila que reemplaza. El rótulo
   accesible ya anuncia la categoría.
5. La lista de la agenda pasa a fondo `surfaceVariant` (las tarjetas son `surface`): sin
   ese contraste la tarjeta sería invisible.

### L12 — VERIFICACIÓN EN DISPOSITIVO FÍSICO (la prueba pendiente, CERRADA)

- **Dispositivo**: Xiaomi 24094RAD4G (HyperOS, Android 16, `Europe/Madrid`), conectado
  por **adb wireless** (`adb pair` + `adb connect`, puerto aleatorio). El `adb install`
  bloqueado por MIUI (`INSTALL_FAILED_USER_RESTRICTED` → `persist.security.adbinput`
  vacío) se resolvió instalando el APK a mano desde Descargas; `adb shell input` tampoco
  sirve (MIUI bloquea `INJECT_EVENTS`), así que la navegación la hizo la persona.
- **Backend**: `ENV_FILE=.env.prod` en `:3000` contra Supabase (pooler), `db: ok`, health
  por `http://192.168.0.17:3000/api/health` → 200.
- **Datos de prueba** creados en la DB de Supabase para poder ver la pantalla con
  contenido: usuario `ui.prueba@parental.test`, vínculo "Prueba UI Actividades", hija
  "Martina Prueba" y 4 actividades (29/29/30 oct y 1 nov), todas asignadas a ese usuario
  para que aparezca el botón "Confirmar".
- **Lo que se vio en pantalla y coincide con el arteboard 03**: barra superior en `surface`
  con hairline y los tres íconos (calendario / vista / +); fila de filtros con contadores
  ("Todos 4 · Eventos 3 · Obligaciones 1"); tarjetas con riel de color de categoría,
  título, insignia de estado, hora monoespaciada, línea de límite, etiqueta de categoría
  y botón "Confirmar" con check; encabezado de grupo en minúsculas con "· hoy" en `primary`;
  pista "Deslizá una tarjeta para completar o posponer".
- **La hoja de calendario funciona**: mes "Octubre De 2026", encabezado LU…DO, rejilla de
  lunes a domingo, días de otro mes atenuados, el 29 con disco `primary` (hoy), **puntos
  de categoría bajo el 29 (verde + azul) y bajo el 30 (rojo)** — es decir, exactamente los
  días con actividades — y pie con "Hoy" / "Cerrar".
- **0 errores de app** en logcat (0 `FATAL EXCEPTION`).
- **Dos cosas que se aclararon y NO son defectos de la app**:
  1. Las horas (22:00, 23:30…) son correctas: el teléfono y la máquina están en
     `Europe/Madrid` (CET, +1) y los instantes se pintan en la zona del dispositivo. Mis
     datos de prueba se sembraron con offset `-03:00` y por eso caen a esa hora.
  2. "Límite: 31 oct 08:31" sale del BACKEND: el `CreateActivityDto` no lleva `deadline`
     y el servicio pone un default de `now + 36h` a todas. No es la tarjeta.
- **Un hallazgo real de UX** (no del mock, de verlo en el teléfono): la actividad del **1
  de noviembre no se ve en la rejilla de octubre**, porque los días de otro mes están
  atenuados e inertes (como pedía el arteboard) y no llevan puntos. En pantalla, el
  calendario parece mentir. Pendiente de decisión: ¿puntos atenuados en los días de otro
  mes, y/o hacerlos pulsables?

### L13 — T8 (lote de seguimiento, prompted por el uso en el teléfono)

Lo que pidió quien usa la app después de probar la pantalla en su teléfono, más una
 incoherencia que sólo se ve en el dispositivo:

1. **La pista mentía.** Decía *"Deslizá una tarjeta para completar o posponer"* y esas dos
   acciones **no existen** en el swipe: el servidor ofrece Cancelar / No asistir /
   Eliminar, y para un miembro que no es el creador, SÓLO "No asistir". La pista venía
   del arteboard, que asumía acciones que la app no tiene. Ahora: **"Deslizá una tarjeta
   para ver sus acciones"** (`SWIPE_HINT`, con un guard que falla si vuelve a prometer
   "completar" o "posponer").
2. **El swipe se cierra cuando la asignación está confirmada** (`assignedConfirmed`):
   `availableSwipeActions` devuelve lista vacía y `hasSwipeActions()` se DERIVA de ella
   (que si no, la UI preguntaría por una función y el gesto ofrecería la otra).
3. **"No asistir" se mudó a la hoja de detalle** (`TextButton` en `error`, visible si el
   servidor manda `canDecline` y el estado no es terminal). Sin esto, cerrar el swipe
   después de confirmar le habría quitado a la persona la única forma de decir "no
   puedo asistir": una regresión funcional disfrazada de pulido. El gesto accidental se
   queda en la tarjeta; la decisión va en la hoja.
4. **Los botones del swipe dejaron de ser todos iguales**: `Eliminar` en `errorContainer`,
   `Cancelar` en `secondaryContainer`, `No asistir` en `primaryContainer`, cada uno con su
   icono (`Delete`, `Close`, `PersonOff`). Antes los tres eran `primary` lleno, con la
   acción irreversible pintada como una neutra. CERO colores nuevos: son papeles del
   esquema.
5. **La tarjeta confirmada ahora se ve**: chip "Confirmada" (check + `primaryContainer`).
   Antes el botón "Confirmar" desaparecía al confirmar y la tarjeta quedaba
   indistinguible de una que nunca fue tuya.
- **Pruebas**: 2 casos nuevos en `SwipeActionPolicyTest` (asignación confirmada → sin
  gesto; la pista no promete acciones inexistentes). Suite: 23 / **204** / 0 fallos.
- **En el dispositivo, el botón "Confirmar" ya se había usado**: "Turno con el pediatra"
  pasó a `assisting` / `assignedConfirmed: true` — el flujo de confirmación funciona de
  punta a punta contra el backend local.

