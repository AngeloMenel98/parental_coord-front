# Wave 3 — Verticals (T25–T31, + T18 and T19 pulled forward from wave 2)

Objective: deliver the L wave of the `gastos-and-ux-resolution` program — the two
verticals that close the app: the **verify window** (the activity lifecycle the user
described in L5/L6/L9) and the **Gastos** module (the money). Then the two cross-cutting
slices that depend on them (attachment → expense, realtime) and the design pass.

Branch: `feat/wave-0-quick-wins` (F) · `ft/supabase-cloud` (B).
Repos: **F** = `Documentos/parental_coord-front` (KMP Compose) · **B** =
`Documentos/parental_coordination_back` (NestJS + TypeORM + Postgres + Supabase Storage).
Delivery: one work-unit commit per task; tests and docs travel with the behavior.
Slice boundary: this wave starts at front `cf3c12b` and back `6c04ed3` (the current tips of
both branches) — each task below is its own commit, and the wave closes with the last one.
Test runners: **B** `npx jest` (`pnpm` is not installed in this environment; the scripts in
`package.json` are the source of truth) · **F**
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :shared:testDebugUnitTest`.
Program source: `odd/tasks/gastos-and-ux-resolution.md` — **revision 4** (working copy:
rulings R1–R16, with the user's L9/L10 corrections). The wave table itself only exists in
revision 3: `git show HEAD:odd/tasks/gastos-and-ux-resolution.md`.

Scope note: waves 0 and 1 are closed (T01–T12). Wave 2 is closed except **T15, T16, T35**
(agenda, week swipe, compliance formula) — those stay in wave 2 and are **not** in this
document. **T24** was delivered in wave 2 (pulled forward). **T18** and **T19** are pulled
forward into this wave: their backend prerequisites (T25, T24) land here, and a Gastos
module without a detail view (T19) or without the verify surface (T18) is not a vertical.

**Front test facts (every front writer):** there is **no Compose UI test harness** and
**no Ktor mock engine**. UI contracts are asserted at the **source level**
(`androidUnitTest`, e.g. `ActivityCardAccessibilityTest.kt` reads the composable's source
text) and data seams use an `open` api class + a fake subclass (`commonTest`, e.g.
`NotificationsViewModelTest.kt`) with an `UnconfinedTestDispatcher` and **no**
`Dispatchers.setMain`. The runner is
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :shared:testDebugUnitTest`.
**Front workers run one at a time** — gradle writes the shared `shared/build/`.

---

## Specs

### S1 — T28 · Expenses read API: list + filters + summary/balance (B)

PRD `PRD_expense` §5.1, §5.2, verbatim:

> **FR-1:** Cards update in real time when an expense changes status.
>
> **FR-2:** Tapping a card filters the list by that status. **[Assumption]**
>
> **FR-4:** List sorted by most recent first.
>
> **FR-5:** Tapping a row opens the expense detail view.
>
> **FR-6:** Amounts use a single consistent currency format (see Issues).
>
> **FR-7:** Empty, loading and error states are defined.

Ruling **R1** (one signed balance, two named views) is the source of the summary shape:

> The endpoint returns `{ saldo, porPagar, porCobrar, counts: {pendientes, aprobados, pagados,
> rechazados} }` so no client ever recomputes it.

Contract (this is what the front codes against — it must not drift):

| Route | Response |
|---|---|
| `GET /expenses/bond/:bondId` | `PaginatedExpensesDto { items: ExpenseResponseDto[]; total: number; limit: number; offset: number }` |
| `GET /expenses/bond/:bondId/summary` | `ExpenseSummaryDto { saldo: number; porPagar: number; porCobrar: number; counts: { pendientes: number; aprobados: number; pagados: number; rechazados: number } }` |

Query DTO `ListExpensesQueryDto` (class-validator, `forbidNonWhitelisted` is on globally —
an unknown query param is a 400):

| Param | Type | Default | Notes |
|---|---|---|---|
| `limit` | int 1..100 | 20 | same bounds as `ListNotificationsQueryDto` |
| `offset` | int ≥ 0 | 0 | |
| `status` | `ExpenseStatus` | — | `pending` / `approved` / `rejected` / `paid` |
| `categoryId` | uuid | — | R2: one taxonomy, the existing `category` table |
| `from` | `YYYY-MM-DD` | — | filters `expense_date` (inclusive) |
| `to` | `YYYY-MM-DD` | — | filters `expense_date` (inclusive) |

Rules:

- **Membership first.** A viewer who is not an active member of the bond gets **404**, never
  403 — the module's existing contract (`loadForViewer` → `isActiveBondForMember`). A bond
  that does not exist is also 404. The list never leaks that an expense exists.
- **`deleted_at IS NULL` always** — a cancelled expense is hidden from the list and from
  the summary, exactly as `findAliveById` hides it from the detail.
- **Sort** `created_at DESC, id DESC` (FR-4 "most recent first"; the deterministic
  tie-breaker is the notifications precedent).
- **The balance is computed by the server, from the viewer's point of view.**
  `saldo = Σ (myShare − otherShare)` over the expenses with status `approved` or `paid`
  (R1: pending is not a debt until it is agreed, rejected is dead). The requester is the
  **payer** (`split_ratio_payer`), the counterpart is the **other**
  (`split_ratio_other`): `myShare = amount × ratio[me] / 100`.
  `porPagar = abs(min(saldo, 0))`, `porCobrar = abs(max(saldo, 0))` — unsigned
  magnitudes, never a signed number under a label that hides the sign.
- **The counts** are the number of non-deleted expenses per status for the bond.
- **Money is `numeric(12,2)`** and `pg` returns it as a **STRING** — the mapper converts
  (`Number(...)`), the aggregate must do the same (`SUM(...)::numeric` → `Number`).
  Rounding is Postgres' `numeric`, not JavaScript floats.
- **`ExpenseResponseDto` gains one field: `hasDocument: boolean`** — derived, never stored
  (R6): true when an `expense_attachment` row exists for the expense. The `expense_attachment`
  table already exists (the entity is there; only the API is missing), so the flag is a
  `EXISTS` over that table. This is what lets the list render the PRD's `sin doc.` tag
  (FR-9) from the first day of the Gastos module.
- No new endpoint invents a second money rule: `ExpenseResponseDto` is reused **verbatim**
  for the list items, so the detail and the list cannot disagree.

### S2 — T25 · Verify window, `created` timeout, server flags, 48 h lead time (B)

Rulings **R3** (revision 4 matrix), **R14** (one constant, three anchors), **R16** (48 h
lead time) plus the three rulings the user made when this wave was started.

**User decisions, verbatim (2026-11-02):**

> "once the activity ends (in_progress) goes to verify for 36hs. if the user does not click
> the button 'Finalizar' it goes to overdue, otherwise to done"

> "Also on edit"

> "T8 only from `assigned`; created/verify are terminal"

**The state machine.** The catalog is closed; the new rules take the free slots `T12`,
`T13`, `T14` and a new id for the `created` timeout. `T4` (`assigned → created`, the
"Desasignar" transition) is **removed** — R3 revision 4: assignment is irreversible.

| Rule | From → To | Actor | Effect | Event |
|---|---|---|---|---|
| `T12` | `in_progress` → `verify` | system | set `verifyAt`, **set `deadline` = now + 36 h**, cancel the END timer | `activity.verify` |
| `T13` | `verify` → `done` | bond member | set `completedAt`, clear `deadline`, clear `verifyAt` | `activity.done` |
| `T14` | `verify` → `overdue` | system | clear `deadline` | `activity.verify_expired` |
| `T16` (new id) | `created` → `overdue` | system | clear `deadline` | `activity.created_overdue` |
| `T11` (KEPT) | `in_progress` → `done` | bond member | set `completedAt` | `activity.done` |
| `T7` (existing) | `assigned` → `overdue` | system | — | `activity.overdue` |
| `T8` (existing, **REDUCED**) | `overdue` → `not_assisting` | assignee | see the origin rule | `activity.not_assisting` |
| ~~`T8`~~ (**REMOVED**) | ~~`overdue` → `assisting`~~ | — | — | la aceptación tardía se quitó (2026-11-03) |

**T11 stays.** The `END` timer no longer closes the activity (it moves it to `verify`), but
the **member** keeps the ability to close it early — before its scheduled end. That
capability exists today (`T11`, actors `system` + `bond_member`) and nothing in the user's
ruling removes it: L5 adds the verification window, it does not take the early close away.
`T11` therefore survives with the **system actor dropped** (the system no longer closes
from `in_progress`; the timer goes to `verify`) and the **bond member kept**. Without this,
an activity that finished at 10:00 with a 15:00 end time could not be closed until 15:00 —
a silent loss of a capability that nobody asked to lose.

**The three anchors (R14 + the user's first decision).** `RESPONSE_TIMEOUT_HOURS` (36) is
the only constant; `deadline` means "the expiry of the window the activity is waiting in":

| Window | State | Anchor | Deadline |
|---|---|---|---|
| Assignment | `created` | `created_at` | `created_at + 36 h` (armed at create) |
| Acceptance | `assigned` | the moment it entered `assigned` | `now + 36 h` |
| Verification | `verify` | **the moment it entered `verify`** | `now + 36 h` |

The verification window **opens on arrival** (the user's decision, verbatim above): an
activity whose end passed more than 36 h ago still gets its full 36 h to be completed.
This is the `max(end, now) + 36h` variant, one line away from the literal anchor in R14.

**Timers.**

- `END` is retargeted: source `in_progress` now goes to **`verify`**, not `done`.
- A new timer kind **`VERIFY_TIMEOUT`** (source `verify` → `overdue`) is armed when the
  activity enters `verify`, with the 36 h delay.
- `RESPONSE_TIMEOUT` is also armed at **create** (source `created` → `overdue`).
- `timerOpsForTransition` gains `CREATED` (arm the response timeout) and `VERIFY` (arm the
  verify timeout) and cancels the right timers on the way out.

**The sweep.** `findDueForTransition` and `dueTargetFor` must both know about `created`
and `verify` — today they only whitelist `assigned`, `assisting` and `in_progress`, so a
lost timer would never self-heal. Five swept states after this change.

**The `overdue` origin rule (the user's third decision).** The late response (`T8`) exists
**only** when the activity reached `overdue` from `assigned`. An activity that timed out
from `created` (nobody assigned it) or from `verify` (nobody completed it) is terminal in
`overdue`: no "Confirmar", no "No asiste" — only what the matrix allows.

**⚠ THE LATE ACCEPTANCE IS GONE (the user's decision, 2026-11-03).** `T8` used to have TWO
halves: `overdue → assisting` (the late acceptance) and `overdue → not_assisting` (the
late "No asiste"). The first one was **removed**: a `created`→`assigned`→`overdue`
activity kept its "Confirmar" button alive after the 36 h window had closed, and the
assignee could still confirm it late. It happened with "A largo plazo": its window
expired on Oct 31, the button stayed, and on Nov 3 it was confirmed late and went back to
`assisting`. It looked like a live activity with an action button, and it was not.
Now `canConfirm` is true **only** in `assigned`, and the rule `overdue → assisting` is out
of the catalog — so the endpoint answers **409**, not just "the button disappeared"
(hiding the button while the door stays open would make the flag a lie).
The late **"No asiste"** stays, and it is what keeps the state from becoming a dead end:
the assignee hands the activity back (`not_assisting`), and from there anyone can
reassign it (`T9`). Without that exit, an expired activity could only be cancelled.

The origin is derived from data, not from a new status:

| Origin | Signal | `T8` allowed |
|---|---|---|
| from `created` | `assigned_to IS NULL` (nobody was ever assigned) | no |
| from `verify` | `verify_at IS NOT NULL` (it entered the verification window) | no |
| from `assigned` | `assigned_to IS NOT NULL AND verify_at IS NULL` | yes |

`verify_at` does not exist yet — it is a **new nullable timestamp column** on `activity`,
set when the activity enters `verify` and cleared when it leaves. This is the one schema
change in this task and it is deliberate: the alternative (inferring the origin from
`deadline` arithmetic) is a guess. Migration + entity + a structural migration spec, the
same shape as `1792819200000-AddExpenseModelFields`; the timestamp must be **after**
`1793491200000` (the last migration in the repo) — `1793577600000-AddActivityVerifyAt`.

> **Correction (found while implementing).** The first draft of this spec read
> "`assigned_at IS NULL`". The `activity` table has **no `assigned_at` column** — it has
> `assigned_to` (the uuid) and, separately, `confirmed_at` / `declined_at` /
> `completed_at` / `cancelled_at`. The signal is therefore `assigned_to IS NULL`, which
> classifies the three origins exactly the same way: nobody was ever assigned vs. the
> activity went through the verification window vs. it timed out waiting for an
> acceptance.

**The server flags.** The matrix's decision table arrives as data, in the activity DTOs
(the front stops deriving permissions client-side — today it renders "Finalizar" in every
status except `done` and derives "Confirmar" from a rule that ignores the status; both
produce 409s):

| Flag | True in | Route that backs it |
|---|---|---|
| `canConfirm`, `canDecline` | `assigned`, and `overdue` **when the origin is `assigned`** (the assignee only) | `POST /activities/:id/confirm`, `POST /activities/:id/decline` |
| `canComplete` | `in_progress`, `verify` (any bond member) | `POST /activities/:id/complete` |
| `canDelete`, `canCancel` | unchanged (creator && !isPast) | `DELETE /activities/:id`, `POST /activities/:id/cancel` |
| `canAssign` | `created` (any bond member) | **none** — T3 has no route (see below) |
| `canReassign` | `not_assigning` (any bond member) | **none** — T9 has no route (see below) |
| `canUnassign` | **gone** — T4 is removed, the flag never existed and must not appear | — |

> **Why `canAssign` and `canReassign` are NOT exposed in this wave.** The matrix says the
> buttons exist, but the engine has **no route** for either transition: the activities
> controller exposes create / confirm / decline / delete / cancel / list / detail /
> compliance / complete — and **no assign, no reassign**. A flag whose action has no
> endpoint is a promise the server cannot keep (the front would render a button that
> 404s), and `assigned_to` is only ever written at create. Those two flags (and the two
> routes they need) belong with **T21**, the status-aware action matrix, which is where
> the matrix becomes a button. The flags that DO have a route ship now.

`canComplete` covers `in_progress` **and** `verify` (R3: "D-7 queda superado"). The existing
`POST /activities/:id/complete` route must accept **both** sources: the transition catalog
is what decides, so `verify → done` (T13) travels through the same endpoint as
`in_progress → done` (T11) — no new route, and the front's `completeActivity` call does
not change.

**The 48 h lead time (R16 + the user's second decision).**

- `MIN_LEAD_TIME_HOURS = 48`, its own constant, separate from `RESPONSE_TIMEOUT_HOURS`.
- **On create**: `scheduledStart < now + 48 h` → **400** with the message
  "La actividad debe programarse con al menos 48 horas de anticipación".
- **On edit** (the user's decision, verbatim "Also on edit"): the same validation runs
  whenever an edit changes `scheduledStart`.
  > **There is no activity edit endpoint today.** The controller has create / confirm /
  > decline / delete / cancel / list / detail / compliance / complete — and no PATCH.
  > So the "also on edit" half of the ruling is **armed but parked**: the validation
  > lives in ONE reusable guard that the create path calls now, and the future edit
  > route calls the same guard. The alternative — adding an edit endpoint to the wave —
  > would be API surface nobody asked for.
- Existing activities are not touched — the rule validates writes, not history.

**The notification copy.** The closed event catalogue grows by three: `activity.verify`
("Actividad por verificar"), `activity.verify_expired` ("La verificación venció") and
`activity.created_overdue` ("Actividad sin asignar"). The catalogue has 8 rules and its
spec asserts exactly 8; `event-catalog.spec.ts` asserts exactly 9 produced event types.
Both assertions are updated on purpose. Removing T4 leaves `activity.unassigned` with no
producer: the event constant stays (it is a public webhook contract that a subscriber may
filter on) and it moves to the "catalogued but not emitted" bucket the catalogue already
has — it is **not** deleted.

**The specs that are deliberately reversed** (this is the only place in the program where a
previously asserted ruling is undone): `activity-transition.map.spec.ts` (the
`[CREATED, OVERDUE]` / `[VERIFY, DONE]` / `[VERIFY, CANCELLED]` invalid pairs, the
`timerOpsForTransition(VERIFY) == []` assertion, the "terminal states have no outgoing
rule" loop, and the `T4` assertion), `activity-timers.processor.spec.ts`
(`targetStatusFor(END) = DONE`), `activity-timers.sweep.spec.ts`
(`dueTargetFor(in_progress, past end) = DONE`), plus
`activities.repository.spec.ts` (the due predicate), `activities.service.spec.ts`
(the system-only rule and the `T9` deadline) and `event-catalog.spec.ts`.

### S3 — T18 · Verify window in the front: the "Finalizar" button, the countdown, the
48 h picker (F)

Ruling R3 (the matrix) and R16's front half; the backend is S2.

- The activity card and the detail sheet show the `verify` state with its own label
  ("Por verificar" — the label and the colour already exist: `statusSemanticLabel`
  (`TimelineFormatters.kt:160-171`) maps `VERIFY` → "Por verificar", and `StatusTokens`
  (`Color.kt:159-212`) already carries `Status.Verify` for both themes).
- The **"Finalizar"** button appears in `in_progress` **and** `verify` — and nowhere else.
  The label stays **"Finalizar"** (the user's own wording; the matrix calls it
  "Completado", the app says "Finalizar" today). Today the button is gated only by
  `if (actividad.status != "done")` in `ui/detail/ActividadDetailSheet.kt:379-397`, so it
  is painted in every status and 409s on click — the gate becomes the server's
  `canComplete`.
- The buttons come from the server flags (`canComplete`, `canConfirm`, `canDecline`,
  `canAssign`, `canReassign`) — the front stops deriving them. The client rule
  `canConfirmAssignment(assignedTo, assignedConfirmed, currentUserId)`
  (`ActivitiesModels.kt:116`) is **removed**: it ignores the status and it is the reason
  the "Confirmar" affordance appears where the server would refuse.
- The detail sheet shows how long is left in the window: the activity already carries
  `deadline` and the card already renders `DeadlineLabel` ("Límite: " / "Límite vencido: ")
  from `formatDeadline(deadline, zone)` (`TimelineFormatters.kt:119`) — the countdown
  reuses that value, it does not invent a second clock.
- The create form's date/time picker **disables anything before now + 48 h** and says why.
  The rule lands in `validateCreateForm` (`CreateForm.kt:52`) as a new
  `CreateFormError(CreateField.DATE, …)` — the same function `submitCreate`
  (`ActivitiesViewModel.kt:627-629`) re-runs, so the UI and the viewmodel cannot diverge —
  and the picker's floor moves from `NoPastDatesFrom` (`CreateDateConversion.kt:67`) to
  `NoPastDatesFrom(now + 48 h)`. The message is the server's, verbatim:
  "La actividad debe programarse con al menos 48 horas de anticipación". The server
  validates anyway; this is UX, not security.
- There is no "Desasignar" button anywhere: T4 is gone.

### S4 — T29 · Gastos front: summary cards + list + filters (F)

PRD `PRD_expense` §5.1, §5.2; ruling R1 (the labels and the signs).

- The Gastos screen opens from the bottom navigation — the nav item already exists
  (`BottomNavBar.kt:19` — `Gastos = BottomNavItem("expenses", "Gastos", AccountBalanceWallet)`)
  but it is inert: `App.kt:181-186` `onNavigateBottom` only accepts `"home"` and
  `"activities"`. Add the `"expenses"` branch before the `else` that renders `HomeScreen`.
- **The DI is a stub — the wiring is manual.** Koin is never started: `appModule`
  (`core/di/AppModule.kt`) is referenced nowhere, and every `feature/<x>/di/<X>Module.kt`
  is an empty `TODO`. The real composition is in `App.kt`: `remember { ApiClient.xxxApi() }`
  + `remember { XxxViewModel(api, token) }` + `DisposableEffect { onDispose { vm.destroy() } }`.
  So the Gastos module needs `ApiClient.expensesApi()` and the `App.kt` wiring — not a
  Koin module that would never be loaded.
- **Three cards** at the top: **"Por pagar"** (amber, what I owe), **"Por cobrar"**
  (green, what I am owed — R1 adds this one), **"Pendientes"** (count awaiting action).
  The numbers come from `GET /expenses/bond/:bondId/summary`; the front never recomputes
  money.
- The list shows, per PRD §5.2: title, status badge (`Pendiente` / `Aprobado` /
  `Pagado` / `Rechazado`), category tag, the `sin doc.` warning tag, creator + relative
  date ("María G. · 1d ago"), and the amount right-aligned.
- Tapping a card filters the list by that status (FR-2); tapping a row opens the detail
  (FR-5, T19).
- Money uses the existing `formatMoney()` (`core/util/MoneyFormatter.kt:24`, `internal` →
  visible inside the `shared` module, which is where the Gastos screens live) — FR-6.
- The creator + date line uses `formatRelativeTime()` (`core/util/RelativeTime.kt:28`) and
  `formatShortDate()` (`:56`), the same helpers the notifications list uses.
- The category tag uses `CategoryResolver.build()` (`core/ui/components/CategoryResolver.kt:30`),
  which maps the `category` table to name + colour + emoji.
- Empty, loading and error states are defined (FR-7).

### S5 — T19 · Gastos front: the expense detail + approve / reject / pay (F)

PRD `PRD_expense` §5.4, FR-10, FR-11; the backend is T24 (delivered in wave 2).

- The detail shows the expense and the **policy decision that the server already sends**
  (`canEdit`, `canCancel`, `canApprove`, `canReject`, `canMarkPaid`, `isTerminal`): the
  buttons are rendered from data, and the client never reimplements FR-11.
- **Aprobar** → `POST /expenses/:id/approve`; **Rechazar** → `POST /expenses/:id/reject`
  (optional reason); **Marcar pagado** → `POST /expenses/:id/pay`.
- The requester can **edit** (`PATCH /expenses/:id`) and **cancel**
  (`DELETE /expenses/:id`) only while the status is `Pendiente` — and the server enforces
  it; the front only reflects it.

### S6 — T27 · Gastos front: "+ Solicitar" form + document upload + the `sin doc.` warning (F)

PRD `PRD_expense` §5.3, verbatim:

> | Field | Required | Notes |
> |---|---|---|
> | Title | Yes | Short description |
> | Amount | Yes | Numeric, currency-aware |
> | Category | Yes | Single select |
> | Date of expense | Yes | Defaults to today |
> | Supporting document | No, but encouraged | Photo or PDF; missing → `sin doc.` flag |
> | Notes | No | |
> | Split | Yes | Default 50/50 **[Assumption]** |
>
> **FR-9:** If no document is attached, show a non-blocking warning and set the `sin doc.` flag.

- The form posts to `POST /expenses/:bondId` (T24) — `concept`, `amount`, `categoryId`,
  `expenseDate`, `splitRatioPayer` (default 50), `description`.
- The categories come from the existing `category` table (R2 — one taxonomy), read through
  the categories API the front already uses.
- The `sin doc.` warning is **non-blocking**: the expense is submitted without a document
  and the list shows the tag. The flag is derived ("no `expense_attachment` row"), never
  stored as a boolean (R6).
- **The document upload has no backend surface yet.** `expense_attachment` exists only as
  a table (`expense-attachment.entity.ts`) — the presign/confirm cycle in
  `ActivitiesApi.kt:309,330` is scoped to `/activities/:id/attachments/*`. So **T27 ships
  the form and the non-blocking warning** (which is exactly what FR-9 asks for) and the
  actual upload lands with **T30**, which brings the expense attachment endpoints.
  Inventing an upload endpoint in the front is not an option; the front must not pretend
  the upload exists.

### S7 — T30 · Attachment → expense flow ("¿Es un gasto?") (F+B)

Ruling R6, PRD `prd_improvement` D-13…D-18.

- One storage object, two rows: the existing `act_attachment` and a new
  `expense_attachment` pointing at the same Supabase key. The bytes are never copied.
- `expense.activity_id` records the origin (the column already exists — T14).
- The activity keeps its attachment when the user cancels the conversion.
- The front asks "¿Es un gasto?" after picking a file; if yes, the expense form opens
  pre-filled.

**The back half (the API the front needs).** The activity attachment cycle
(`/activities/:id/attachments/presign|confirm`) is activity-scoped; the expense needs its
own, and the conversion needs a way to point an expense at an object that already exists:

| Route | Contract |
|---|---|
| `POST /expenses/:id/attachments/presign` | the same response shape as the activity cycle (`{ uploadUrl, path, method, headers, expiresIn }`), with the storage key namespaced per expense |
| `POST /expenses/:id/attachments/confirm` | creates the `expense_attachment` row for the uploaded object |
| `POST /expenses/:id/attachments/convert` | body `{ activityAttachmentId }`: creates the `expense_attachment` row pointing at the **same** storage object as the activity's attachment and sets `expense.activity_id` — verifying that the activity and the expense belong to the **same bond** (T24 deliberately refused `activityId` on create for exactly this reason) |

Only the requester may attach; any bond member may read. The `sin doc.` flag stays
**derived** — the presence of an `expense_attachment` row — and is never a stored boolean
(R6).

### S8 — T26 · Realtime: the `expense.changed` frame + the live refresh (F+B)

Ruling R9: the socket, not polling.

- The gateway emits an `expense.changed` frame when an expense changes status, on the
  same connection that already carries `notification.created`.
- The frame does not exist yet, on either side: `RealtimeModels.kt` declares only
  `activity.changed` and `notification.created`, and the envelope requires
  `v == REALTIME_PROTOCOL_VERSION (1)`. The front half adds `TYPE_EXPENSE_CHANGED`, an
  `ExpenseChangedMessage`, a third coalescer and `expenseSignals` on
  `RealtimeConnectionManager` (`:92-98` is the routing `when`).
- The front refetches the summary and the list on that frame. Polling stays only for the
  notification badge.

### S9 — T31 · Design-system pass: spacing, type scale, radius, skeletons, empty states (F)

PRD `prd_improvement` H-2, H-3, PRD_expense FR-7.

- Touches every screen. The tokens are frozen in `core/ui/theme/` — reuse them, do not
  add new ones.
- This is the one task in the wave with **no deterministic test** (risk item 6): it needs
  manual verification on a small screen.

---

## Tasks

| ID | Task | Side | Specs | Route | Status | Commit |
|---|---|---|---|---|---|---|
| T28 | Expenses read API: list + filters + summary/balance | B | S1 | delegated (writer, `src/expenses/**`) | done | B `b894206` |
| T25 | Verify window, `created` timeout, server flags, 48 h lead time | B | S2 | delegated in two work units: **T25a** state machine + `verify_at` + the origin guard, then **T25b** the server flags + the 48 h lead time + the notification copy | done | B `45bc9b8` + `5a61c16` + `4a43bc3` |
| T18 | Verify window in the front + the 48 h picker | F | S3 | inline (no subagent runtime in this session) | done | F `8a43020` |
| T29 | Gastos front: summary cards + list + filters | F | S4 | delegated together with T19 (one front writer: the read surface) | done | F `0e30788` |
| T19 | Gastos front: the expense detail + approve / reject / pay | F | S5 | delegated together with T29 | done | F `0e30788` |
| T27 | Gastos front: "+ Solicitar" form + `sin doc.` warning | F | S6 | delegated (front writer) | done | F `8cdc674` |
| T30 | Attachment → expense flow (+ the expense attachment API) | F+B | S7 | B delegated (writer, `src/expenses/**`); F inline | done (the front was explicitly left unreviewed, L21) | B `1cefa36`; F `9e57774` |
| T26 | Realtime: `expense.changed` + the live refresh | F+B | S8 | B delegated (writer, `src/realtime/**` + relay); F inline | done | B `31ccdc3`; F `e78d853` |
| T31 | Design-system pass | F | S9 | inline (read-only map delegated) | in progress (small-screen check pending) | — |

Delivery order: **T28 → T25 → {T29, T19, T27} → T18 → T30 → T26 → T31**. The front Gastos
read surface (T29+T19) codes against the S1 contract and lands in parallel with the backend
work; T27 and T18 land after it; T31 closes the wave.

**One front writer at a time** — gradle writes the shared `shared/build/`. The two backend
writers run in parallel with disjoint surfaces, but they SHARE the jest suite and the
type check: each was told to report the foreign failures separately instead of chasing
them.

---

## Open items

1. ~~**Prod (Supabase) is three migrations behind**~~ — DROPPED by the user on
   2026-11-03 ("forget about supabase for now"). `1792819200000` (the expense model
   fields), `1793491200000` (the outbox aggregate type) and `1793577600000`
   (`activity.verify_at`) were never applied to prod; dev and test are at 16. It stays
   as a record: applying a migration to prod is a remote write and needs the user's
   word, and that word was explicitly withheld for now. NOT a task of this wave.
2. **The back worktree carries pre-existing prettier noise** in 9 files (43 insertions,
   36 deletions — formatting only, not authored by this wave). It stays out of every
   commit except where a file this wave must touch (T25 touches
   `src/notifications/fan-out/activity-notification.map.ts`); there the noise is
   inseparable from the change and the commit says so.
3. **The wave-2 leftovers (T15, T16, T35) are still open** and are not in this wave.
4. **The front repo has three uncommitted documents** (`gastos-and-ux-resolution.md`
   revision 4, `wave-1-ux-quick-wins.md`, `wave-2-medium.md`) from the previous session.
   The `wave-1` working copy is a REWRITE that dropped its `## Specs`, `## Tasks` and
   `## Log`; committing it as-is would delete the wave-1 history, so it stays
   uncommitted until that is decided (L25).
5. **The branch is far past a reviewable slice.** `feat/wave-0-quick-wins` carries
   32 574 authored changed lines since `origin/main` across 87 commits (waves 0–3).
   Each wave commits in ~400-line units, but the BRANCH as a whole is not a
   reviewable unit: a PR would have to be cut per wave (the wave-3 slice boundary
   is the front `cf3c12b`), and pushing is the user's decision.

---

## Log

- **L1 (user request, verbatim, 2026-11-02):** "Look in engram and odd/task/ to start wave
  3 in Documentos/parental_coord-front"

- **L2 (user rulings, verbatim, 2026-11-02) — the three points the resolution left open.**
  All three are now closed and are written into S2:

  > "once the activity ends (in_progress) goes to verify for 36hs. if the user does not
  > click the button 'Finalizar' it goes to overdue, otherwise to done"

  > "Also on edit"

  > "T8 only from `assigned`; created/verify are terminal"

  Consequences recorded: the verification window **opens on arrival** into `verify` (the
  `max(end, now) + 36h` variant of R14's literal anchor); the 48 h lead time validates
  **edits** too, not only creation; and late acceptance (`T8`) is restricted to the
  `assigned`-origin overdue, which is why the `overdue` origin has to be derivable from
  stored data.

- **L3 (recovery, 2026-11-02).** The state was recovered **before** any write: the Engram
  mirror of the wave-2 document (`odd/wave-2-medium/tasks`), the T22/T24 observations
  (`odd/wave-2-medium/t22`, `odd/wave-2-medium/t24`) and the wave-2 document itself.
  Wave 2 closed with **T24** delivered as B `7975695` + `0aa8742` and **T23** as B
  `6c04ed3` (the outbox became polymorphic in `2c35d58` and `0c3f866`). The wave-2
  document's own "next" note names the same two bottlenecks this wave starts from: **T28**
  (the expenses read API) and **T25** (the verify window).

- **L4 (reconciliation between revision 3 and revision 4, 2026-11-02).** The working copy
  of the resolution is **revision 4** (it carries the user's L9/L10 corrections: one
  timeout constant with **three** anchors and the 48 h lead time) but it **dropped the
  task table** — the wave table only exists in revision 3 (`git show HEAD:...`). The two
  revisions disagree on the wave-3 content, so the table was rebuilt from revision 3 and
  **every line was re-read against revision 4's rulings**. The differences that matter
  for this wave: R14 gains the `created` anchor (a third window) and the user's decision
  moves the verification anchor to the arrival in `verify`; R3 revision 4 **removes T4**
  (assignment is irreversible) and adds `canAssign` / `canReassign` / `canComplete`; R16
  is new and adds the 48 h lead time.

- **L5 (the deviation, stated before it is implemented, 2026-11-02).** R14 claims "no new
  column" for the deadline generalization — that claim survives. But the user's third
  ruling ("T8 only from `assigned`; created/verify are terminal") cannot be expressed
  without knowing **how** the activity reached `overdue`, and the stored data does not
  say: `assigned_at` is set on both the `assigned`-origin and the `verify`-origin paths.
  S2 therefore adds **one** nullable timestamp, `activity.verify_at`, set when the
  activity enters `verify`. The alternative — inferring the origin from the arithmetic of
  `deadline` — would be a guess about a permission.

- **L6 (the front has no "Desasignar", 2026-11-02).** The removal of T4 has no front
  impact: `SwipeAction` is `DELETE` / `CANCEL` / `DECLINE` and there is no unassign
  affordance anywhere in `shared/src`.

- **L7 (the wave boundaries, 2026-11-02).** T18 and T19 are pulled forward from wave 2
  (their prerequisites land in this wave). T15, T16 and T35 stay in wave 2. T31 stays in
  wave 3 but is the last task of the wave: it touches every screen and has no
  deterministic test.

- **L8 (the front evidence, 2026-11-02) — three facts that changed the plan.**
  1. **The Koin DI is a stub.** `appModule` is referenced nowhere and every
     `feature/<x>/di/<X>Module.kt` is an empty `TODO` — the real composition is manual in
     `App.kt`. The Gastos module therefore needs `ApiClient.expensesApi()` + the `App.kt`
     wiring, not a Koin module that would never be loaded. The `Gastos` bottom-nav item
     already exists (`BottomNavBar.kt:19`) and is inert because `onNavigateBottom`
     (`App.kt:181-186`) only accepts `"home"` and `"activities"`.
  2. **The expense document upload has no backend surface.** `expense_attachment` is a
     table, not an API: the presign/confirm cycle is activity-scoped
     (`/activities/:id/attachments/*`). So **T27 ships the form + the non-blocking `sin
     doc.` warning** (which is what FR-9 actually asks for) and the upload lands with
     **T30**. The front must not invent an upload endpoint.
  3. **`expense.changed` does not exist on either side** — `RealtimeModels.kt` declares
     only `activity.changed` and `notification.created`. T26 defines the frame on both
     ends.

- **L9 (the routing of the wave, 2026-11-02).** Three writers run in parallel: **T28**
  (`src/expenses/**`), **T25a** (`src/activities/**`, `src/queues/activity-timers/**`,
  `src/database/**`, `src/webhooks/events/**`) and the **front read surface**
  (`feature/expenses/**`, `App.kt`, `ApiClient.kt`) — the two backend surfaces are
  disjoint, the front is a different repo. **T29 and T19 ship together** in one front
  writer: FR-5 ("tapping a row opens the expense detail view") means a list without its
  detail is not a deliverable, and the two share one module, one api client and one
  viewmodel. **T25 splits in two**: the state machine (T25a) and the flags + lead time +
  copy (T25b), because the state machine is the highest-risk unit in the program and
  deserves its own review and its own commit.
  The two backend writers share the jest suite and the type check, so both were told to
  label the foreign failures instead of chasing them — a shared suite is a shared
  failure surface.

- **L10 (the review of the delegated work, 2026-11-02) — three findings, all fixed
  before the commits.**
  1. **T28 had two N+1s.** The page asked for the documents of every row with one
     `existsBy` per row (up to 100 round trips per request) and resolved the bond members
     once per row as well. Both are now ONE query per page: the attachments come with an
     `IN` and a `Set`, the member list is resolved once and the counterpart of each row
     comes from it. The worker's own spec had pinned the N+1 (it asserted one `existsBy`
     per expense id), so that spec was rewritten to pin the batched query instead.
  2. **T25a had replaced `T11` instead of adding `T13` next to it.** The effect was that
     the member could no longer close an activity **before** its scheduled end — a
     capability that exists today and that nobody asked to lose. `T11` is back with the
     **system actor dropped** (the `END` timer now goes to `verify`) and the **bond member
     kept**; the spec that asserted "409 from in_progress" now asserts the early close.
     The rule table in S2 was corrected to match.
  3. **My own spec was wrong about a column.** S2 read "`assigned_at IS NULL`" to detect
     the `created` origin, but `activity` has no `assigned_at` column — the signal is
     `assigned_to IS NULL`, which classifies the three origins identically. The spec now
     says so, with the correction noted.

  What the review confirmed as correct: the balance formula (`split_ratio_payer` is the
  **requester's** share, per the create DTO's own wording, so the sign of `saldo` follows
  the viewer's role), the `numeric`-as-STRING conversion in both the mapper and the
  aggregate, the `404`-for-a-non-member contract, `deleted_at IS NULL` everywhere, the
  static-route ordering, the three-anchor deadline, the `VERIFY_TIMEOUT` timer, the
  sweep's five swept states, and `activity.unassigned` surviving as a catalogued-but-not-
  emitted constant instead of being deleted from the public webhook contract.

- **L11 (T25a verified against real Postgres, 2026-11-02).** The assess for the range
  `6c04ed3..45bc9b8` came back **high** risk with an independent verifier added (the
  escalation names item 1: a migration plus a new state), and `reviewDue: false`
  (under budget), so no native review ceremony was started for this candidate.

  The migration `1793577600000-AddActivityVerifyAt` was executed on **test** (15 → 16),
  **reverted** (the column was gone: `information_schema` says 0) and **re-applied**
  (16 again) — so it is idempotent and its `down()` is clean. The data probe inside a
  rolled-back transaction on test: 15 activities, all `assisting`, **0 with `verify_at`**
  (the no-backfill claim holds), 0 unassigned, 0 with a deadline (consistent with T5
  clearing it). Then it was applied to **dev** (16 migrations, the column present) — the
  repository now reads and writes `verify_at`, so a dev database without the column
  would break every activity read, exactly as T13 did in wave 2. **Prod (Supabase) was
  not touched**: it is three migrations behind and a prod migration is a remote write
  that needs the user's word.

  Full back suite after the fix: **80 suites / 946 tests green**, type check clean,
  eslint clean on every file this work unit owns (the five errors in
  `activity-attachments.service.ts` are **pre-existing at HEAD** — they are part of the
  tree noise and were left alone).

- **L12 (the front read surface — delivered and reviewed, 2026-11-02).** `F 0e30788`
  (the Gastos cards + list + detail) and `F 64f32f3` (the build fix below). The review
  found **four defects**, all fixed before the commit:

  1. **CRITICAL — the edit would have failed EVERY time.** The PATCH serialized the
     request data class, and the Ktor client has `explicitNulls` on: every field the
     user did not touch travelled as `null`, which the backend reads as *"set to null"*
     (its `buildPatch` checks `!== undefined`), against NOT NULL columns. On top of that
     the body carried `splitRatioOther`, a property the backend does not have — it
     derives the counterpart — and `forbidNonWhitelisted` turns that into a **400**. The
     body is now assembled field by field (`toPatchBody()`, a pure function with its own
     test), and the model no longer exposes `splitRatioOther`.
     The delivered spec **pinned the bug**: it asserted the exact broken body — and its
     name (*"omite campos nulos del cuerpo"*) said the opposite of what it asserted. A
     test whose name contradicts its expectation is worse than no test.
  2. **The cards were wrong.** "Por cobrar" was mapped to the `paid` status: tapping
     *"what I am owed"* showed the **already settled** expenses. And the PRD's third card
     ("Pagados") was missing. Now there are **four** cards — the PRD's set plus R1's
     "Por cobrar" — and the two **balance** cards do not filter at all (a balance is not
     a status; its sign already says who owes whom), while the two **count** cards filter
     by theirs (FR-2).
  3. **The edit dialog asked for a raw category UUID** ("ID de categoría"). It now shows
     the category name; the picker arrives with the create form (T27) and is reused
     there. Asking someone to type a UUID is not an interface.
  4. **No source-level UI test** — my brief gave the worker no `androidUnitTest`
     surface, so the house pattern for UI contracts was missing. There is one now
     (`ExpensesScreenContractTest`), pinning the four cards, the flags-driven buttons,
     the `sin doc.` tag and the shape of the PATCH body.

  **The front's metadata build was broken before this wave**: `:shared:build` failed at
  HEAD (from the wave-1 top bar work) — `toSortedMap()` is not in this common stdlib and
  the `when` over the expect enum `DayOfWeek` needed an `else`. The Android target
  compiled, which is why it went unnoticed. Fixed in its own commit (`F 64f32f3`).

  **The independent verification of the backend came back 8/8 PASS** (timer coherence,
  the sweep's five states, the overdue origin classification — checked with an exhaustive
  traversal of the rule graph, the deadline, the money, the read API's authorization, the
  migration). Five advisories, no blockers: three are now fixed (`ROUND(saldo, 2)` — the
  balance was publishing 20 decimals; `CANCEL_EFFECTS` now clears `verify_at`; the
  sweep's docstring names five states) and two are recorded instead of "fixed": no
  deadline backfill for pre-existing `created` rows (**0** of them in dev and test) and
  the stale deadline T7/T8 leave behind (pre-existing behaviour, pinned by the spec).

- **L13 (the copy for the three new events, 2026-11-02) — `B 4a43bc3`, and a style commit
  before it.** T25a started emitting `activity.verify`, `activity.verify_expired` and
  `activity.created_overdue`, and the notification catalogue had **no rule** for them:
  `resolveNotificationRule` returned null and the events were silently skipped. Each one
  now has its own Spanish title (≤ 40 chars, the compile-time check covers them) and a
  body that differs from it (N-2). The recipients follow the meaning of each event: the
  verify notice goes to the assignee and the creator (either can confirm), the expiry
  notice also reaches the coordinator (like the generic overdue), and the "never assigned"
  notice goes to the creator and the coordinator — there is no assignee to tell.

  Before that, `B 2348933` normalized the formatter on
  `activity-notification.map.ts`: the file had carried uncommitted prettier drift since
  before the wave, and the committed version **failed lint**. Normalizing it in its own
  commit is what keeps this change to the same file reviewable on its own. The other
  eight files with the same drift were left alone: they are not this wave's business.

- **L14 (T25b — the flags and the 48 h rule, 2026-11-02) — `B 5a61c16`, with two fixes
  to the delivered work.**
  - **`canConfirm` must NOT carry `isPast`.** The delivered version derived it from the
    same `canRespond` as `canDecline`, which included `!isPast`. That would have hidden
    the "Confirmar" button **exactly in the late acceptance** — a late acceptance is
    precisely the case of an activity that already ended, and the activity is usually
    past by then. The app already allowed confirming a past activity (its client rule
    never looked at the clock), so this would also have been a silent loss. `canConfirm`
    is now free of `isPast`; `canDecline` keeps it (saying "I won't go" about an
    activity that already happened means nothing, and that was the flag's existing rule).
  - **The origin rule was written TWICE** — once in the policy, once in the service's
    transition guard. It is now ONE exported function, `overdueOrigin()`, used by both.
    Two copies of a rule that decides who may accept late is a guaranteed drift.

  What the review confirmed: the policy is still PURE (a structural subject, no
  TypeORM), the list query selects every column (so `status` and `verify_at` reach the
  policy in the list as well as in the detail), the 48 h boundary is **inclusive**
  (exactly 48 h is allowed, which is what "al menos 48 horas" means), the guard lives in
  ONE reusable place for the future edit route, and the seed script creates no
  activities (so the new rule does not break it).

  One thing the status restriction fixed for free: the old `canDecline` had no status
  check, so the app offered "No asiste" on an activity that was already running — a
  transition the map does not have (409).

- **L15 (T27 — the "+ Solicitar" form, 2026-11-02) — `F 8cdc674`.** The PRD §5.3 form
  (concepto, monto, categoría, fecha, notas, división 50/50), validated before it lets
  the user submit, posting to `POST /expenses/:bondId` with the body assembled **field by
  field** (the same lesson as the PATCH: the Ktor client encodes nulls and the backend
  rejects unknown properties, so the body carries exactly the fields the form filled —
  never `splitRatioOther`, never `childId`/`activityId`).
  The category is picked **by name** from the existing `category` table (R2) through the
  categories API the activities feature already uses.
  **There is no upload field, and that is deliberate**: the expense attachment API does
  not exist yet (T30), so the form says the document is missing and the expense goes in
  without it — which is exactly what FR-9 asks (a non-blocking warning). The "sin doc."
  tag in the list is the server's derived flag, never a stored boolean.
  The review found no defect in the delivery; the only change was the success snackbar
  ("Gasto creado"), which the activities form already had and this one did not.

- **L16 (T30, back half, 2026-11-02) — `B 1cefa36`.** The three expense attachment routes
  the "¿Es un gasto?" flow needs: `presign` (key namespaced
  `expenses/{bondId}/{expenseId}/{uuid}.{ext}`, same response shape as the activity
  cycle), `confirm` (creates the row for an object already in storage, rejecting a path
  outside the expense prefix) and `convert` (R6: a **second** row pointing at the SAME
  storage object as an activity attachment, setting `expense.activity_id`).
  Authorization follows the module contract and S7 says it explicitly: a **non-member**
  gets 404 — the expense does not exist for them — and a member who is **not the
  requester** gets 403. `hasDocument` stays derived from the row, never stored.
  Two structural decisions worth keeping: the service does **not** import
  `@nestjs/typeorm`/`typeorm` (the lookup lives in
  `ExpenseAttachmentsRepository.findActivityAttachment`), so the "third copy of the
  authorization rule" never appears; and `convert` refuses across bonds — an activity
  and an expense from different bonds share no storage namespace to point at.
  19 tests across service and repository, including the requester rule and the
  cross-bond refusal.

- **L17 (T26, back half, 2026-11-02) — `B 31ccdc3`.** The expense outbox row now becomes
  an `expense.changed` frame on the **same** socket that already carries
  `activity.changed` and `notification.created`. `eventId` comes from the row id,
  `expenseId` from `aggregateId`, `bondId`/`status`/`actor` from the payload and
  `occurredAt` from `createdAt` — the expense payload carries no timestamp, so a new one
  is not invented. Payload extras are denylisted against the reserved keys so a producer
  cannot clobber the contract by adding a field with the same name.
  `parseRealtimeFrame` accepts the new type and **drops** a frame without `bondId` or
  `expenseId`, like the other two; the gateway delivers it to the bond audience through
  the existing path; the relay publishes it **only** for `aggregateType === 'expense'`,
  so an expense event still never turns into an activity frame.
  Tests cover the mapping, the parse guards, the publisher (one frame, disabled no-op,
  swallowed Redis failure), the gateway audience and the relay split.

- **L18 (T18 — the verify window in the front + the 48 h picker, 2026-11-02) —
  `F 8a43020`.** Front suite green: **371 tests, 0 failures** (22 in
  `CreateFormValidationTest`, 12 in `CreateDateConversionTest`, 7 new in
  `VerifyWindowContractTest`).
  - **"Finalizar" now reads `canComplete`.** The gate was `status != "done"`, so the
    button was painted in `created`, `assigned`, `overdue`… and the POST answered 409.
    The label stays **"Finalizar"** (the user's wording; the matrix calls it
    "Completado") and `isCompleting` stays a ViewModel flag, not a sheet state: the sheet
    does not unmount on completion, so a `Loading(id)` would wipe the context the user
    just acted in.
  - **"Confirmar" now reads `canConfirm` from the DTO** and the local rule is gone:
    `canConfirmAssignment(assignedTo, assignedConfirmed, currentUserId)` and its two
    extensions were deleted from `ActivitiesModels.kt`, with a "do not reintroduce" note
    in their place — it was a second copy of the policy, and the copy that ignored the
    status is the one that 409s. That also removed the now-dead `currentUserId`
    pass-through from `ActividadDetailSheet`, `DetailFooter`, `AgendaTimelineRow`,
    `AgendaTimeline`, `WeekTimeline`, `ActividadesListScreen` and `TimelineView`.
    `ActivitiesViewModel.currentUserId` **stays** (its constructor is shared by `App.kt`
    and ~7 tests; deleting it is churn with no behavioural value once nothing reads it
    there — T21 can take it out with the assignment routes).
  - **The verify window is a card in the detail body**, titled "Por verificar", shown
    when `status == "verify"`, built from `parseInstant(deadline)` + `formatDeadline` +
    `formatTime` — the SAME server instant the card renders, never a second clock: if
    the backend does not send `deadline`, the card is not painted instead of inventing a
    due date. The existing "Límite evidencia" pair in `DetailPairsTest` was left alone
    (it is pinned and it answers a different question).
  - **The 48 h rule lands in `validateCreateForm(form, now = Clock.System.now())`**, the
    same function `submitCreate` re-runs, so UI and ViewModel cannot diverge. The message
    is the server's, verbatim, in ONE constant (`MIN_LEAD_TIME_MESSAGE`), pinned by a
    test that compares it against the literal. The boundary is **inclusive** (47 h
    invalid, 48 h and 49 h valid) — the backend compares with `<`, and a `<=` here would
    make the front more restrictive than the server.
  - **The picker floor is day-grained on purpose.** `earliestStartUtcMillis()` returns the
    UTC midnight of the day that completes the 48 h, because the Material3 `DatePicker`
    only picks dates and cannot express "from 23:30 on the boundary day". So the boundary
    day is fully selectable and its early hours are rejected by `validateCreateForm` with
    the same message. Trade-off, consciously chosen: over-restricting (blocking the whole
    day) costs the user a full day of margin; under-restricting costs one inline error,
    which is the lesser evil. `todayUtcMillis()` stays — the **expense** date picker uses
    it, where a past date is valid.
  - **`canAssign` / `canReassign` / `canDecline` are NOT consumed yet.** The back exposes
    all three, but the front has no assign/reassign route (that is T21) and "no asiste"
    is still a swipe whose actions are derived client-side; wiring a flag with no surface
    would be dead data. Recorded here so the deferral is a decision, not an oversight.
  - **Two existing contracts were kept on purpose**, both now pinned by tests: the
    `assignedConfirmed` + "Asignación confirmada" branch in the detail sheet
    (`ActivityCardConfirmedChipTest` requires it — the detail is where the per-person
    datum still means something, R4), and the `sin fin programado` /
    `fin anterior al inicio` validations, untouched by the new date rule.

- **L19 (T26 front half — `expense.changed` live refresh, 2026-11-02) — `F e78d853`.**
  The front subscribes to the shared expense signal and reloads the expense list and
  summary from REST. The user reports the front suite green: 378 tests, 0 failures.
  T26 is complete alongside back commit `31ccdc3`.

- **L20 (T30 front half — attachment-to-expense flow, 2026-11-02).**
  The activity detail asks **"¿Es un gasto?"** after file selection. Both answers save
  the activity attachment; **Sí** opens Gastos with the filename (without extension) as
  the concept, and a successful expense create calls `/expenses/:id/attachments/convert`
  with that activity attachment ID. Closing the expense form clears the pending
  conversion and leaves the activity attachment intact. A conversion failure leaves the
  created expense and reports that the document remains on the activity; it does not
  attempt a destructive rollback. `ExpensesApi` also exposes the S7 presign/confirm
  routes using the existing attachment request/response contracts. Verification:
  `./gradlew :shared:allTests --no-daemon --console=plain` passed. F changes are in the
  working tree and are not committed. RDD is enabled: assessment returned `high` with
  `unassessable` because the new model file is untracked; selectorless STATUS then failed
  twice in `pre_native` while opening its read-only target (`read-only file system`). No
  review authority was created or approved; native review remains unavailable.

- **L21 (user decision, 2026-11-02) — "deja T30 sin revision. continua con T31".**
  The user explicitly leaves T30 unreviewed; no T30 review is to be run. Continue with T31.

- **L22 (user decision, 2026-11-02) — "yes".** The user approved a one-time
  `gpt-6-luna` substitution for the required T31 read-only explorer because the
  repository's assigned `gpt-6.1-luna` is unavailable in this runtime.

- **L23 (T31 progress, 2026-11-02):** Added shared empty/loading state composables and
  adopted the frozen spacing/shape/type tokens across the home, expenses, notifications,
  and admin surfaces. `:shared:testDebugUnitTest` and `:androidApp:assembleDebug` pass.
  Installed/launched the debug APK on the authorized USB device and confirmed the Home
  top bar, error state, and bottom navigation render within the small screen. The app
  reports its backend is unreachable; navigating to Activities via ADB failed because
  the device denied input injection, so other-screen visual checks remain pending. The
  Engram mirror remains pending because this runtime has no registered session identity.


- **L24 (T30 front half — delivered, 2026-11-03) — `F 9e57774`.**
  The work that L20 left in the working tree is now a commit: 13 files, 287
  insertions / 26 deletions. The commit is **T30 only** — T31's hunks were split
  out of `ExpensesScreen.kt` first (it carried both the attachment draft prefill
  and the `Spacing`/skeleton tokens), and the T31 changes to `HomeScreen`,
  `AdminScreen` and `NotificationsScreen` were stashed aside, so the commit was
  verified on its own tree: `:shared:allTests` → **82 suites / 766 tests, 0
  failures**, `:shared:testDebugUnitTest` included. T31 was restored afterwards
  from a patch taken before the split, and the working tree holds it again.

  Two facts the commit confirmed:
  - `bottomRoute = "expenses"` resolves: `App.kt:314` handles the Gastos branch
    and `BottomNavBar.kt:19` already carries the item. The nav was not inert
    after all.
  - `ExpensesApi` now exposes the three routes of B `1cefa36` with the SAME
    request/response contracts the activity attachments use; the create sheet
    dropped its own picker, so there is exactly ONE upload path (the contract
    test that used to assert "no attachment in the form" now asserts "no second
    picker", with the reason in the message).

- **L25 (the comment cleanup, 2026-11-03) — the criterion, and what it spared.**
  The user asked to delete the unnecessary comments. The criterion applied: **a
  comment earns its place when it records something the code cannot say — a
  decision, a contract, a constraint, a warning, or the reason for an
  otherwise-opaque construct. It does not when it only says what the next line
  does.** What that deleted:
  - `ExpensesApi.kt` — the in-method copy of the "the PATCH body is assembled
    field by field" rule, which the `toPatchBody` KDoc already states. Two
    copies of one rule is a guaranteed drift (the same defect T25b had with
    `overdueOrigin()`).
  - `ExpensesScreen.kt` — the in-method copy of the four-cards rule, already in
    the screen's KDoc.
  - `ExpensesViewModel.kt` — five KDocs that restated the function name
    (`openDetail`, `reject`, `pay`, `openCreateSheet`, `closeCreateSheet`).
  - `App.kt` — four narration lines ("Restore persisted session on startup",
    "Login in progress", "Mark login as pending", "Fetch bonds and set the
    first one as active").

  What it **kept**, on purpose: every decision record — the REQ mappings, the
  server-flags-as-authority rules, the `explicitNulls`/`forbidNonWhitelisted`
  contract, the "do not reintroduce" notes (`canConfirmAssignment`, the A3
  declined reason, `visibleDeclinedReason`), the thread-safety and race notes,
  and the comments that explain an otherwise-empty `catch` or `else`. Those are
  not noise: this codebase was bitten by exactly those regressions, and the
  review log is full of "the rule was written twice" and "the copy that ignored
  the status is the one that 409s". A blanket purge would have deleted the
  institutional memory, not the noise.

- **L26 (tres hallazgos del usuario sobre el entorno real, 2026-11-03) — el worker
  estaba muerto desde T30-back.** El usuario reportó tres cosas que no se ven en los
  tests: las tarjetas del Home no aparecían, el detalle de un gasto pintaba UUIDs, y
  una actividad del lunes 2 que ya había pasado no estaba en `overdue`.

  **1 · EL WORKER NO ARRANCABA — y por eso las actividades no avanzaban.** El contenedor
  `parental-coordination-worker` estaba en un crash-loop: `Nest can't resolve dependencies
  of the ExpensesRepository … "ExpenseAttachmentEntityRepository" at index [1] is
  available in the NotificationsFanOutModule context`. T30-back le dio a
  `ExpensesRepository` un SEGUNDO repositorio (el de los adjuntos), y
  `NotificationsFanOutModule` —que provee ese repository A MANO, re-declarando la lista de
  `forFeature` en vez de importar `ExpensesModule` para no arrastrar el controller al
  worker— quedó con la lista vieja. Tres consecuencias, todas del mismo crash:
  **SIN timers** (nadie armaba el `END`, ni el `RESPONSE_TIMEOUT`, ni el
  `VERIFY_TIMEOUT`), **SIN barrido** (el reparador de 15 minutos) y **SIN fan-out**
  (nadie recibía una sola notificación). La actividad del lunes 2 quedó en `assisting`
  para siempre: `assisting → in_progress → verify` nunca corrieron. **Es el mismo modo de
  fallo que el `StorageModule` del `WorkerModule`:** un módulo que provee a mano tiene
  que replicar la lista de `forFeature` del módulo al que reemplaza.
  Arreglado en B `8b8b7de` (una entidad en la lista, más la nota que lo explica). Con el
  worker arriba, el barrido immediately alcanzó a los dos rezagados: la del lunes 2 pasó
  a `verify` con la ventana armada (`verify_at` = ahora, `deadline` = ahora + 36 h) y la
  otra, de `assigned`, a `overdue`. El fan-out drenó el outbox y creó las tres
  notificaciones que faltaban ("Actividad vencida", "en curso", "por verificar").
  ⚠ La actividad del lunes 2 **no va a `overdue` todavía, y no es un bug**: la ventana de
  verificación son 36 h desde la LLEGADA a `verify` (la ruling del usuario: la ventana se
  abre al llegar), así que vence el 4 de noviembre a las 22:43 — o antes si alguien la
  completa.

  **2 · EL DETALLE DE UN GASTO PINTABA UUIDs.** "Solicitado por", "Contraparte" y
  "Aprobado por" mostraban el id crudo. La categoría ya venía con su nombre
  (`categoryName`), así que los miembros siguen la misma regla: **el servidor resuelve el
  nombre para mostrar** (B `d7698b6` + F `c90507e`). Los nombres salen de UNA consulta
  por página (`findMemberNames`), como los ids de los miembros — por gasto sería un N+1.
  Esa consulta NO filtra por `left_at`: el nombre es un dato de la persona, no del
  vínculo (alguien que se fue deja de ser contraparte, no deja de tener nombre). Verificado
  contra la API viva: `requestedByName: 'Maria Ollama'`, `counterpartName: 'Juan Perez'`.

  **3 · LAS TARJETAS DEL HOME NO ESTABAN EN EL TELÉFONO — porque el APK era viejo.** El
  `lastUpdateTime` del paquete instalado era del **2026-11-02 15:18**, anterior a T20 (que
  se entregó el 3 de noviembre): el dispositivo corría el APK de la ola 3, sin las tres
  tarjetas. Las APIs que las alimentan funcionan (`/expenses/bond/:id/summary` → 200 con
  `{saldo: 320, …}`; `/activities/bond/:id` → 200 con las flags), así que no era un
  problema de datos sino de binario. El APK nuevo está compilado (`:androidApp:assembleDebug`
  en verde, 422 tests en el front) y **pendiente de instalar**: el teléfono se desconectó
  del USB antes de poder hacerlo. Hasta que se instale, el Home del dispositivo no muestra
  "Asignadas", "Vencidas" ni "Saldo gastos", y la hoja de detalle no ofrece "Asignar" ni
  "Reasignar".

- **L27 (la aceptación tardía se quita — 2026-11-03).** El usuario reportó que la
  actividad "A largo plazo" —que ya había vencido— **seguía mostrando el botón
  "Confirmar"**, y que al confirmarla volvió a `assisting`. Los datos lo confirman:
  `confirmed_at = 2026-11-03 11:07`, con la ventana vencida el 31 de octubre.

  **La causa no era un botón mal pintado: era la RULING.** `canConfirm` era TRUE también en
  `overdue` (la aceptación tardía, `T8`), y el botón se pintaba desde ese flag. El
  `T8` tenía DOS mitades —`overdue → assisting` (aceptar tarde) y `overdue → not_assisting`
  ("no asiste" tarde)— y la primera se va.

  **Qué cambió (B, un commit).** El catálogo pierde la regla `overdue → assisting`: el
  endpoint ahora responde **409** como cualquier transición inexistente, y no sólo
  "desaparece el botón" (esconderlo con la puerta abierta haría que el flag mintiera —
  la misma lección del "do not reintroduce"). Y `canConfirm` queda **sólo en `assigned`**.

  **El "No asiste" tardío SE QUEDA, y es lo que evita el callejón sin salida.** El que no
  aceptó en la ventana devuelve la actividad (`not_assisting`), y de ahí la reasigna
  cualquiera (T9). Sin esa salida, una actividad vencida sólo se podría cancelar — que es
  justo lo que el usuario resolvió con la tarea T21.

  **Por qué no era una pérdida de capacidad.** La aceptación tardía se puso en su día
  para que una actividad vencida no quedara sin salida. Con `T9` (reasignar) ya hay
  salida, y mejor: el que la rechaza la devuelve y otro la toma con una ventana nueva.

  **Los tests.** El mapa (`overdue → assisting` ahora es `undefined`, y su prueba lo
  dice), la política (en `overdue` el asignado NO puede confirmar pero SÍ puede decir que
  no asiste) y el servicio (el `confirm` tardío es un 409 y no escribe). **84 suites /
  1047 tests**, `tsc`/eslint/prettier limpios en los archivos tocados. El front NO cambia:
  el botón ya se pintaba desde `canConfirm` del DTO, así que sigue al flag solo.
