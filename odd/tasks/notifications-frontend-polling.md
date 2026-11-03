# Notifications frontend — poll-only (slice 4)

Objective: give the app a notification centre against the shipped backend read path, plus
a polled unread badge on Home. Branch `sdd/activity-swipe-actions`. Delivery: single unit
(no PR). Test runner: `./gradlew :shared:testDebugUnitTest` (+ `:androidApp:assembleDebug`).

## Specs

- **S1 (HOME-08 badge).** Home top bar shows a bell with the unread count, quoting the spec:
  "notification bell badge to show unread count". The count comes from
  `GET /api/notifications/unread-count`, polled every 30 s while Home is composed, and
  refreshed immediately when Home is (re)entered and when the list screen returns.
  Badge hidden at 0; displays `99+` above 99.
- **S2 (open the list).** Tapping the bell opens the notifications screen. Back returns to
  Home without leaving the Home/Activities bottom-navigation state behind.
- **S3 (list).** `GET /api/notifications?limit=20&offset=N` with Bearer token. Items render
  title, body, relative time, and an unread marker. Newest first is the backend order and
  must not be re-sorted client-side.
- **S4 (pagination).** "Cargar más" appears while `items.size < total`; it requests the next
  offset and appends. No duplicated items after append.
- **S5 (mark one read).** Tapping an unread item calls `PATCH /api/notifications/{id}/read`
  (`{id, isRead:true}`). Success flips the row to read and decrements the badge (floor 0).
  Failure keeps the row unread and surfaces the error without losing the list.
- **S6 (mark all read).** An action marks every unread notification read via
  `PATCH /api/notifications/read-all` (`{affected}`), flips all rows to read and zeroes the
  badge. Concurrent taps must not fire the request twice.
- **S7 (states).** Loading, empty ("No tenés notificaciones"), and error-with-retry states
  exist for the list. A failed page load must not clear already-loaded items.
- **S8 (poll-only, no push).** No WebSocket frame, no push registration, no background
  service. The badge is API-polled, as the spec's open item states: "Push notifications:
  out of scope; badge counts are API-polled."

Out of scope for this slice: navigating from a notification to its activity
(`refEntityType`/`refEntityId` stay in the model for slice 3), `notification.created`
realtime, per-notification unread filter UI, localisation resources (the app hardcodes
Spanish copy in composables — this slice follows that convention).

## Tasks

| ID | Spec | Route | Status | Commit |
|----|------|-------|--------|--------|
| T1 | S1, S3 | inline | done | 66de4fa |
| T2 | S3, S4, S7 | inline | done | 66de4fa |
| T3 | S1, S2 | inline | done | 66de4fa |
| T4 | S5, S6, S8 | inline | done | 66de4fa |
| T5 | S1–S8 | inline | done | 66de4fa |

## Log

### L1 — user request (verbatim)

> continue with slice 4 then

### L2 — scope source

Slice 4 of the notifications roadmap, defined from the 2026-10-23 inventory (#1582) and
`docs/specs/parental-views-implementation/spec.md` HOME-08: frontend has zero notification
code; the backend read contract (4 routes) is shipped and reusable as-is.

### L3 — decisions

- Badge ownership: a plain `UnreadBadge` state holder + `UnreadCountPoller`, both in
  `feature/notifications/ui`, created in `App.kt` (the repo's composition root; Koin modules
  are stubs). This keeps the count consistent across Home and the list without a second
  source of truth.
- The list is user-wide (no `bondId` query): notifications are per-user and `bond_id` is
  nullable on the backend, so filtering by the active bond would hide bond-less ones.
- Tapping a row marks it read; it does not navigate (no navigation library exists and the
  activity target needs slice 3's routing decision).
- Refresh is an explicit top-bar action plus load-on-open — no pull-to-refresh (Material3
  pull-to-refresh is experimental in this Compose version and is not used anywhere in the repo).
- Total `list` calls: `unreadCount` is fetched by the poller and after list mutations, never
  by the list endpoint, which does not return it.

### L4 — implementation + evidence

Files (all new under `feature/notifications/` unless noted):

- `domain/model/NotificationModels.kt` — `NotificationItem`, `NotificationsPage`.
- `data/remote/NotificationsApi.kt` — the 4 endpoints, `open` for test fakes.
- `ui/UnreadBadge.kt` — `UnreadBadge` holder + `UnreadCountPoller` (30 s, injectable
  interval/dispatcher).
- `ui/NotificationTimeFormatter.kt` — `formatRelativeTime(iso, now, timeZone)`.
- `ui/NotificationsViewModel.kt` — list state, pagination, mark one/all, badge sync.
- `ui/NotificationsScreen.kt` — list, load-more, empty/error/retry, snackbar.
- `ui/NotificationsBell.kt` — bell + count bubble (`99+`), a11y description with count.
- `di/NotificationsModule.kt`; wired `core/di/AppModule.kt`, `core/network/ApiClient.kt`.
- `App.kt` — owns `UnreadBadge` + poller; poller runs only while Home is visible and
  `showNotifications` is false; `DisposableEffect` destroys VM/poller on exit.
- `feature/home/ui/HomeScreen.kt` — `unreadCount`/`onOpenNotifications` params and the
  bell in the top-bar actions, before "Cerrar sesión".

Evidence: `./gradlew :shared:testDebugUnitTest` → 25 new tests, 0 failures
(`NotificationsViewModelTest` 14, `NotificationTimeFormatterTest` 6,
`UnreadCountPollerTest` 5); `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL.

Correction vs L3: "back calls `poller.refreshNow()`" was not needed. Opening the list
stops the poller, and closing it restarts the loop, whose first iteration fetches
immediately — same freshness, one less code path. `refreshNow()` remains available for
callers that need an out-of-cycle fetch.

Not executed (no emulator/device attached): manual on-device walkthrough of the badge,
list and mark-all flows. Verified by compilation + unit tests only.

### L5 — Slice 5: el badge deja de ser poll-only (supersede S8)

Request (verbatim): "arranca con el slice del hoisting"

**S8 queda superseded, no borrada:** el contador sigue siendo el de la slice 4, pero ya no
espera 30 s a enterarse de una notificación nueva. `notification.created` (slice 3 del back)
llega por el WebSocket y refresca el badge al instante.

Cambios:

- `App.kt` — `RealtimeConnectionManager` se crea en la rama `Screen.Home` (antes vivía dentro
  de `bottomRoute == "activities"`), keyed en `token`. `DisposableEffect(realtimeManager, bondId)`
  es el ÚNICO dueño de `start()`/`stop()`; sin `bondId` no arranca (el handshake cerraría 4403
  en bucle). La rama Activities ya no crea ni detiene el manager: sólo destruye su VM.
- `App.kt` — puente `notificationSignals.collect { unreadPoller.refreshNow() }`. `refreshNow()`
  funciona con el bucle parado, así que el badge también se actualiza en la lista o en
  Actividades.
- `UnreadCountPollerTest` — +1 test (6 en la suite): `refreshNow` con el bucle parado, que no
  lo arranca, y que dos señales son dos fetches.

Invariantes que se mantienen: un manager por sesión, exactamente un `stop()` por manager,
`start()`/`stop()` idempotentes, `resyncSignals` intra-escritorio sin recargas nuevas (nadie
lo colecciona en Home), cero frames → cero requests.

Evidence: `./gradlew :shared:testDebugUnitTest` → 154 tests, 0 failures (18 suites);
`./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL.

Not executed: verificación on-device (sin emulador conectado) — falta ver el badge saltar sin
esperar el poll. El wiring de `App.kt` no tiene cobertura automática (no hay test de
composición en este repo) ⇒ `Risk: item 6`.
