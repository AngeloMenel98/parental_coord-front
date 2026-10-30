# Wave 0 — Quick wins (T01–T04)

Objective: deliver the cheapest wave of the `gastos-and-ux-resolution` program — four
front-only tasks with no schema, no new endpoint, and no dependency on the expenses
vertical.
Branch: `feat/wave-0-quick-wins`, forked from `sdd/activity-swipe-actions` @ `d18ed1e`.
Delivery: one work-unit commit per task; tests and docs travel with the behavior they
cover.
Test runner: `./gradlew :shared:testDebugUnitTest` (common + Android unit tests).
Program source: `odd/tasks/gastos-and-ux-resolution.md` (revision 3) — rulings R1–R15,
waves 0–4, critical path.

Scope note: this document covers **wave 0 only**. Wave 1 (T05–T12) is the next
authorized slice and gets its own document; the rulings it needs (R4, R5, R7, R15) are
already written down in the resolution.

---

## Specs

### S1 — T01 · Remove the "Confirmada" chip from the activity card

User, verbatim:

> "T01 remove "Confirmada" chip"

PRD `prd_improvement` §6.1, verbatim:

> "**A-1:** **Remove the "Confirmada" chip.**"

Resolution `gastos-and-ux-resolution.md` R4, verbatim:

> "**Ruling.** Remove the chip from the **card** (A-1) — it duplicates the status pill
> and is per-assignee information shown on a per-activity surface. The derived flag
> survives and is shown in the **detail sheet** as a per-assignee line, where it is
> meaningful. Nothing is lost and no schema changes."

Resolution task row, verbatim:

> "| T01 | Remove the "Confirmada" chip from the activity card | F | A-1 (R4) | 1h |
> Built: `ActivityCard.kt:276-298`. Data already derived server-side. Test: card renders
> no "Confirmada". |"

Observable outcome: the activity card never renders a "Confirmada" chip, and the detail
sheet keeps announcing the per-assignee confirmation.

### S2 — T02 · Populate `compliance` in `HomeSummary`

User, verbatim:

> "T02 wire compliance into HomeSummary"

Resolution task row, verbatim:

> "| T02 | Populate `compliance` in `HomeSummary` | F | H-6 | 2h | Built:
> `HomeApi.getCompliance` + `ComplianceResponse` model exist, but `getHomeSummary`
> returns `compliance = null`. |"

PRD `prd_improvement` H-6, verbatim:

> "**H-6:** Define and document the formula for "cumplimiento" (see Open Questions)."

Observable outcome: `HomeApi.getHomeSummary(token)` returns a `HomeSummary` whose
`compliance` is the bond's compliance payload, and the Home screen renders that card
without a second round trip.

### S3 — T03 · `childrenIds` in the front activity model + child names

User, verbatim:

> "T03 childrenIds in the model"

Resolution task row, verbatim:

> "| T03 | Expose `childrenIds` in the front activity model + resolve child names | F |
> A-3, D-5 | 2h | Built: backend DTO already exposes `childrenIds`; front `Actividad`
> lacks the field. Names come from `ActivitiesViewModel.children` (already loaded). |"

PRD `prd_improvement`, verbatim:

> "**A-3:** Each card shows the **child/children** linked to the activity. If more than
> one, show avatars or a compact chip list (`+N` if it overflows)."

> "**D-5:** Show **children** linked to the activity (names/avatars)."

Observable outcome: an activity deserialized from `GET /activities/bond/:bondId` carries
the backend's `childrenIds`, and one resolver turns those ids into the child names the
view already has loaded.

### S4 — T04 · "Fin programado" in the create form + end-after-start validation

User, verbatim:

> "T04 "Fin programado" in the create form"

PRD `prd_improvement` §6.4, verbatim:

> "**A-14:** Add an optional field **"Fin programado"** (date + time)."
> "**A-15:** Validation: end must be after start. Show inline error in Spanish."
> "**A-16:** If empty, the activity has no end time and displays only the start time."

Resolution task row, verbatim:

> "| T04 | "Fin programado" in the create form + end-after-start validation | F |
> A-14…A-16 | 3h | Built: backend `create-activity.dto.ts:57-60` already accepts
> `scheduledEnd`; front `CreateDateConversion.kt:91` hardcodes `scheduledEnd = null`.
> Front-only. |"

Observable outcome: the create form collects an optional end date + end time, refuses to
submit an end that is not after the start (inline, in Spanish), and sends
`scheduledEnd` in the POST body when the field is filled.

---

## Tasks

| ID | Task | Specs | Route | Status | Commit |
|---|---|---|---|---|---|
| T01 | Remove the "Confirmada" chip from the activity card | S1 | inline | done | `feat(activities): remove the Confirmada chip from the activity card` |
| T02 | Populate `compliance` in `HomeSummary` | S2 | inline | pending | — |
| T03 | `childrenIds` in the front model + child-name resolver | S3 | inline | pending | — |
| T04 | "Fin programado" in the create form + end-after-start validation | S4 | inline | pending | — |

---

## Log

- **L1 (user request, verbatim):** "look for the last engram session about improvements,
  Cheapest → most expensive, in waves:

  XS (1-3 h) — T01 remove "Confirmada" chip · T02 wire compliance into HomeSummary · T03
  childrenIds in the model · T04 "Fin programado" in the create form

  S (3-6 h) — T05 currency formatter · T06 localize dates · T07 assignee on card · T08
  children on card · T09 detail: date+children+assignee · T10 Spanish notification copy ·
  T11 children filter · T12 global top bar + bell + logout

  M (1-1.5 d) — T13 notification soft delete (back) · T14 expense migration · T15 "Todo el
  día" + day dots · T16 swipe week · T17 notification delete UI · T35 compliance formula ·
  T18 verify front · T19 Gastos detail · T20 Home metric cards · T21 status-aware buttons ·
  T22 expense policy+audit · T23 expense notifications

  L (2-3 d) — T24 expense transitions API · T25 verify window (back) · T26 realtime
  expense.changed · T27 Gastos create form · T28 expenses API + balance · T29 Gastos
  list+cards · T30 attachment→expense · T31 design pass

  XL (3-8 d) — T32 filters+search+dispute · T33 custom splits+export · T34 OCR (deferred)

  Total ≈ 49 person-days. Critical path: T14 → T28 → T29 → T30 (expenses) and T25 → T18
  → T21 (verify). Waves 0-1 (≈5 d) are free of dependencies — start there. and start from
  the least expensive"

- **L2:** The last Engram session about this program was recovered before writing
  anything: the resolution document `odd/tasks/gastos-and-ux-resolution.md` (revision 3,
  written 2026-10-30 01:30) plus its two source PRDs (`/home/angelo/prd_improvement.md`,
  `/home/angelo/PRD_expense.md`). Wave 0 is taken from that document's "Tasks — least to
  most expensive" table, rows T01–T04. The user's wave list and the resolution agree
  task-for-task.
- **L3:** Baseline read before the first write: `ActivityCard.kt` (chip at :276-298,
  rendered from the `assignedConfirmed` branch at :205/:215), `HomeApi.kt`
  (`getHomeSummary` returns `compliance = null`; `getCompliance` exists), `HomeViewModel.kt`
  (a second `fetchCompliance` round trip), `HomeModels.kt` (`HomeSummary.compliance`
  already exists), `ActivitiesModels.kt` (`Actividad` without `childrenIds`),
  `CreateDateConversion.kt:91` (`scheduledEnd = null`), `CreateForm.kt`,
  `CreateActivitySheet.kt`, `ActivitiesViewModel.kt` (form mutations at :554-582), and the
  back repo's `create-activity.dto.ts:57-60` + `activities.service.ts:116-125`
  (`scheduledEnd must be strictly after scheduledStart`).
- **L4:** Test infrastructure is real: `commonTest` (pure Kotlin) + `androidUnitTest`
  (JVM, source-level tests — this repo has no `createComposeRule`, so UI contracts are
  asserted on the source, as `ActivityCardAccessibilityTest` already does). There is **no**
  Ktor mock engine, so every new test targets a pure function or a fake API subclass —
  the `open class` + `Fake…Api` pattern already used by `NotificationsViewModelTest`.
- **L5 — T01 evidence:** RED first: `ActivityCardConfirmedChipTest` (new,
  `androidUnitTest`, source-level) failed 3 of 4 — the card still painted "Confirmada",
  still had the `ConfirmedChip` composable and still branched on `assignedConfirmed`; the
  fourth assertion (the detail sheet keeps "Asignación confirmada") passed from the start.
  GREEN: the chip and its branch are gone from `ActivityCard.kt` (the `assignedConfirmed`
  field is untouched — the swipe policy and the detail sheet still use it), and the dead
  `Modifier.size` import went with it. Suite: 24 suites / 208 tests / 0 failures.
- **L6 — slice boundary:** the wave-0 work starts at the fork point `d18ed1e`
  (`sdd/activity-swipe-actions`). The branch's diff against `origin/main` also contains the
  33 commits of the swipe program inherited from that base; wave 0's own commits are the
  four work units after `d18ed1e`, one per task.
