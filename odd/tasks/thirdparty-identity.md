# Terciarios con identidad propia

**Objetivo**: que un progenitor pueda crear un acceso temporal con datos de contacto
(nombre, apellido, teléfono, documento), que ese terciario sea asignable como
asignatario de actividades con su nombre visible, y que las cards muestren el nombre
del terciario sólo a quien lo autorizó; los demás miembros ven "Autorizado por YO".

**Ramas**: `feature/thirdparty-identity` en ambos repos, desde `develop`.
**Delivery**: commits unitarios convencionales en ambas ramas; no push ni PR salvo pedido.
**Test runner back**: `pnpm test` / jest (spec por servicio). **Front**: `./gradlew :shared:testDebugUnitTest`.
**Espejo back de este doc**: `odd/tasks/thirdparty-identity.md` en `parental_coordination_back`.

## Specs

- **S1** — "yo poder crear un terciario y agregarle los nombres y algun dato mas ahi":
  el alta de acceso temporal captura y persiste `firstName`, `lastName`, `phone`,
  `document`. Obligatorios: firstName, lastName (mín 2, máx 80); opcionales: phone,
  document (máx 30). El alta existente por email no cambia de forma.
- **S2** — "cuando yo creo una actividad y se la puedo asignar a alguien tiene que
  aparecer el nombre de ese terciario": el dropdown de asignatarios incluye a los
  terciarios con grant ACTIVO del propio visor como opciones elegibles, con su nombre
  declarado; el servidor ya filtra por dueño.
- **S3** — "en las cards de activities tiene que mostrarse el nombre del terciario
  para el que lo autorizo, para los demas solo muestra quien lo autorizo".
- **S4** — los campos viajan como `firstName|lastName|phone|document` en el alta
  (`POST /bonds/:bondId/third-parties`), en `GET /bonds/:bondId/third-parties` y en
  `GET /third-parties/me`.
- **S5** — la política de visibilidad (S3) vive en una única función policy del shared,
  cubierta por unit tests de ambos casos.
- **S6** — degradación: si el back viejo no manda los campos nuevos (nullable con
  default), ni el alta, ni el dropdown, ni las cards se rompen.
- **S7** — la visibilidad del nombre real la decide el SERVIDOR: el payload de
  actividades llega con
  `attribution = null | { kind: "member", name } | { kind: "third_party", name?, authorizedByName }`
  donde `name` sólo presente si el requester es el autorizante del grant.
- **S8** — el enmascaramiento viewer-aware también aplica al propio `GET /bonds`:
  el autorizante ve el nombre real del terciario en la lista de miembros; otro
  miembro ve la máscara actual.
- **S9** — "le podes otorgar los datos al third party ya creado": el PATCH de un
  acceso vigente también acepta identidad (`firstName`, `lastName`, `phone`,
  `document`); los campos presentes se aplican, los ausentes NO borran lo
  persistido (mismo PATCH parcial que relationship/accessEnd/note).

## Tasks

- T1 (S1) back DONE `9410292` (back `9410292`) — DTO/entity/migración `1794403200000`.
- T2 (S1) back DONE `9410292` — `third-parties.service.identity.spec.ts` (4 tests).
- T3 (S1) front DONE `8643b4c` — wizard con identidad + mensajes propios.
- T4 (S2) front DONE `4aa381d` — grants activos del visor como candidatos (4 tests).
- T5 (S3,S5) front DONE `4aa381d` — `AttributionPolicy.kt` única + línea en
  `ActivityCard.kt` (5 tests).
- T6 (S5,S6) front DONE `4aa381d` — suite 76/756; `:androidApp:assembleDebug` OK.
- T7 (S4,S7) back DONE `1beb690` — atribución server-side en listByBond/getDetail.
- T8 (S2,S8) back DONE `1beb690` — `hideThirdPartyIdentity` viewer-aware + asignación.
- T9 (S9) back DONE `9d4a55a` — `UpdateThirdPartyDto` acepta identidad; PATCH parcial;
  `identity-patch.spec.ts` (3 tests). Suite 107/1260.
- T10 (S9) front DONE `158f381` — hoja de edición precargada; `toPatchBody()` parcial;
  2 tests. Suite 76/758.

## Estado final

- Front: `feature/thirdparty-identity` pusheada (commits `8643b4c`, `4aa381d`, `158f381` + docs).
- Back: `feature/thirdparty-identity` pusheada (commits `9410292`, `1beb690`, `9d4a55a` + docs).
- Deuda conocida: `findThirdPartyDisplayNames` sin spec SQL de repositorio; guard de
  método opcional en `bonds.service` (double viejo del T12-verifier); listByBond +3
  queries fijas por request (O(1), no N+1).
- Revisión nativa (RDD): riesgo medio, `reviewDue=false` (`under_budget`); sin lineage.

## Log

- L1 (verbatim): "la idea primero es yo poder crear un terciario y agregarle los nombres
  y algun dato mas ahi .. luego cuando yo creo una actividad y se la puedo asignar a
  alguien tiene que aparecer elnombre de ese terciario, hoy aparece como Autorizado por
  Angelo .. luego en las cards de activities tiene qe mostrarse el nombre del terciario
  para el que lo autirzo , para los demas solo muestra quien lo autirzo"
- L2 (decisión): datos de alta = nombre + apellido + teléfono + documento;
  asignación = terciario como opción en dropdown; trabajo en ambos repos.
- L3 (evidencia): back en `parental_coordination_back`, entidad `auth_third_party`,
  `CreateThirdPartyDto` sólos email/relationship/accessEnd/note antes de la feature.
- L4 (verbatim): "le podes otorgar los datos al third party ya creado? Ahora subi esto a
  github .. y pasamos a la parte de actividades." — abre S9; push autorizado en ambos repos.
- L5 (diseño): el pedido "tarjeta -> Mejorar la visualizacion de los datos, me queres
  hacer un diseno nuevo con como esta ahora toda la vista" abrió `design/activity-card-ui/`
  con 3 artboards (01-ficha-alineada, 02-personas-al-frente, 03-riel-de-urgencia).
  Pendiente: elección del artboard.

- L6 (handoff, verbatim del tool): "Implement the design artboard
  design/activity-card-ui/02-personas-al-frente.html" — la tarjeta "Personas al frente"
  se implementa en Compose como feature nueva `activity-card-stack` (doc propio,
  `odd/tasks/activity-card-stack.md`); esta doc queda como feature de origen.
