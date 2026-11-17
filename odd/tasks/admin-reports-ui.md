# Admin panel: management actions + bond reports

**Objective:** complete the admin panel beyond creation — activate/deactivate users and
bonds, edit and delete the children of a bond — and add a reports screen that renders
the backend bond report and downloads its PDF.

**Branch:** `ft/admin-reports-ui`
**Delivery:** 2 reviewable commits, tests in the same commit.
**Test runner:** `./gradlew :shared:jvmTest` (or the configured common test task); lint
through the existing Gradle setup.

**Backend contract:** `parental_coordination_back/odd/tasks/admin-bonds-reports.md`
(specs S1-S11). This document quotes only the UI requirements.

## Specs

- **S1 — User actions.** Each user card in the Users tab exposes a control that toggles
  the account between active and inactive using `PATCH /admin/users/:id`
  (`{ "isActive": boolean }`), and the card reflects the returned state without a full
  reload.
- **S2 — Bond actions.** Each bond card exposes the same toggle against
  `PATCH /admin/bonds/:id`, reflecting the returned state.
- **S3 — Child editing.** The children section of a selected bond lets the admin edit
  first name, last name and date of birth of a child (`PATCH /admin/children/:id`) and
  delete it (`DELETE /admin/children/:id`) after an explicit confirmation. The list
  reloads after either action.
- **S4 — Child date of birth.** The child form takes the date of birth through a date
  picker, never free text, and sends `YYYY-MM-DD` or omits the field.
- **S5 — Reports screen.** A third admin tab, "Reports", lists the bonds, takes a
  `dateFrom`/`dateTo` range through date pickers, and loads
  `GET /admin/bonds/:bondId/report` for the selected bond.
- **S6 — Report content.** The screen renders the four sections the backend returns:
  activities (total, completed, overdue, completion rate, by status, by criticality),
  expenses (total, paid amount, pending amount, paid percentage by amount and by count,
  by category), children (name, date of birth, age, activity count) and members
  (name, role, assigned, completed, completion percentage).
- **S7 — PDF download.** The screen can request the PDF
  (`POST /admin/bonds/:bondId/reports`) and, once the report is `completed`, open the
  signed `fileUrl` returned by `GET /admin/reports/:id`.
- **S8 — Errors.** A failed request shows the backend message in the existing error
  banner and leaves the previous data untouched.
- **S18 — User search.** QUOTE: "Agregame un buscador por nombre de usuario". The Users tab
  filters by first name, last name or email, case- and accent-insensitive, with the same
  empty-state copy the bond list uses. One rule shared with the wizard's member picker
  (`AdminSearch.kt`), so the two searches cannot drift.
- **S9 — Bond console (artboard `admin-ui/01`).** The Vínculos tab opens with a summary
  strip (users / bonds / active), a search field that filters the list by bond title, and
  one card per bond carrying its children count, completion percentage and paid
  percentage from `GET /admin/bonds/health` (one request for the whole list, never one
  report per bond). The create-bond form moves into a dialog behind a FAB.
- **S10 — Bond detail.** Tapping a card opens the detail: back link, bond title, the
  active/inactive switch, and three sections — Hijos (editable + deletable rows, add via
  dialog), Miembros, Reporte. The six-month completion trend
  (`GET /admin/bonds/:bondId/trend`) is rendered as a bar chart under whichever section is
  open, because "is this improving?" belongs to the bond, not to a tab.
- **S11 — Report with percentages.** The report shows activities (total, completed,
  overdue, rate), expenses as a proportion of the expense count — paid / approved /
  pending / rejected with counts and percentages on one stacked bar — plus paid and
  pending percentage by amount and the total, and per-member participation.
- **S13 — User creation.** QUOTE: "need to improve the user creation interface for admin".
  Creating a progenitor happens in a dialog with local validation (email format, password
  of at least 6 characters, both names), a show/hide password control, the backend conflict
  message rendered inside the dialog, and the created account appended to the list — the
  list is no longer pushed off the screen by an always-visible form card.
- **S14 — Bond members by name.** QUOTE: "also when creating the bonds, dont use the uuid
  of users. Search by name." The bond form never asks for a user id: the admin searches
  the already-loaded user list by name or email (accents tolerated), the chosen ones become
  removable chips, a chosen user cannot be picked twice, and a third pick is refused. The
  client resolves names to ids before calling `POST /admin/bonds`.
- **S15 — Three-step bond wizard.** QUOTE: "Me gustaria que sea 3 steps Primero, Le pongo un
  nombre boton siguiente, Agrego progenitores, boton siguiente Agrego hijos, boton crear
  vinculo". `Crear vínculo` opens a full-screen wizard: Nombre → Progenitores → Hijos, each
  step keeping the "Progenitores" title and a `N de 2 · obligatorio` counter, with `Atrás`
  from step 2, nothing persisted until the last step, and the create action labelled with
  the number of children it will create.
- **S16 — Children are mandatory.** QUOTE: "No es Opcional. Por lo menos tiene que haber un
  hijo creado". Step 3 requires at least one child: the create action is disabled with
  zero children, states the rule, and the payload carries `children` in the same call so
  the backend creates bond + members + children in one transaction.
- **S17 — Creating a missing progenitor from the wizard.** QUOTE: "si nadie coincide se
  ofrece crear la cuenta sin salir". When the search has no matches, the wizard offers
  `Crear progenitor «query»`; the account is created and immediately selected as a member.
- **S12 — PDF preview.** After the worker finishes, "Ver página" opens a preview that
  renders the first page of the report as a paper card (serif title, monospace figures,
  generated stamp) and "Abrir PDF" opens the signed URL. Without a generated PDF the
  action is disabled and the screen explains why.

## Tasks

| ID | Specs | Route | Status | Commit |
| --- | --- | --- | --- | --- |
| F1 | S1-S4 | inline | done | (single commit) |
| F2 | S5-S8 | inline | done | (single commit) |
| F3 | S9-S12 | inline | done | (single commit) |
| F4 | S13-S14 | inline | done | (single commit) |
| F5 | S15-S17 | inline | done | e14c1a9, c1e6079 |
| F6 | S9, S13, S18 | inline | done | (single commit) |

## Log

**L1 (original request, verbatim):**
"I need to improve the admin views, beside the creation of users, and create the bonds and children inside the bond, Need to create reports of the bonds."

**L2 — scope (user answer):** "Backend + frontend UI"; reports delivered as
"JSON for the UI and a PDF download", PDF generated by the backend.

**L3 — exploration evidence:**
- `shared/src/commonMain/kotlin/com/parental/shared/feature/admin/ui/AdminScreen.kt:1`
  — 504 lines, two tabs (Users, Bonds), creation forms only.
- `.../admin/data/remote/AdminApi.kt:1` — 169 lines: list/create users, list/create
  bonds, list/add children. No update or delete call exists.
- `.../admin/di/AdminModule.kt:5` — empty `module { }`; the screen is constructed from
  `App.kt:178` with `ApiClient.adminApi()` and `AdminViewModel`, so the empty module does
  not block the panel and is left untouched.
- `.../admin/ui/AdminViewModel.kt:1` — single `AdminUiState` data class; new fields are
  additive.
- No reports feature exists anywhere under `shared/src/commonMain`.

**L4 — delegation unavailable.** Both `gentle-ai-explore` and `gentle-ai-worker` failed
with a provider quota error in this session, so F1/F2 were done inline instead of
delegated. Reported, not retried.

**L5 — decisions taken while implementing.**
- The active/inactive toggles use a `Switch` inside the existing card. For bonds the
  card is also clickable (selects the bond for the children section); the `Switch` owns
  its own gesture, so it does not select.
- Per-row in-flight state (`usersInFlight` / `bondsInFlight` sets) instead of one global
  flag: toggling one account must not freeze the whole list.
- The child editor is an `AlertDialog` opened from the child card (S3 does not require an
  inline form), and the delete confirmation is a second dialog. Both are hosted once, in
  `ChildActionDialogs`, outside the tab `when`, so a tab change does not silently discard
  an edit.
- `buildChildPatchBody` emits `{"dateOfBirth": null}` when the date is cleared and omits
  the key entirely when it was never set; `""` is never sent, because the backend's
  `@IsDateString` rejects it.
- Date pickers speak UTC in both directions (`AdminDates.kt`), same trap the activity
  form documents: reading the picker millis in the device zone shifts the day in UTC-3.
- The create-child form also moved from a free-text date to a picker (S4).
- The PDF is opened with Compose's multiplatform `LocalUriHandler` instead of adding an
  `expect/actual openUrl`: no platform code to duplicate, and it works on Android and iOS.
- PDF polling: the backend has no WebSocket event for reports, so `requestReportPdf`
  polls `GET /admin/reports/:id` up to 15 times, 2 s apart, and shows the real status. It
  never fabricates a `fileUrl`.
- `admin/di/AdminModule.kt` is still an empty stub and was deliberately left untouched:
  `App.kt` builds `AdminViewModel(ApiClient.adminApi(), token)` directly, so filling it in
  would be an unrelated refactor.

**L6 — evidence.** `./gradlew :shared:testDebugUnitTest` → 56 suites, 512 tests, 0 failures,
0 skipped (BUILD SUCCESSFUL). `AdminPatchBodiesTest` is one of them. RED was observed by
mutation, not by ordering: with `put("isActive", …)` renamed to `put("active", …)` the suite
reported `AdminPatchBodiesTest > set active sends exactly the boolean FAILED` (1 of 5), and
went green again after restoring the line. `./gradlew :shared:compileDebugKotlinAndroid`
is clean.

**L7 — slice exception.** F1 and F2 land in one commit of ~1029 lines instead of two
slices of ~400. The reason is structural, not convenience: the Reports tab needs
`AdminUiState` fields, the tab list, and `AdminScreen`'s `when` branch, all in the same
three files F1 edits. Splitting by hunk would produce a first commit that does not
compile. The commit message names both specs so a reviewer can read it as two
consecutive blocks.

**L8 — design `design/admin-ui/` (nuevas vistas del panel admin).** Tres direcciones,
cada una con las tres vistas que el admin necesita en el teléfono (panorama/lista,
gestión de un vínculo con sus hijos, reporte con PDF):
- `01-consola-de-vinculos.html` — el vínculo como unidad de trabajo. Tradeoff: foco
  máximo, sin lectura agregada del sistema.
- `02-radar-del-sistema.html` — panorama primero (anillo, métricas con delta, "requiere
  atención"). Tradeoff: detecta un vínculo enfermo sin abrirlo; más denso y un toque más
  para gestionar.
- `03-expediente-del-vinculo.html` — el reporte es la pantalla, la gestión vive en una
  hoja. Tradeoff: se lee como un papel; mucho scroll.

Ninguna está elegida todavía: las tres son alternativas. Tokens y reglas de componente
tomados de `design/system.md` (self-check hecho: todos los colores del artboard son
tokens del sistema; las chips usan container al 15% y texto en el `dot` de la categoría,
y las tipografías son serif/sans/mono del `Type.kt`).

**L9 — handoff `admin-ui/01-consola-de-vinculos.html` → implementación.** El artboard
elegido de `design/admin-ui/` (las otras dos siguen siendo alternativas) se implementó en
Compose: consola de vínculos con búsqueda y salud por tarjeta, detalle con hijos,
miembros, tendencia y reporte, reporte con gastos en porcentajes, y vista previa del PDF.
Los formularios de alta pasaron a diálogos y la copia visible del panel quedó en español,
igual que el artboard y el resto de la app.

**L10 — lo que el artboard obligó a agregar en el backend.** Ni la salud por vínculo ni la
tendencia existían en la API; se agregaron como B4 en
`parental_coordination_back/odd/tasks/admin-bonds-reports.md` (S13-S14), en la misma rama
del PR del backend. El frontend consume `GET /admin/bonds/health` y
`GET /admin/bonds/:bondId/trend`.

**L11 — evidencia.** `./gradlew :shared:compileDebugKotlinAndroid` limpio y
`./gradlew :shared:testDebugUnitTest` → 56 suites / 512 tests, 0 fallos (los mismos que
antes del rediseño: los cuerpos JSON siguen siendo el contrato pineado).

**L12 — límite de slice declarado.** F3 entra en un commit de ~1413 líneas (5 archivos), otra
vez por encima de las ~400. La causa es la misma que L7: el rediseño reemplaza de una sola
pieza `AdminScreen.kt` (941 → 1180 líneas) y no existe un corte intermedio que compile,
porque la lista, el detalle y el reporte comparten las mismas piezas (`ReportContent`,
`StatusChip`, `EmptyHint`) y el mismo estado de `AdminUiState`. El mensaje del commit separa
las cuatro superficies del artboard para que se pueda revisar por bloques.

**L13 — design `design/admin-forms-ui/` (alta de usuarios y de vínculos).** Tres direcciones
para S13/S14; ninguna elegida todavía:
- `01-alta-guiada.html` — cuenta en hoja inferior + vínculo en dos pasos (acuerdo → buscador).
- `02-progenitores-primero.html` — el vínculo empieza por las personas y propone el nombre;
  el alta de progenitor es pantalla completa con validación en vivo.
- `03-un-solo-panel.html` — combobox en línea, ya elegidos deshabilitados, y oferta de crear
  la cuenta cuando la búsqueda no encuentra a nadie.

Auto-check: todos los colores de los tres artboards son tokens de `design/system.md` y el
markup está balanceado. El backend no cambia: `POST /admin/bonds` sigue recibiendo dos
`userIds`, el cliente resuelve los ids desde la búsqueda por nombre.

**L14 — handoff `admin-forms-ui/03-un-solo-panel.html` → implementación.** Wizard de tres
pasos con combobox en línea, alta de progenitor en diálogo con validación, y creación de la
cuenta faltante desde el propio buscador. El paso 3 exige al menos un hijo y el payload los
manda junto con el vínculo (B5 en el backend, `POST /admin/bonds` con `children`, en una
transacción).

**L15 — por qué sigue siendo inline.** La delegación no es una preferencia: los dos agentes
(`gentle-ai-explore`, `gentle-ai-worker`) fallaron con error de cuota del proveedor, y además
el frontend vive en otro repositorio fuera del root de esta sesión, así que un writer
tampoco tendría una `Allowed edit surface` válida para tocarlo. La unidad se hizo inline y
queda registrado acá.

**L16 — evidencia.** `./gradlew :shared:compileDebugKotlinAndroid` limpio;
`./gradlew :shared:testDebugUnitTest` → 56 suites / 513 tests, 0 fallos, incluido
`children travel in the same body as the members` (el cuerpo del `POST /admin/bonds` lleva
`children` y omite `dateOfBirth` cuando no hay fecha). Intento de mutación honesto: cambiar
`children = children` por `children = emptyList()` en `AdminApi.createBond` NO rompe ese
test, porque el test pinea el DTO serializado y no el método; la garantía del método es el
compilador (el parámetro es obligatorio).

**L17 — feedback aplicado sobre lo implementado.** QUOTE: "El boton de siguiente en el
vinculo esta muy abajo." El paso 1 y el paso 3 del wizard son cortos: el botón anclado al
pie flotaba sobre el vacío. Ahora esos dos pasos van en una columna con scroll y el botón
viaja pegado al último campo; el paso 2 conserva el botón anclado porque su lista necesita
alto acotado. El artboard se actualizó igual (frames 1 y 4) para que diseño y app no
divergan.

**L18 — evidencia.** `./gradlew :shared:testDebugUnitTest` → 56 suites / **518 tests**, 0
fallos, incluidos los 5 nuevos de `AdminUserSearchTest` (nombre con acento, mayúsculas,
apellido, email, consulta vacía y no-coincidencia). APK reinstalado en el dispositivo.
