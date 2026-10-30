# Resolution — PRDs `prd_improvement.md` + `PRD_expense.md` (parental-coordination)

Revision: **3** — R3 and R14 rewritten after the user's correction (Log L5); R14 reshaped
again into one timeout constant with two anchors, and R15 added (Log L6).
Objective: reconcile the two PRDs against the code that actually exists, rule on every
conflict and open question they leave open, and produce one backlog ordered from the
cheapest task to the most expensive.
Scope: cross-repo. Front `Documentos/parental_coord-front` (Kotlin Multiplatform Compose),
Back `Documentos/parental_coordination_back` (NestJS + TypeORM + Postgres + Supabase Storage).
Sources: `/home/angelo/prd_improvement.md` (Draft v0.1, UX/UI), `/home/angelo/PRD_expense.md`
(Draft v0.1, Gastos), plus the back repo's `docs/PRD.md` for the domain model.
Method: every ruling below is anchored to a file that was read; every backlog line states
whether the work is new or already built. No code is changed by this document.
Test runners: front `./gradlew :shared:testDebugUnitTest`; back `pnpm test`.
Estimates: person-hours, order-of-magnitude for ordering only — not a quote.

---

## Rulings

These are the decisions that make both PRDs implementable as one program. Each one names
the PRD text it resolves.

### R1 — One signed balance, two named views (resolves `prd_improvement` OQ2 and `PRD_expense` OQ3 / Issue #4)

The two PRDs ask for the same number under different names and opposite signs:
`prd_improvement` H-5 wants "Saldo gastos" green when positive, red when negative;
`PRD_expense` FR-3 wants "Por pagar" in yellow as a positive-looking total owed.
Both are undefined in their own PRDs, and `PRD_expense` Issue #4 records that the shipped
UI shows `$16.80` where the pending items sum to `$480 + $16.80` — the number nobody can
reproduce.

**Ruling.** One server-computed balance per bond, signed from the current user's point of
view, over expenses in status `approved` or `paid` (money that is committed; `pending` is
not a debt until agreed, `rejected` is dead):

```
myShare   = amount * split_ratio[me]
otherShare= amount * split_ratio[other]
saldo     = Σ (myShare - otherShare)      // > 0: I am owed.  < 0: I owe.
```

Both views read the same field:

| Surface | Label | Value | Sign/colour |
|---|---|---|---|
| Home metric card (H-5) | `Saldo gastos` | signed `saldo` | green if > 0, red if < 0 |
| Gastos header card (FR-3) | `Por pagar` | `abs(min(saldo, 0))` — what **I** owe | amber, always ≥ 0 |
| Gastos header card | `Por cobrar` (new) | `abs(max(saldo, 0))` — what I am owed | green |

Rule: a signed number is never rendered under a label that hides its sign. `Por pagar` and
`Por cobrar` are unsigned magnitudes; `Saldo gastos` is signed and labelled as such.
The endpoint returns `{ saldo, porPagar, porCobrar, counts: {pendientes, aprobados, pagados,
rechazados} }` so no client ever recomputes it.

### R2 — One category taxonomy, not two (resolves `PRD_expense` FR-12 and `prd_improvement` D-15)

`PRD_expense` §5.5 defines categories Salud/Educación/Recreación as if expenses owned them.
`prd_improvement` D-15 asks to pre-select the expense category **from the activity**, which
is only possible if activities and expenses share one taxonomy.

**Ruling.** The existing `category` table is the single taxonomy for both modules.
`src/categories/entities/category.entity.ts` already carries `name`, `color`, `icon`,
`criticality` and is already linked to activities (`activity.category_id`). Expenses get a
`category_id` FK to the same table. Consequences:
- No `expense_category` table, no second icon/color system, no per-module category config.
- `PRD_expense` FR-12 ("categories configurable in a later release") is satisfied by the
  existing `category.is_active` flag, not by a new table.
- If the seeded categories do not cover expense semantics (Salud/Educación/Recreación are
  health/education/recreation-shaped, activities use `criticality`), the seed is **extended**.
  Existing activity categories are never renamed.

### R3 — The nine states are the spec; the transitions are the engine's, not the PRD's (resolves `prd_improvement` D-7…D-10)

`prd_improvement` §7.3 proposes a button matrix over `PENDING`, `IN_PROGRESS`, `COMPLETED`,
`OVERDUE`, `CANCELLED`. The domain has nine: `created`, `assigned`, `assisting`,
`in_progress`, `verify`, `done`, `overdue`, `not_assisting`, `cancelled`
(`src/activities/entities/activity.entity.ts`), driven by a **closed transition catalog**
(`src/activities/activity-transition.map.ts`) plus a **timer engine**.

**Ruling.** The nine states stay as they are. The PRD's five-state matrix is **discarded,
not translated**: three of its cells ("Reprogramar", "Reabrir", "Editar") name transitions
that do not exist in the engine, and `done`/`cancelled` are terminal with no outgoing rule
at all (`activity-transition.map.spec.ts` asserts it for every target). The matrix below is
derived from what the engine can actually do.

**The engine, as implemented:**

- *User transitions* (`RULES`): T3 `created→assigned`, T4 `assigned→created` (unassign),
  T5 `assigned→assisting`, T6 `assigned→not_assisting`, T8 `overdue→assisting`,
  T8 `overdue→not_assisting`, T9 `not_assisting→assigned`, T11 `in_progress→done`,
  T15 `→cancelled` from six sources.
- *System transitions* (timers + a 15-minute reconciliation sweep):
  - `RESPONSE_TIMEOUT` — source `assigned` → `overdue`, at `deadline = now + 36h`.
  - `START` — source `assisting` → `in_progress`, at `scheduledStart`.
  - `END` — source `in_progress` → **`done`**, at `scheduledEnd ?? scheduledStart`.

**The authoritative matrix** (this is the spec the UI implements):

| Status | Who acts | Button | → | Rule |
|---|---|---|---|---|
| `created` | any bond member | **Asignar** | `assigned` | T3; sets `deadline = now + 36h` |
| `assigned` | assignee | **Confirmar** | `assisting` | T5, sets `confirmedAt` |
| `assigned` | assignee | **No asiste** | `not_assisting` | T6, sets `declinedAt` |
| `assigned` | any member | **Desasignar** | `created` | T4, clears the deadline |
| `assigned` | system | — | `overdue` | `RESPONSE_TIMEOUT` timer, 36h |
| `assisting` | — | **none** | `in_progress` | T10 at `scheduledStart` — the wait is automatic |
| `in_progress` | member | **Completado** | `done` | T11, sets `completedAt` |
| `in_progress` | system | — | `verify` | **R14** (to build) — today it jumps straight to `done` |
| `verify` | member | **Completado** | `done` | **R14** (to build) |
| `verify` | system | — | `overdue` | **R14** (to build), after the 36h window |
| `overdue` | assignee | **Confirmar** / **No asiste** | `assisting` / `not_assisting` | T8 — late acceptance |
| `not_assisting` | any member | **Reasignar** | `assigned` | T9, resets the deadline |
| `done` | — | none | — | terminal |
| `cancelled` | — | none | — | terminal — reached through two different doors, see below |

**`cancelled` is one status with two doors.** `delete` ("Eliminar") and `cancel` ("Cancelar")
both land on T15 → `cancelled`, and the difference lives in the columns, not the status:

| Path | Endpoint | Columns written | Visible afterwards | Reason |
|---|---|---|---|---|
| **Eliminar** (soft delete) | `DELETE /activities/:id` | `status = cancelled`, **`deleted_at` = now** | hidden (`deleted_at IS NULL` filters it out) | none — restorable within 5000 ms (`POST /:id/restore`) |
| **Cancelar** (call off) | `POST /activities/:id/cancel` | `status = cancelled`, `cancelled_at` = now | **still visible** as "Cancelada" | mandatory, 3–200 chars, creator only |

Both are gated by the same policy (`canDelete` / `canCancel` = creator && not past). The front
already distinguishes them in the swipe menu (`SwipeAction.DELETE` without a reason,
`SwipeAction.CANCEL` with one). The PRD's D-9 ("destructive actions require confirmation")
covers both, and they must never be collapsed into one button: *Eliminar* hides the activity,
*Cancelar* keeps it visible with a reason attached.

Two consequences the PRD does not know about:

1. **D-7 is superseded.** "Finalizar is visible only after the activity is `IN_PROGRESS`"
   becomes: *Completado is visible in `in_progress` and in `verify`, and nowhere else.*
2. **The buttons must be server-owned.** Today the front renders "Finalizar" in **every**
   status except `done` (`ActividadDetailSheet.kt:378` — `if (actividad.status != "done")`)
   and derives "Confirmar" from a local rule that ignores the status entirely
   (`canConfirmAssignment` in `ActivitiesModels.kt:105` — only `assignedTo == currentUserId
   && !assignedConfirmed`). Both produce 409s on click. The matrix must arrive as server
   flags, like `canDelete`/`canCancel`/`canDecline` already do — plus a real `canConfirm`
   and a new `canComplete`.

### R4 — The "Confirmada" chip is a derived per-person fact, not the activity status (resolves `prd_improvement` A-1)

A-1 asks to remove the "Confirmada" chip. The backend already dropped the stored boolean
`assigned_confirmed` (migration `DropActivityAssignedConfirmed`) and derives it:
`assignedConfirmed = status === assisting && confirmedAt != null`
(`src/activities/dto/activity-response.dto.ts`). The chip still ships on the card
(`feature/activities/ui/ActivityCard.kt:276-298`).

**Ruling.** Remove the chip from the **card** (A-1) — it duplicates the status pill and is
per-assignee information shown on a per-activity surface. The derived flag survives and is
shown in the **detail sheet** as a per-assignee line, where it is meaningful. Nothing is
lost and no schema changes.

### R5 — Notification copy lives in the backend, not in a front i18n file (resolves `prd_improvement` N-4)

N-4 asks for a central i18n file so copy can be reviewed and translated. But the notification
row is written by the server: the fan-out renders `title`/`body` from hard-coded templates
(`src/notifications/fan-out/activity-notification.map.ts`) and inserts them.

**Ruling.** The copy catalogue is a **backend** module — a typed map from event type to
`{ title, body }` in Spanish — because that is where the text is produced. The front renders
`notification.title` / `notification.body` verbatim; it never formats notification copy.
Two changes to the current design fall out of this:
- `title === body` by design (see the `renderNotificationText` comment) must be **split**:
  N-2 requires title ≤ 40 chars and description ≤ 100 chars, which is impossible with one string.
- The 8 existing templates are in English and must be replaced with the Spanish ones in N-3.
The front "i18n file" becomes a copy-review surface for the backend catalogue, not a
front-end resource.

### R6 — A converted attachment shares the storage object, and gets its own expense row (resolves `prd_improvement` D-11…D-18)

D-13/D-18: after picking a file, ask "¿Es un gasto?"; if yes, open a pre-filled expense; if
the user cancels, the file stays on the activity. That means the file must be able to belong
to both an activity and an expense at once.

**Ruling.**
- One storage object, two rows: the existing `act_attachment` row and a new
  `expense_attachment` row point at the same Supabase object key. Bytes are never copied.
- `expense.activity_id` (new FK) records the origin, satisfying D-17 ("the expense shows a
  reference to the activity").
- The activity keeps its attachment (D-18 holds by construction).
- `expense_attachment` existing ⇒ the `sin doc.` flag is **not** set (D-15). The flag is
  derived as "no `expense_attachment` row", never stored as a boolean.

### R7 — Expense amounts are per-party shares, and the split is fixed 50/50 in v1

`PRD_expense` OQ1/OQ4 leave both open; the schema already has `split_ratio_payer` and
`split_ratio_other` (`src/expenses/entities/expense.entity.ts`).

**Ruling.** The stored `amount` is the **total** of the expense. Each party's obligation is
`amount * ratio`. v1 fixes the split at 50/50 (`PRD_expense` §5.3 already assumes it), and
the ratio columns stay in the schema so custom splits (v1.2) need no migration. The UI shows
the total prominently and the user's share secondarily — never the other way around.
Custom splits are deferred (T33).

### R8 — Expense notifications ride the existing outbox → fan-out pipeline

`PRD_expense` §7 lists four expense events. The repo already has an outbox → fan-out →
`notification.created` socket pipeline for activities.

**Ruling.** Expense events reuse that pipeline: `expense.created`, `expense.approved`,
`expense.rejected`, `expense.paid`, plus `expense.stale` (pending > 7 days). The 7-day
reminder is a scheduler, following the existing `notifications-retention.scheduler.ts`
precedent. No second notification transport is built.

### R9 — "Cards update in real time" means the socket, not polling (resolves H-5 / FR-1)

Both PRDs require live counters. The repo already emits `notification.created` frames.

**Ruling.** Expense state changes emit an `expense.changed` frame over the same gateway; the
front refetches the balance/counts on that frame instead of polling. Polling stays only for
the notification badge, which already has it.

### R10 — Soft delete of notifications is a per-user column, filtered everywhere (resolves N-6/N-7)

N-6 demands the record survive in the database. The activity soft-delete already sets the
precedent: a nullable `deleted_at` column plus a partial index
(`src/activities/entities/activity.entity.ts`).

**Ruling.** Add `notification.deleted_at` (nullable) + partial index. Filter
`deleted_at IS NULL` in **every** read path — list, unread count, mark-read, mark-all-read,
retention selection — because the PRD risk section already flags the leak risk ("requires
filtering in every notification query to avoid leaks in counts"). The row is never hard-deleted
by the user; the existing retention processor may still purge it later by age.

### R11 — Counts for the Home cards are computed from data the client already has

The activities endpoint has **no** query filters (`GET /activities/bond/:bondId` takes only
`bondId`). "Asignadas" and "Vencidas" would otherwise force new server filters.

**Ruling.** Both counts are derived **client-side** from the already-loaded bond activity
list (the list is per-bond and small). Only "Saldo gastos" needs a server endpoint, because
money must never be recomputed on the client (R1). This removes an entire backend slice
from the Home-cards task.

### R12 — Sequencing: Gastos before the Home card, not after

The two PRDs' release plans conflict. `prd_improvement` Phase 2 puts the Home summary cards
early; `PRD_expense` MVP puts the expense module first. The Home card's "Saldo gastos"
depends on the balance endpoint, which is part of the expense module.

**Ruling.** `PRD_expense` MVP comes first (T28–T27), then the Home metric cards (T20), then
the attachment → expense flow (T30). `prd_improvement` Phase 1 items (top bar, notification
copy + soft delete, activity card) are cheap and independent, so they run first, in parallel.

### R13 — Explicitly out of scope for this program

OCR prefill of the amount (`prd_improvement` OQ6, T34 — deferred, high cost, low value
until the flow is proven); payment-provider integration and multi-guardian support
(`PRD_expense` §3/§12); month view in the agenda (OQ5); third-party/lawyer visibility of
expense history (`PRD_expense` OQ6); restore of deleted notifications (`prd_improvement` OQ7
— the undo snackbar in N-8 covers the 5-second window; full restore is not built).

### R14 — One timeout constant, two anchors: `verify` is declared but unreachable today

`verify` is in the TypeScript enum, in the Postgres enum, in the front's label map
("Por verificar", `TimelineFormatters.kt:152`) and in its color map (`Color.kt:176`) — but
**no rule enters it and no rule leaves it**. `activity-transition.map.spec.ts:139-140`
asserts `[VERIFY, DONE]` and `[VERIFY, CANCELLED]` are *invalid*, and `:215` asserts
`timerOpsForTransition(VERIFY)` is empty. The engine today goes `in_progress → done`
automatically when the end time passes (`activity-timers.processor.ts`:
`targetStatusFor(END) = DONE`; `activity-timers.sweep.ts`: `dueTargetFor`).

**Ruling.** There is **one** waiting window, measured by **one** constant
(`RESPONSE_TIMEOUT_HOURS`, default 36 — `activities.service.ts:304`), applied at **two**
anchors. What expires differs; the length does not.

| Window | State | Anchor | Expiry | Transition |
|---|---|---|---|---|
| Acceptance | `assigned` | the moment it became `assigned` | `assigned_at + 36h` | `assigned → overdue` (T7) — **already implemented** |
| Verification | `verify` | the activity's date/time end (`scheduledEnd ?? scheduledStart`) | `end + 36h` | `verify → overdue` (T14, **new**) |

The timeline of one activity:

```
t0  assigned            deadline = t0 + 36h          (RESPONSE_TIMEOUT armed)
t1  assigned + 36h  → overdue                        (nobody accepted in time)
    …assignee confirms → assisting                   (T5 / T8)
t2  scheduledStart   → in_progress                    (START timer)
t3  scheduledEnd     → verify                         (END timer, retargeted from `done`)
t4  t3 + 36h        → overdue                        (VERIFY_TIMEOUT, new)
    …"Completado" at any point in [t3, t4] → done     (T13)
```

- **`deadline` is generalized, not duplicated.** It becomes *"the expiry of the window the
  activity is currently waiting in"*: set to `now + 36h` on entering `assigned` (already the
  case — `computeResponseDeadline`), set to `(scheduledEnd ?? scheduledStart) + 36h` on
  entering `verify`, cleared everywhere else. **No new column and no migration**; the one
  column already carries both windows.
- **New rules** in the closed catalog. The rule-id space already has the holes `T12`, `T13`,
  `T14`: `T12` = `in_progress → verify` (system), `T13` = `verify → done` (member),
  `T14` = `verify → overdue` (system).
- The **`END` timer's target changes from `done` to `verify`**, and a fourth timer kind
  `VERIFY_TIMEOUT` (source `verify` → `overdue`) is enqueued on landing in `verify`.
- **The sweep needs a fourth clause.** `findDueForTransition`
  (`activities.repository.ts:248-270`) whitelists three statuses today — `assigned`,
  `assisting`, `in_progress`. A `verify` row would never be swept, so a lost
  `VERIFY_TIMEOUT` timer would never self-heal. Both the query and `dueTargetFor` need the
  `verify` case, or the 15-minute safety net silently stops covering it.
- The spec assertions that encode the old decision are **deliberately reversed**:
  `activity-transition.map.spec.ts:139-140` (`[VERIFY, DONE]` invalid),
  `:215` (`timerOpsForTransition(VERIFY)` empty),
  `activity-timers.processor.spec.ts:55,87` (`targetStatusFor(END) = DONE`),
  `activity-timers.sweep.spec.ts:61,67,97` (`dueTargetFor(in_progress, past end) = DONE`).
  This is the only place in the program where a previously asserted ruling is undone.
- New outbox events `activity.verify` and `activity.verify_expired` extend the closed event
  catalog from 9 to 11, with their own notification templates and fan-out rules.
- **Late-path consequence (flagged for the user).** With the literal anchor, an activity
  whose date/time passed more than 36h ago enters `verify` with an already-expired window
  and is flipped to `overdue` by the next sweep (≤ 15 min) — the "Completado" button is then
  barely reachable. The literal rule is the ruling; the one-line alternative is
  `max(anchor, now) + 36h`, so the window starts on arrival and is never shorter than 36h.
- One inconsistency to fix while in there: the attachment guard treats `overdue` as terminal
  (`activity-attachments.service.ts:70` rejects `done`, `cancelled` **and** `overdue`), while
  the transition map treats `overdue` as recoverable (T8). `verify` must not inherit that
  confusion.

### R15 — The compliance formula: calendar month, `done` only, `cancelled` excluded (resolves H-6, OQ1)

H-6 requires the "cumplimiento" formula to be defined and documented; OQ1 asks who the
secondary bar represents. The implementation is close to the PRD but not equal to it:

- **Period**: `getComplianceForBond` (`activities.service.ts:581-602`) uses the **current
  calendar month** (`from = new Date(year, month, 1)`), while the PRD asks for the **last 90
  days** and its example (`82%`) assumes 90.
- **Numerator**: `COUNT(a.id) FILTER (WHERE a.status = 'done')`
  (`bond-members.repository.ts:76`).
- **Denominator**: every activity assigned to that member in the period
  (`a.assigned_to = bm.user_id`) — **including cancelled and soft-deleted ones**, because the
  query filters on nothing but the assignee, the bond and the date range.

**Ruling.**
- **Period → the current calendar month, as implemented.** The PRD's "last 90 days" is
  **amended, not the code**: H-6's example (`82%` over 90 days) becomes *the percentage for
  this month*, and the UI copy must say so — "Este mes", never "últimos 90 días". The card
  currently renders the raw backend period string (`"2026-10"`), which becomes "Este mes" /
  "Octubre 2026".
- **Denominator → exclude `cancelled`.** `cancelled` is the **soft-delete status**: `delete`
  writes `status = cancelled` **and** `deleted_at` (hidden, restorable 5 s); `cancel` writes
  `cancelled` + `cancelled_at` + a mandatory reason (still visible). Neither is a *missed*
  obligation, so neither belongs in the denominator. This also fixes a live inconsistency: the
  query never filtered `deleted_at IS NULL`, so an activity the creator removed with
  "Eliminar" was still counted against their compliance percentage.
- **Numerator → `done` only.** `verify` does **not** count: an activity awaiting
  confirmation is not a completed obligation, and counting it would make the number lie about
  the other parent's compliance. The visible consequence is that after R14 the percentage
  lags by up to 36h per activity — that lag is the price of "confirmed", and it is stated
  in the UI copy next to the number, not hidden.
- **The secondary bar → every member of the bond, including the current user.** The
  endpoint already returns one row per member and the front already renders it
  (`HomeScreen` → `ComplianceCard` → `ComplianceMemberRow`), so OQ1 is answered by the code:
  it is not "the other party", it is the whole bond.
- The formula is **written down** in the repo next to the endpoint — H-6 asks for
  documentation, not just a number:
  `cumplimiento(usuario, mes) = done(asignadas al usuario en el mes) / asignadas al usuario en el mes − cancelled`.

---

## Tasks — least to most expensive

Ordered by estimate. Repo: **F** = front, **B** = back, **F+B** = both.
"Built" = the code already exists and the work is wiring/change, not construction.

### Wave 0 — XS (1–3 h): no schema, no new endpoint

| ID | Task | Repo | PRD | Est | Notes |
|---|---|---|---|---|---|
| T01 | Remove the "Confirmada" chip from the activity card | F | A-1 (R4) | 1h | Built: `ActivityCard.kt:276-298`. Data already derived server-side. Test: card renders no "Confirmada". |
| T02 | Populate `compliance` in `HomeSummary` | F | H-6 | 2h | Built: `HomeApi.getCompliance` + `ComplianceResponse` model exist, but `getHomeSummary` returns `compliance = null`. |
| T03 | Expose `childrenIds` in the front activity model + resolve child names | F | A-3, D-5 | 2h | Built: backend DTO already exposes `childrenIds`; front `Actividad` lacks the field. Names come from `ActivitiesViewModel.children` (already loaded). |
| T04 | "Fin programado" in the create form + end-after-start validation | F | A-14…A-16 | 3h | Built: backend `create-activity.dto.ts:57-60` already accepts `scheduledEnd`; front `CreateDateConversion.kt:91` hardcodes `scheduledEnd = null`. Front-only. |

### Wave 1 — S (3–6 h): one file, one surface

| ID | Task | Repo | PRD | Est | Notes |
|---|---|---|---|---|---|
| T05 | Locale-aware currency formatter, 2 decimals, everywhere | F | H-7, FR-6, Issue #1 | 3h | New: no money formatter exists in the front at all. Add + unit tests. |
| T06 | Localize relative dates ("1d ago") and fix truncated dates ("05 dé") | F | Issues #2, #3 | 3h | Built: `NotificationTimeFormatter.kt`, `TimelineFormatters.kt`. |
| T07 | "Asignada a: [name]" on the activity card | F | A-2 | 3h | Built: `assignedTo` (user UUID) + `memberNames` already in the VM. Resolve name client-side. |
| T08 | Children on the activity card (avatars / `+N` chip) | F | A-3 | 3h | Depends on T03. |
| T09 | Detail sheet: date + time range, children, assignee | F+B | D-4…D-6 | 5h | Mostly built; the detail DTO lacks `childrenIds` → +2h on the back. |
| T10 | Notification copy: Spanish, title ≠ body, length-capped | B | N-1…N-4 (R5) | 5h | Built: 8 templates in `activity-notification.map.ts`, English, `title === body`. Split the template and rewrite the 6 example rows. Update specs. |
| T11 | Children filter in Activities ("Todos" + one per child, multi-select) | F | A-5…A-7 | 5h | Built: `ActivitiesViewModel.children: List<VinculoChild>` already loaded → pure client-side filter. |
| T12 | Global `AppTopBar`: bell + unread badge + logout with confirm | F | G-1…G-5 | 6h | Built: each screen owns its `TopAppBar`; logout exists only on Home/Admin. Extract one composable, add 44×44 hit area, `contentDescription`, logout dialog. |

### Wave 2 — M (1–2 d): migration or new service, contained

| ID | Task | Repo | PRD | Est | Dep | Notes |
|---|---|---|---|---|---|---|
| T13 | Soft delete for notifications (column + filters in every query) | B | N-6, N-7 (R10) | 1d | — | Touches `notification.repository.ts` (list :303, unread :202, mark-read :227, mark-all :246, retention :285) + a new migration + specs. |
| T14 | Expense model migration: `category_id`, `activity_id`, `deleted_at` | B | R2, R6 | 1d | — | Built: `expense` table exists but has none of the three. Migration + entity + migration spec. |
| T15 | Agenda: "Todo el día" section + per-day activity indicators | F | A-12, A-13 | 1d | — | Built: `WeekAgenda.kt`, `DayAgenda.kt`, `CalendarChrome.kt` exist. |
| T16 | Swipe to change week (horizontal pager) | F | A-9 | 1d | — | Built: agenda exists; the pager gesture does not. |
| T17 | Notification delete UI: swipe / menu, undo snackbar, "borrar todas" | F | N-5, N-8, N-9 | 1d | T13 | Built: `NotificationsScreen.kt` has no delete gesture today. |
| T35 | Compliance formula: exclude `cancelled`, document it, fix the period copy | B+F | H-6, OQ1 (R15) | 0.5d | — | Built: the period stays the **calendar month** (the PRD's "90 days" is amended). The denominator currently counts soft-deleted activities — the query never filtered `deleted_at IS NULL`. Exclude `cancelled`, write the formula down next to the endpoint, and replace the raw period string ("2026-10") with "Este mes" in the card. |
| T18 | Verify window — front: "Completado" in `verify`, countdown, labels | F | D-7 (R3, R14) | 1d | T25 | Built: "Por verificar" label and colour already exist. Needs the `canComplete` flag and the `verify` state in the card/detail. |
| T19 | Gastos front: expense detail view + approve/reject/pay UI | F | §5.4, FR-11 | 1.5d | T24 | |
| T20 | Home metric cards: Asignadas / Saldo gastos / Vencidas | F | H-5 (R1, R11) | 1.5d | T28 | Counts client-side (R11); only the balance comes from the server. |
| T21 | Status-aware action matrix on the detail sheet | F | D-7…D-10 (R3) | 1.5d | T18, T25 | The matrix in R3 is the spec. Also kills the live defect: "Finalizar" renders in every status but `done` today. |
| T22 | Expense policy + audit (who may act, every status change logged) | B | FR-10, FR-11 | 1.5d | T14 | Built: `audit_log` table and `audit` module exist. Mirrors `activity-policy.service.ts`. |
| T23 | Expense notifications via outbox fan-out + 7-day stale reminder | B | §7 (R8) | 1.5d | T14, T22 | Built: fan-out pipeline + scheduler precedent. |

### Wave 3 — L (2–4 d): new vertical slice

| ID | Task | Repo | PRD | Est | Dep | Notes |
|---|---|---|---|---|---|---|
| T24 | **Expenses API**: approve / reject / pay transitions | B | FR-8, FR-10, FR-11 | 2d | T14, T22 | Built: `ExpenseStatus` enum already has `pending/approved/rejected/paid`. |
| T25 | **Verify window — backend state machine** | B | R3, R14 | 2.5d | — | New rules T12/T13/T14; `END` timer retargeted to `verify`; fourth timer kind `VERIFY_TIMEOUT`; `deadline` generalized to "the window the activity is waiting in" (one constant, two anchors — **no new column**); sweep query + `dueTargetFor` gain a `verify` clause (without it a lost timer never self-heals); `canComplete`/`canConfirm` server flags; two new outbox events + fan-out rules; **reverses** the `activity-transition.map.spec.ts` assertions that today declare `[VERIFY, DONE]` invalid. Highest-risk item in the program. |
| T26 | Realtime: `expense.changed` frame + live card refresh | F+B | H-5, FR-1 (R9) | 2.5d | T28, T29 | Built: `notification.created` frame precedent in the gateway. |
| T27 | **Gastos front**: "+ Solicitar" form + document upload + `sin doc.` warning | F | §5.3, FR-9 | 2.5d | T28 | Built: Storage presign/confirm pattern exists in `ActivitiesApi`. |
| T28 | **Expenses API**: list + filters + summary/balance endpoint | B | FR-1…FR-7, R1 | 3d | T14 | Built: `src/expenses/` holds **only** entities + a module registration — no controller, no service, no DTO, no repository. The `expense`/`expense_attachment` tables and the Storage upload pattern (activity attachments) are reusable. The largest single item in the program. |
| T29 | **Gastos front**: summary cards + list + filters | F | FR-1…FR-7 | 3d | T28 | New feature module (`feature/expenses/`): models, api, viewmodel, screen, DI. |
| T30 | Attachment → expense flow ("¿Es un gasto?") | F+B | D-13…D-18 (R6) | 3d | T14, T27, T29 | Both ends: `expense_attachment` row sharing the storage object, `expense.activity_id`, pre-filled form, cross-references, cancel keeps the file. |
| T31 | Design-system pass: spacing, type scale, radius, skeletons, empty states | F | H-2, H-3, FR-7 | 3d | — | Touches every screen. Tokens are frozen in `core/ui/theme/` — reuse, do not add. |

### Wave 4 — XL (3–8 d): deferred by the PRDs' own release plans

| ID | Task | Repo | PRD | Est | Dep | Notes |
|---|---|---|---|---|---|---|
| T32 | Expense filters + search + reject/dispute flow with reason | F+B | v1.1 | 3d | T19, T24 | `ExpenseStatus.REJECTED` exists but no reason field. |
| T33 | Custom splits, custom categories, PDF/CSV export | B+F | v1.2 | 5d | T28 | Split columns already exist (R7); the rest is new. |
| T34 | OCR prefill of the amount from a receipt | F+B | OQ6 | 8d | — | Deferred by R13. Do not start until the manual flow is proven. |

**Program total ≈ 49 person-days** (excluding T34). The Gastos MVP (T14, T22, T24, T28, T19,
T27, T29) is ≈ 14 d and gates the Home cards and the attachment flow.

---

## Critical path

```
T25 (verify window, back) ─┬─► T18 (verify front) ─► T21 (action matrix)
                            │
T14 (expense migration) ───┼─► T22 (policy/audit) ─► T24 (transitions API) ─► T19 ─► T32
                            ├─► T23 (expense notifications)
                            └─► T28 (expenses API + balance) ─┬─► T27 (create form) ─► T30
                                                               ├─► T29 (Gastos list) ─► T30
                                                               ├─► T20 (Home cards)
                                                               └─► T26 (realtime)
```

Waves 0 and 1 (T01–T12, ≈ 5 person-days) have no dependencies on the expenses program and
can be delivered immediately; that is where the visible UX gain per hour is highest.
The longest chain is T14 → T28 → T29 → T30 (≈ 10 d). **T28 is the critical path** for the
expenses program, and **T25 is the critical path** for the activity program.

## Build order

1. **Wave 0 + 1** (T01–T12) — cheap, independent, immediately visible.
2. **Wave 2** (T13–T23) — the two migrations (T13, T14) land early because they are the
   hard dependencies of everything behind them; T25 must land before T18 and T21.
3. **Wave 3** (T24–T31) — the verify window, the expenses vertical, then the Home cards,
   then the attachment flow, then the design pass and realtime.
4. **Wave 4** (T32–T33) — v1.1/v1.2 scope from `PRD_expense`'s own plan.

## Risk register (Task Size high-risk items)

| Item | Where | Why |
|---|---|---|
| 1 — data | T13, T14, T25, T28, T24, T35 | Migrations and status transitions write stored rows. `expense` has no soft-delete yet, so a bad transition is not trivially reversible. T25 adds a state the engine has never run **and** generalizes `deadline`, the column the `assigned` path already depends on — the most-used path in the app. T35 changes the compliance denominator, so every historical percentage moves. |
| 2 — security | T25, T28, T24, T27, T19 | Expense visibility is per-bond and per-role; a leak exposes financial and health data of a minor. Every new endpoint must enforce bond membership like `getDetail` does. The new `canComplete`/`canConfirm` flags decide who may finish an activity — they are an authorization surface, not a hint. |
| 3 — contracts | T28 (new endpoint), T25 (new `can*` flags in the DTO), T20 (new card) | The balance shape in R1 and the new permission flags become contracts the front and any future client depend on. |
| 4 — concurrency | T22, T24, T25 | Two parties can act on the same activity or expense at once. The verify window widens the race: the user clicks "Completado" exactly as the sweep expires it. The existing `version` optimistic lock and CAS-safe sweep are the precedent. |
| 5 — delivery | T26, T33 | Socket contract and export format are external-facing surfaces. |
| 6 — untestable | T31 | A pure visual pass has no regression test; it needs manual verification on small screens (the PRD's own weekly-agenda risk). |

---

## Log

- **L1 (user request, verbatim):** "read the prd_improvent.md and prd_expense.md for
  parental-coordintaton project, create a resolution and show me from the least to most
  expensive task"
- **L2:** Both PRDs read in full; the back repo (`src/`, migrations, fan-out, policy, timer
  engine) and the front repo (`shared/src/commonMain`, 85 Kotlin files, `odd/tasks/`,
  `design/`) were inspected to establish what already exists. Every ruling cites the file it
  rests on.
- **L3:** Rulings R1–R13 resolve every open question and conflict between the two PRDs.
  Where a ruling overrides a PRD's assumption, the PRD text it overrides is named in the
  ruling.
- **L4:** Open items still needing a human decision — none block the backlog: the only
  judgement calls are R1 (the exact balance semantics; a different reading of "Por pagar"
  changes only the label and the sign convention, not the arithmetic) and R7 (fixed 50/50
  in v1, which `PRD_expense` §5.3 already assumes).
- **L5 (user correction, verbatim):** "if i understand correctly dont rewrite the statuses of
  activity, use the 9 to see how it work if It is created only => the actor could assignedTo,
  if is asssined -> confirmar , if it is assisting wait for in progress (dont show any
  button), if it passed it should stay in verify for the 36hs action required, and if the user
  clicked on Completado button the pass to Done otherwise to Overdue."

  **What L5 changed.** R3 was wrong in kind, not in detail: revision 1 *translated* the PRD's
  five-state matrix onto the nine states and invented cells ("Reprogramar", "Reabrir",
  "Editar") for transitions the engine does not have. L5 asks for the opposite: keep the nine
  states, and derive the behaviour from how the engine actually moves between them. R3 is
  rewritten as the engine's own transition catalog plus timers, and the resulting matrix
  matches L5's description step for step (`created` → assign → `assigned` → confirm →
  `assisting` → wait, no button → `in_progress` → end passes → `verify` for 36h with action
  required → "Completado" ⇒ `done`, otherwise ⇒ `overdue`).

  **What L5 exposed.** The `verify` state is declared but unreachable: no rule enters or
  leaves it, the transition-map spec actively asserts that `[VERIFY, DONE]` is *invalid*, and
  the `END` timer jumps straight from `in_progress` to `done`. The verify window described in
  L5 therefore does not exist yet — it is new work, now scoped as **R14** (backend, T25) and
  **T18** (front), and it is the highest-risk item in the program because it reverses a
  previously asserted ruling and touches the timer engine and the sweep.

  **One reading to confirm.** L5 says "36hs" for the verify window. The code already has a
  36-hour window with a different meaning: `RESPONSE_TIMEOUT_HOURS` (default 36) is the
  *response* window — `assigned` → `overdue` when the assignee does not accept in time
  (`activities.service.ts:304`). R14 keeps the two separate and configurable, with the new
  `VERIFY_WINDOW_HOURS` also defaulting to 36. If L5 meant a single shared window, R14
  collapses to one constant — one line of change, no difference to the matrix.

  **ID remap (revision 1 → revision 2).** The backlog was renumbered to keep the
  cost-ordering intact after inserting the verify slice. Old → new:
  T13→T13, T14→T17, T15→T21, T16→T14, T17→T22, T18→T15, T19→T16, T20→T23, T21→T28,
  T22→T24, T23→T29, T24→T27, T25→T19, T26→T20, T27→T30, T28→T31, T29→T26, T30→T32,
  T31→T33, T32→T34. New: T18 (verify front), T25 (verify back).
- **L6 (user correction, verbatim):** "if the response timeout hours pass in assinged should
  go to overdue, also if the activity pass the date/time(in_progress) + response_timeout
  should go to overdue"

  **What L6 changed.** Revision 2 of R14 split the waiting window into two separately
  configured constants (`RESPONSE_TIMEOUT_HOURS` for the acceptance window,
  `VERIFY_WINDOW_HOURS` for the verification window). L6 collapses them: **one** constant,
  **two** anchors. The acceptance window is anchored on the moment of assignment
  (`assigned_at + 36h` → `overdue`, already implemented as T7); the verification window is
  anchored on the activity's date/time end (`end + 36h` → `overdue`, new). The two windows
  promise different things to the parents but they are the same duration measured from
  different moments, so they share the constant.

  **Consequences recorded in R14.**
  - `deadline` becomes "the expiry of the window the activity is waiting in" and carries both
    anchors — **no new column, no migration**, and the `verify_deadline` column proposed in
    revision 2 is dropped.
  - `assigned + 36h → overdue` needs **no change**: it is already T7 plus the
    `RESPONSE_TIMEOUT` timer plus the first clause of the sweep query.
  - `in_progress` past its date/time **plus** `response_timeout` → `overdue` is the new path,
    and it runs through `verify`: `in_progress` → `verify` at the end time (the `END` timer
    retargeted from `done`), `verify` → `overdue` 36h later (a fourth timer kind,
    `VERIFY_TIMEOUT`).
  - The sweep needs a fourth clause: `findDueForTransition` whitelists `assigned`,
    `assisting` and `in_progress` only, so a `verify` row would never be swept and a lost
    `VERIFY_TIMEOUT` would never self-heal.

  **One reading still open.** The anchor of the verification window is literal
  (`end + 36h`). An activity whose date/time passed more than 36h ago therefore enters
  `verify` with an expired window and flips to `overdue` on the next sweep. The alternative
  is `max(end, now) + 36h` — one line — so the window always opens when the activity arrives.
  Flagged in R14 for the user's call.

- **L7:** R15 added after reading `getComplianceForBond` and
  `findActiveMembersWithActStats`: the compliance period is the **current calendar month**,
  not the PRD's 90 days; the denominator counts cancelled activities; and `verify` will not
  count as completed, so the percentage lags by up to 36h per activity after R14. New task
  **T35**.
- **L8 (user correction, verbatim):** "the period in complianceForBond it should stay as
  calendar month, cancelled status is when the actor soft delete the activity"

  **What L8 changed.** R15 revision 1 ruled the period to 90 days (following the PRD's
  wording) and proposed parameterizing it. L8 keeps the **calendar month** — so the **PRD
  is amended, not the code**: H-6's "porcentaje de los últimos 90 días" and its `82%`
  example become "el porcentaje de este mes", and the UI copy must say "Este mes". The
  compliance card currently renders the raw backend period (`"2026-10"`), which becomes
  "Este mes" / "Octubre 2026".

  L8 also settles the *meaning* of `cancelled`, which revision 1 had described as "a
  cancelled obligation". It is the **soft-delete status**: `delete` writes
  `status = cancelled` **and** `deleted_at` (the activity disappears from the lists and is
  restorable for 5000 ms); `cancel` writes `cancelled` + `cancelled_at` + a mandatory reason
  (the activity stays visible as "Cancelada"). The two doors are now spelled out in R3 and
  must never be collapsed into one button — *Eliminar* hides, *Cancelar* keeps visible.

  **Live inconsistency this exposes.** The compliance denominator never filtered
  `deleted_at IS NULL` (`bond-members.repository.ts:75-83`), so an activity the creator
  removed with "Eliminar" was still counted against their compliance percentage. Excluding
  `cancelled` from the denominator fixes that as a side effect, and T35 now carries it.

  **Net effect on the backlog.** T35 drops from 1d to 0.5d (no period parameterization) and
  gains the copy fix; the program total moves to ≈ 49 person-days. R3's matrix gains the
  two-doors table for `cancelled`. Nothing else in the plan moves.
