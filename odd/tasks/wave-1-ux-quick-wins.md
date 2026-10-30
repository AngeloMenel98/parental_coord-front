# Wave 1 — UX quick wins (T05–T12)

Objective: the second-cheapest wave of the `gastos-and-ux-resolution` program — eight
"one file, one surface" tasks (3–6 h each). No schema, no new endpoint, no dependency on
the expenses vertical; **Waves 0 and 1 are free of dependencies and can be delivered
immediately**.
Branch: `feat/wave-0-quick-wins` (the wave-0 branch — the program is one branch; wave 0
is closed at `1f4d357`).
Two repos: **F** = front `Documentos/parental_coord-front` (Kotlin Multiplatform Compose),
**B** = back `Documentos/parental_coordination_back` (NestJS + TypeORM + Postgres).
Delivery: one work-unit commit per task; tests and docs travel with the behavior.
Test runners: front `./gradlew :shared:testDebugUnitTest`; back `pnpm test` (jest).
Program source: `odd/tasks/gastos-and-ux-resolution.md` (revision 3) — rulings R1–R15.

Scope note: wave 0 (T01–T04) is closed in `odd/tasks/wave-0-quick-wins.md`; this
document starts where that one stopped, and the spec numbering continues (S5…S12).

---

## Specs

### S5 — T05 · Locale-aware currency formatter, 2 decimals, everywhere

User, verbatim (wave list):

> "T05 currency formatter"

Resolution task row, verbatim:

> "| T05 | Locale-aware currency formatter, 2 decimals, everywhere | F | H-7, FR-6,
> Issue #1 | 3h | New: no money formatter exists in the front at all. Add + unit tests. |"

PRD `prd_improvement` H-7, verbatim:

> "**H-7:** Currency formatting is consistent (`$632,80` everywhere, 2 decimals,
> locale-aware)."

PRD `PRD_expense` FR-6, verbatim:

> "**FR-6:** Amounts use a single consistent currency format (see Issues)."

PRD `PRD_expense` Issue #1, verbatim:

> "**Inconsistent number formatting:** `$16.80` in the summary, `$16,8` in the list. | Use
> one locale-aware formatter with 2 decimals everywhere. |"

Observable outcome: ONE formatter in the front that renders money with two decimals and
the locale's separators, with unit tests pinning the separators, the decimals and the
sign. It has no consumer yet — the Gastos screens that will use it are T29/T33.

### S6 — T06 · Localize relative dates and fix truncated dates

User, verbatim (wave list):

> "T06 localize dates"

Resolution task row, verbatim:

> "| T06 | Localize relative dates ("1d ago") and fix truncated dates ("05 dé") | F | Issues
> #2, #3 | 3h | Built: `NotificationTimeFormatter.kt`, `TimelineFormatters.kt`. |"

PRD `PRD_expense` Issues #2 and #3, verbatim:

> "**Mixed language:** UI is Spanish but dates read "1d ago", "10 ago", "05 dé". | Fully
> localize relative and absolute dates. |"

> "**Truncated / malformed date** on the Clases de natación row ("05 dé"). | Fix date
> formatting and truncation. |"

PRD `PRD_expense` §9, verbatim:

> "Relative dates for recent items ("1d ago") must be localized."

Observable outcome: the front renders relative dates in Spanish ("hace 1 d", "hace 2 h")
and absolute dates without the truncated month abbreviation that produced "05 dé".

### S7 — T07 · "Asignada a: [name]" on the activity card

User, verbatim (wave list):

> "T07 assignee on card"

Resolution task row, verbatim:

> "| T07 | "Asignada a: [name]" on the activity card | F | A-2 | 3h | Built: `assignedTo`
> (user UUID) + `memberNames` already in the VM. Resolve name client-side. |"

PRD `prd_improvement` A-2, verbatim:

> "**A-2:** Each card shows **"Asignada a: [name]"**."

Observable outcome: the activity card shows who it is assigned to, resolving the
`assignedTo` UUID through the `memberNames` map the ViewModel already holds.

### S8 — T08 · Children on the activity card (avatars / `+N` chip)

User, verbatim (wave list):

> "T08 children on card"

Resolution task row, verbatim:

> "| T08 | Children on the activity card (avatars / `+N` chip) | F | A-3 | 3h | Depends on
> T03. |"

PRD `prd_improvement` A-3, verbatim:

> "**A-3:** Each card shows the **child/children** linked to the activity. If more than
> one, show avatars or a compact chip list (`+N` if it overflows)."

Observable outcome: the card shows the children of the activity, using the `childrenIds`
that T03 put on the model and the `resolveChildNames` seam T03 added.

### S9 — T09 · Detail sheet: date + time range, children, assignee

User, verbatim (wave list):

> "T09 detail: date+children+assignee"

Resolution task row, verbatim:

> "| T09 | Detail sheet: date + time range, children, assignee | F+B | D-4…D-6 | 5h |
> Mostly built; the detail DTO lacks `childrenIds` → +2h on the back. |"

PRD `prd_improvement`, verbatim:

> "**D-4:** **Date includes time** (e.g. `30 oct 2026 · 16:30`). If a scheduled end exists,
> show the range."

> "**D-5:** Show **children** linked to the activity (names/avatars)."

> "**D-6:** Show "Asignada a"."

Observable outcome: the detail sheet shows when (with the range when there is a scheduled
end), who it is assigned to, and which children it concerns. The assignee line already
exists (`ActividadDetailSheet.kt`); the children need `childrenIds` on the back's detail
DTO first.

### S10 — T10 · Notification copy: Spanish, title ≠ body, length-capped

User, verbatim (wave list):

> "T10 Spanish notification copy"

Resolution task row, verbatim:

> "| T10 | Notification copy: Spanish, title ≠ body, length-capped | B | N-1…N-4 (R5) | 5h |
> Built: 8 templates in `activity-notification.map.ts`, English, `title === body`. Split the
> template and rewrite the 6 example rows. Update specs. |"

PRD `prd_improvement` N-1…N-4, verbatim:

> "**N-1:** Rewrite titles and descriptions to be clear, short and actionable, in Spanish."
> "**N-2:** Pattern: **Title** = what happened (≤ 40 chars). **Description** = who/what/when
> + next step (≤ 100 chars)."
> "**N-3:** Include the activity or expense name and the actor where relevant."
> "**N-4:** Copy lives in a central i18n file to ease future review/translation."

And the six example rows, verbatim:

> "| Activity assigned | Nueva actividad asignada | María G. te asignó "Clases de natación"
> para el 05/11 a las 17:00. |"
> "| Activity overdue | Actividad vencida | "Reunión escolar" venció ayer. Reprográmala o
> márcala como finalizada. |"
> "| Expense requested | Nuevo gasto por aprobar | Carlos R. solicitó $136,00 por "Excursión
> escolar". Revísalo. |"
> "| Expense approved | Gasto aprobado | Tu gasto "Medicamento, antibiótico" fue aprobado. |"
> "| Expense paid | Gasto pagado | Se registró el pago de $224,00 por "Clases de natación". |"
> "| Activity completed | Actividad finalizada | Carlos R. finalizó "Entrega de
> documentos". |"

Ruling R5, verbatim:

> "**Ruling.** The copy catalogue is a **backend** module — a typed map from event type to
> `{ title, body }` in Spanish — because that is where the text is produced. The front
> renders `notification.title` / `notification.body` verbatim; it never formats notification
> copy."

Observable outcome: every activity notification the server produces carries a Spanish
title (≤ 40 chars) and a Spanish body (≤ 100 chars) that are DIFFERENT strings, and the
templates live in one typed catalogue.

### S11 — T11 · Children filter in Activities

User, verbatim (wave list):

> "T11 children filter"

Resolution task row, verbatim:

> "| T11 | Children filter in Activities ("Todos" + one per child, multi-select) | F |
> A-5…A-7 | 5h | Built: `ActivitiesViewModel.children: List<VinculoChild>` already loaded →
> pure client-side filter. |"

PRD `prd_improvement`, verbatim:

> "**A-5:** A children filter appears **only if the family has more than one child**."
> "**A-6:** Options: "Todos" (default) + one option per child. Multi-select **[Assumption]**."
> "**A-7:** The filter persists during the session and combines with other filters."

Observable outcome: with more than one child the Activities screen offers a children
filter ("Todos" + one chip per child, multi-select); with one child or none it does not
appear; the selection survives the session and combines with the existing filters.

### S12 — T12 · Global top bar: bell + unread badge + logout with confirmation

User, verbatim (wave list):

> "T12 global top bar + bell + logout"

Resolution task row, verbatim:

> "| T12 | Global `AppTopBar`: bell + unread badge + logout with confirm | F | G-1…G-5 | 6h
> | Built: each screen owns its `TopAppBar`; logout exists only on Home/Admin. Extract one
> composable, add 44×44 hit area, `contentDescription`, logout dialog. |"

PRD `prd_improvement` §4, verbatim:

> "**G-1:** The top bar is present in **all views**."
> "**G-2:** It contains **icons only**: notifications (bell, with unread badge) and log out."
> "**G-3:** Log out icon asks for a confirmation (dialog) before ending the session."
> "**G-4:** Both icons have accessible labels (`aria-label` / content description) and a
> minimum 44×44 pt hit area."
> "**G-5:** Bell shows an unread count badge (cap at `9+`)."

Observable outcome: ONE top bar composable used by every view, with a bell that carries
the unread badge (capped at "9+") and a logout icon that asks for confirmation before
ending the session; both icons are labelled for screen readers and are at least 44×44 dp.

---

## Tasks

| ID | Task | Specs | Repo | Route | Status | Commit |
|---|---|---|---|---|---|---|
| T05 | Locale-aware currency formatter, 2 decimals | S5 | F | delegated | done | `feat(core): add the shared money and date formatters` |
| T06 | Localize relative dates, fix truncated dates | S6 | F | delegated | done | `feat(core): add the shared money and date formatters` |
| T07 | "Asignada a: [name]" on the activity card | S7 | F | delegated | done | `feat(activities): show the assignee and the children on the card` |
| T08 | Children on the activity card | S8 | F | delegated | done | `feat(activities): show the assignee and the children on the card` |
| T09 | Detail sheet: date + time range, children, assignee | S9 | F+B | delegated | pending | — |
| T10 | Notification copy: Spanish, title ≠ body, capped | S10 | B | delegated | pending | — |
| T11 | Children filter in Activities | S11 | F | delegated | pending | — |
| T12 | Global top bar: bell + badge + logout | S12 | F | delegated | pending | — |

---

## Log

- **L1 (user request, verbatim):** "start wave 1"

  The wave definition comes from the program document: `odd/tasks/gastos-and-ux-resolution.md`
  (revision 3), "Wave 1 — S (3–6 h): one file, one surface", rows T05–T12 — the same
  rows the user pasted in the original request.
- **L2 — baseline read before the first write:** `memberNames: Map<String, String>` is
  already in `ActivitiesUiState` (`ActivitiesViewModel.kt:96`) and the detail sheet already
  prints "Asignado a" (`ActividadDetailSheet.kt:245`); `ActivityFilters.kt` exists;
  `NotificationTimeFormatter.kt` and `TimelineFormatters.kt` exist; five screens own a
  `TopAppBar` (`AdminScreen`, `NotificationsScreen`, `HomeScreen`, `ActividadDetailSheet`,
  `ActivitiesScreen`). On the back: `src/notifications/fan-out/activity-notification.map.ts`
  holds 8 English templates and `renderNotificationText` returns `title === body` by
  design; `ActivityDetailDto` has no `childrenIds`.
- **L3 — back repo baseline:** the back repo is on `ft/supabase-cloud` with **pre-existing
  uncommitted changes** (`src/worker.module.ts` — `ENV_FILE` + `StorageModule` — and
  `.atl/skill-registry.md`) plus an untracked `design/`. Those are NOT part of this wave:
  the wave-1 back commits touch only their own files, and the pre-existing dirty state is
  left exactly as it was found.
- **L4 — the two repos never build at the same time in one project:** the front build
  writes to `shared/build/`, so two front workers running gradle at once would corrupt
  each other. The front workers therefore run one after another; the back worker runs in
  parallel with them (different project, different toolchain: jest).
- **L5 — T05 + T06 evidence:** delegated to `gentle-ai-worker` (bounded surfaces); the
  parent reviewed the diff line by line and re-ran the suite. RED: the new tests did not
  compile (`formatMoney` did not exist). GREEN: 29 suites / 247 tests / 0 failures.
  What the parent changed, and why:
  - **The money formatter crashed above 1E7.** The worker built it on
    `Double.toString()`, which switches to scientific notation from `1E7` — and the
    backend's `numeric(12,2)` goes up to `9.999.999.999,99`, so a real amount would have
    died with a `NumberFormatException`. The formatter now expands the exponent before
    parsing it, and rejects absurd magnitudes loudly instead of formatting nonsense.
  - **The detail date was made worse.** The worker routed `formatDetailDate` through
    `formatLongDate`, which drops the year for the current year ("Miércoles, 5 De
    Agosto") — a detail sheet that stops saying WHEN something happened, and a shape
    that drifts from the PRD's "30 oct 2026 · 16:30". The detail date keeps its own
    format ("5 ago 2026"): day, WHOLE abbreviated month, year always. The zone became an
    injectable parameter (default = production) so the test is deterministic.
  - **The relative-time formatter moved to `core/util/`.** It was correct but lived in
    the notifications package, so the Gastos list (T29) would have had to import a
    feature package to say "hace 1 d" — and two formatters would have ended up saying
    two different things in the same family. `formatShortDate` is now named, and the
    absolute fallback is numeric on purpose: a month spelled out is what got truncated
    into "05 dé".
- **L6 — T07 + T08 evidence:** delegated to `gentle-ai-worker` (bounded surfaces); the
  parent reviewed the diff and re-ran the suite. RED: the new tests did not compile
  (`memberNames` / `children` did not exist on the card or on the label). GREEN: 31
  suites / 260 tests / 0 failures. What the parent changed: the worker had built the
  children as a row of CHIPS measured with `SubcomposeLayout`, and its degraded case is
  bad — a long child name makes NINGÚN chip fit and the card ends up showing "+1" with
  nobody's name on it. It is now ONE line ("Para: Ana, Luis" / "Para: Ana, Luis +1"),
  which says the same thing, never cuts a name in half and measures nothing. The
  semantic label (the ONE phrase the card announces) grew with the card: it now says
  "Asignada a: …" and "Para: …", so TalkBack reads them with the rest and not as loose
  fragments.
