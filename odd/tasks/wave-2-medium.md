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
object and gets its own expense row). The `expense` table exists and has none of the
three columns. Acceptance: one additive, idempotent migration + the entity fields +
a structural migration spec, in the shape of `AddActivityCategoryId`.

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

### S7 — T18, T21 (F, blocked on T25) · T19 (F, blocked on T24) · T20 (F, blocked on T28)

Not startable in this wave: their backend prerequisite is wave 3.
**T25 is the backend bottleneck** — `verify` is declared in the activity status enum
but no path enters or leaves it; the engine goes `in_progress → done` directly.

### S8 — T22 · Expense policy + audit (B, depends on T14)

PRD `PRD_expense` FR-10, FR-11. The `audit_log` table and the `audit` module exist;
mirror `activity-policy.service.ts`.

### S9 — T23 · Expense notifications via outbox fan-out + 7-day stale reminder (B, depends on T14, T22)

Ruling R8: expense notifications ride the existing outbox → fan-out pipeline. The
scheduler precedent exists and, since `bcf4c67`, the worker actually boots.

---

## Tasks

| ID | Task | Side | Specs | Route | Status | Commit |
|---|---|---|---|---|---|---|
| T13 | "Eliminar" una notificación = marcarla leída. **Sin cambio en la base** | B | S1 | — | closed (no work needed) | — |
| T14 | Expense model migration: `category_id`, `activity_id`, `deleted_at` | B | S2 | — | pending | — |
| T15 | Agenda: "Todo el día" + per-day indicators | F | S3 | — | pending | — |
| T16 | Swipe to change week (pager) | F | S4 | — | pending | — |
| T17 | Notification delete UI: acción por fila + "borrar todas" (bandeja de no leídas) | F | S5 | inline | done | F `cca2715` |
| T35 | Compliance formula: exclude `cancelled`, period copy | B+F | S6 | — | pending | — |
| T18 | Verify window in the front | F | S7 | — | blocked on T25 | — |
| T19 | Expense detail + approve/reject/pay | F | S7 | — | blocked on T24 | — |
| T20 | Home metric cards | F | S7 | — | blocked on T28 | — |
| T21 | Status-aware action matrix | F | S7 | — | blocked on T18, T25 | — |
| T22 | Expense policy + audit | B | S8 | — | pending (after T14) | — |
| T23 | Expense notifications + 7-day reminder | B | S9 | — | pending (after T14, T22) | — |

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

- **2026-10-31 · next.** Backend root of the critical path is T14 (`T14 → T22 → T24 →
  T28 → T29/T27 → T19/T20`). T17 landed as F `cca2715`: 35 suites / 294 tests
  green, `assembleDebug` clean.
