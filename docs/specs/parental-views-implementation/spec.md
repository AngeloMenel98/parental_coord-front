# Delta Spec: Implement Core Feature Screens

**Change:** parental-views-implementation
**Status:** Draft
**Date:** 2026-09-06
**Derived from:** Proposal #887, PRD v0.1

---

## Overview

This change replaces stub data on the Home screen with real API integration, implements three new feature screens (Actividades, Gastos, Terceros), and adds bottom navigation. The app follows the existing MVVM pattern with `MutableStateFlow<UiState>`, `ApiResult<T>` sealed class, and manual dependency construction (no DI framework).

**Critical constraint:** Backend REST controllers do not yet exist. All API contracts below are derived from backend entity schemas. Frontend builds against planned contracts; integration testing requires backend implementation in parallel.

---

## Capability 1: Home/Dashboard — Real API Integration

**Current state:** `HomeApi.getHomeSummary()` returns `HomeSummary.EMPTY` (all zeroes/nulls). UI components already render the data correctly when provided.

### User Stories

| ID | Story | Priority |
|----|-------|----------|
| HOME-01 | As a logged-in parent, I want to see my compliance percentage so I know my standing | Must |
| HOME-02 | As a logged-in parent, I want to see my co-parent's compliance percentage | Must |
| HOME-03 | As a logged-in parent, I want to see summary counters (pending activities, overdue, pending expenses) | Must |
| HOME-04 | As a logged-in parent, I want to see my next 3-5 upcoming activities | Must |
| HOME-05 | As a logged-in parent, I want to see a recent activity feed | Should |
| HOME-06 | As a logged-in parent, I want a greeting with my name and current date | Must |
| HOME-07 | As a logged-in parent, I want to see my child's profile info | Should |
| HOME-08 | As a logged-in parent, I want the notification bell badge to show unread count | Should |

### Acceptance Criteria

| ID | Criterion | Verifiable |
|----|-----------|------------|
| HOME-AC01 | `HomeApi.getHomeSummary()` calls `GET /bonds/{bondId}/summary` with Bearer token and returns parsed `HomeSummary` | ✅ Unit test: mock HttpClient, verify request URL, headers, response mapping |
| HOME-AC02 | `HomeSummary.compliance` is null-safe — UI renders gracefully when compliance data is absent | ✅ Visual: no crash, compliance card hidden |
| HOME-AC03 | `HomeSummary.upcomingActivities` contains at most 5 items sorted by `scheduledDate` ascending | ✅ Unit test: mock response with 8 items, assert list size ≤ 5 |
| HOME-AC04 | `HomeSummary.pendingActivities`, `overdueActivities`, `pendingExpenses` are non-negative integers | ✅ Unit test: negative values in response → clamp to 0 |
| HOME-AC05 | HTTP 401 response triggers session clear and redirect to login | ✅ Unit test: mock 401, assert `SessionManager.clear()` called |
| HOME-AC06 | Network error shows retry button; tapping it re-calls `loadHomeData()` | ✅ Visual + unit test |
| HOME-AC07 | Loading state shows `CircularProgressIndicator` while API call is in-flight | ✅ Visual |
| HOME-AC08 | `bondId` is read from `SessionManager` and passed to API calls | ✅ Unit test: verify `SessionManager.bondId` is non-null after login |
| HOME-AC09 | Screen pulls-to-refresh: swipe down re-fetches home data | ✅ Visual |

### Data Requirements

**Endpoint:** `GET /bonds/{bondId}/summary`

**Request:**
```
Authorization: Bearer {token}
Accept: application/json
```

**Response 200:**
```json
{
  "child": {
    "id": "uuid",
    "firstName": "string",
    "lastName": "string",
    "dateOfBirth": "2024-03-15",
    "coordinatorName": "string | null"
  },
  "compliance": {
    "parent1Name": "string",
    "parent1Percentage": 85.0,
    "parent2Name": "string",
    "parent2Percentage": 72.0
  },
  "pendingActivities": 3,
  "overdueActivities": 1,
  "pendingExpenses": 2,
  "upcomingActivities": [
    {
      "id": "uuid",
      "title": "Pediatría control",
      "type": "EVENT | OBLIGATION",
      "category": "SALUD | EDUCACION | FAMILIAR | SOCIAL | RECREACION",
      "status": "CREATED | ASSIGNED | IN_PROGRESS | PENDING_VERIFICATION | COMPLETED | OVERDUE",
      "assignedTo": "parent1 | parent2",
      "scheduledDate": "2026-09-10T10:00:00",
      "dueDate": "2026-09-10",
      "priority": "URGENT | HIGH | MEDIUM | LOW"
    }
  ],
  "unreadNotifications": 4
}
```

**Response 401:** triggers session clear
**Response 5xx:** returns `ApiResult.Error` with status code

### UI Components

- `HomeScreen` — already exists, no structural changes needed
- `GreetingSection` — already implemented, no changes
- `ComplianceCard` — already implemented, renders real data
- `CountersRow` — already implemented, renders real data
- `ActivityCard` — already implemented, renders real data
- `ChildCard` — already implemented, renders real data

### SessionManager Changes

Add `bondId: String?` field:

```kotlin
var bondId: String? by mutableStateOf(null)
    private set

fun setSession(token: String, userName: String, role: String?, bondId: String?) {
    // ...existing...
    bondId?.let { Platform.saveString(KEY_BOND_ID, it) }
    this.bondId = bondId
}
```

Bond ID sourced from: `GET /auth/me` response (new field) or `GET /bonds/mine` endpoint.

### Edge Cases

| Case | Behavior |
|------|----------|
| `bondId` is null after login | Show error: "No hay vínculo activo. Contacte soporte." |
| Compliance data is null | Hide compliance card entirely (already handled) |
| `upcomingActivities` is empty | Hide "Próximos" section (already handled) |
| `upcomingActivities` has 10+ items | API returns max 5; no client-side truncation needed |
| HTTP 401 on any Home API call | Clear session, redirect to Login |
| Network timeout (> 30s) | Show error with retry button |
| Response contains unknown enum value for status/category | Render as-is (raw string), log warning |

---

## Capability 2: Actividades Screen (New)

### User Stories

| ID | Story | Priority |
|----|-------|----------|
| ACT-01 | As a parent, I want to see all activities in a list sorted by date | Must |
| ACT-02 | As a parent, I want to filter activities by tab: Todos, Eventos, Obligaciones | Must |
| ACT-03 | As a parent, I want to create a new activity with title, category, type, due date, assigned person, and notes | Must |
| ACT-04 | As a parent, I want to see each activity's status badge with correct color and label | Must |
| ACT-05 | As a parent, I want to advance an activity's status (e.g., mark as "En progreso") | Must |
| ACT-06 | As a parent, I want to see an activity's audit trail (status change history) | Should |
| ACT-07 | As a parent, I want to see activities flagged as "Vencido" highlighted in red | Must |
| ACT-08 | As a parent, I want to see activities flagged as "Disputa" highlighted in orange | Must |
| ACT-09 | As a parent, I want to edit an existing activity | Should |
| ACT-10 | As a parent, I want to see who each activity is assigned to | Must |

### Acceptance Criteria

| ID | Criterion | Verifiable |
|----|-----------|------------|
| ACT-AC01 | Screen loads with "Todos" tab selected by default, showing all activities | ✅ Visual |
| ACT-AC02 | Tapping "Eventos" tab filters to only `type == "EVENT"` activities | ✅ Unit test: mock list with mixed types, assert filtered count |
| ACT-AC03 | Tapping "Obligaciones" tab filters to only `type == "OBLIGATION"` activities | ✅ Unit test |
| ACT-AC04 | "+ Nuevo" button opens activity creation form | ✅ Visual |
| ACT-AC05 | Creation form requires: title (non-empty), category (dropdown), type (dropdown), due date (date picker), assigned to (dropdown: parent1/parent2) | ✅ Unit test: empty title → validation error |
| ACT-AC06 | Successful creation calls `POST /activities` and refreshes list | ✅ Unit test: mock 201, assert list refresh |
| ACT-AC07 | Status lifecycle action calls `PATCH /activities/{id}/status` with new status | ✅ Unit test: verify request body |
| ACT-AC08 | Valid status transitions enforced: CREATED→ASSIGNED, ASSIGNED→IN_PROGRESS, IN_PROGRESS→PENDING_VERIFICATION, PENDING_VERIFICATION→COMPLETED | ✅ Unit test: invalid transition → error |
| ACT-AC09 | Exception states (OVERDUE, DISPUTA) are set by backend, not client-initiated | ✅ Unit test: client cannot set OVERDUE |
| ACT-AC10 | Audit trail call: `GET /activities/{id}/audit` returns list of status changes with timestamps | ✅ Unit test |
| ACT-AC11 | Empty state: "No hay actividades" message when list is empty | ✅ Visual |
| ACT-AC12 | Pull-to-refresh re-fetches activity list | ✅ Visual |

### Data Requirements

**List endpoint:** `GET /bonds/{bondId}/activities?type=EVENT|OBLIGATION|ALL`

**Response 200:**
```json
{
  "activities": [
    {
      "id": "uuid",
      "title": "Control pediátrico",
      "type": "EVENT | OBLIGATION",
      "category": "SALUD | EDUCACION | FAMILIAR | SOCIAL | RECREACION",
      "status": "CREATED | ASSIGNED | IN_PROGRESS | PENDING_VERIFICATION | COMPLETED | OVERDUE | DISPUTA",
      "assignedTo": "uuid-of-parent",
      "assignedToName": "María García",
      "scheduledDate": "2026-09-10T10:00:00",
      "dueDate": "2026-09-10",
      "priority": "URGENT | HIGH | MEDIUM | LOW",
      "notes": "Llevar carnet de vacunación",
      "createdAt": "2026-09-01T08:00:00",
      "updatedAt": "2026-09-05T14:00:00"
    }
  ]
}
```

**Create endpoint:** `POST /bonds/{bondId}/activities`
```json
// Request
{
  "title": "string (required, 1-200 chars)",
  "type": "EVENT | OBLIGATION",
  "category": "SALUD | EDUCACION | FAMILIAR | SOCIAL | RECREACION",
  "assignedTo": "uuid (optional)",
  "scheduledDate": "2026-09-10T10:00:00 (ISO-8601)",
  "dueDate": "2026-09-10 (optional)",
  "priority": "MEDIUM (default)",
  "notes": "string (optional, max 2000 chars)"
}
// Response 201: full Activity object
```

**Status update endpoint:** `PATCH /activities/{id}/status`
```json
// Request
{
  "status": "ASSIGNED | IN_PROGRESS | PENDING_VERIFICATION | COMPLETED"
}
// Response 200: updated Activity object
```

**Audit trail endpoint:** `GET /activities/{id}/audit`
```json
// Response 200
{
  "entries": [
    {
      "id": "uuid",
      "previousStatus": "CREATED | null",
      "newStatus": "ASSIGNED",
      "changedBy": "uuid",
      "changedByName": "María García",
      "changedAt": "2026-09-02T09:00:00",
      "note": "optional note"
    }
  ]
}
```

### UI Components

| Component | Description |
|-----------|-------------|
| `ActivitiesScreen` | Top-level screen composable with Scaffold |
| `ActivityTabRow` | `TabRow` with 3 tabs: Todos, Eventos, Obligaciones |
| `ActivityList` | `LazyColumn` of `ActivityCard` items, filtered by active tab |
| `ActivityCard` | Card showing title, date, category chip, status badge, assigned-to |
| `ActivityDetailSheet` | Bottom sheet or full screen showing activity details + status actions |
| `ActivityCreateForm` | Form with fields: title, category dropdown, type dropdown, date picker, assignee dropdown, notes |
| `StatusActionButton` | Button showing next valid status transition (e.g., "Iniciar") |
| `AuditTrailList` | List of audit entries with timestamp, user, status change |
| `CategoryChip` | `AssistChip` with category label and color coding |
| `StatusBadge` | `Surface` with rounded shape, status label, color-coded |

### Status Lifecycle Model

```
                    ┌─────────────┐
                    │   CREATED   │
                    └──────┬──────┘
                           │ assign
                    ┌──────▼──────┐
              ┌─────│  ASSIGNED   │─────┐
              │     └──────┬──────┘     │
              │ dispute    │ start      │ dispute
              │     ┌──────▼──────┐     │
              │     │ IN_PROGRESS │     │
              │     └──────┬──────┘     │
              │            │ verify     │
              │     ┌──────▼──────────┐ │
              │     │ PENDING_VERIFY  │ │
              │     └──────┬──────────┘ │
              │            │ complete   │
              │     ┌──────▼──────┐     │
              │     │  COMPLETED  │     │
              │     └─────────────┘     │
              │                         │
    ┌─────────▼─────────┐     ┌────────▼────────┐
    │     OVERDUE       │     │    DISPUTA      │
    │  (backend-set)    │     │  (either party) │
    └───────────────────┘     └─────────────────┘
```

Valid client-initiated transitions:
- `CREATED → ASSIGNED` (assign action)
- `ASSIGNED → IN_PROGRESS` (start action)
- `IN_PROGRESS → PENDING_VERIFICATION` (verify action)
- `PENDING_VERIFICATION → COMPLETED` (complete action)
- `ASSIGNED → DISPUTA`, `IN_PROGRESS → DISPUTA` (dispute action — either party)
- `OVERDUE` — set by backend when `dueDate` passes without COMPLETED
- `DISPUTA` → resolved by reassignment or deletion (backend-mediated)

### Edge Cases

| Case | Behavior |
|------|----------|
| Activity has no `assignedTo` | Show "Sin asignar" badge, allow assignment |
| Activity is OVERDUE | Red highlight, "Vencida" badge, no forward status action available |
| Activity is in DISPUTA | Orange highlight, "Disputa" badge, no forward status action |
| User tries invalid status transition | Backend returns 400, show error toast |
| User creates activity with past due date | Backend rejects or allows (depends on backend policy); frontend allows submission |
| Activity list is empty for a tab | Show "No hay actividades de tipo [X]" |
| Notes field is optional | Submit without notes if empty |
| Network error on create | Show error toast, form remains filled for retry |
| Two parents try to update status simultaneously | Last-write-wins (backend handles) |

---

## Capability 3: Gastos Screen (New)

### User Stories

| ID | Story | Priority |
|----|-------|----------|
| GAS-01 | As a parent, I want to see a summary bar with total pending amount, count pending, count paid | Must |
| GAS-02 | As a parent, I want to see all expenses in a list sorted by date | Must |
| GAS-03 | As a parent, I want to submit a new expense with title, category, amount, date, receipt, and split arrangement | Must |
| GAS-04 | As a parent, I want to approve an expense submitted by my co-parent | Must |
| GAS-05 | As a parent, I want to dispute an expense I disagree with | Must |
| GAS-06 | As a parent, I want to see expenses flagged "Sin doc." highlighted | Must |
| GAS-07 | As a parent, I want to see expenses in "Disputa" status highlighted | Must |
| GAS-08 | As a parent, I want to mark an approved expense as "Pagado" | Should |
| GAS-09 | As a parent, I want to see who submitted each expense | Must |

### Acceptance Criteria

| ID | Criterion | Verifiable |
|----|-----------|------------|
| GAS-AC01 | Summary bar shows: total pending amount (formatted as currency), count pending expenses, count paid expenses | ✅ Visual |
| GAS-AC02 | Expense list loads from `GET /bonds/{bondId}/expenses` | ✅ Unit test |
| GAS-AC03 | "+ Solicitar" button opens expense submission form | ✅ Visual |
| GAS-AC04 | Submission form requires: title (non-empty), category (dropdown), amount (positive number), date (date picker) | ✅ Unit test: empty title → error, negative amount → error |
| GAS-AC05 | Receipt upload is optional but triggers "Sin doc." flag if absent | ✅ Unit test: submit without receipt → `documented: false` |
| GAS-AC06 | Successful submission calls `POST /bonds/{bondId}/expenses` and refreshes list | ✅ Unit test |
| GAS-AC07 | Approve action calls `POST /expenses/{id}/approve` | ✅ Unit test |
| GAS-AC08 | Dispute action calls `POST /expenses/{id}/dispute` with optional reason | ✅ Unit test |
| GAS-AC09 | Mark-as-paid action calls `POST /expenses/{id}/pay` | ✅ Unit test |
| GAS-AC10 | Expense amounts display with locale-appropriate currency formatting (es-ES) | ✅ Visual: "$1,234.56" or "$1.234,56" |
| GAS-AC11 | Empty state: "No hay gastos registrados" when list is empty | ✅ Visual |

### Data Requirements

**List endpoint:** `GET /bonds/{bondId}/expenses`

**Response 200:**
```json
{
  "summary": {
    "totalPendingAmount": 1250.00,
    "pendingCount": 3,
    "paidCount": 7
  },
  "expenses": [
    {
      "id": "uuid",
      "title": "Medicina antibiótica",
      "category": "SALUD | EDUCACION | FAMILIAR | SOCIALES | RECREACION | OTROS",
      "amount": 45.50,
      "date": "2026-09-03",
      "submittedBy": "uuid-of-parent",
      "submittedByName": "María García",
      "status": "PENDIENTE | APROBADO | PAGADO | DISPUTA",
      "documented": true,
      "splitArrangement": "50_50 | CUSTOM | SUBMITTING_PARENT",
      "splitPercentage": 50,
      "createdAt": "2026-09-03T10:00:00"
    }
  ]
}
```

**Submit endpoint:** `POST /bonds/{bondId}/expenses`
```json
// Request
{
  "title": "string (required, 1-200 chars)",
  "category": "SALUD | EDUCACION | FAMILIAR | SOCIALES | RECREACION | OTROS",
  "amount": 45.50,
  "date": "2026-09-03",
  "notes": "string (optional, max 1000 chars)",
  "splitArrangement": "50_50 | CUSTOM | SUBMITTING_PARENT",
  "splitPercentage": 50
}
// Response 201: full Expense object
// Receipt: separate endpoint POST /expenses/{id}/receipt (multipart/form-data)
```

**Approve endpoint:** `POST /expenses/{id}/approve`
```json
// Request: empty body or { "note": "optional" }
// Response 200: updated Expense object
```

**Dispute endpoint:** `POST /expenses/{id}/dispute`
```json
// Request
{
  "reason": "string (required, 1-500 chars)"
}
// Response 200: updated Expense object
```

**Mark paid endpoint:** `POST /expenses/{id}/pay`
```json
// Request: empty body
// Response 200: updated Expense object
```

### UI Components

| Component | Description |
|-----------|-------------|
| `ExpensesScreen` | Top-level screen composable with Scaffold |
| `ExpenseSummaryBar` | Horizontal bar showing total pending $, pending count, paid count |
| `ExpenseList` | `LazyColumn` of `ExpenseCard` items |
| `ExpenseCard` | Card showing title, category chip, amount, submitted-by, date, status badge, doc flag |
| `ExpenseCreateForm` | Form with: title, category dropdown, amount input, date picker, notes, split arrangement, receipt upload |
| `ExpenseDetailSheet` | Bottom sheet with full details + approve/dispute/pay actions |
| `StatusBadge` | Same pattern as Activities screen |
| `DocumentationFlag` | "Sin doc." chip shown when `documented == false` |
| `AmountText` | Formatted currency text with locale support |

### Status Model

```
PENDIENTE → APROBADO → PAGADO
    │
    └──→ DISPUTA (terminal until resolved)
```

- `PENDIENTE`: Initial state after submission
- `APPROBADO`: Co-parent approves
- `PAGADO`: Marked as paid (by either party)
- `DISPUTA`: Disputed by co-parent; requires resolution (deletion, adjustment, or agreement)

### Edge Cases

| Case | Behavior |
|------|----------|
| Expense has no receipt | Show "Sin doc." warning chip, allow submission |
| Amount is 0 | Backend rejects (400); frontend validates > 0 |
| User disputes their own expense | Backend rejects (403); frontend should disable dispute on own expenses |
| User tries to approve already approved expense | Backend returns 200 idempotently or 400; frontend disables button |
| Receipt upload fails (network) | Show error, allow retry without losing form data |
| Currency formatting | Use `NumberFormat.getCurrencyInstance(Locale("es", "ES"))` or platform equivalent |
| Summary bar values are 0 | Show "$0.00" and "0" counts normally |
| Expense list is empty | Show empty state message |

---

## Capability 4: Terceros Screen (New)

### User Stories

| ID | Story | Priority |
|----|-------|----------|
| TER-01 | As a parent, I want to see a list of all authorized third parties | Must |
| TER-02 | As a parent, I want to see each third party's name, role, and access duration | Must |
| TER-03 | As a parent, I want to add a new third party with name, role, expiration, and scope | Must |
| TER-04 | As a parent, I want to edit a third party's details | Should |
| TER-05 | As a parent, I want to revoke a third party's access immediately | Must |
| TER-06 | As a parent, I want to scope a third party's access to specific activities/categories | Should |
| TER-07 | As a parent, I want to see explanatory copy about what third-party access means | Must |
| TER-08 | As a parent, I want expired third-party access to be automatically revoked | Must |

### Acceptance Criteria

| ID | Criterion | Verifiable |
|----|-----------|------------|
| TER-AC01 | Screen loads third-party list from `GET /bonds/{bondId}/third-parties` | ✅ Unit test |
| TER-AC02 | List shows name, role label, and access period (start → end) for each | ✅ Visual |
| TER-AC03 | "+ Agregar tercero autorizado" button opens add form | ✅ Visual |
| TER-AC04 | Add form requires: name (non-empty), role (dropdown), expiration date (date picker), activity scoping (optional multi-select) | ✅ Unit test |
| TER-AC05 | Successful add calls `POST /bonds/{bondId}/third-parties` and refreshes list | ✅ Unit test |
| TER-AC06 | Edit calls `PATCH /third-parties/{id}` with changed fields | ✅ Unit test |
| TER-AC07 | Revoke calls `DELETE /third-parties/{id}` and removes from list | ✅ Unit test |
| TER-AC08 | Revoke requires confirmation dialog ("¿Revocar acceso de [name]?") | ✅ Visual |
| TER-AC09 | Expired third parties show "Expirado" badge and cannot be edited (only revoked or re-invited) | ✅ Visual |
| TER-AC10 | Empty state: "No hay terceros autorizados" when list is empty | ✅ Visual |
| TER-AC11 | Explanatory copy is shown at top of screen when list is non-empty | ✅ Visual |

### Data Requirements

**List endpoint:** `GET /bonds/{bondId}/third-parties`

**Response 200:**
```json
{
  "thirdParties": [
    {
      "id": "uuid",
      "name": "Abuela Rosa",
      "role": "FAMILY | CAREGIVER | MEDICAL | EDUCATIONAL",
      "email": "rosa@email.com",
      "accessStart": "2026-09-01",
      "accessEnd": "2026-12-31",
      "isActive": true,
      "scopedActivities": ["uuid-activity-1"],
      "scopedCategories": ["SALUD", "EDUCACION"],
      "createdAt": "2026-09-01T08:00:00"
    }
  ]
}
```

**Add endpoint:** `POST /bonds/{bondId}/third-parties`
```json
// Request
{
  "name": "string (required, 1-100 chars)",
  "role": "FAMILY | CAREGIVER | MEDICAL | EDUCATIONAL",
  "email": "string (optional, valid email)",
  "accessEnd": "2026-12-31 (required, must be future date)",
  "scopedActivities": ["uuid (optional, array of activity IDs)"],
  "scopedCategories": ["SALUD | EDUCACION | FAMILIAR | SOCIALES | RECREACION (optional)"]
}
// Response 201: full ThirdParty object
```

**Edit endpoint:** `PATCH /third-parties/{id}`
```json
// Request (partial update)
{
  "name": "string (optional)",
  "role": "string (optional)",
  "accessEnd": "string (optional)",
  "scopedActivities": ["uuid (optional)"],
  "scopedCategories": ["string (optional)"]
}
// Response 200: updated ThirdParty object
```

**Revoke endpoint:** `DELETE /third-parties/{id}`
```json
// Response 200: { "revoked": true }
```

### UI Components

| Component | Description |
|-----------|-------------|
| `ThirdPartiesScreen` | Top-level screen composable with Scaffold |
| `ThirdPartyExplanatoryCopy` | `Card` with text explaining third-party access and restrictions |
| `ThirdPartyList` | `LazyColumn` of `ThirdPartyCard` items |
| `ThirdPartyCard` | Card showing name, role chip, access dates, active/expired badge, edit/revoke buttons |
| `ThirdPartyAddForm` | Form with: name, role dropdown, email, expiration date picker, category scoping (multi-select chips) |
| `ThirdPartyEditForm` | Same as add form, pre-filled with current values |
| `RevokeConfirmDialog` | `AlertDialog` with confirmation message and cancel/revoke buttons |
| `RoleChip` | `AssistChip` with role label |
| `AccessStatusBadge` | "Activo" (green) or "Expirado" (red) badge based on `accessEnd` vs current date |

### Edge Cases

| Case | Behavior |
|------|----------|
| Access expiration date is in the past at creation | Backend rejects (400); frontend validates future date |
| Third party access expires while user is on screen | Auto-revoke triggered by backend; frontend re-fetches on next refresh |
| Third party email is optional | Allow submission without email |
| Scoped activities list is empty | Grant access to all activities within scoped categories |
| Scoped categories is empty | Grant access to all categories (full access) |
| User tries to revoke themselves | Not applicable (third parties are different users) |
| Third party has no scoped categories or activities | Show "Acceso completo" badge |
| Network error on revoke | Show error toast, third party remains in list |
| List is empty | Show explanatory copy + empty state message |

---

## Capability 5: Bottom Navigation

### User Stories

| ID | Story | Priority |
|----|-------|----------|
| NAV-01 | As a parent, I want a persistent bottom navigation bar with 4 tabs: Inicio, Actividades, Gastos, Terceros | Must |
| NAV-02 | As a parent, I want the active tab highlighted | Must |
| NAV-03 | As a parent, I want tapping a tab to switch screens without losing scroll position | Should |
| NAV-04 | As a parent, I want badge counts on tabs for pending items | Should |

### Acceptance Criteria

| ID | Criterion | Verifiable |
|----|-----------|------------|
| NAV-AC01 | Bottom nav bar is visible on Home, Actividades, Gastos, Terceros screens | ✅ Visual |
| NAV-AC02 | Bottom nav bar is NOT visible on Login or Admin screens | ✅ Visual |
| NAV-AC03 | Tapping "Inicio" navigates to Home screen | ✅ Visual |
| NAV-AC04 | Tapping "Actividades" navigates to Actividades screen | ✅ Visual |
| NAV-AC05 | Tapping "Gastos" navigates to Gastos screen | ✅ Visual |
| NAV-AC06 | Tapping "Terceros" navigates to Terceros screen | ✅ Visual |
| NAV-AC07 | Active tab has filled icon + primary color; inactive tabs have outline icon + onSurface color | ✅ Visual |
| NAV-AC08 | Navigation preserves screen state (ViewModels not destroyed on tab switch) | ✅ Visual: scroll position and loaded data persist |
| NAV-AC09 | Bottom nav uses `NavigationBar` Material3 component | ✅ Code review |
| NAV-AC10 | Tab icons: Inicio = Home, Actividades = List/Checklist, Gastos = Receipt/AccountBalance, Terceros = People | ✅ Visual |

### Navigation Architecture

**Approach:** Extend existing `Screen` sealed class in `App.kt`. Use Compose `NavHost` with `NavigationBar` for tab switching.

```
App
├── Login (no bottom nav)
├── Admin (no bottom nav)
└── MainScaffold (with bottom nav)
    ├── Home
    ├── Actividades
    ├── Gastos
    └── Terceros
```

**Key implementation detail:** The `MainScaffold` composable wraps a `Scaffold` with a `NavigationBar` bottom bar. Each tab's screen is rendered inside the scaffold's content area. ViewModels are created at the `MainScaffold` level (or per-tab via `remember`) and survive tab switches.

### UI Components

| Component | Description |
|-----------|-------------|
| `MainScaffold` | `Scaffold` with `NavigationBar` bottom bar containing 4 `NavigationBarItem`s |
| `NavigationBarItem` (×4) | Icon + label for each tab; selected state tracked by current route |
| Screen navigation | `NavHost` with routes: `home`, `activities`, `expenses`, `third_parties` |

### Navigation Routes

| Route | Screen | Bottom Nav Label |
|-------|--------|-----------------|
| `home` | HomeScreen | Inicio |
| `activities` | ActivitiesScreen | Actividades |
| `expenses` | ExpensesScreen | Gastos |
| `third_parties` | ThirdPartiesScreen | Terceros |

### Deep Linking Considerations

- **Push notification deep links:** When a notification is tapped, navigate to the relevant screen (e.g., "new expense" → Gastos tab). Implement via `NavDeepLink` or manual routing on notification tap.
- **Initial route:** After login, default to Home tab. Backend may specify initial tab in auth response (deferred to future change).
- **Back navigation:** System back button on Android should navigate within tab stack first, then exit app. On iOS, swipe-back should work within nav stack.

### Edge Cases

| Case | Behavior |
|------|----------|
| User taps same tab | No-op (already on that screen) |
| Screen data fails to load on non-active tab | Show error state when user switches to that tab |
| Badge count changes while on different tab | Badge updates when user returns to that tab |
| Deep link to a screen user lacks access to | Redirect to Home tab |
| Quick successive tab taps | Debounce or ignore; no race conditions on ViewModel creation |

---

## Cross-Cutting Specifications

### API Pattern

All new API classes follow the established `AuthApi` pattern:

```kotlin
class ActivitiesApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun listActivities(token: String, bondId: String, type: String? = null): ApiResult<ActivityListResponse> {
        return try {
            val response = client.get("$baseUrl/bonds/$bondId/activities") {
                header("Authorization", "Bearer $token")
                type?.let { parameter("type", it) }
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }
}
```

### Model Naming Convention

All DTOs use **snake_case JSON field names** to match backend entity serialization directly. No manual mapping layer.

```kotlin
@Serializable
data class ActivityDto(
    val id: String,
    val title: String,
    val type: String,
    val category: String,
    val status: String,
    @SerialName("assigned_to") val assignedTo: String?,
    @SerialName("assigned_to_name") val assignedToName: String?,
    @SerialName("scheduled_date") val scheduledDate: String,
    @SerialName("due_date") val dueDate: String?,
    val priority: String,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)
```

### Enum Values (Frontend Strings)

| Domain | Values |
|--------|--------|
| Activity type | `EVENT`, `OBLIGATION` |
| Activity status | `CREATED`, `ASSIGNED`, `IN_PROGRESS`, `PENDING_VERIFICATION`, `COMPLETED`, `OVERDUE`, `DISPUTA` |
| Category | `SALUD`, `EDUCACION`, `FAMILIAR`, `SOCIAL`, `RECREACION` |
| Expense status | `PENDIENTE`, `APROBADO`, `PAGADO`, `DISPUTA` |
| Expense category | `SALUD`, `EDUCACION`, `FAMILIAR`, `SOCIALES`, `RECREACION`, `OTROS` |
| Priority | `URGENT`, `HIGH`, `MEDIUM`, `LOW` |
| Split arrangement | `50_50`, `CUSTOM`, `SUBMITTING_PARENT` |
| Third party role | `FAMILY`, `CAREGIVER`, `MEDICAL`, `EDUCATIONAL` |

### Files Affected (New)

| File | Purpose |
|------|---------|
| `model/ActivityModels.kt` | Activity DTOs, status/type enums |
| `model/ExpenseModels.kt` | Expense DTOs, status/category enums |
| `model/ThirdPartyModels.kt` | Third-party DTOs, role enum |
| `model/BondModels.kt` | Bond summary DTOs |
| `api/ActivitiesApi.kt` | Activity CRUD + status + audit API |
| `api/ExpensesApi.kt` | Expense CRUD + approve/dispute/pay API |
| `api/ThirdPartiesApi.kt` | Third-party CRUD + revoke API |
| `api/BondsApi.kt` | Bond summary endpoint |
| `ui/ActivitiesScreen.kt` | Activities screen composable |
| `ui/ActivitiesViewModel.kt` | Activities ViewModel + UiState |
| `ui/ExpensesScreen.kt` | Expenses screen composable |
| `ui/ExpensesViewModel.kt` | Expenses ViewModel + UiState |
| `ui/ThirdPartiesScreen.kt` | Third-parties screen composable |
| `ui/ThirdPartiesViewModel.kt` | Third-parties ViewModel + UiState |
| `ui/BottomNavigation.kt` | Navigation bar composable |

### Files Affected (Modified)

| File | Change |
|------|--------|
| `api/HomeApi.kt` | Replace stub with real API calls |
| `api/ApiClient.kt` | Register new API classes |
| `session/SessionManager.kt` | Add `bondId` field |
| `ui/App.kt` | Extend `Screen` sealed class, add `MainScaffold` with bottom nav, wire new screens |
| `ui/HomeScreen.kt` | Wrap in `MainScaffold`, remove standalone top-level screen |
| `model/HomeModels.kt` | No changes needed (models already match planned API) |

---

## Open Items (for design phase)

1. **Receipt upload:** Deferred from this change per proposal. Future: `POST /expenses/{id}/receipt` with multipart.
2. **Compliance formula:** Backend-determined; frontend renders whatever percentage is returned.
3. **Push notifications:** Out of scope; badge counts are API-polled.
4. **Multi-child support:** `bondId` scopes to one bond; multi-child within a bond is backend's responsibility.
5. **Legal export:** Not in scope.
