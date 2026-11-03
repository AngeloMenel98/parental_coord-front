# Wave 2 — Medium (T13–T23, T35)

Objective: deliver the middle wave of the `gastos-and-ux-resolution` program — the
tasks that need a migration, a new query path or a new service, but stay inside one
vertical slice.

Branch: `feat/wave-0-quick-wins` (F) · `ft/supabase-cloud` (B).
Repos: **F** = `Documentos/parental_coord-front` (KMP Compose) · **B** =
`Documentos/parental_coordination_back` (NestJS + TypeORM + Postgres).
Delivery: one work-unit commit per task; tests and docs travel with the behavior.
Test runners: **B** `npx jest` (`pnpm` is not installed in this environment; the
scripts in `package.json` are the source of truth) · **F**
`./gradlew :shared:testDebugUnitTest`.
Program source: `odd/tasks/gastos-and-ux-resolution.md` — rulings **R1–R16** and the
wave table. The working copy is a 220-line revision 4 that dropped the backlog; the
T01–T35 table lives in `git show HEAD:odd/tasks/gastos-and-ux-resolution.md`.

Scope note: waves 0 and 1 are closed (T01–T12). This document covers wave 2 only.
`T32`, `T33` and `T34` are deferred by R13 and are not in scope.

---

## Specs

### S1 — T13 · "Eliminar" una notificación = marcarla leída (sin cambio en la base)

PRD `prd_improvement` §8, verbatim:

> **N-5:** User can delete a notification from the view (swipe and/or action menu).
>
> **N-6:** Deleting **does not remove the record from the database**. Implement as a
> **per-user soft delete** (e.g. `deleted_at` / `hidden_for_user` flag).
>
> **N-7:** Deleted notifications no longer appear in the list or the unread count.
>
> **N-8:** Optional "Deshacer" snackbar for a few seconds after deleting.
>
> **N-9:** Bulk action: "Borrar todas" with confirmation **[Assumption]**.

**User decision, verbatim (2026-10-31):**

> "yo te pedi en el T13 que no sea un cambio en el db, sino que se eliminen de la
> vista de notificacion. que pase a estar leida solamente"

**Ruling — this supersedes R10's `deleted_at` column.** N-6's own words are
"does not remove the record from the database", and marking it read satisfies that
literally with zero schema: the row stays, its state changes to read. So:

- **No migration, no column, no query filters.** The `deleted_at` + partial index work
  was reverted (commit discarded, column dropped from dev and test, `migrations` back
  to 13 rows) after confirming the column held no non-null value anywhere.
- "Eliminar" = `PATCH /api/notifications/:id/read` — already exists.
- "Borrar todas" = `PATCH /api/notifications/read-all` — already exists.
- The item leaves the view **on the client**: the server has no notion of "hidden", so
  hiding is a front-side responsibility (T17).

**Consequence that follows from having no DB state:** the notification is not gone, it
is read. That is enough only because of S5's decision: the list is an unread inbox
(`unreadOnly=true`), so read rows never come back to the view.

### S2 — T14 · Expense model migration (`category_id`, `activity_id`, `deleted_at`)

Ruling R2 (one taxonomy, not two) + R6 (a converted attachment shares the storage
object and gets its own expense row). The `expense` table existed and had none of the
three columns. Delivered as B `23d193b`.

- `category_id` → `category(id)` — the same table `activity.category_id` already uses;
  no `expense_category`. `ON DELETE RESTRICT`: a category holding expenses cannot be
  deleted without deciding what happens to that money.
- `activity_id` → `activity(id)`, the expense's origin (R6/D-17). `ON DELETE SET NULL`:
  deleting an activity can never delete money.
- `deleted_at` — the expense "Eliminar", same convention as `activity.deleted_at`
  (mark, never purge) with a **partial** index `WHERE deleted_at IS NOT NULL`.
- **All three nullable and no backfill, deliberately.** Historical rows have no
  category and no origin, and picking one for them would invent accounting data. Making
  the category mandatory on create is a DTO rule for T22, not a schema rule — `SET NOT
  NULL` here would force fabricating a category for every existing expense.
- Verified against real Postgres in **test and dev**: `run` → 3 columns, 3 indexes, 2 FKs
  with the right delete policy; `revert` → nothing left; `run` again. Data probe in a
  rolled-back transaction: insert with category + activity OK, an unknown category UUID
  rejected by `FK_expense_category`. 10 structural specs; full suite 71 / 776 green.

### S3 — T15 · Agenda: "Todo el día" section + per-day activity indicators (F)

PRD `prd_improvement` A-12, A-13. `WeekAgenda.kt`, `DayAgenda.kt` and
`CalendarChrome.kt` already exist.

### S4 — T16 · Swipe to change week (F)

PRD `prd_improvement` A-9. The agenda exists; the horizontal pager gesture does not.

### S5 — T17 · Notification delete UI: action per row, "borrar todas" (F)

PRD `prd_improvement` N-5, N-8, N-9. `NotificationsScreen.kt` had no delete gesture,
and `NotificationsViewModel.markRead` used to keep the item in the list
(`it.copy(isRead = true)`).

**User decision, verbatim (2026-10-31):** "B solamente" — answering the fork left open
by S1. The list becomes an **unread inbox**: `NotificationsApi.list` sends
`unreadOnly=true`, so a read notification does not come back on refresh. Consequence
accepted with the choice: read notifications can no longer be seen at all, deleted or
not, because the backend has no "mark unread" and therefore no read-history view.

Acceptance:

- `NotificationsApi.list` takes `unreadOnly` and sends it; `refresh`/`loadMore` pass
  `true`. Backend already supports it (strict literal `true`/`false`, 400 otherwise) —
  **no backend change**.
- The row action (tap on the row, plus a trailing trash icon) calls
  `api.markRead(token, id)` and **removes the item**, decrementing `total` so
  "Cargar más" does not stay enabled for rows that no longer exist. On failure the row
  stays and the error goes to the snackbar.
- "Borrar todas" calls `api.markAllRead(token)` behind an `AlertDialog` confirmation
  and empties the list (`items = [], total = 0`). The top-bar action becomes the trash
  icon — under an unread inbox "marcar todas" and "borrar todas" are the same call.
- **No "Deshacer" (N-8 is optional).** There is no "mark unread" endpoint, so an undo
  could only re-insert the row locally and would silently vanish on the next refresh.
  Shipping that affordance would be a false promise; it is deliberately left out.
- No swipe gesture: N-5 allows "swipe **and/or** action menu"; the action is the
  per-row trash icon. Swipe would need `SwipeToDismissBox` + animation state for no
  behavioural gain.
- Empty state now reads "No tenés notificaciones nuevas" and explains that read ones
  are not shown again.
- The bell badge follows `unread-count`, which already drops once the rows are read.

### S6 — T35 · Compliance formula: exclude `cancelled`, fix the period copy (B+F)

PRD `prd_improvement` H-6 / OQ1, ruling R15. The period stays the **calendar month**
(the PRD's "90 days" is amended). The denominator currently counts soft-deleted
activities — the query never filtered `deleted_at IS NULL`. Exclude `cancelled`, write
the formula next to the endpoint, and replace the raw period string (`"2026-10"`) with
`"Este mes"` in the card.

### S7 — T21 · The status-aware action matrix on the detail sheet (F+B)

**The matrix is the spec.** R3, verbatim (the `prd_improvement` five-state matrix is
discarded, not translated):

| Estado | Quién actúa | Botón | → | Si pasan 36 h sin acción |
|---|---|---|---|---|
| `created` | cualquier miembro | **Asignar** | `assigned` | → `overdue` |
| `assigned` | asignado | Confirmar / No asiste | `assisting` / `not_assisting` | → `overdue` |
| `assisting` | nadie | ninguno (espera automática) | `in_progress` al `scheduledStart` | |
| `in_progress` | sistema | | `verify` al fin (`scheduledEnd ?? scheduledStart`) | |
| `verify` | miembro | **Completado** | `done` | → `overdue` |
| `overdue` | asignado | **No asiste** | `not_assisting` (T8) | |
| `overdue` | — | ~~Confirmar~~ | ~~`assisting`~~ — **la aceptación tardía se quitó (2026-11-03)** | |
| `not_assisting` | cualquier miembro | **Reasignar** | `assigned` (T9, reinicia la ventana) | |
| `done` | | ninguno | terminal | |
| `cancelled` | | ninguno | terminal | |

"The buttons are decided by the SERVER" (R3, verbatim): "Los botones los decide el
servidor. Hoy el front muestra 'Finalizar' en todos los estados salvo done
(`ActividadDetailSheet.kt:378`) y deriva 'Confirmar' de una regla local que ignora el
estado (`canConfirmAssignment`, `ActivitiesModels.kt:105`). Ambos provocan 409. La matriz
llega como flags del servidor, como ya hacen `canDelete`/`canCancel`/`canDecline`."

| Flag | Vale en |
|---|---|
| `canAssign` | `created` |
| `canConfirm` | `assigned` (sólo el asignado) — **la aceptación tardía se quitó (2026-11-03)** |
| `canDecline` | `assigned` y `overdue` (sólo el asignado) |
| `canReassign` | `not_assisting` |
| `canComplete` | `in_progress` y `verify` |
| `canUnassign` | eliminado (siempre false un release) |

**The scope correction (L1 of this wave).** The plan filed T21 as front-only. It is
**not**: the backend exposes `canDelete`, `canCancel`, `canConfirm`, `canDecline` and
`canComplete` — and **neither `canAssign` nor `canReassign` exists**, and there is **no
assign or reassign route** (`activities.controller.ts` has `create`, `confirm`, `decline`,
`DELETE`, `cancel`, `complete`, and the reads). The engine is ready — `T3`
(`created → assigned`) and `T9` (`not_assisting → assigned`) are in the closed catalog with
`actors: ['bond_member']` — but nothing can reach them. The product consequence: an
activity created without an assignee can never be assigned (it only times out to
`overdue`), and an activity somebody declined is a **dead end**: it can only be cancelled.

So T21 is **F+B**, and the two halves are separated by the contract below.

**The contract (both halves implement THIS; do not invent another spelling).**

- `POST /activities/:id/assign` — body `{ "assignedTo"?: "<userId>" }`. Omitted ⇒ the
  actor takes it. `assignedTo` given ⇒ that member of the bond takes it.
- `POST /activities/:id/reassign` — body `{ "assignedTo"?: "<userId>" }`. Same rule.
- Both routes answer `200` with the updated activity, like `confirm`/`decline` do.
- Both flags are added to the **activity summary AND the activity detail** DTOs, next to
  `canComplete`: `canAssign`, `canReassign` (both `boolean`, `@Expose()`).
- `canAssign` is true **iff** `status === 'created'`. `canReassign` is true **iff**
  `status === 'not_assisting'`. Neither depends on who asks: any **member of the bond**
  may act (R3 says "cualquier miembro" for both rows). Membership is the service's job
  (`assertActivityMember`), never the policy's.
  - ⚠ **QUIÉN PUEDE REASIGNAR — y quién NO.** No es "el que la asignó": esa figura no
    existe en el modelo. La fila guarda `created_by` y `assigned_to`; un reasignado
    cambia `assigned_to` y `created_by` sigue siendo el mismo. Quien reasigna es
    **cualquier miembro del vínculo** —ni el creador ni el anterior asignado—, y la
    precondición NO es "la asignó él" sino **"el asignado la rechazó"** (el estado es
    `not_assisting`; R3: "T9 (Reasignar), que exige que el asignado haya rechazado").
    Lo que el creador conserva es **Cancelar** (`canCancel = isCreator && !isPast`,
    con motivo obligatorio), y lo que el asignado conserva son **Confirmar** y
    **No asistir** (`canRespond`: sólo el asignado, y sólo en `assigned`/`overdue`).
  - ⚠ **Lo que impide ROBAR una asignación viva NO es la ruta: es el catálogo.** No hay
    regla hacia `assigned` desde `assigned`/`assisting`/`in_progress`/`verify`, así que
    un intento ahí es un **409**. La ruta no acota el estado por sí misma: `assign` sobre
    una actividad en `not_assisting` aplica T9, y `reassign` sobre una en `created`
    aplica T3 — las dos rutas resuelven la transición por el ESTADO, como resuelve todo
    el resto del módulo. El reachable set es `{created, not_assisting}` en las dos, y
    duplicar el estado en la ruta sería una segunda copia de la regla.
- Errors, matching the module's conventions: **404** the activity does not exist or the
  caller is not a member of its bond · **409** there is no legal transition to
  `assigned` from the current status (a live assignment, a terminal state, a cancelled
  one) · **404** `assignedTo` is not an active member of the activity's bond (the message
  must not reveal whether that user exists at all).
- The transition goes through the **existing chokepoint** `ActivitiesService.transition()`,
  never a direct write: `to: ActivityStatus.ASSIGNED` with `fields: { assignedTo }`.
  `T3`/`T9` already carry `deadline: 'set'`, so the 36 h window is re-armed by
  `wireTimers` exactly like the other window changes.
- **One change to the catalog:** `T9` gains `declinedAt: false` (and `confirmedAt: false`)
  in its `sideEffects`. A reassignment must start the new assignee from a clean sheet:
  the old assignee's `declined_at` would otherwise keep the A3 decline reason on screen
  for somebody who never declined. `T3` is unchanged (a `created` activity has neither
  timestamp).

**The front half.** The detail sheet renders the matrix: "Asignar" when `canAssign`,
"Reasignar" when `canReassign`, each opening a **member picker** over the SAME
`memberCandidates` list the create form already uses (the bond's members, from
`GET /bonds` — no new request). The front model gains `canAssign` / `canReassign` with
`= false` defaults, so an older server degrades to "no buttons" instead of "buttons that
fail" (the same rule the existing flags follow). The client computes **no** flag: the
matrix is read, never re-derived.

### S11 — T20 · The Home metric cards (F)

Three cards on the Home screen, verbatim from the wave table: "Home metric cards:
Asignadas / Saldo gastos / Vencidas".

R1, verbatim: "Home metric card (H-5) | `Saldo gastos` | signed `saldo` | green if > 0,
red if < 0" and "Rule: a signed number is never rendered under a label that hides its
sign. `Por pagar` and `Por cobrar` are unsigned magnitudes; `Saldo gastos` is signed and
labelled as such. The endpoint returns `{ saldo, porPagar, porCobrar, counts: {pendientes,
aprobados, pagados, rechazados} }` so no client ever recomputes it."

R11, verbatim: "**Ruling.** Both counts are derived **client-side** from the already-loaded
bond activity list (the list is per-bond and small). Only 'Saldo gastos' needs a server
endpoint, because [the money is never recomputed in the client]."

- **"Asignadas"** — the count of activities **assigned to the current user** in the loaded
  bond list. Client-side (R11).
- **"Vencidas"** — the count of activities in `overdue` in the loaded bond list.
  Client-side (R11).
- **"Saldo gastos"** — the signed `saldo` from `GET /expenses/bond/:bondId/summary`
  (T28's endpoint, already in the front's `ExpensesApi`). Green if `> 0`, red if `< 0`.
  The client NEVER recomputes the balance (R1).
- The two counts are **pure functions** over the loaded list, with their own tests (the
  house pattern: a pure function + a source-level contract test). The balance comes from
  the server as-is; the front does not read `porPagar`/`porCobrar` for this card.
- A card that cannot be computed (no bond, no list yet) shows its **loading/unavailable**
  state — the same one the compliance card uses. It never shows `0` for "not loaded yet",
  because `0` is a number the user would read as "you have none".

### S8 — T22 · Expense policy + audit (B, depends on T14)

PRD `PRD_expense` FR-10, FR-11. Delivered as B `f2d190b`. The policy is pure, like
`ActivityPolicyService`: takes a structural subject, returns the whole decision, and
throws nothing — the calling service turns a `false` into the 403 and resolves
`counterpartId` from the bond, because an expense stores who asked, not who the other
party is.

The rules, as the PRD states them:

- FR-11 — edit and cancel: requester only, and only in `pending`.
- §5.4 — approve and reject: counterpart only, and only in `pending`. A requester does
  not approve their own expense; a third party can do nothing at all.
- §5.4 — mark paid: from `approved`, either party (the payer marks it paid, the payee
  confirms receipt).
- `paid` and `rejected` are terminal: no transition leaves them.

**Decision — `counterpartId` can be `null`.** While the bond has one member there is no
counterpart, so nobody can approve. That is deliberate: the alternative is letting a
requester approve alone because there is nobody to disagree, which makes the status
meaningless.

**Decision — the audit action is derived, not passed in.** `recordStatusChange` maps the
`from:to` pair to `expense_approve` / `expense_reject` / `expense_pay`, with
`expense_status_change` as the fallback. Letting the caller name the action lets the
audit trail lie about which transition happened.

`ExpensesAuditRepository` reuses `audit_log` with `entity_type = 'expense'` and no DDL:
the status pair goes in `old_value`/`new_value`, the readable reason in `detail`, and
FR-10's user and timestamp in their own columns. The optional `EntityManager` exists
because a transition must write expense + audit + notification in one transaction — a
rollback must not leave an audit claiming something that did not happen.

### S9 — T23 · Expense notifications via outbox fan-out + 7-day stale reminder (B, depends on T14, T22)

PRD_expense §7 and ruling R8. Five events (§7's four rows plus the reminder):

| Event | Type | Recipient |
|---|---|---|
| `expense.created` | `expense_created` | the counterpart (who has to approve) |
| `expense.approved` | `expense_approved` | the requester |
| `expense.rejected` | `expense_rejected` | the requester |
| `expense.paid` | `expense_paid` | the requester |
| `expense.stale` | `expense_stale` | the counterpart, once, after 7 days pending |

R8 says these ride the **existing** outbox → fan-out → `notification.created` socket, and
explicitly rules out a second transport. The scheduler precedent is
`notifications-retention.scheduler.ts` (BullMQ repeatable, no `setInterval`, no cron).

**Blocker found while reading the pipeline — the outbox is activity-shaped.** R8 assumed it
was generic, but `outbox_event.aggregate_id` carries `FK_outbox_event_activity` referencing
`activity(id) ON DELETE CASCADE`, the entity has an `activity` relation on it, and
`WebhookPayloadBuilder` reads `event.aggregateId` as an activity id. An expense event
literally cannot be inserted. So T23 starts with an infrastructure step that R8 did not
anticipate:

- add `aggregate_type` (`'activity' | 'expense'`), default `'activity'` so the nine
  existing rows and every activity producer keep their meaning;
- **drop** `FK_outbox_event_activity`. A polymorphic aggregate cannot carry a FK to one of
  its two possible tables. This is the price of reuse, and R8 chose reuse over a second
  transport. The `bond_id` FK stays, so no event can reference a bond that does not exist;
- index `(aggregate_type, aggregate_id)`: the reminder's anti-join and any per-aggregate
  read need it, and PostgreSQL does not index FK columns for you.

**Decision — expense events are notification-only, not webhook events.** The webhook
contract is activity-shaped (`activity.state_changed`, `data.activity`). R8 asks for
notifications and R9 puts expense changes on a socket frame, not a subscription. Emitting a
half-built `data.activity` envelope for an expense would be worse than not emitting it, so
the relay skips webhook fan-out when `aggregate_type != 'activity'`. The event is still
marked dispatched, so the notification fan-out runs exactly once.

**Decision — `aggregate_version` for expenses is the lifecycle ordinal.** `expense` has no
`version` column (T24 deliberately used compare-and-swap instead), and inventing a counter
would be a lie. `pending → 1`, `approved|rejected → 2`, `paid → 3`: the transition sequence
is monotonic, so the ordinal is derivable and honest. The CAS is still the lock.

**Decision — the reminder fires once per expense, keyed on the outbox.** `NOT EXISTS
(select 1 from outbox_event where aggregate_id = e.id and event_type = 'expense.stale')`.
Keying on the notification row would re-fire whenever fan-out is disabled; keying on the
outbox row makes "one reminder, ever" the invariant. The actor is `{type:'system'}`.

**Decision — "7 days pending" measures the expense date, not `created_at`.** The expense
carries the date the money was spent; an expense logged today for a purchase 10 days ago is
already stale, and the PRD's column is `expense_date`. The reminder window is
`expense_date <= current_date - 7` with `status = 'pending'` and `deleted_at IS NULL`.

---

### S10 — T24 · Expenses write API (B, pulled forward from wave 3, depends on T14, T22)

Pulled into this wave because **T19 (front, in this wave) is blocked on it**: the
detail screen cannot be built against an API that does not exist. Delivered as B
`7975695` (repository + DTOs) and B `0aa8742` (service + controller + module).

Seven routes, all behind `JwtAuthGuard`:

| Route | PRD |
|---|---|
| `POST /expenses/:bondId` | §5.3 · create, born `pending` |
| `GET /expenses/:id` | detail + the policy decision for the viewer |
| `PATCH /expenses/:id` | FR-11 · edit |
| `DELETE /expenses/:id` | FR-11 · cancel (`deleted_at`, no purge) |
| `POST /expenses/:id/approve` | FR-10 |
| `POST /expenses/:id/reject` | FR-10, optional reason |
| `POST /expenses/:id/pay` | FR-10 |

**Decision — the response carries the decision, not just the data.** The DTO includes
`canEdit`, `canCancel`, `canApprove`, `canReject`, `canMarkPaid` and `isTerminal`
computed for the caller. The UI has to know which buttons to show without reimplementing
FR-11 in the client, and without learning the rules by hitting a 403. The rules live in
the server; the client consumes them as data.

**Decision — a transition's order is what makes it correct.** Decide with
`ExpensePolicyService`, write with compare-and-swap using the status the policy just
approved, and audit **inside the same transaction**; only then re-read. If somebody moved
the expense in between, the `UPDATE` affects 0 rows and the client gets 409 instead of an
invented status. The audit shares the `EntityManager` so a rolled-back transition never
leaves a lying audit row.

**Decision — concurrency without a new column.** `UPDATE ... WHERE id AND status =
:expectedFrom AND deleted_at IS NULL`, keyed off `affected`. No `version` column, no
schema change: reading and then writing would leave the window open.

**Decision — a non-member gets 404, not 403.** It does not confirm the expense exists
(same rule as `activityNotFound()`). A member who is not the right party does get 403.

**Decision — plain Nest exceptions, not `CodedException`.** `ACTIVITY_ERROR_CODES` is a
closed union owned by the activities swipe contract and is not polluted with another
domain. Expense clients do not need to guess from a code: the response already tells them
what they may do.

**Decision — the split accepts only the requester's share.** The other party's share is
always the complement, `round((100 - payer) * 100) / 100`. Two independent numbers would
let 60/60 through.

**Decision — `childId` and `activityId` are not accepted on create.** Neither is in the
§5.3 form, and `activity_id` comes from an attachment conversion (R6) that lands with T27
and must verify the activity belongs to the same bond. Accepting it now would let a
client point an expense at another bond's activity with nothing checked.

An empty `PATCH` is 400, not a silent no-op. `2026-02-31` passes the regex and blows up in
Postgres, so the date is re-validated against the calendar in the service.

**`markDeleted`, not `softDelete`** — `Repository.softDelete` already exists in TypeORM
with a different signature.

Tests: 43 specs, written first (RED: module missing), with the **real** policy and only
persistence mocked. Full suite 74 / 850 green, build and eslint clean. Verified on real
Postgres (dev, 14 migrations) with 47 checks: `numeric` arrives from the driver as a
STRING and the mapper converts it; a lost CAS affects 0 rows and does not overwrite; the
`deleted_at` filter hides a cancelled expense without deleting it; and a failed audit
rolls the expense back instead of leaving it orphaned.

---

## Tasks

| ID | Task | Side | Specs | Route | Status | Commit |
|---|---|---|---|---|---|---|
| T13 | "Eliminar" una notificación = marcarla leída. **Sin cambio en la base** | B | S1 | — | closed (no work needed) | — |
| T14 | Expense model migration: `category_id`, `activity_id`, `deleted_at` | B | S2 | inline | done | B `23d193b` |
| T15 | Agenda: "Todo el día" + per-day indicators | F | S3 | — | pending | — |
| T16 | Swipe to change week (pager) | F | S4 | — | pending | — |
| T17 | Notification delete UI: acción por fila + "borrar todas" (bandeja de no leídas) | F | S5 | inline | done | F `e52cc7e` |
| T35 | Compliance formula: exclude `cancelled`, period copy | B+F | S6 | — | pending | — |
| T18 | Verify window in the front | F | S7 | inline (wave 3) | done | F `8a43020` |
| T19 | Expense detail + approve/reject/pay | F | S7 | delegated (wave 3) | done | F `0e30788` |
| T20 | Home metric cards | F | S11 | inline (parent) | done | F `4d16111` |
| T21 | Status-aware action matrix | F+B | S7 | back delegated (writer, `src/activities/**`); front delegated (writer) | done | B `b7c65d2`; F `741ed6a` |
| T22 | Expense policy + audit | B | S8 | inline | done | B `f2d190b` |
| T23 | Expense notifications + 7-day reminder | B | S9 | inline | done | B `6c04ed3` |
| T24 | Expenses write API: create, detail, edit, cancel, approve/reject/pay | B | S10 | inline | done | B `7975695` + `0aa8742` |

Unblocked right now with **no dependency at all**: T14, T15, T16, T35, T17.
T13 needs no backend work at all (see S1): both endpoints it needs already exist.

---

## Unplanned fix carried into this wave

`B bcf4c67 fix(worker): boot the worker with StorageModule and honour ENV_FILE`.

The worker never started on any host: `WorkerModule` did not import `StorageModule`
(`@Global()` only exposes providers to *importers*), and it hardcoded
`envFilePath: '.env'` while `AppModule` reads `process.env.ENV_FILE`. The
notification relay and the timer engine had therefore never run anywhere, Vercel
included. Verified by watching 6 queued outbox events become 6 notification rows.
T23 depends on this.

---

## Open items

1. **`gastos-and-ux-resolution.md` revision 4 lost the backlog.** R12 sequences "MVP
   de `PRD_expense` (T14…T27)" but those numbers no longer exist in the working copy;
   they only exist in HEAD (revision 3).
2. **Three product decisions still open** (R3, R14, R16): what `overdue →
   Confirmar/No asiste` does; whether the 48 h lead time also applies to *editing* the
   date; and whether the `verify` anchor is literal (`fin + 36h`) or
   `max(fin, ahora) + 36h`. With the literal anchor, an activity that ended more than
   36 h ago enters `verify` already expired.
3. **Stray untracked file in the front repo**: `e T09-front and T12"` (308 bytes of
   `git log` output, from a botched redirect on 2026-10-30). Not deleted without the
   user's word.
---

## Log

- **L1 (user request, verbatim):** "empeza con la wave 2"

- **L2 (user request, verbatim):** "olvidate de k8s, usemos por ahora Supabase como
  prod y comopose/local como dev" — Supabase Cloud is prod, docker compose is dev,
  no third environment. Env split shipped in `B 66e8cf0`.

- **L3 (user request, verbatim):** "no lo trackees al design" — the `design` symlink
  is excluded through `.git/info/exclude`, not `.gitignore`.

- **2026-10-31 · T13 as a DB change — DELIVERED, THEN REVERTED (see L4).** Started at the wave's first task because it is the
  only one that is both dependency-free and backend. Observed RED first (6 new tests
  failing, 29 existing passing), then GREEN. Existing test
  `excludes bond-less rows when bondId is given` asserted `/is null/i` over every
  condition, which the new soft-delete invariant tripped; narrowed to
  `/bond_id\s+is\s+null/i` — the escape hatch it actually protects — instead of
  loosening it. Full suite: **71 suites / 778 tests green**; `npm run build` and
  `eslint` clean.

- **2026-10-31 · T13 verified against real Postgres — then reverted (see L4).** The
  migration was executed on `parental_coordination_test` (13 → 14), `revert`ed (column
  and index both gone), and re-applied. Real data probe inside a rolled-back
  transaction: 2 notifications, 1 soft-deleted → unfiltered count 2, list 1 row,
  unread count 1. Then applied to the **dev** database (`parental_coordination`, also
  13 → 14): without it, every notification read would fail locally with "column
  `deleted_at` does not exist", because the repository now filters on a column that
  `synchronize: false` never creates.
  (Supabase prod was never touched, so it stayed on 13 all along — nothing pending there.)

- **L4 (user correction, verbatim, 2026-10-31):** "yo te pedi en el T13 que no sea un
  cambio en el db, sino que se eliminen de la vista de notificacion. que pase a estar
  leida solamente"

- **2026-10-31 · T13 rebuilt as "no DB change" and the DB work reverted.** Took the
  user's ruling over R10: N-6 says only "does not remove the record from the database",
  and marking it read satisfies that with zero schema. Rollback, in this order:
  `git reset --mixed bcf4c67` (the `f32c865` commit survives only in the reflog),
  removed `1792732800000-AddNotificationDeletedAt.ts` and its spec, `git checkout --`
  over `src/notifications`, then a manual SQL rollback on **both** dev and test
  (`DROP INDEX IDX_notification_deleted_at`, `ALTER TABLE notification DROP COLUMN
  deleted_at`, `DELETE FROM migrations WHERE timestamp = 1792732800000`) after checking
  the column held **0 non-null values** in both databases. Both are back to 13
  migrations; `notification` is back to its 11 columns; nothing to deploy, nothing
  pending on Supabase. Post-rollback: `npx jest src/notifications
  src/database/migration-specs` → **17 suites / 207 tests green**, `npm run build` clean.
  The endpoints T17 needs already exist — `PATCH /api/notifications/:id/read` and
  `PATCH /api/notifications/read-all` — so T13 is closed with no work and T17 is
  unblocked and front-only.

- **2026-10-31 · accepted consequence.** Without server state, "deleted" means "read":
  the item leaves the list in memory and reappears (read) on the next refresh, because
  `NotificationsApi.list` sends no `unreadOnly`. Whether that is acceptable, or the
  screen should become an unread inbox (`unreadOnly=true`, and then *every* read
  notification disappears), is the one question left open on T17.

- **L5 (user decision, verbatim, 2026-10-31):** "B solamente" — the fork left open by
  S1 is closed as **B**: the notification list becomes an unread inbox
  (`unreadOnly=true`). Accepted consequence, stated before the choice and now recorded:
  read notifications can no longer be seen at all, deleted or not, because the backend
  has no "mark unread" endpoint.

- **2026-10-31 · T17 implemented (front only).** `NotificationsApi.list` gained an
  `unreadOnly` param (default `false`, sent as a query param; the backend already
  validates the strict literal) and the ViewModel passes `true` on both `refresh` and
  `loadMore`. `markRead` now removes the row and decrements `total`; `markAllRead`
  empties the list. Screen: per-row trash icon (`contentDescription` "Eliminar
  notificación"), the row tap keeps deleting, top-bar action became the trash icon with
  an `AlertDialog` confirmation, and the empty state explains that read notifications
  are not shown again. **No "Deshacer"** — N-8 is optional and an undo that cannot be
  persisted (no "mark unread" endpoint) would lie to the user. No swipe: N-5 says
  "swipe and/or action menu", and the action menu is the trash icon.

- **2026-11-01 · T14 delivered as B `23d193b`.** The root of the expenses critical path.
  Chose nullable + no backfill over `NOT NULL` (see S2): the schema cannot invent a
  category for rows that never had one, so "required" belongs to the T22 DTO.
  Structural specs first (RED: module missing), then the migration. Applied to dev and
  test — **prod (Supabase) is now one migration behind**: `npx typeorm-ts-node-commonjs
  -d src/database/data-source.ts migration:run` with the prod env is a remote write and
  needs explicit approval.

- **2026-11-01 · T22 delivered as B `f2d190b`.** 20 policy specs + 11 audit specs,
  written first (RED: module missing). Full suite 73 / 807 green, build and eslint
  clean. Caught a hole in my own spec before implementing: the subject started out as
  `{ status, requestedBy }`, which makes a counterpart and a stranger indistinguishable —
  both are "not the requester". Added `counterpartId` and a test for the bond with a
  single member.

- **2026-11-01 · tree noise.** The back worktree carries cosmetic prettier reformatting
  of 10 files I did not author (`activities.module.ts`, the two attachment files, two
  migration files, two migration specs, `activity-notification.map.ts`, its spec). No
  substantive change. Left out of every commit; not mine to decide.

- **2026-11-01 · T24 delivered as B `7975695` + `0aa8742`, pulled forward from wave 3.**
  T19 is in this wave and was blocked on it, so the backend had to come first. Split into
  two reviewable units — data access + contract, then behavior — instead of one 1.5k-line
  commit. 43 specs written first (RED: module missing), with the real policy and only
  persistence mocked. Full suite 74 / 850 green, build and eslint clean.

  Chose compare-and-swap over a `version` column: `UPDATE ... WHERE id AND status =
  :expectedFrom AND deleted_at IS NULL` with `affected` as the answer. No schema change,
  and reading before writing would have left the window open. Chose to return the policy
  decision in the response so T19 renders buttons from data instead of reimplementing
  FR-11 in Kotlin.

  Verified against real Postgres (dev, 14 migrations), not just mocks — 47 checks. The
  ones that mattered: `numeric` really does come back from `pg` as a STRING and the mapper
  converts it (`amount!` typed as `number` in the entity is a lie at runtime); a stale CAS
  affects 0 rows and leaves the row untouched; `deleted_at` removes the expense from reads
  while the row survives with its status intact; and a throwing audit rolls the expense
  back, so no orphan row.

  Deliberately **not** included: `childId`/`activityId` on create (neither is in the §5.3
  form; `activity_id` belongs to T27's attachment conversion, which must verify the bond).
  A PATCH that could null the category (the column is nullable only for historical rows).

- **2026-11-01 · dev DB was gone.** The `parental-coordination-postgres` container did not
  exist (a leftover `pc-postgres` from another project held port 5432). Brought it back
  with `docker compose up -d postgres`; the named volume still had both databases at 14
  migrations, so no data was lost.

- **2026-11-01 · next.** Critical path moved one step: **T28** (expense list, filters,
  balance) is now the unblocked backend bottleneck for T20/T29, and **T19** (front) is
  unblocked. T23 is also free (T14 + T22 done). T25 (verify state) still blocks T18/T21.
  Prod Supabase still one migration behind (`1792819200000`) and needs explicit approval.


- **2026-11-01 · T23 implementation complete; review consent pending.** B `6c04ed3` adds transactional expense-created/status events, a daily BullMQ stale reminder using an anti-join and `FOR UPDATE SKIP LOCKED` in one transaction, and activity-only webhook/realtime gates. Verification: 78 suites / 901 tests, build, targeted ESLint/Prettier, and diff check passed. A rolled-back local test-Postgres probe accepted the stale-selection/locking SQL. Independent scratch verification found no candidate-caused blocker (74 focused tests and build passed); it did not run the full suite or a concurrent multi-worker DB probe. Receipt-driven review is enabled; the candidate-specific review consent remains unanswered, so T23 is not marked done.


- **2026-11-01 · T23 review acknowledged.** The user granted candidate-specific review consent. The reliability lens returned no findings; exact native acknowledgement succeeded and burned the review authority for this candidate. No correction was required. T23 is complete.


- **2026-11-03 · L2 — the wave reopens: T20 and T21.** The user asked for T20/T21 and
  for a PR from `develop` to `main`, and dropped the prod migration item ("forget about
  supabase for now"). Two facts found while reading the code, both recorded in S7/S11:

  1. **T21 was filed as front-only and is not.** The plan assumed the server already
     exposed the flags. It does not: `ActivityPolicyService` computes `canDelete`,
     `canCancel`, `canConfirm`, `canDecline` and `canComplete` — there is **no
     `canAssign` and no `canReassign`**, and `activities.controller.ts` has **no assign
     and no reassign route** (`create`, `confirm`, `decline`, `DELETE`, `cancel`,
     `complete` and the reads). The engine was ready — `T3` (`created → assigned`) and
     `T9` (`not_assisting → assigned`) are in the closed catalog with
     `actors: ['bond_member']` — but nothing could reach them. Consequence: an activity
     created without an assignee could never be assigned (it only timed out to
     `overdue`), and an activity somebody declined was a **dead end** (cancel only).
     The wave-3 log (L18) had claimed "the back exposes all three" — it does not, and
     that sentence was the reason the task looked front-only.
  2. **The wave-3 claim that the assignment happens at creation is only half true.**
     `activities.service.ts:402` reads `dto.assignedTo ?? null` and
     `resolveInitial(assignedTo != null)` — an activity created WITH an assignee starts
     in `assigned`, and one created WITHOUT it starts in `created`, where nothing can
     pick it up. That is why the `created` row of the matrix was reachable in the data
     but dead in the product.

  So T21 became **F+B**: the back half adds the two flags and the two routes, the front
  half consumes them. The contract between the halves is written out in S7 and both
  halves implement exactly that.

  **T20** (the three Home metric cards) stayed front-only, as filed. Two decisions the
  plan did not spell out, made here and recorded in S11:
  - **"Asignadas" and "Vencidas" count MY activities**, not the bond's: the Home is a
    personal panel and the balance already comes signed from the current user's point of
    view (R1). "Vencidas" is the late slice of "Asignadas" — the same load, overdue.
  - **A metric that could not be computed shows a dash, never a `0`.** A `0` the user
    reads as "you have none" is worse than a hole: `null` (no list, no session user id)
    and `0` (you really have none) are different facts.

- **2026-11-03 · L3 — T20 y T21 entregadas.** **T20** → F `4d16111` (11 archivos,
  671+/14−): las tres tarjetas del Home, el conteo en cliente (`HomeCounts.kt`, función
  pura) y el plegado de las tres fuentes en el resumen. **T21** → B `b7c65d2` (la mitad
  del back) y F `741ed6a` (la mitad del front).

  **Back (B `b7c65d2`, 10 archivos, 486+/3−).** Las dos flags en la política (PURA: sin
  repositorio ni servicios) y en los DOS DTO (resumen y detalle); las dos rutas
  `POST /:id/assign` y `POST /:id/reassign` con `assignedTo` opcional (sin destino la
  toma el actor); la transición pasa por el ÚNICO chokepoint `transition()`; y **T9 gana
  `declinedAt: false` y `confirmedAt: false`** — un reasignado arranca en limpio, o el
  motivo de rechazo del anterior quedaba en pantalla para alguien que nunca rechazó.
  `buildTransitionFields` limpia también el `declined_reason`. La respuesta es ESTRECHA
  (`{ id, status, assignedTo }`), como las demás acciones del módulo: devolver la fila
  publicaba `version`, `bondId` y `deletedAt` — columnas internas que ningún cliente
  necesita. Es una corrección del padre sobre el trabajo entregado. Suite: **84 suites /
  1026 tests**, `tsc` limpio, eslint limpio, prettier limpio.

  **Front (F `741ed6a`, 8 archivos, 913+).** Los dos flags en el modelo (con `= false`
  por defecto: un servidor viejo degrada a "sin botones", no a "botones que fallan"), las
  dos llamadas con el cuerpo armado CAMPO POR CAMPO (`{}` sin destino), los dos botones
  en la hoja de detalle y un selector que REUSA el `memberCandidates` del alta — sin
  petición nueva y sin una segunda derivación de candidatos. Suite: **421 tests** en
  `testDebugUnitTest`, 0 fallos; `:androidApp:assembleDebug` en verde.

  **Una corrección del padre sobre el trabajo entregado.** La primera versión parcheaba
  el detalle en el cliente: `status` y `assignedTo` del servidor, y `canAssign`/
  `canReassign` en `false` **derivados por el cliente**. Eso es una segunda copia de la
  política — exactamente lo que la regla del repo prohíbe ("Si el cliente derivara un
  flag, habría dos copias de la política y divergirían en cuanto cambiara una") — y
  dejaba `canConfirm` viejo: el que se asigna la actividad no veía su "Confirmar
  asignación" hasta reabrir la hoja. Ahora, tras la acción, el detalle se **vuelve a
  PREGUNTAR** al servidor (un GET, sin pasar por `Loading`) y el cliente no calcula
  ningún flag. El test nuevo lo fija: tras asignarse a sí mismo, el `canConfirm` que
  muestra la hoja es el que mandó el servidor.

  **Lo que NO se verificó:** nada de esto se vio en un dispositivo. El APK se compiló y
  la suite está verde, pero el botón "Asignar" con su selector no se ha abierto en el
  teléfono; y sin el back publicado, un servidor real todavía no manda las flags
  (`canAssign`/`canReassign` vienen en `false` por defecto ⇒ sin botones).

- **2026-11-03 · L4 — la revisión del riesgo 2 (autorización) y una corrección de
  contrato.** El usuario preguntó si `canReassign` era para "el que la asignó". **No**:
  quien reasigna es cualquier miembro del vínculo, y la precondición es que **el
  asignado la rechazó** (el estado `not_assisting`), no quién la asignó. Esa figura no
  existe en el modelo: la fila guarda `created_by` y `assigned_to`, y un reasignado
  cambia `assigned_to` mientras `created_by` sigue siendo el mismo. El creador conserva
  **Cancelar** (con motivo) y el asignado conserva **Confirmar** / **No asistir**. Es lo
  que dice R3 ("cualquier miembro" en las dos filas; "T9 … que exige que el asignado
  haya rechazado"), y el catálogo de transiciones ya lo decía antes de esta ola: `T3` y
  `T9` llevan `actors: ['bond_member']` desde que se escribieron.

  **El riesgo 2 revisado punto por punto.** El permiso NO lo decide el front: lo
  calculan `canAssign`/`canReassign` en la política pura del servidor, y lo que impide
  robar una asignación viva NO es la ruta sino el **catálogo**: no hay regla hacia
  `assigned` desde `assigned`/`assisting`/`in_progress`/`verify`, así que un intento ahí
  es un 409 (probado). El objetivo tiene que ser un miembro activo del vínculo, y si no
  lo es es un 404 **sin revelar si ese usuario existe** — la misma regla que usa el
  módulo de gastos. El guard de "tarde" (T8) no aplica aquí: no hay aceptación tardía
  en juego, hay una asignación nueva. Y el actor no puede salirse del chokepoint: la
  transición pasa por `transition()`, que valida la regla, el actor y la versión.

  **La corrección.** La spec decía "409 — el estado no es el que la ruta sirve". Eso NO
  es lo que hace el código, y la diferencia no era cosmética: **las dos rutas son
  intercambiables**. `assign` sobre una actividad en `not_assisting` aplica T9, y
  `reassign` sobre una en `created` aplica T3, porque ambas resuelven la transición por
  el ESTADO (como todo el módulo) y no por la ruta. Comprobado con una sonda descartable
  antes de decidir, no de memoria. Lo que NO cambia es el conjunto alcanzable
  (`{created, not_assisting}`) ni el 409 de una asignación viva. La decisión fue
  **corregir la spec y no el código**: acotar el estado en la ruta duplicaría la regla
  del catálogo en otro capa —justo el "dos copias de una regla" que este repo viene
  arrastrando— y el front nunca cruza las dos (llama `assign` sólo con `canAssign` y
  `reassign` sólo con `canReassign`). Los dos casos cruzados quedan **fijados en
  tests** (`applies T9 when the activity is in not_assisting` y `applies T3 when the
  activity is in created`), para que el día que alguien duplique el estado en la ruta
  el test lo delate.

- **2026-11-03 · L5 — la verificación independiente de la autorización (el recibo del
  riesgo 2).** Declarar `Risk: item 2` obliga a una verificación independiente, y no
  estaba hecha: se lanzó un revisor adversarial sobre la superficie de autorización de
  `assign`/`reassign`, con la consigna de no creerse el modelo sino intentarlo.

  **Veredicto: ningún defecto.** SeRenew ataron doce vías de entrada y las doce
  quedaron bloqueadas, sin una sola escritura: llamador no miembro · llamador que dejó
  el vínculo · vínculo inactivo · objetivo que dejó el vínculo · objetivo de otro
  vínculo · objetivo que no existe · actividad borrada · actividad de otro vínculo ·
  los cuatro estados de asignación viva (`assigned`/`assisting`/`in_progress`/`verify`) ·
  los terminales y `overdue` · la doble asignación concurrente (un 200 y un 409 por el
  CAS). El revisor dio la cadena de código que lo hace imposible: el controlador → el
  servicio → `loadForMember` → el `WHERE deleted_at IS NULL` + `member.left_at IS NULL AND
  bond.is_active` → el chokepoint → el `FOR UPDATE` con el CAS.

  **Cinco huecos sin fijar, ahora cerrados (B `12ae188`).** La respuesta estrecha (nada de
  `version`, `bondId` ni `deletedAt`); el **actor** en el payload del outbox (una
  reasignación es atribuible); el 409 de cada estado vivo, vencido y terminal **en las dos
  rutas**; la lectura sin las filas borradas (el guard del soft delete vive en el `WHERE`
  del repositorio, y ahora está fijado que la ruta usa esa lectura); y la pertenencia del
  objetivo acotada al vínculo. En el catálogo, las cuatro reglas que impiden volver a
  `assigned` desde una asignación viva.

  **Un endurecimiento del tipo.** `assignedTo` estaba en `ApplyTransitionFields` sólo por
  laintersección ad-hoc del servicio: la escritura sobrevivía porque el repositorio hace
  `Object.assign(row, input.fields)`, o sea que el TIPO no sabía de la columna que
  estaba escribiendo. Ahora `assignedTo` está declarado en el tipo.

  **Un defecto MÍO, corregido.** `262888b` había dejado una violación de `prettier` en el
  spec del servicio (una línea de más de 100 caracteres). El gate con `--fix` la tapaba;
  el `prettier --check` sin `--fix` la ve. Corregida.

  **Un hallazgo preexistente, NO de este trabajo (queda reportado, no se toca).** El webhook
  **pierde el actor**: `payload-builder.ts:107` hace `actor: asString(raw.actor) ?? ''`
  pero el actor del outbox es un objeto `{type, id, role}`. La fila del outbox SÍ lo
  lleva (la auditoría de la transición es correcta); lo que se pierde es la copia que va
  al webhook. El spec del webhook se pasa a mano un actor de tipo string, así que nunca
  lo atrapa. Es de la ola 3, no de T21, y arreglarlo cambiaría el contrato de TODOS los
  eventos — no se toca en este alcance.

  **Dos notas de endurecimiento que no se tocan (y por qué).** El chequeo de pertenencia
  del objetivo queda FUERA de la transacción: un miembro que saliera entre el chequeo y el
  commit podría ser asignado — inofensivo, porque un ex-miembro recibe 404 en la actividad.
  Y `canAssign`/`canReassign` no llevan `isPast` (a diferencia de `canDelete`/`canCancel`):
  las rutas tampoco lo llevan, así que bandera y ruta coinciden, y el barrido de 15 min
  sigue avanzando la actividad — no queda ningún estado zombi.
