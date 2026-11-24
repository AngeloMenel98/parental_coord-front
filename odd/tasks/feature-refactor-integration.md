
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
