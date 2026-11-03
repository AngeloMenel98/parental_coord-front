# Home — "Hoy, con anillo"

Objetivo: implementar en Compose el artboard `design/home-ui/04-hoy-con-anillo.html`
(combinación del 01 con la card de cumplimiento del 02), con la paleta dark Material
Baseline pedida. Branch: `feat/home-hoy-con-anillo`. Entrega en commits por unidad de
revisión. Runner: `./gradlew :shared:testDebugUnitTest` y `:shared:allTests`.

## Specs

S1. **Saludo centrado.** Fecha del día (`Martes 3 de noviembre` en el artboard; el texto
real usa la fecha actual con el mismo formato `"$dia, $díaDeMes $mesAbrev $año"`), luego
`Hola, {nombre}` en serif (headline), y **debajo y centrado** el chip de vínculo:
`Vínculo activo` / `Vínculo inactivo`.

S2. **Tarjeta "Hoy" clickeable.** Toda la tarjeta es el objetivo táctil: sin chevrons ni
botón "Agenda". Contiene el rótulo `Hoy`, el conteo (`3 actividades` en el artboard; texto
real `"${n} actividades"`, singular `1 actividad`), hasta 2 filas con hora (mono), título,
punto de categoría + nombre, y `StatusTag`; y si hay más, el pie
`"${restantes} actividad(es) más hoy"`. Al tocarla navega a la pestaña Actividades.
Sin actividades: la tarjeta se muestra con `No hay actividades para hoy` y sigue navegando.

S3. **Franja de métricas centrada y pulsable.** Una sola superficie con tres celdas
separadas por hairlines: `Asignadas`, `Vencidas`, `Saldo gastos`. Label y valor centrados.
Sin chevrons. Cada celda es un botón: `Asignadas` y `Vencidas` navegan a Actividades;
`Saldo gastos` navega a Gastos. Usuario textual de la revisión: *"estas cards me deberian
llevar a las actividades asignadas, a la vista de gastos."*

S4. **Anillo de cumplimiento.** Card con `Cumplimiento`, período
(`Noviembre · 8 de 12 tareas`), y `Detalle`. Anillo de **12 segmentos**; el porcentaje
propio centrado en serif y debajo `X de Y`. El color va de rojo a verde según el avance:
`< 25%` rojo, `< 50%` naranja, `< 75%` ámbar, `>= 75%` verde, usando el par
**dot/content** de `StatusTokens` (Overdue / InProgress / Verify / Done). Debajo, una fila
por otro miembro con barra fina y porcentaje.

S5. **Hijos.** La sección se titula `Hijos` (no `Familiares`): *"En vez de familiares, que
sean 'Hijos' o algo similar."* Cada hijo es un chip pulsable con avatar de inicial, nombre y
estado: `"${n} vencida(s)"` si tiene vencidas, si no `Al día`.

S6. **Tema.** El esquema oscuro pasa a la paleta Material Baseline: primary `#BB86FC`,
primaryVariant/primaryContainer `#3700B3`, secondary `#03DAC6`, surface/background
`#121212`, error `#CF6679`, onPrimary/onSecondary/onError `#000000`,
onSurface/onBackground `#FFFFFF`. Roles que la tabla no define: superficie de tarjeta
`#1E1E1E` (`#121212` + overlay blanco 8%), `onSurfaceVariant` `#B3B3B3`, `outline`
`#8A8A8A`, divisores al 12% de blanco. El ítem seleccionado del nav usa un color con
contraste AA sobre la píldora: `primary` en claro, `onPrimaryContainer` en oscuro
(`#BB86FC` sobre `#3700B3` da 4.39:1 y NO alcanza).

S7. **Invariantes que no cambian.** `—` para una métrica que no se pudo calcular (nunca
`0`); el saldo se formatea con `formatMoney` y se colorea por signo con `StatusTokens`
(verde > 0, rojo < 0); los conteos se derivan en el cliente de la lista del vínculo y el
saldo NO se recalcula. Los estados loading / error / sin vínculo siguen existiendo.

## Non-goals

- Filtro preseleccionado en Actividades (asignadas/vencidas): hoy la pantalla sólo filtra
  por tipo e hijos. Las celdas navegan a la pestaña; el filtro queda como follow-up.
- Selector de tema dentro de la app: el tema sigue a `isSystemInDarkTheme()`. El switch
  sol/luna del artboard es andamiaje de revisión.

## Tasks

| ID | Spec | Ruta | Estado |
| --- | --- | --- | --- |
| T1 | S6 | inline | hecho · `eb84c1d` |
| T2 | S2,S5 | inline | hecho · `63f917e` |
| T3 | S1,S3,S4,S5 | inline | hecho · `feat(home): redesign Inicio` |
| T4 | S2,S4,S5,S7 | inline | hecho · en los dos commits anteriores |
| T5 | S6 | inline | hecho · `eb84c1d` (system.md) |

Los writers delegados no arrancaron: la sesión padre está en `/home/angelo`, que no es un
repo git, y el bootstrap del writer exige un project root seguro. El trabajo se hizo
inline.

## Log

L1 (verbatim): "lets redesign the home screen of parental frontend"

L2 (verbatim): "estas cards me deberian llevar a las actividades asignadas, a la vista de gastos."

L3 (verbatim): "Me gustaria que no sea light mode, por ahi buscar q sea opcional"

L4 (verbatim): "Me gusta tanto el resumen de actividades de hoy , donde se encuentra lo de vinculo activo, no uses los chevron para navegar que sea una card clickeable."

L5 (verbatim): "En vez de familiares, que sean 'Hijos' o algo similar."

L6 (verbatim): "Me gusta que tenga forma circular con cada tarea completada. Por ahi que a medida q va llegando al 100% pase de rojo a verde."

L7 (verbatim): "usa un toggle switch con una luna o sol para claro o oscuro."

L8 (verbatim): "Podes hacer una combinacion entre el 01 y el 02 entre lo que te comente? es decir usar todo lo del uno pero cambiarle la card de porcentajes"

L9 (verbatim): "me imagino que esto queda afuera de la view"

L10 (verbatim): "me gustaria q los iconos esten por dentro del toggle switch"

L11 (verbatim): "los datos tienen que estar centrados"

L12 (verbatim): paleta dark Material Baseline — primary #BB86FC, primaryVariant #3700B3,
secondary #03DAC6, secondaryVariant #018786, background/surface #121212, error #CF6679,
onPrimary #000000, onSecondary #000000, onBackground/onSurface #FFFFFF, onError #000000.

L13 (verbatim): "Implement the design artboard design/home-ui/04-hoy-con-anillo.html"

L14 (decisión): el artboard quedó `design/home-ui/04-hoy-con-anillo.html`; los artboards
01–03 se conservan como alternativas.

L15 (decisión): S3 navega a la pestaña, sin filtro preseleccionado (ver Non-goals).

L16 (ejecución): `develop` no es la rama por defecto del remoto, así que el trabajo va en
`feat/home-hoy-con-anillo`. **Frontera de slice**: el conteo del gate mide la rama entera
contra `origin/main` (99 commits heredados de `develop`, ~34k líneas); los commits de esta
feature son las unidades de revisión reales:
`eb84c1d` tema (~200), `63f917e` dominio+API+tests (656), `feat(home): redesign Inicio`
(pantalla + contrato), `docs(design)` (documento + artboards).

L17 (desvío de S4/S5): el artboard pide un botón `Detalle` en la card de cumplimiento y
chips de hijo pulsables. No hay destino para ninguno de los dos (no existe pantalla de
detalle de cumplimiento ni de hijo), así que se omitieron en vez de dejar botones que no
hacen nada. Cuando exista el destino, se agrega el `onClick`.

L18 (desvío de polaridad): las tarjetas del Home usan `surfaceVariant` como contenedor,
igual que el resto de la app, en vez de la polaridad invertida del artboard (pantalla
`surfaceVariant`, tarjeta `surface`). El track del anillo pasa a `outlineVariant` porque
`surfaceVariant` sobre una tarjeta `surfaceVariant` es invisible.
