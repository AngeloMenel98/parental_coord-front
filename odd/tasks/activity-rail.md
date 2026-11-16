# Actividades — Riel del tiempo

**Objetivo:** implementar el artboard `design/activity-calendar-ui/03-riel-del-tiempo.html` en el stack real (Compose Multiplatform, módulo `shared`): un riel cronológico continuo con eje horario, bloques de altura proporcional a la duración, solapamientos lado a lado, encabezados de día pegajosos y plegables, mapa de densidad mensual plegable, panel "Lista del día", FAB único de alta y retorno a hoy.
**Rama:** `ft/activity-rail` (desde `develop`).
**Entrega:** unidades de ~400 líneas autoradas, tests junto al comportamiento, Conventional Commits. Sin push ni PR.
**Runner:** `./gradlew :shared:testDebugUnitTest` · compilación `./gradlew :androidApp:assembleDebug`.

## Specs

**S1.** "Implement the design artboard design/activity-calendar-ui/03-riel-del-tiempo.html … Use the artboard as the visual spec: map its parts to the project's existing components and styles, and do not copy the artboard HTML verbatim."

**S2.** "Take colors, typography, spacing, radius and shadows from design/system.md, the project's token source."

**S3.** El riel: "Un único riel cronológico con encabezados de día pegajosos y plegables, eje horario por día, píldoras proporcionales y solapamientos lado a lado". Altura de bloque proporcional a la duración, con altura mínima de toque para duraciones cortas.

**S4.** Mapa de densidad mensual: "codifica cantidad con opacidad y número —y que se muestra u oculta desde el pill del encabezado del calendario—". Ubicación corregida por el humano: "ME referia a que este arriba a la derecha, como antes."

**S5.** "Esto me imagino que es para plegar y desplegar el dia." El control del encabezado de día pliega y despliega ese día; no es de alta.

**S6.** "Me gustaba el Riel del tiempo, que te pueda llevar al Hoy". Un control que lleva al día de hoy en el riel.

**S7.** "Poneme el boton de agregar en un circulo abajo." El alta es un único FAB circular abajo a la derecha.

**S8.** El flujo de completado no cambia: `Confirmar` → *Por verificar* → *Cumplida*. Sin checkbox que saltee la verificación del otro progenitor. Decisión del humano: "Mantene el flujo como antes".

**S9.** Sin coral y sin nav nueva: se conservan el acento teal y las categorías del servidor; el nav sigue siendo `Inicio · Actividades · Gastos · Terceros`. Decisión del humano: "Solo la mecánica, sobre el dominio actual".

**S10.** No existen subtareas en el modelo (`Actividad` en `ActivitiesModels.kt` no tiene `subtasks`). El contador `2/5` del artboard se omite; no se inventa el dato.

**S11.** Accesibilidad: etiqueta semántica por bloque (título, rango, duración, estado), contraste AA, foco visible, objetivos ≥ 44dp, estado nunca solo por color. **Excepción decidida por el humano:** los chips de filtro dentro del calendario van a 34dp, como el mockup; siguen por encima del mínimo de 24dp de WCAG 2.2 y el resto de los objetivos táctiles conserva 44dp.

**S12.** "Fijate que el boton de 'Hoy' si no hay nada en la fecha, tiene que devolver que no hay actividad." Si el día de hoy no tiene actividades, el control de "Hoy" no salta en silencio: informa que hoy no hay actividades.

**S13.** "El btono de ver Mes/ocultar con solo los chevron es suficiente." El control de mostrar/ocultar el mapa de densidad queda solo con el chevron: sin texto.

**S14.** "dentro del calendario deberia estar el filtro por Hijos y por Evento/Obligacion." Los filtros por hijo y por tipo (Eventos / Obligaciones) viven dentro del panel del calendario, no en una fila aparte debajo.

**S15.** El riel abre anclado en la línea de "ahora" **solo si hay una actividad en curso**; si no hay nada en curso, abre en el primer bloque del día. Decisión del humano: "anclar solo si hay algo en curso".

## Tasks

| ID | Specs | Ruta | Estado | Commit |
| --- | --- | --- | --- | --- |
| T1 | S3 | delegado | hecho | 947671a |
| T2 | S3 | delegado | hecho | a7821c6 |
| T3 | S3, S11 | delegado | hecho | a7821c6 |
| T4 | S3, S5 | delegado | hecho | 20d4c2d, d35ca32 |
| T5 | S4 | delegado | hecho | f2554e3, f757f00 |
| T6 | S6, S7, S8 | delegado | hecho | c817fd0 |
| T7 | S11 | delegado | hecho | 9351d42 |
| T9 | S3, S11 | delegado | hecho | f54f0a6, 34c6174 |
| T10 | S12, S13, S14, S15 | delegado | en curso | — |
| T8 | S1, S3 | delegado | pendiente | — |

- **T1** — Derivación pura del riel: eje del día, escala minutos→dp, carriles de solapamiento, ticks horarios. `UI/RailDerivation.kt` + `commonTest/.../RailDerivationTest.kt`.
- **T2** — Canaleta horaria: gutter con etiquetas `TypeTokens.time` y grilla de horas.
- **T3** — Bloque proporcional de actividad: riel de categoría, estado, altura mínima, semántica.
- **T4** — Secciones de día con encabezado pegajoso y plegado; sección `Sin fecha de inicio`.
- **T5** — Mapa de densidad mensual inline y plegable, reutilizando `buildMonthGrid` / `buildDayDots`.
- **T6** — Integración de pantalla: FAB único, control "Hoy", índice de salto sobre el riel.
- **T7** — Estados del riel (carga, vacío, error) y guardas de montaje del swipe.
- **T8** — Retiro de `AGENDA`/`SEMANA`: enum de modo, `Crossfade`, `AgendaList`, `WeekAgenda`, `WeekOverview`, `DayAgenda`, `DayAgendaHeader`, la hoja modal de salto y los tests/guardas que queden huérfanos. Última tarea, cuando el riel ya está montado: la rama compila en todo momento.

**Secuencia:** el riel se construye al lado de los modos actuales y el retiro va al final (T8). Así cada commit intermedio compila y se puede revisar sin una pantalla rota.

## Log

**L1.** "Implement the design artboard design/activity-calendar-ui/03-riel-del-tiempo.html … Implement it in the project's real stack. Use the artboard as the visual spec: map its parts to the project's existing components and styles, and do not copy the artboard HTML verbatim. Take colors, typography, spacing, radius and shadows from design/system.md, the project's token source. Report progress with the gentle_design tool … This design is not linked to an ODD feature document: size it under the project's ODD rules as usual."

**L2.** Decisiones del humano sobre el PRD v0.2: "Solo la mecánica, sobre el dominio actual" y "Mantene el flujo como antes".

**L3.** Comentarios del canvas sobre `03`: "Poneme el boton de agregar en un circulo abajo." · "ME referia a que este arriba a la derecha, como antes." · "Me gustaba el Riel del tiempo, que te pueda llevar al Hoy" · "Esto me imagino que es para plegar y desplegar el dia."

**L4.** Evidencia de exploración: filas = `TimelineRow(actividad, scheduledStart, localStart, deadline)` (`TimelineFormatters.kt:47`); no hay layout proporcional ni `stickyHeader` en uso; `Actividad` no tiene subtareas; el modo actual es `rememberSaveable` de pantalla (`ActivitiesScreen.kt:96-98`); tests puros en `commonTest` con zona pasada por parámetro.

**L5.** Decisión de producto: resuelta en L6. Bloqueaba T4 y T6.

**L6.** El humano eligió **"Reemplaza Agenda y Semana"**: el riel es la única vista de Actividades; se eliminan los modos `AGENDA`/`SEMANA` y su `Crossfade`. El retiro se ejecuta en T8, después de montar el riel, para que la rama compile en todos los commits.

**L7.** Fronteras de entrega de esta rama. Base: `develop` (que está 101 commits adelante de `origin/main`; el medidor de slice del harness compara contra `origin/main` y contea ese trabajo ajeno, no el de esta feature). Entregas propias:

| Commit | Contenido | Líneas |
| --- | --- | --- |
| `2da2013` | `odd/tasks/activity-rail.md` | 68 |
| `41a8bb1` | `design/activity-calendar-ui/` (7 archivos) | 1681 |
| `947671a` | `RailDerivation.kt` + `RailDerivationTest.kt` | 551 |
| `a7821c6` | `RailCanvas.kt` + `RailGeometry` + `railHourTicks` internal | 452 |
| `20d4c2d` | `RailIndex.kt` + `RailIndexTest.kt` | 273 |
| `d35ca32` | `RailTimeline.kt` | 493 |
| `f2554e3` | `MonthDensity.kt` + test + `isOverdueStatus` compartido | 364 |
| `f757f00` | `MonthDensityMap.kt` + `MonthMapGeometry` | 606 |
| `c817fd0` | `ActivitiesScreen.kt` + guardas de pantalla y swipe | 929 |
| `9351d42` | estados del riel + foco + swipe, con guardas | 580 |
| `f54f0a6` | modo angosto de la barra de swipe | 557 |
| `34c6174` | regla de 44dp: tercer estado sin gesto | 539 |
| `7f9d5c6` | `applicationId` por `-PappId`, default intacto | 5 |

La siguiente unidad arranca desde `d35ca32`.

**L8.** La entrega de T4 se había armado como un solo commit de 766 líneas, por encima del objetivo de ~400. Se partió en dos (`20d4c2d` índice + tests, `d35ca32` composables) reescribiendo el último commit propio, todavía sin push: el índice tiene consumidor recién en T6, pero se sostiene solo con sus 11 tests, y el timeline se revisa después sin arrastrar la aritmética.

**L9.** `f757f00` son 606 líneas en un solo archivo de composables (`MonthDensityMap.kt`, 537 de las cuales son KDoc). No se parte: es un componente único y la alternativa sería trocear un archivo por su documentación, que empeora la revisión en vez de mejorarla. Queda registrado como excepción al objetivo de ~400.

**L10.** `c817fd0` son 929 líneas (585 altas, 344 bajas): la reescritura del contenido de una pantalla de 696 líneas más sus guardas, que tienen que moverse en el mismo commit porque penean justamente lo que la pantalla dejaba de montar. Gate del padre: 50 suites, 473 tests, 0 fallos.

**L11.** Consecuencia del reemplazo que el humano no decidió explícitamente: hoy las acciones de swipe (cancelar / no asistir / eliminar) viven en las filas de la agenda. Al retirar la agenda del montaje, esa vía desaparece y queda solo el detalle. T7 las restituye sobre los bloques del riel para mantener paridad; si el humano prefiere detail-only, se revierte esa parte de T7.

**L12.** Defecto introducido por el riel y encontrado en T7: `ActivitySwipeBox` revela la barra al 55% del ancho del bloque. En un bloque de media calle (~146dp) la barra queda en ~80dp y el tercer botón se recorta: el gesto y las acciones funcionan y TalkBack las alcanza, pero el tercer botón no es alcanzable con el dedo. Decisión del humano: **"Modo angosto en ActivitySwipeBox"** — bajo un umbral de ancho, la barra usa acciones con ícono y nombre accesible en vez de tres botones con texto. Es seguro porque tras T8 ese componente queda con un solo consumidor: el riel. Va como T9.

**L13.** T9 quedó en tres estados decididos por ancho medido, sin flag del llamador: `LABELLED` (barra revelada ≥ 160dp), `ICON_ONLY` (cada acción conserva ≥ `SwipeMinActionWidth = 44.dp`) y `NO_GESTURE`, que **no compone `pointerInput`** y deja las acciones al detalle y a `customActions`. Números de referencia a ~300dp de canvas: 296dp → etiquetas (162.8dp), 146dp → íconos (48.67dp por acción), 96dp → sin gesto. La guarda tiene prueba de mutación: romper la condición de la rama sin gesto hace fallar el test.

**L14.** Consecuencia en pantalla real, pendiente de criterio de producto: en un teléfono de 360dp el canvas queda en ~268dp, así que los bloques de dos carriles (130dp) y de tres caen en `NO_GESTURE`. Las actividades solapadas pierden el swipe y su única vía táctil es el detalle. Si eso resulta demasiado restrictivo en el dispositivo, la salida correcta es acelerar el acceso al detalle, no bajar el objetivo de 44dp.

**L15.** El primer APK se instaló sobre el paquete equivocado: el teléfono tiene `com.parental.android.local` (desarrollo, el del icono con la L) y `com.parental.android` (producción). El humano confirmó que la app de trabajo es `.local`. Para que los dos ids convivan sin editar el archivo a mano, `androidApp/build.gradle.kts` toma el `applicationId` de `-PappId` y cae en `com.parental.android`; `aapt2` confirma que sin la propiedad el paquete sigue siendo el de producción. Instalado sobre `.local` conservando la sesión.

**L16.** El humano pidió mergear la rama a `develop` y reinstalar `.local` desde ahí. El merge se hace con T8 pendiente: `AgendaList`, `WeekAgenda`, `WeekOverview`, `DayAgenda*`, `AgendaTimelineRow` y `CalendarJumpSheet` siguen en disco, ya sin montarse. Es deuda de código muerto, no de comportamiento: la pantalla usa solo el riel.

**L17.** Corrección de base: la app de desarrollo se compila desde `ft/admin-reports-ui` (34 commits que `develop` no tenía), así que instalar un build de `develop` retrocedió Home, Gastos y Admin en el teléfono. La rama correcta es `ft/rail-on-admin` (base de trabajo + riel) y el build de desarrollo usa la convención del repo: `-PlocalBuild=true -PapiBaseUrl=http://192.168.0.17:3000/api -PwsUrl=ws://192.168.0.17:3000/realtime`. El `-PappId` que agregué queda sin efecto: es un segundo mecanismo para lo mismo.

**L18.** Pedido del humano, textual: "dejame en develop mergeado y pusheado los 34 commits" → merge de `ft/rail-on-admin` en `develop` (`010aa92`) y push a `origin/develop`. Nuevos requisitos: S12, S13, S14 (T10).

**L19.** Artboard 03 actualizado y aprobado (S12, S13, S14). Decisiones del humano para la implementación: "utiliza los chips del mockup" (34dp, excepción registrada en S11) y "anclar solo si hay algo en curso" (S15). El mapa de densidad abre **expandido** por defecto, porque los filtros viven dentro y plegarlo los dejaría inalcanzables.
