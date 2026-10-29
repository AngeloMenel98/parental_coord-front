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
| T2 | S2, S4 | inline | pending | — |
| T3 | S5 | inline | pending | — |
| T4 | S3 | inline | pending | — |
| T5 | S8 | inline | pending | — |
| T6 | S6 | inline | pending | — |
| T7 | all | inline | pending | — |

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
