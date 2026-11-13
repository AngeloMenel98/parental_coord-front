# Terciarios temporales — vista restringida con acceso temporal

**Objetivo:** habilitar un tercer tipo de usuario (terciario temporal) que recibe actividades
asignadas por un progenitor concreto dentro de un Bond, con acceso con fecha de vencimiento y
revocación anticipada, y una vista reducida: Home propio y sólo sus actividades asignadas, sin
capacidad de crear, rechazar ni cancelar, pudiendo únicamente finalizar.

**Estado:** L4. Front del artboard `03-permiso-explicito` implementado y verificado (T7, T8, T9, T13). Back: grant (T1) y módulo `third-parties` (T2) implementados y verificados; siguen T3–T6 y T10–T12.
**Ramas:** back `parental_coordination_back` · front `parental_coord-front` (hoy en `ft/activity-stage-rail`, con cambios sin commitear en `Bitacora*`).
**Entrega:** por definir (ver `## Tasks`); unidades de ~400 líneas, tests junto al comportamiento, Conventional Commits, sin push ni PR.
**Runners:** front `./gradlew :shared:testDebugUnitTest` + `./gradlew :androidApp:assembleDebug` · back `pnpm test` + `pnpm build`.

## Specs

**S1.** "una nueva view que es la de los usuarios terciarios temporales".

**S2.** "los progenitores puedan asignarle algunas tareas a personas autorizadas, donde tengan un acceso temporal".

**S3.** "sean solo los progenitores quienes le otorguen actividades".

**S4.** "Estos terceros no puede crear activades".

**S5.** "en su vista solo pueden ver el Home con sus datos, no lo de los progenitores (osea reusar la vista Home de progenitores pero sin ver los datos de los otros dentro del BOnd)".

**S6.** "Lo mismo en actividades, solo pueden ver sus activades asociadas/asignadas".

**S7.** "Cada tericiario pertenece a un progenitor, el otro no puede asignarle activades".

**S8.** "el usuario terciario solo puede poner actividad finalizada. No lo puede rechazar, osea una vez que se le asigna ya esta confirmado/asiste".

**S9.** "Tiene que estar lo mas restringido posible este usuario".

**S10.** "para canComplete tiene que poder visualizar el boton quien esta asignado, al igual que agregar archivos, etc.."

**S11.** "Pero el In_progress tiene que estar, lo mismo que el verify. para que llegue al completado."

**S12.** "no ve el nombre del terciario pero que fue autirozao por el progenitor a ir otra persona. El terciario solo recibe notificaciones de que se le asigno una actividad y puede ver cuando es y la info de lo que tiene que hacer."

**S13.** "Corregir el canComplete para todos."

### Decisiones acordadas (L2, L4)

- **D1.** Este entregable es sólo propuesta técnica; no se escribe código hasta aprobarla.
- **D2.** El terciario pertenece al **Bond** y se agrega el **progenitor dueño** como campo de scope (no se crea un modelo padre-hijo paralelo).
- **D3.** Acceso: **fecha de expiración fija obligatoria** + **revocación anticipada** por el progenitor dueño. El terciario nunca extiende ni reactiva.
- **D4 (L4-1).** Credenciales por SMTP/Gmail: **diferido**, se resuelve más adelante. Hasta entonces, el alta crea el usuario y el acceso se administra por el progenitor.
- **D5 (L4-4).** `canComplete` se restringe al **asignado para todos** (S13), no sólo para el terciario. El botón "Finalizar" y la carga de archivos son del asignado (S10).
- **D6 (L4).** El ciclo completo se mantiene: `assisting → in_progress → verify → done` (S11). No se agrega un atajo `assisting → done` ni se saltea ningún estado.

## Hallazgos que condicionan el diseño

Verificados con `path:line` sobre el código actual.

**F1 — `canComplete` no mira al viewer.** `back/src/activities/activity-policy.service.ts:105` devuelve `canComplete` sólo por status (`IN_PROGRESS`/`VERIFY`). Cualquier miembro del Bond puede finalizar cualquier actividad, incluidas las ajenas. Rompe S9 por sí solo y se corrige para todos (S13, D5).

**F2 — `complete()` no pasa por la política.** `back/src/activities/activities.service.ts:482-493` valida únicamente pertenencia al Bond (`assertActivityMember`). No exige assignee ni flag. El control real de "quién puede completar" no existe hoy.

**F3 — `canAssign` / `canReassign` no miran al viewer.** `activity-policy.service.ts:96-97` sólo comparan status. La autorización del `POST :id/assign` y `:id/reassign` queda en manos del servicio (`:629-660`), sin pasar por flags. Es el punto exacto donde debe entrar "sólo el progenitor dueño" (S7).

**F4 — El Home y la lista de actividades son bond-wide.** `GET /bonds` devuelve todos los miembros con nombre, apellido, avatar y `systemRole` (`back/src/bonds/repositories/bonds.repository.ts:17-37`); la compliance devuelve una fila por miembro con su porcentaje (`back/src/bonds/repositories/bond-members.repository.ts:103-128`); el resumen de gastos agrega todo el Bond (`back/src/expenses/expenses.repository.ts:209-233`); `findByBondIdOrdered` no filtra por assignee (`back/src/activities/repositories/activities.repository.ts:102-118`). Todo eso se filtraría tal cual a un terciario (S5/S6).

**F5 — El realtime es bond-wide.** `deliverToBond` manda `activity.changed`/`expense.changed` a **todos** los miembros activos (`back/src/realtime/realtime.gateway.ts:138-142`). Un terciario recibiría el stream completo (S9).

**F6 — Ya existe esquema muerto de terceros.** `back/src/expenses/entities/auth-third-party.entity.ts` (`bond_id`, `user_id`, `authorized_by`, `start_date`, `end_date` **nullable**, `status active|suspended|revoked`, `scope_note`) y `third-activity-participation.entity.ts` (`asignado|confirmado|rechazado|completado`), creadas en `back/src/database/migrations/1785503810000-initial-schema.ts:154-170`. **Cero repos, servicios, controllers o DTOs las usan.** Es el lugar natural para el grant, pero hoy no otorga nada: el permiso real vive en código, no en la tabla.

**F7 — Roles actuales.** `SystemRole { user, admin }` (`back/src/users/entities/user.entity.ts:12-15`); `BondMemberRole { progenitor, coordinador }` en la DB, con `third_party` declarado **sólo en TS y explícitamente "never persisted"** (`back/src/bonds/entities/bond-member.entity.ts:11-17`; enum DB en `.../1785503810000-initial-schema.ts:21`). El JWT lleva `sub`, `email`, `systemRole` (`back/src/auth/auth.service.ts:60-65`), sin `bondId`, y vive 24 h sin refresh (`back/src/auth/auth.module.ts:21`).

**F8 — No hay capa de autorización.** La tenencia se resuelve por request con `BondsService.isActiveBondForMember(bondId, userId)` repartido por cada servicio (`activities.service.ts:99-105`, `expenses.service.ts:92`, `children.service.ts:19`). No hay guard, interceptor ni policy de tenant. En el front no hay ninguna abstracción de permisos: la única rama de rol es `App.kt:106-113` (`admin` → Admin, resto → Home), y `role` es un `String` crudo en `SessionManager`.

## Diseño propuesto

### 1. Modelo de identidad y permiso

Dos ejes, nunca uno solo:

| Eje | Dónde vive | Valores | Para qué |
|---|---|---|---|
| Tipo de cuenta | `user.system_role` | `user`, `admin` | Enrutado grueso, superficies de administración |
| Rol en el Bond | `bond_member.role` | `progenitor`, `coordinador`, **`third_party`** (nuevo) | Autorización efectiva |
| Grant temporal | `auth_third_party` | `active`, `suspended`, `revoked`, `expired` | Vigencia y dueño (S7) |

**Regla dura:** el JWT **no** autoriza nada. Es una pista de enrutado. Toda decisión se resuelve contra la DB en cada request, porque revocar tiene que surtir efecto inmediato aunque el token viva 24 h más (S9).

`user.system_role` **no** cambia: un terciario es `user` con `bond_member.role='third_party'`. Evita un valor de enum nuevo en `system_role`, evita migrar el JWT y deja el rol donde se resolvió que pertenece (el Bond, D2).

### 2. Grant: endurecer `auth_third_party` (F6)

Reutilizar la tabla, agregándole la semántica que hoy no tiene:

- `end_date` pasa a **NOT NULL** (S2, D3). Las filas existentes son inexistentes en la práctica, pero la migración debe fallar ruidosamente si encuentra alguna sin fecha en vez de inventarla.
- `authorized_by` se vuelve la **fuente del dueño** (S7) con FK a `user` y `ON DELETE RESTRICT`.
- Nuevas columnas: `relationship` (enum `FAMILY|CAREGIVER|MEDICAL|EDUCATIONAL`, alineado con `docs/specs/parental-views-implementation/spec.md:495`), `revoked_at`, `revoked_by`, `revoked_reason`.
- Índice único parcial `(bond_id, user_id) WHERE status='active'`: un terciario no puede tener dos grants vivos en el mismo Bond.
- `status` gana `expired` (vencimiento por fecha) separado de `revoked` (decisión humana); la distinción importa para el mensaje que ve la persona y para la auditoría.

Migraciones: patrón `back/src/database/migrations/<epoch-ms>-Add...ts` con `IF NOT EXISTS`, `up`/`down` y spec compañera; para el enum DB, el precedente seguro es `1792555200000-AlignSystemRoleAndReportStatusEnums.ts:32-57` (Postgres no permite quitar valores y un valor nuevo no se puede usar en la misma transacción que lo crea).

### 3. Autorización: un único chokepoint

Hoy la tenencia está copiada en cada servicio (F8). Para que un terciario no herede por accidente los permisos de un progenitor, la propuesta es centralizar:

```
resolveBondAccess(bondId, userId) -> {
  role: 'progenitor' | 'coordinador' | 'third_party',
  grant?: { id, authorizedBy, startDate, endDate },   // sólo third_party
}
```

Con dos primitivas fail-closed, y **todo servicio de Bond pasa por ellas**:

- `assertBondAccess(bondId, userId)` — membresía activa **y**, si es terciario, `status='active'` con `now() BETWEEN start_date AND end_date`. Reemplaza las llamadas sueltas a `isActiveBondForMember`.
- `assertCanAssignTo(bondId, callerId, targetUserId)` — si el target es terciario, exige `grant.authorized_by === callerId` (S7). Aplica a `assign` y `reassign` (F3).

Memoización por request (un solo `SELECT` por request, no uno por servicio). Vencimiento perezoso: si el grant venció, se marca `expired` + `audit_log` y la request se rechaza con 403; un job en el worker (ya existe BullMQ) barre vencimientos para que el estado no dependa de que alguien entre.

### 4. Endpoints

**Gestión (progenitor):**

| Método | Ruta | Nota |
|---|---|---|
| GET | `/bonds/:bondId/third-parties` | Sólo los `authorized_by = caller`. El otro progenitor no los ve (S7) |
| POST | `/bonds/:bondId/third-parties` | `{ email, firstName?, relationship, accessEnd, note? }` → crea usuario + `bond_member(third_party)` + grant, en una transacción y con audit |
| PATCH | `/third-parties/:id` | `accessEnd`, `relationship`, `note`. Sólo el dueño |
| DELETE | `/third-parties/:id` | Revocación inmediata, `status='revoked'`, audit. Sólo el dueño |

**Lectura (terciario):**

| Método | Ruta | Nota |
|---|---|---|
| GET | `/third-parties/me` | Su grant: nombre del progenitor dueño, `accessEnd`, `relationship` |
| GET | `/bonds` | Redactado: metadatos del Bond + dueño, **sin** lista de miembros ni hijos (F4/S5) |
| GET | `/activities/bond/:bondId` | Filtrado en query a `assigned_to = viewer` (S6) |
| GET | `/activities/:bondId/compliance` | 403 para terciario (expone a los progenitores, F4) |
| GET | `/expenses/**` | 403 para terciario (saldo del hogar, F4) |

**Actividades (terciario):** sólo `POST /activities/:id/complete`. Todo lo demás (create, assign, reassign, confirm, decline, cancel, delete, restore) 403.

### 5. Máquina de estados y política (S8, S10, S11, S13)

Hay **tres** piezas, y ninguna es un atajo:

1. **Auto-confirmación al asignar.** Si el target es terciario, `assign` deja la actividad en `assisting` con `confirmedAt = now()` en la misma transacción. No hay paso de aceptación, así que no hay ventana donde pueda rechazar (S8).
2. **El ciclo completo se respeta (S11, D6).** El terciario recorre `assisting → in_progress → verify → done` como cualquier asignado. **No** se agrega `assisting → done`: el atajo saltearía `in_progress` y `verify`, que son justamente los estados que el humano pidió conservar. ⇒ Desaparece de esta propuesta la transición nueva de `activity-transition.map.ts`; alcanza con no bloquear al asignado.
   - A confirmar en implementación: hoy `in_progress` no lo dispara un endpoint sino el paso del tiempo / el worker (`BitacoraDerivation.kt`: "`En progreso` llega por tiempo"). Si el worker no empuja a `in_progress` y `verify` para una actividad asignada, el camino queda cortado antes de `done`. Se verifica antes de escribir código, no se inventa.
3. **El asignado es el único que finaliza (S13, D5), para todos los roles.** Esto cambia el comportamiento actual de los progenitores: hoy `canComplete` no mira el viewer.

Política resultante, extendiendo `PolicySubject`/`evaluate` con el rol del viewer:

| Flag | Progenitor (nuevo) | Terciario (nuevo) |
|---|---|---|
| `canComplete` | `isAssignee` y status ∈ {in_progress, verify} | igual: `isAssignee` y status ∈ {in_progress, verify} |
| `canUploadAttachment` | quien puede ver la actividad (hoy) | `isAssignee` |
| `canAssign` / `canReassign` | status only (**F3**, se restringe al dueño en servicio) | siempre `false` |
| `canConfirm`, `canDecline` | como hoy | siempre `false` (S8) |
| `canDelete`, `canCancel` | `isCreator && !isPast` | siempre `false` |

**Corrección obligatoria además de la feature:** `complete()` (F2) debe pasar por la política y no sólo por membresía. Sin eso, S13 no se cumple aunque la UI oculte los botones.

**Adjuntos (S10):** el asignado —terciario incluido— sube y ve archivos de **su** actividad. Como el terciario sólo ve actividades asignadas a él (S6), no hace falta un filtro nuevo de adjuntos: hereda el de la actividad. Se revisa igual `activity-attachments.service.ts:57-62`, que hoy autoriza contra `listForUser()` (todos los Bonds del usuario) y no contra la actividad.

### 6. Frontend

- `SessionManager` gana `bondRole: String?` y `accessEnd: String?`; se llenan en el login desde `getMyBonds().first().role` / `GET /third-parties/me`.
- Enrutado en `App.kt:106-113`: `systemRole == admin` → Admin; `bondRole == "third_party"` → árbol terciario; resto → Home. Se corrige el `else -> Unit` de `App.kt:196-202`.
- **Árbol del terciario:** sólo `Home` y `Actividades` (+ detalle). Sin Gastos, sin Terceros, sin FAB de alta.
- **Home del terciario:** pantalla propia que reutiliza los composables hoja de `HomeScreen` (saludo, agenda de hoy, tira de métricas) y **no** monta `ComplianceRingCard` (nombres y % de los progenitores, F4), ni el saldo del Bond, ni `ChildrenSection`. Datos: `GET /third-parties/me` + actividades propias + contadores propios (`HomeCounts.kt:47-62` ya es self-scoped). Se descarta parametrizar `HomeScreen` con un modo: una pantalla separada hace que la restricción sea auditable por construcción y deja intacto el Home de los progenitores.
- **Actividades del terciario:** `ActivitiesViewModel` gana `isThirdParty`; se oculta el FAB (`ActivitiesScreen.kt:532-535`), el swipe set queda vacío, y el footer del detalle sólo muestra "Finalizar" (`ActividadDetailSheet.kt:547`). La defensa real son los flags del server + 403; el gate de UI es sólo cosmético.
- **Manejo global de 401/403 por grant revocado o vencido:** limpiar sesión y mostrar "Tu acceso temporal terminó" en lugar de dejar la app rota. Sin esto, D3 y S9 quedan a medias: el token sigue vivo 24 h.

### 7. Realtime y notificaciones (F5, S12)

- **Realtime:** `deliverToBond` excluye a los miembros `third_party` del broadcast general y les entrega **sólo** frames de actividades cuyo `assigned_to` sea su `userId`. El resto de los miembros conserva el comportamiento actual: es un filtro de exclusión, no un rediseño del stream.
- **Notificaciones:** el terciario recibe **sólo** el evento de asignación (`assigned`). Hoy la matriz de `activity-notification.map.ts` lo incluiría además en `created` (como "member") y en `in_progress`/`verify`/`done` (como assignee). Se lo saca de `created` y se acota a lo que el humano pidió: "solo recibe notificaciones de que se le asigno una actividad". El resto del contenido lo ve entrando a la actividad (S12: "puede ver cuando es y la info de lo que tiene que hacer").

### 8. Invitación y credenciales

Punto abierto real (ver `## Decisiones abiertas`). Recomendación por defecto: el progenitor crea el terciario con email, y el back genera un token de invitación de un solo uso con 72 h para fijar contraseña. Si el proyecto no tiene mailer hoy, el fallback es un código de acceso de un solo uso que el progenitor ve una vez y entrega en mano; el terciario debe cambiarlo al entrar. Ninguna de las dos rutas se implementa hasta que elijas.

## Tasks

Todas pendientes. Cada una se aprueba antes de arrancar (D1).

| ID | Spec | Tarea | Repo | Ruta | Estado |
|---|---|---|---|---|---|
| T1 | S2, S7 | Migraciones: `third_party` en `bond_member_role`, `end_date NOT NULL`, `relationship`, `revoked_*`, índice único parcial, `expired` | back | inline | hecho (L9) |
| T2 | S2, S3, S7 | Módulo `third-parties`: entidades vivas, repo, service, controller, DTOs, audit `third_party_grant`/`revoke` | back | inline | hecho (L9) |
| T3 | S9 | `resolveBondAccess` + `assertBondAccess` + `assertCanAssignTo`, memoizado por request; migrar los servicios de Bond al chokepoint | back | inline | hecho (L12–L14) |
| T4 | S8, S11, S13 | Política: `canComplete` sólo asignado **para todos** + rol del viewer en el resto de los flags + auto-confirmación al asignar + `complete()` pasando por la política (F1–F3). Sin transiciones nuevas | back | inline | hecho (L15–L17) |
| T5 | S5, S6 | Lista filtrada por assignee en query, `/bonds` redactado, 403 en compliance y gastos | back | inline | parcial (L20: filtro en query hecho; falta `/bonds` redactado y los 403) |
| T6 | S12 | Realtime: excluir terciarios del broadcast y entregar sólo sus actividades. Notificaciones: sólo `assigned` | back | inline | pendiente |
| T7 | S1, S5 | `bondRole` en sesión derivado de `Vinculo.members`, enrutado por rol, árbol de navegación del terciario | front | inline | hecho (L8) |
| T8 | S5 | `ThirdPartyHomeScreen`: composición propia, sin anillo de cumplimiento, sin saldo, sin hijos | front | inline | hecho (L8) |
| T9 | S4, S6, S10, S11 | Restricciones en Actividades: sin FAB, sin swipe, chips por estado, footer con "Finalizar" y adjuntos para el asignado | front | inline | hecho (L8) |
| T10 | S9 | 401/403 por grant revocado/vencido: limpiar sesión y mensaje "Tu acceso temporal terminó" | front | inline | pendiente |
| T11 | S9, S13 | Pruebas cruzadas: matriz de capacidades por rol en back (incluye el cambio de `canComplete` para progenitores), e2e de terciario, tests de sesión/enrutado en front | ambos | delegado | pendiente |
| T12 | S12 | Identidad del terciario: el otro progenitor ve "Autorizado por <progenitor>", sin nombre ni perfil, en DTO de Bond, de actividad y en notificaciones | back | delegado | pendiente |
| T13 | S1, S2, S9 | Diálogos de permiso del artboard 03: onboarding "Tu permiso, en concreto" en el primer ingreso + "Lo que no podés hacer acá" en el detalle, abiertos desde iconos de información; sin notas explicativas dentro del producto | front | inline | hecho (L8) |

## Decisiones abiertas

1. **Credenciales del terciario** — diferido por D4. Queda para cuando se retome el SMTP/Gmail: sin mailer en el back hoy, el alta deja la cuenta creada y el progenitor administra el acceso.
2. **Verificación previa a implementar:** que el worker efectivamente empuje `in_progress` y `verify` en una actividad asignada. Si no lo hace, el camino a `done` está cortado y hay que resolverlo sin saltear estados (D6). No es una decisión de producto: es un dato que falta.
3. **Acceso revocado o vencido:** confirmar que el mensaje "Tu acceso temporal terminó" y el cierre de sesión son la UX que querés, en lugar de un modo de sólo lectura.

## Log

**L1 (pedido original, literal).** "quiero agregar una nueva view que es la de los usuarios terciarios temporales. La idea viene de que los progenitores puedan asignarle algunas tareas a personas autorizadas, donde tengan un acceso temporal y sean solo los progenitores quienes le otorguen actividades. Estos terceros no puede crear activades y en su vista solo pueden ver el Home con sus datos, no lo de los progenitores (osea reusar la vista Home de progenitores pero sin ver los datos de los otros dentro del BOnd) Lo mismo en actividades, solo pueden ver sus activades asociadas/asignadas. Cada tericiario pertenece a un progenitor, el otro no puede asignarle activades. Ademas el usuario terciario solo puede poner actividad finalizada. No lo puede rechazar, osea una vez que se le asigna ya esta confirmado/asiste. Tiene que estar lo mas restringido posible este usuario"

**L2 (decisiones del humano).** Alcance: "Primero propuesta técnica sin escribir código". Pertenencia: "Terciario → Bond, con scope por progenitor". Temporalidad: "Fecha fija + revocación anticipada".

**L3 (relevamiento).** Exploración front (roles, Home, actividades, navegación) y back (auth, modelo, tenencia, política de actividades, realtime). Hallazgos F1–F8 con `path:line`. Spot check propio de `activity-policy.service.ts:55-110` confirmando F1 y F3.

**L4 (correcciones del humano, literal).** "para canComplete tiene que poder visualizar el boton quien esta asignado, al igual que agregar archivos, etc.. la idea es que el terciario no pueda tocar muchos botones, entonces si se le asigna es porque esta confirmado q va a ir ese usuario. Pero el In_progress tiene que estar, lo mismo que el verify. para que llegue al completado. 1- Por ahi pasarle un smpt gmail al mail dle terciario. (pero dejalo para mas adelante) 2- no ve el nombre del terciario pero que fue autirozao por el progenitor a ir otra persona. El terciario solo recibe notificaciones de que se le asigno una actividad y puede ver cuando es y la info de lo que tiene que hacer. 4-Corregir el canComplete para todos. Comitia lo que tenes tanto en el front como el back, pasalo a develop y hace ramas nuevas desde ahi"

**L5 (git, ejecutado).** Back: commit `6885cc1 chore(atl): refresh the skill registry` sobre `ft/admin-bonds-reports`, fast-forward de `develop` a esa punta (56 commits de la línea de admin/reportes, sin push) y rama nueva `ft/third-party-access` desde `develop`. Front: `ft/activity-stage-rail` estaba exactamente en `develop`; se commiteó el trabajo de bitácora y el ícono de producción, `develop` avanzó por fast-forward y la misma rama nueva `ft/third-party-access` se creó desde ahí. Este documento se commitea en la rama nueva.

**L6 (diseño).** `design/terciario-ui/` — tres direcciones para la vista del terciario, cada una con Inicio, Actividades y Detalle: `01-solo-lo-tuyo.html`, `02-agenda-prestada.html`, `03-permiso-explicito.html`. Tokens de `design/system.md`; reutiliza la geometría de la fila de agenda y el contrato de accesibilidad. Ninguna dibuja anillo de cumplimiento, saldo de gastos, hijos, FAB de alta ni acciones de rechazo, porque exponen datos del vínculo o capacidades que el terciario no tiene.

**L7 (handoff de diseño).** "Implement the design artboard design/terciario-ui/03-permiso-explicito.html"; `gentle_design` registró `in_progress` y el handoff no commitea nada. Alcance: **sólo front**, T7/T8/T9/T13 reabiertas. Decisiones fijadas en los comentarios del canvas (todos resueltos): el permiso se explica en un popup la primera vez que ingresa y después desde el icono de información; Actividades filtra con chips **Asignadas / En progreso / Vencidas / Finalizadas**; las prohibiciones viven en un popup del detalle ("Lo que no podés hacer acá") abierto desde un icono arriba a la derecha; nada de notas explicativas dentro del producto. Las cards de actividad, la fila de agenda y el riel de etapas se reutilizan; la única pieza nueva es la acción al final de la fila. `bondRole` se deriva en el front desde `Vinculo.members` + `userId`, así que el enrutado no espera cambios de back. Dependencia: `GET /third-parties/me` (T2) no existe todavía; la tarjeta de permiso consume ese contrato y degrada sin inventar datos.

**L8 (implementación del front, sobre el handoff de L7).** Nuevos: `feature/thirdparty/domain/model/ThirdPartyModels.kt`, `feature/thirdparty/data/remote/ThirdPartiesApi.kt`, `feature/thirdparty/ui/{ThirdPartyPolicy.kt, ThirdPartyViewModel.kt, ThirdPartyTaskRow.kt, ThirdPartyHomeScreen.kt, ThirdPartyActivitiesScreen.kt, ThirdPartyActivitySheet.kt, ThirdPartyPermissionDialogs.kt, ThirdPartyRoot.kt}`. Tocados: `App.kt` (`Screen.ThirdParty` + derivación de rol), `SessionManager.kt` (`bondRole`), `BottomNavBar.kt` (`items` con default de los cuatro tabs), `ApiClient.kt` (`thirdPartiesApi()`). Pruebas: `ThirdPartyRestrictionsContractTest` (13 aserciones de fuente sobre S1–S13 y L7) y `ThirdPartyViewModelTest` (6 casos: filtro por assignee, degradación sin grant, onboarding de una sola vez, días restantes, finalizar con éxito y con error).

Verificación: `:shared:testDebugUnitTest` → 676 tests, 0 fallas; `:androidApp:assembleDebug` → OK. El build local necesita `-Dorg.gradle.java.installations.paths=/usr/lib/jvm/java-17-openjdk,/usr/lib/jvm/java-21-openjdk` porque Gradle detecta el JRE de la extensión de VS Code (sin `jlink`) y el `JdkImageTransform` falla; no se tocó `gradle.properties`.

Decisiones de implementación: (a) `bondRole` se deriva en el cliente desde `Vinculo.members` + `userId`, también al restaurar sesión, para que una sesión vieja no caiga en el Home bond-wide; (b) **`accessEnd` NO va a `SessionManager`** — desvío consciente de T7: el vencimiento es dato del servidor y cachearlo 24 h en la sesión sería una segunda fuente de verdad, así que vive en el ViewModel y se pide en cada carga; (c) sin `GET /third-parties/me` (T2) la tarjeta degrada: usa el nombre del progenitor derivado de `GET /bonds` sólo si hay exactamente uno, no pinta vigencia y no abre el onboarding (sin fecha no hay permiso temporal que anunciar); (d) el onboarding se recuerda por usuario (`ui.third_party_permit_seen.<userId>`); (e) el detalle sólo permite adjuntar archivos, no renombrar ni borrar (S9); (f) las filas reutilizan medidas y chips de la agenda pero no el contenedor de swipe, porque el gesto no existe para este rol.

Pendiente de back para que la vista funcione con datos reales: T2 (grant), T5 (filtro por assignee en query y `/bonds` redactado), T6 (realtime acotado), T10 (401/403 por grant vencido o revocado) y T12 (identidad del terciario en el DTO del vínculo).

**Cortes de entrega (L8).** `0cff20e` contrato + policy · `882a236` ViewModel + diálogos · `603c537` Inicio + Root · `65f9579` Actividades + detalle · `03b0582` sesión/enrutado · `891529c` pruebas · `0986fe4` diseño + este log. Sin push ni PR.

**L9 (back: T1 + T2).** Back en `ft/third-party-access`: `068dd5c` migración + su spec · `82ba4fb` entidad del grant, repositorios y DTOs · `6acf570` servicio, controller, módulo y registro en `AppModule` · `94d70be` specs del servicio. Verificación: `pnpm build` OK; `pnpm test` → **96 suites, 1138 tests, 0 fallas** (25 nuevos: 17 del servicio, 8 de la migración). La migración se corrió además contra un **Postgres 16 real** (contenedor descartable en el puerto 55432, sin volumen): cadena completa desde cero, esquema inspeccionado (`end_date` y `relationship` NOT NULL, índice único parcial `WHERE status='active'`, los tres índices, `bond_member_role=progenitor,coordinador,third_party`, `auth_third_status=active,suspended,revoked,expired`, `third_party_relationship=family,caregiver,medical,educational`), `migration:revert` OK y **re-ejecución OK** (las guardas y los `ADD VALUE IF NOT EXISTS` son idempotentes).

Migración `1793664000000-AddThirdPartyAccessGrant`: `third_party` en `bond_member_role` y `expired` en `auth_third_status` con `ADD VALUE IF NOT EXISTS`; tipo `third_party_relationship`; `end_date` NOT NULL y `relationship` NOT NULL precedidos por guardas que **fallan ruidosamente** (D3: si hay filas sin fecha o filas sin relación, la migración se detiene en vez de inventar); `revoked_at`/`revoked_by` (FK `ON DELETE RESTRICT`)/`revoked_reason`; índice único **parcial** `(bond_id, user_id) WHERE status='active'` para que revocar y volver a otorgar siga siendo posible; índices por `bond_id`, `user_id` y `authorized_by` (la consola del dueño filtra por el último). `down()` revierte todo lo que Postgres permite revertir y deja las dos etiquetas de enum (no se pueden quitar) con el motivo escrito.

Rutas: `GET /bonds/:bondId/third-parties`, `POST /bonds/:bondId/third-parties`, `PATCH /third-parties/:id`, `DELETE /third-parties/:id` y `GET /third-parties/me`; todas con `JwtAuthGuard`.

Decisiones de implementación: (a) **tenencia por dueño, no por vínculo**: sólo el progenitor que otorgó el grant lo lista, lo edita y lo revoca (S7), y un grant ajeno responde **404 y no 403** para no confirmar que el id existe; (b) el alta **no crea credenciales** (D4): busca la cuenta por email y responde **422** si no existe — inventar una contraseña convertiría un pendiente de producto en una decisión de seguridad; (c) un progenitor o coordinador **no se degrada** a `third_party` (409); (d) membresía + grant + audit en **una transacción**; (e) `GET /third-parties/me` hace **vencimiento perezoso** (marca `expired` y audita) y devuelve el grant con su estado para que la persona lea que venció, pero responde **403** si fue revocado o suspendido; (f) la entidad sigue viviendo en `expenses/entities` (F6) porque la entidad de participaciones declara la relación inversa y su metadata tiene que resolverse: deuda anotada, no movida a ciegas.

Corrección en el front que habilitó el back (commit aparte): `ThirdPartyViewModel` ya no cae al progenitor derivado cuando el grant responde **403** — ese fallback existe sólo para cuando el endpoint todavía no existía. Test nuevo: “un 403 del grant no cae en el progenitor derivado”.

Sigue pendiente para que la vista funcione con datos reales: T3 (chokepoint `assertBondAccess`/`assertCanAssignTo`), T4 (política + auto-confirmación al asignar), T5 (filtro por assignee en query y `/bonds` redactado), T6 (realtime y notificaciones acotadas), T10 (sesión ante 401/403), T11 (matriz de capacidades) y T12 (identidad del terciario en el DTO del vínculo).

**L10 (verificación independiente del back y correcciones).** Un verificador independiente auditó el módulo contra S1–S13 y encontró tres defectos reales, ya corregidos en `c06a976` (`fix(third-parties): sweep lapsed grants and revalidate the owner before writing`):

- **F1 (medio).** Un grant vencido que nadie había barrido seguía `active`, bloqueaba volver a otorgar el acceso al mismo par y la consola del dueño lo mostraba como vigente. Ahora la lista y el alta vencen perezosamente las filas vencidas antes de leer o insertar, dentro de la misma transacción (el índice único parcial sólo mira el estado, no la fecha).
- **F2 (bajo).** El 409 del alta nombraba el estado del grant ajeno y delataba su existencia al otro progenitor. El mensaje ahora es idéntico lo haya otorgado quien lo haya otorgado, consistente con la regla 404-antes-que-403 del resto del módulo.
- **F3 (bajo).** `PATCH` y `DELETE` no revalidaban que el dueño siguiera siendo progenitor activo del vínculo; ahora pasan por `assertProgenitor`. Es la única guarda el día que exista un flujo de salida o degradación de miembro.
- **F4 (advisory, no es defecto de T1/T2).** La revocación todavía no quita el acceso de verdad: la autorización vigente es `isActiveBondForMember`, así que un revocado sigue leyendo el vínculo hasta que entre T3. El comentario del servicio se corrigió para decir lo que pasa hoy en lugar de lo que pasará.

Diez pruebas nuevas en `third-parties.lifecycle.regression.spec.ts` fijan F1, F2, F3 y los caminos que la primera tanda no cubría (revocar un grant ajeno → 404, revocar un vencido → 409, vencimiento idempotente, `accessEnd` pasado en el update, relación + nota con su audit). Verificación: `pnpm build` OK; `pnpm test` → **97 suites, 1148 tests, 0 fallas**.

La verificación independiente dejó dos límites explícitos: el comportamiento del índice único parcial a nivel DB (que dos filas `active` del mismo par sean rechazadas, y que revocar + volver a otorgar funcione) se verificó por inspección del índice en Postgres, no ejecutando el `INSERT` conflictivo, y no hay spec HTTP/e2e del módulo (no hay infraestructura de Postgres en tests en este repo; la convención son specs estructurales).

**L11 (review nativa, abierta).** `gentle_review` creó la lineage `review-010e218935c43f8b` (tier **high**, 23 archivos, 2165 líneas, presupuesto de corrección 200). Quedó en el paso `collect`: el próximo movimiento son **cuatro corridas de revisión, una por lente** (risk, resilience, readability, reliability), que están pendientes de decisión humana por su costo de modelo. No bloquea la entrega; cualquier cambio de contenido obliga a re-freezar.

**L12 (review nativa, cerrada con escalación; T3 unidad 1).** Corrieron las cuatro lentes y el refuter (5 corridas de modelo; ~123 KB de prompt por lente). Cierre del proveedor: **`escalated`**, causa `unknown_causality`. Siete hallazgos: cinco **corroborados** (`R1-001`, `R3-001`, `R3-002`, `R4-001`, `R4-002`, todos con "probe unavailable on pi") y dos con causalidad desconocida (`R1-002`, `R1-003`, lente risk) que son los que escalan la acción al mantenedor. El proveedor **no abrió ciclo de corrección**, así que nada quedó bloqueado; el detalle de cada hallazgo vive en el registro nativo y no viaja a este log. Los tres defectos que la verificación independiente había marcado (F1–F3) ya estaban corregidos **antes** de congelar el candidato, así que lo revisado es el código final.

**T3, unidad 1 (`dc6ad39`).** `BondAccessService` en `src/bonds/` con `resolveBondAccess`, `assertBondAccess` y `assertCanAssignTo`, más `BondAccessCache` (memo por request, sin estado compartido: la revocación sigue siendo inmediata) y `BondMembersRepository.findAccessRow` — una sola consulta con `LEFT JOIN` a `auth_third_party` filtrado por `status = 'active'` en la condición, para que un tercero sin grant siga devolviendo su fila en vez de desaparecer. Reglas: no miembro → **404**; `left_at` no nulo → sin acceso; tercero con grant vencido **por fecha** → sin acceso aunque el estado siga `active` y nadie lo haya barrido; tercero sin grant vivo → **403** "Tu acceso temporal terminó"; asignarle a un tercero exige ser el progenitor que lo autorizó (S7).

`ThirdPartiesService.assertProgenitor` ya delega en el chokepoint, así que el módulo nuevo no tiene su propia copia de la regla. Verificación: `pnpm build` OK; `pnpm test` → **98 suites, 1157 tests, 0 fallas** (9 nuevos del chokepoint).

Falta de T3 (unidad 2): migrar los call-sites — `activities.service.ts`, `expenses.service.ts`, `children.service.ts` y `bonds.service.ts` siguen usando `isActiveBondForMember`, así que **hoy la revocación todavía no quita el acceso** a esos endpoints (es exactamente lo que F4 de la verificación independiente señaló).

**L13 (textos de los siete hallazgos y correcciones).** Los textos se extrajeron del registro nativo (`.git/gentle-ai/review-transactions/v2/<lineage>/review-state.json`, `admitted_role_results`). Hallazgos y disposición:

| ID | Severidad | Hallazgo | Estado |
|---|---|---|---|
| `R1-001` / `R3-001` / `R4-001` | CRITICAL / CRITICAL / **BLOCKER** | Revocar (y vencer) sólo cambiaban el grant: la fila de `bond_member` quedaba abierta y el resto del back autoriza con `isActiveBondForMember`, así que el `DELETE` respondía 200 y el tercero conservaba el vínculo | **corregido**: revocar y vencer cierran la membresía (`left_at`); volver a otorgar la reabre |
| `R4-004` | WARNING | `expireIfLapsed` hacía `save` de una fila leída antes: una revocación concurrente quedaba pisada por `expired`, perdiendo `revokedAt`/`revokedBy` | **corregido**: `UPDATE ... WHERE status='active'` |
| `R3-003` | WARNING | `2026-02-30` pasa la regex del DTO y la comparación lexicográfica; Postgres la rechaza → 500 | **corregido**: validación de fecha de calendario real → 400 |
| `R3-004` | WARNING | El chequeo de grant vigente y el INSERT no son atómicos: dos altas simultáneas del mismo par → violación del índice único → 500 | **corregido**: `23505` se traduce al 409 previsto |
| `R2-001` | WARNING | `findLatestForUser` ordenaba sólo por `createdAt` entre vínculos: `/third-parties/me` podía devolver el grant de **otro** vínculo | **corregido**: prefiere el grant `active` |
| `R2-002` | SUGGESTION | El KDoc nombraba un `assertOwner` inexistente | **corregido** |
| `R2-003` | SUGGESTION | Normalización de email duplicada en vez de reutilizar la del DTO de auth | diferido (tocaría el DTO de auth; la regla hoy es igual en ambos lados) |
| `R1-002` / `R1-003` (escalados) + `R4-002` / `R3-002` | CRITICAL | `src/auth/dto/auth.dto.ts` normaliza el email a minúsculas y recorta espacios de la contraseña **sin backfill ni búsqueda case-insensitive**: una cuenta existente con mayúsculas o con espacios en la contraseña deja de autenticarse | **no es de T1/T2** — viene de un commit anterior de la rama; queda para decisión del humano |

Verificación tras las correcciones: `pnpm build` OK; `pnpm test` → **98 suites, 1162 tests, 0 fallas** (5 casos nuevos en la spec de regresión).

**T3, unidad 2 (paso 1 — `342c351`).** En vez de migrar servicio por servicio, `BondsService.isActiveBondForMember` **delega en el chokepoint**: es la puerta que ya usan `activities`, `expenses`, adjuntos, webhooks y el filtro de destinatarios de notificaciones, así que un grant revocado o vencido deja de servir en todo el backend de una vez — sin tocar siete servicios ni sus dobles de test. Cuatro pruebas nuevas en `bonds.service.spec.ts` fijan la bisagra (incluido el caso que la review marcó como BLOCKER: tercero sin grant → no es miembro activo). Verificación: `pnpm build` OK; `pnpm test` → **99 suites, 1166 tests, 0 fallas**.

Falta de la unidad 2 (paso 2, lo único que queda de T3): `assertCanAssignTo` en el alta y la reasignación de actividades (`activities.service.ts:~660`), para que a un tercero sólo le asigne el progenitor que lo autorizó. Requiere tocar el constructor de `ActivitiesService` y cuatro specs que lo construyen.

**L14 (T3 cerrada).** Paso 2 (`feat(activities): only the granting progenitor may assign work to a third party`): `assertCanAssignTo` en `reassignInternal`, el método por el que pasan `assign` y `reassign`, así que la regla de S7 no puede divergir entre los dos. Con eso T3 queda completa: chokepoint (`BondAccessService`), delegación de `isActiveBondForMember` (que arregla revocación/vencimiento en todos los dominios) y la regla de asignación a terceros. Verificación: `pnpm build` OK; `pnpm test` → **99 suites, 1166 tests, 0 fallas**.

**Pendiente crítico heredado (no es de esta feature):** el commit `75d5aef` (`fix(auth): normalize the login and register credentials`) normaliza emails a minúsculas y recorta espacios de la contraseña sin backfill. Está en `develop` **local** y en la rama, pero **no** en `origin/develop` ni en `origin/main`, así que no está desplegado; el riesgo aparece al pushear `develop`. Conteo de cuentas afectadas: consultable para emails (`SELECT count(*) FROM "user" WHERE email <> lower(email)`), **no** para contraseñas (es un hash) — de ahí que la corrección segura sea un fallback en el login (probar la contraseña recortada y, si falla, la original) más un backfill de emails con detección de colisiones.

**L15 (T4, F1 y F2 — la corrección obligatoria).** `canComplete` dejó de derivarse del estado solo: ahora exige `isAssignee`, **para todos los roles** (S13/D5/F1), así que el otro progenitor o un tercero sin asignación recibe `false`. `complete()` pasa por la política antes de transicionar (F2) con un orden deliberado: si el llamante **no es el asignado** → **403**; si **es** el asignado y el estado no habilita → se deja al `transition()`, que responde **409** sin duplicar la máquina de estados. Tres pruebas de la spec de política/actividades afirmaban la regla vieja (“cualquier miembro del vínculo puede finalizar”) y se invirtieron en vez de borrarse; se agregó el caso 403 del POST directo. Verificación: `pnpm build` OK; `pnpm test` → **99 suites, 1167 tests, 0 fallas**.

Falta de T4: (a) el rol del viewer en el resto de los flags — un tercero nunca puede asignar, reasignar, confirmar, rechazar, cancelar ni borrar (S8); (b) auto-confirmación al asignar (`confirmedAt` en la misma transacción, sin paso de aceptación) para que el tercero no tenga ventana de rechazo (S8/D6).

**L16 (T4 · matriz del rol, S8).** `evaluate()` ahora recibe el **rol** del viewer (opcional, con default no-tercero para no romper llamantes) y, si es tercero, devuelve la matriz completa en un solo lugar: `canDelete/canCancel/canAssign/canReassign/canConfirm/canDecline = false` y `canComplete` sólo si es el asignado. `ActivitiesService` resuelve el rol **una vez por request** con el chokepoint (`isThirdPartyViewer`) y lo pasa en los seis call-sites (lista, detalle, completar, borrar, cancelar, rechazar) — el rol no se puede adivinar desde `systemRole`, y resolverlo por el chokepoint hace que un grant revocado no mantenga la matriz abierta. Prueba nueva: un tercero que además es el creador de la actividad no recibe **ninguna** acción, sólo finalizar la propia. Verificación: `pnpm build` OK; `pnpm test` → **99 suites, 1168 tests, 0 fallas**.

Falta de T4 (última rebanada): **auto-confirmación al asignar** (S8/D6) — al asignar a un tercero, `confirmedAt` en la misma transacción, sin ventana de rechazo; hay que verificar antes si el mapa de transiciones permite `created → assisting`.

**L17 (T4 · auto-confirmación al asignar, S8/D6).** Asignar a un tercero escribe `confirmedAt` **en la misma transición** del alta: no hay paso de aceptación, así que no existe ventana para rechazar. Detalle de implementación: `assignedConfirmed` **no es una columna** — el servicio la deriva de `confirmedAt != null` —, así que alcanza con esa marca. No se agregó ninguna arista al mapa de transiciones ni se salteó ningún estado: sigue siendo `created → assigned → assisting`, y el paso a `assisting` lo sigue dando el reloj/worker como para cualquier asignado. Prueba nueva que fija el contrato: al asignar a un tercero el `applyTransition` lleva `confirmedAt`; a un miembro normal, no. Verificación: `pnpm build` OK; `pnpm test` → **99 suites, 1169 tests, 0 fallas**.

**Verificación pendiente de T4 (punto 2 de `## Decisiones abiertas`, no resuelta por esta rebanada):** que el worker/timers efectivamente empujen `assigned → assisting → in_progress → verify` en una actividad asignada. La auto-confirmación cierra la ventana de rechazo, pero si el worker no empuja esos estados el camino a `done` queda cortado antes de llegar. Sigue siendo un dato a verificar, no una decisión de producto.

**L18 (verificación del worker — el dato que faltaba, y el defecto que destapó).** El motor de timers (`activity-timers.processor.ts`) sólo hace cuatro cosas:

| Timer | Origen exigido | Destino |
|---|---|---|
| `RESPONSE_TIMEOUT` | `created` \| `assigned` | **`overdue`** |
| `START` | `assisting` | `in_progress` |
| `END` | `in_progress` | `verify` |
| `VERIFY_TIMEOUT` | `verify` | `overdue` |

**Ningún timer empuja `assigned → assisting`**: esa arista la da `confirmAssignment`, guardada a “sólo el asignado”. Consecuencia directa sobre L17: la auto-confirmación escribe `confirmedAt` pero deja la fila en **`assigned`**, así que a las 36 h el `RESPONSE_TIMEOUT` la pasa a `overdue` y el tercero **no puede finalizarla** (`canComplete` exige `in_progress`/`verify`, y `canConfirm`/`canDecline` son `false` para su rol). Es exactamente el corte que el punto 2 de `## Decisiones abiertas` pedía verificar antes de dar la feature por completa: **el dato está, y dice que la rebanada de L17 quedó incompleta**.

Corrección diseñada (unidad siguiente, no incluida): dar los **dos pasos legales** del mapa en el mismo request — `created → assigned` (con `assignedTo`) y enseguida `assigned → assisting` —, el segundo con **actor de sistema**, porque esa arista está guardada al asignado y acá la aceptación no la da una persona: la da la regla del rol (S8). El primer intento quedó rojo porque el doble de test del alta no modela estado/versión (el segundo paso veía `created`), y se revirtió para no dejar la rama en rojo.

**L19 (auth, decisión del humano — literal).** “no hace falta hacer un backfill porque no hay usuarios con email usando mayusculas ni password con espacios”. Se mantiene la normalización de `75d5aef` tal cual: **sin migración de datos y sin fallback de login**. El riesgo queda aceptado por dato (no hay casos que rompan), no por diseño; si alguna vez aparece una cuenta con mayúsculas o con espacios en la contraseña, no va a poder entrar y el síntoma será un 401 sin explicación. Cierra el pendiente que la review nativa había escalado.

**L20 (T5, primera pieza: la lista filtrada en la query — S6).** `findByBondIdOrdered(bondId, assigneeId?)` agrega `a.assigned_to = :assigneeId` cuando hay assignee, y `listByBond` pasa el id del viewer **sólo si el chokepoint dice que es tercero**. Hasta acá la restricción de S6 era un filtro del cliente sobre una respuesta bond-wide: el vínculo entero viajaba al dispositivo. Ahora el límite se aplica donde se leen las filas. Dos pruebas fijan las dos direcciones (tercero → filtrada; progenitor → sin filtro). Verificación: `pnpm build` OK; `pnpm test` → **99 suites, 1171 tests, 0 fallas**.

Falta de T5: (a) `GET /bonds` redactado para el tercero (sin lista de miembros ni hijos); (b) 403 en compliance y en gastos, que hoy se resuelven sólo por membresía — el chokepoint ya sabe el rol, falta aplicarlo en esas dos superficies.