# Wave 1 — UX quick wins (T05–T12)

Branch: `feat/wave-0-quick-wins` (one branch for the whole program).
Repos: **F** = `Documentos/parental_coord-front` (KMP Compose) · **B** = `Documentos/parental_coordination_back` (NestJS).
Program source: `odd/tasks/gastos-and-ux-resolution.md` (rev 3). PRD refs: `prd_improvement.md`, `PRD_expense.md`.

## Commands

- Front tests: `./gradlew :shared:testDebugUnitTest -q 2>&1 | tail -30` (focused: `--tests "*Name*"`)
- Front install: `./gradlew :androidApp:installDebug`
- Back tests: `corepack pnpm jest <path>`
- Do not touch the pre-existing back dirt: `src/worker.module.ts`, `.atl/skill-registry.md`, `design/`.

## Status

| ID  | Task                                               | Repo | PRD           | Status                              |
| --- | -------------------------------------------------- | ---- | ------------- | ----------------------------------- |
| T05 | Locale-aware money formatter, 2 decimals           | F    | H-7, FR-6     | done                                |
| T06 | Localize relative dates, fix truncated dates       | F    | Issues #2, #3 | done                                |
| T07 | "Asignada a: [name]" on the card                   | F    | A-2           | done                                |
| T08 | Children on the card                               | F    | A-3           | done                                |
| T09 | Detail sheet: date range, children, assignee       | F+B  | D-4…D-6       | done (verify front, see Open items) |
| T10 | Notification copy in Spanish, title ≠ body, capped | B    | N-1…N-4       | done                                |
| T11 | Children filter in Activities                      | F    | A-5…A-7       | done                                |
| T12 | Global `AppTopBar`: bell + badge + logout confirm  | F    | G-1…G-5       | **pending**                         |

## Pending: T12

Extract ONE `AppTopBar` composable used by every view, replacing the `TopAppBar` that
each of these screens owns today: `HomeScreen`, `AdminScreen`, `NotificationsScreen`,
`ActividadDetailSheet`, `ActivitiesScreen`.

Acceptance:

- Icons only: notifications bell and logout (G-1, G-2).
- Bell shows an unread badge, capped at `9+` (G-5).
- Logout asks for confirmation in a dialog before ending the session (G-3).
- Both icons have a `contentDescription` (Spanish) and a hit area of at least 44×44 dp (G-4).
- Logout is available on every view, not only Home/Admin.

Tests: pure function for the badge text (`0` → hidden, `1..9` → number, `10+` → `9+`);
source-level assertions for `contentDescription` and the 44 dp size, following
`ActivityCardAccessibilityTest`. No Compose rule exists in this repo.

Delivery: one commit, `feat(core): add the global top bar with bell and logout confirmation`.

## Decisions to keep (do not redo)

- **R5:** notification copy lives in the backend catalogue (`activity-notification.map.ts`); the front renders `title` / `body` verbatim.
- **Formatters live in `core/util/`** (`formatMoney`, relative time, `formatShortDate`). Money formatter rejects absurd magnitudes and handles values above `1E7` (no scientific notation).
- **Absolute short dates are numeric** on purpose: a spelled-out month is what got truncated into "05 dé". The detail date keeps its own format: day, full abbreviated month, year ("5 ago 2026").
- **Card children** are ONE line: `Para: Ana, Luis` / `Para: Ana, Luis +1`. No chips, no measuring.
- **Card semantic label** includes "Asignada a: …" and "Para: …" for TalkBack.
- **Create form:** `endDate` + `endTime` are optional; end must be strictly after start ("El fin programado debe ser posterior al inicio"); no end date means no end time shown.

## Open items

- **T09 front:** confirm the detail sheet shows the date range and the children (back already exposes `childrenIds`). Check with `git log --oneline -15` and by opening the sheet on the device.
- **A-17:** the scheduled end is sent but not yet shown everywhere; lands with T09/T15.
- **Cleanup deferred to T20:** unreachable `complianceLoading → ComplianceCardLoading` branch in `HomeScreen`.
- **Compliance card period copy** ("Este mes") lands with T35.

## Working rules (to keep token use low)

1. One task per session. Commit, then start a fresh session (or `/compact`).
2. No double review: either work directly and read `git diff` yourself, or delegate and trust green tests, not both.
3. Run focused tests while working; run the full front suite once before the commit.
4. Reference exact file paths in the prompt; do not ask the agent to explore.
5. Update only the Status table when a task closes. No narrative log.

## Last green baseline

Front: 33 suites / 270 tests / 0 failures. Back focused: 13 suites / 302 tests / 0 failures.
