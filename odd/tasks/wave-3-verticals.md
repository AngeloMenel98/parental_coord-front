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
| `T8` (existing) | `overdue` → `assisting` / `not_assisting` | assignee | see the origin rule | `activity.assisting` / `activity.not_assisting` |

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

**The `overdue` origin rule (the user's third decision).** Late acceptance (`T8`) exists
**only** when the activity reached `overdue` from `assigned`. An activity that timed out
from `created` (nobody assigned it) or from `verify` (nobody completed it) is terminal in
`overdue`: no "Confirmar", no "No asiste" — only what the matrix allows.

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

| Flag | True in |
|---|---|
| `canAssign` | `created` (any bond member) |
| `canConfirm`, `canDecline` | `assigned`, and `overdue` **when the origin is `assigned`** (the assignee only) |
| `canReassign` | `not_assisting` (any bond member) |
| `canComplete` | `in_progress`, `verify` (any bond member) |
| `canUnassign` | **gone** — T4 is removed, the flag never existed and must not appear |
| `canDelete`, `canCancel` | unchanged (creator && !isPast) |

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
| T25 | Verify window, `created` timeout, server flags, 48 h lead time | B | S2 | delegated in two work units: **T25a** state machine + `verify_at` + the origin guard, then **T25b** the server flags + the 48 h lead time + the notification copy | T25a done, T25b pending | B `45bc9b8` |
| T18 | Verify window in the front + the 48 h picker | F | S3 | — | pending | — |
| T29 | Gastos front: summary cards + list + filters | F | S4 | delegated together with T19 (one front writer: the read surface) | in flight | — |
| T19 | Gastos front: the expense detail + approve / reject / pay | F | S5 | delegated together with T29 | in flight | — |
| T27 | Gastos front: "+ Solicitar" form + `sin doc.` warning | F | S6 | — | pending | — |
| T30 | Attachment → expense flow (+ the expense attachment API) | F+B | S7 | — | pending | — |
| T26 | Realtime: `expense.changed` + the live refresh | F+B | S8 | — | pending | — |
| T31 | Design-system pass | F | S9 | — | pending | — |

Delivery order: **T28 → T25 → {T29, T19, T27} → T18 → T30 → T26 → T31**. The front Gastos
read surface (T29+T19) codes against the S1 contract and lands in parallel with the backend
work; T27 and T18 land after it; T31 closes the wave.

**One front writer at a time** — gradle writes the shared `shared/build/`. The two backend
writers run in parallel with disjoint surfaces, but they SHARE the jest suite and the
type check: each was told to report the foreign failures separately instead of chasing
them.

---

## Open items

1. **Prod (Supabase) is three migrations behind** — `1792819200000` (the expense model
   fields), `1793491200000` (the outbox aggregate type) and `1793577600000`
   (`activity.verify_at`) were never applied there. Applying a migration to prod is a
   remote write and needs the user's explicit approval. Dev and test are at 16.
2. **The back worktree carries pre-existing prettier noise** in 9 files (43 insertions,
   36 deletions — formatting only, not authored by this wave). It stays out of every
   commit except where a file this wave must touch (T25 touches
   `src/notifications/fan-out/activity-notification.map.ts`); there the noise is
   inseparable from the change and the commit says so.
3. **The wave-2 leftovers (T15, T16, T35) are still open** and are not in this wave.
4. **The front repo has three uncommitted documents** (`gastos-and-ux-resolution.md`
   revision 4, `wave-1-ux-quick-wins.md`, `wave-2-medium.md`) from the previous session.

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
