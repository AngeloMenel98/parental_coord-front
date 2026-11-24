# feature-refactor-integration

Objective: revive PR #5 (friend's branch `feature/refactor`, author asanchez) by
integrating `origin/main` (186 commits ahead) into it, preserving both the new
architecture (Koin DI, HttpClientFactory, SessionAuth, SafeApiCall, reactive
SessionManager) and the features main gained since divergence. Branch:
`integrate/feature-refactor` → pushes to `feature/refactor` (updates PR #5).
Delivery strategy: content commits on the integration branch; PR #5 merge is the
user's call. Test runner: `./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug`.

## Specs

- S1: Quote (user, verbatim): "a mi me gustaria mantener los cambios del PR 5 ya que lo hizo un amigo para mejorarme el frontend. podes hacer algo para complementarlo?"
- S2: The refactored architecture wins for structure: Koin single instances of
  HttpClient/APIs/repositories, `koinViewModel` in session-scoped ViewModelStore,
  shared HttpClient with defaultRequest/Logging/SessionAuth, `safeApiCall`,
  SessionManager as StateFlow, typed request bodies. Do not reintroduce
  `ApiClient.kt` (deleted there); map main's new API usage onto the service layer.
- S3: Every feature present in `origin/main` at 65f13c8 but not in `feature/refactor`
  must keep working after integration: third-party access UI, forced password change,
  notifications (badge/poller/tap-to-detail/swipe/pull-to-refresh), admin reports
  (AdminDetail, AdminActivitiesDetail, AdminReportCounts), expenses ledger/swipe,
  home ring/cards, activity rail/calendar/detail, auth provisional change.
- S4: The Keystore/Keychain hardening (`1294992`: SecureStorage.android.kt,
  KeychainStorage.kt, SessionManager encryption) survives unchanged in intent.
- S5: Existing test suites in `shared/src` keep passing; new code follows repo
  conventions (Spanish UI copy, ODD commits, no i18n resources).

## Tasks

| ID | Spec | Route | Status | Commit |
|----|------|------|--------|--------|
| E1 | S2/S3 | delegated (explore) | pending | - |
| I1 | S2/S3 network+di | pending | pending | - |
| I2 | S3 activities | pending | pending | - |
| I3 | S3 admin | pending | pending | - |
| I4 | S3 notifications+home+expenses | pending | pending | - |
| I5 | S3 session/auth/platform | pending | pending | - |
| V1 | S5 full verify | pending | pending | - |

## Log

- L1 (verbatim): "a mi me gustaria mantener los cambios del PR 5 ya que lo hizo un amigo para mejorarme el frontend. podes hacer algo para complementarlo?"
- L2: Context captured this session (before E1): PR #5 base=main head=feature/refactor
  (2 commits: 19671b5 refactor DI/API/reactive state; 1294992 Keystore/Keychain
  hardening). Merge-base c3d83b26; main is 186 commits ahead; 21-27 conflicted
  files; the worst is modify/delete on ApiClient.kt. Note: refactor branch's
  .gitignore does not cover build/ intermediates — 860 untracked build files show
  on branch switch; never `git add -A`, include .gitignore fix in media/di task.

- L3: Integración completada. Ruta final: cherry-pick de 19671b5 y 1294992 sobre
  origin/main (65f13c8), 21 conflictos resueltos (estructura Koin gana, features
  de main preservados), fakes de 12 suites de test migrados a las firmas nuevas.
  Autoría de asanchez conservada en ambos commits (be9cbb6, 8d194b5).
  Verificación: :shared:testDebugUnitTest 677 tests verde; :androidApp:assembleDebug OK;
  compileKotlinIosArm64 OK; verificación en dispositivo (Xiaomi 24094RAD4G): login,
  restauración de sesión con Keystore (test clave del hardening), features intactos —
  TODO ok. PR #5 revertida a MERGEABLE/CLEAN vía force-push autorizado por el usuario
  (integrate/feature-refactor → feature/refactor).
- L4: Pendiente de seguir (no de esta integración): PR #9 (develop→main, cambio de
  password forzado) — merge После #5; AuthApi necesitará changePassword (typed,
  token-free) cuando develop aterrice en esta base.
