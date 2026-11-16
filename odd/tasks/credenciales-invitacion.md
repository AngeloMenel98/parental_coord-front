# Credenciales de invitación y cambio obligatorio de contraseña

**Objetivo:** que el alta de un tercero temporal cree la cuenta cuando el email no existe y envíe
por mail usuario y contraseña; y que todo usuario con contraseña generada por el sistema tenga que
cambiarla en su primer ingreso.

**Ramas:** back `parental_coordination_back` (`ft/third-party-access`) · front `parental_coord-front`
(`ft/third-party-access`).
**Entrega:** PRs #9 (back) y #3 (front); unidades de ~400 líneas, tests junto al comportamiento,
Conventional Commits. Push y PR ya están autorizados en esta sesión.
**Runners:** back `pnpm build` + `pnpm test` · front `./gradlew :shared:testDebugUnitTest` +
`./gradlew :androidApp:assembleDebug`.

## Specs

**S1.** "poder enviar un mail con usuario y contrasena"

**S2.** "Quiero hacer que todo usuario que ingresa por primera vez, tenga que cambiar la password."

## Decisiones

- **D1.** El bloque SMTP se carga en el `.env` del back (gitignoreado). El runtime de esta sesión
  bloquea cualquier acceso a `.env`, así que lo escribe el humano; el repo solo lleva `SMTP_*`
  referenciados desde `docker-compose.yml` y validados como **opcionales** en `AppConfig`.
- **D2 (plan confirmado: "me parece perfecto").** Email sin cuenta → se crea la cuenta con contraseña
  generada y se envía usuario + contraseña; email con cuenta → no se toca su contraseña y se envía un
  aviso de acceso; si el correo falla en el alta nueva → se revierte cuenta y acceso (nada a medias).
- **D3 (interpretación de S2).** El flag `must_change_password` se activa cuando la contraseña la
  genera el sistema (terceros invitados y altas del admin). Un usuario que se registra elige su propia
  contraseña: no se le fuerza el cambio. Si el humano quería también el registro, es una línea.

## Tasks

| ID | Spec | Tarea | Repo | Ruta | Estado |
|---|---|---|---|---|---|
| T1 | S1, D2 | `nodemailer` + `MailModule`/`MailerService` con config SMTP opcional; sin configuración, el alta falla con un error claro en vez de crear una cuenta sin avisar | back | delegado | hecho (L3) |
| T2 | S1, D2 | Alta de tercero: crear cuenta con contraseña generada y enviar credenciales **después** del commit; si el envío falla, compensar borrando la cuenta. Email existente: aviso sin credenciales | back | delegado | hecho (L3) |
| T3 | S2, D3 | `must_change_password` (migración + entidad) + login con `mustChangePassword` + `POST /auth/change-password` + guard que bloquea el resto mientras el flag esté activo | back | delegado | hecho (L3), review bloqueada |
| T4 | S2 | Front: pantalla de cambio obligatorio y routing; sesión con `mustChangePassword` | front | delegado | hecho (L5) |
| T5 | S1 | Actualizar el copy del artboard 04 (decía "no hay invitación por mail") y diseñar la pantalla de cambio obligatorio | front | inline | hecho (L4–L5) |

## Log

**L1.** "plal bwmx zmvm twsa, esta es la key para el smtp de gmail. y poder enviar un mail con usuario y contrasena" — la app password se redacta de este documento porque el repo se commitea; vive solo en el `.env` del back.

**L2.** "1-podes leer el .env y acomodarlo? el gmail:angelomenel21@gmail.com. 2-me parece perfecto. 3- Quiero hacer que todo usuario que ingresa por primera vez, tenga que cambiar la password."


**L3 (T1–T3 implementadas; review nativa bloqueada por el proveedor).** Back implementado y verificado: `nodemailer` + `MailerService` con SMTP opcional, alta que crea la cuenta con contraseña generada (14 chars, alfabeto sin ambigüedades, bcrypt 10) y manda usuario + contraseña, aviso sin credenciales para cuentas existentes, `must_change_password` + claim en el JWT + `POST /auth/change-password` + guard `403 PASSWORD_CHANGE_REQUIRED` (excepto `change-password` y `me`). `pnpm build` OK; **104 suites, 1232 tests, 0 fallas**. SMTP real verificado en el contenedor: `health=200`, `SMTP_USER` presente, `SMTP_PASS` de 16 caracteres y `nodemailer.verify()` → **SMTP OK** (sin enviar mail).

La review nativa con 4 lentes + refuter encontró dos **CRITICAL** introducidos por el candidato: el correo con contraseña salía **dentro** de la transacción (R3-001: un commit fallido deja credenciales para una cuenta inexistente; R4-001: I/O externo con locks y conexión abiertos). Corrección aplicada (94 líneas de diff): el envío pasa a **después del commit** y, si falla, se compensa borrando la cuenta recién creada (el `DELETE` cascadea membresía y grant). Tests actualizados, incluido "si el commit falla, el mail no se envía".

El **validador dirigido** del proveedor devolvió dos veces el mismo payload JSON truncado (1.848 bytes, sin cierre de array) y el admission lo rechazó sin consumir el slot. Por la regla de no reintentar automáticamente, la review queda **abierta** y el candidato (`d624159`) **sin pushear**. Decisión pendiente del humano: reintentar el validador más tarde (es un defecto del modelo/proveedor) o desactivar RDD para este clon y seguir por la política ordinaria del repo.


**L4 (T5 · diseño y copy).** Tres direcciones en `design/cambio-password-ui/` (una sola cosa, portero, app cerrada) sobre el sistema de tokens; el humano eligió **03-app-cerrada**: hoja modal sobre la app atenuada, que hace sentir el bloqueo como global. Canvas: `design/cambio-password-ui/`. Se sacó el copy falso de los artboards 02 y 04 de `terceros-ui`: donde decía "No hay invitación por mail" ahora dice que la persona recibe las credenciales por mail y que el primer ingreso pide cambiarla. T4 (routing + pantalla + endpoint) queda delegado.


**L5 (T4 · cambio obligatorio en el front, sobre el artboard 03).** Cadena completa: `User.mustChangePassword` + `ChangePasswordRequest` → `AuthApi.changePassword` (Bearer; en no-2xx lee el `message` del JSON) → repositorio (reemplaza token y usuario cacheados) → `SessionManager` (estado + persistencia + `updateToken`/`updateMustChangePassword`, limpieza en `clear()`) → `ChangePasswordViewModel` → `ChangePasswordScreen` → routing. La hoja es un `ModalBottomSheet` **no descartable** (`confirmValueChange != Hidden`) sobre un fondo inerte (topbar "Inicio" + tarjetas skeleton, fuera del árbol de accesibilidad): reproduce 03 sin cargar datos, que igual darían 403. En `App.kt` la precedencia es `mustChangePassword` **antes** de admin/tercero, así que ninguna vista con datos ni los pollers se montan; al apagarse el flag el mismo `when` recalcula y cae en Home/Admin/ThirdParty. Detalle resuelto: mientras el flag está encendido `GET /bonds` da 403 y el login no puede derivar `bondId`/`bondRole` (un tercero habría caído en el Home de progenitores); la derivación se hace una sola vez al salir, con el token nuevo (`deriveBondIfUnblocked`, idempotente). Un solo dueño del flag: lo escribe `App` en `setSession`; `LoginViewModel` sólo lo preserva.

Verificación: RED real (los tests no compilaban: "No parameter with name 'mustChangePassword'", "'changePassword' overrides nothing") → GREEN **69 suites, 703 tests, 0 fallas**; `:androidApp:assembleDebug` OK. Tests nuevos: 11 del VM de cambio (vacío, <8, sin letra, sin dígito, igual a la actual, repetición distinta, éxito que reemplaza token y apaga el flag, error del servidor inline) y 2 del login (el flag llega intacto; el login común sigue sin bloqueo).

Correcciones y límites: el back responde **401** (`Current password is incorrect`), no 400 como yo había supuesto; el VM traduce por el texto del mensaje, así que funciona con 400 o 401, pero un mensaje localizado caería al texto verbatim. Se corrigieron los KDocs de `ThirdPartyModels.kt` y `ThirdPartiesApi.kt` que seguían afirmando el 422 de "email sin cuenta". Sin Compose UI tests en el repo, la pantalla queda verificada por compilación y revisión de código, no visualmente. La persistencia del flag (`Platform.saveString`) no tiene test: en JVM las escrituras son no-op; lo probado es el estado en memoria y el default de `setSession`.


**L6 (follow-up de la review aprobada: derivación del vínculo).** Se cerraron los advisories de `review-cfdddad3d0f96847`. `SessionManager.setSession` ya no hereda el vínculo: `bondRole = null` incondicional y borra las claves persistidas cuando no hay valor; `clear()` deja la sesión sin rastro. El login **no** deriva el vínculo cuando `mustChangePassword` está activo (era un 403 garantizado). El `ChangePasswordViewModel` sólo reemplaza el token y emite `success`; el flag lo apaga `App`, **después** de derivar. La derivación pasa por un `BondDerivationCoordinator` recordado a nivel de sesión: un solo intento en vuelo compartido entre el callback de éxito y el desmontaje de la hoja, presupuesto de 2 intentos por token, marcador `settled` para las respuestas concluyentes (incluido "no hay vínculo / ningún miembro es el usuario") y descarte de la respuesta si el token cambió o desapareció (logout A→B no repuebla el rol). Si se agotan los intentos se loguea y el flag se apaga igual.

Verificación: RED real en los dos tests nuevos (`SessionManagerTest` fallaba heredando `bondRole`; el test del VM fallaba porque el VM apagaba el flag) → GREEN **70 suites, 708 tests, 0 fallas**; `:androidApp:assembleDebug` OK. Límites: sin Compose UI tests, el coordinator y su wireo no tienen cobertura ejecutable; el retry con `delay 250 ms` no se ejerce; la persistencia de las claves se prueba sólo en memoria (Platform sin init es no-op). Riesgo residual aceptado: si la app se cierra en la ventana entre el cambio y la derivación, el flag sigue en `true` y al reabrir vuelve a mostrar la hoja (≤~500 ms); el bloque de restore legacy conserva su derivación de un solo intento y no se tocó.
