
- L5 (2026-11-24, fase 2 — port del change-password): Tras mergear PR #5, la PR #9
  (develop→main) quedó en conflicto en 4 archivos: el flujo de cambio de password
  forzado de develop choca con la arquitectura del refactor. Rama de resolución:
  `fix/pr9-change-password-port` (develop + merge de main). Regla: el shell Koin/token-free
  de main gana; el flujo change-password de develop se porta dentro (SessionManager ya
  portado: mustChangePassword en SessionState + updateToken/updateMustChangePassword +
  persistence; AuthApi.changePassword token-free typed; LoginViewModel con forma de main).

## Specs (fase 2)
- S6: AuthApi tiene changePassword(currentPassword, newPassword) token-free con
  ChangePasswordRequest typed (develop verbatim: "POST /auth/change-password").
- S7: SessionManager expone mustChangePassword persistido (KEY session_must_change_password)
  + updateToken(newToken) + updateMustChangePassword(Boolean); login fija
  mustChangePassword desde la respuesta; clear() reinicia y borra la key.
- S8: App.kt monta la pantalla ChangePassword mientras mustChangePassword=true, gana
  sobre admin/home; ChangePasswordViewModel(authRepository, sessionManager) es Koin
  koinViewModel; al éxito consume el bloqueo y continúa a la vista según rol.

## Tasks (fase 2)
| ID | Spec | Route | Status | Commit |
|----|------|-------|--------|--------|
| CP1 | S7 | inline | done | (stageado en merge) |
| CP2 | S6 | inline | done | (stageado en merge) |
| CP3 | S8 | delegated | pending | - |
| CP4 | S6-S8 | inline | pending | - |
- L6 (2026-11-24, cierre fase 2): Merge ffd13f7 fix/pr9-change-password-port → develop.
  8 conflictos de App.kt resueltos (frame main + port change-password), ChangePasswordViewModel
  como ViewModel Koin, LoginViewModel con mustChangePassword + deriveBondWithRetry token-free,
  AuthModule registra el VM. Alineaciones de tests documentadas por el worker (4, escenarios
  preservados). Verificación: 745 tests / 0 fallos + assembleDebug. PR #9 MERGEABLE/CLEAN.
  Autorización de desviaciones del parent: LoginViewModel (exigida por CP3 bullet 3) y
  derive-then-release en onSuccess (S8 manda sobre el shorthand literal).
