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

**S11.** Accesibilidad: etiqueta semántica por bloque (título, rango, duración, estado), contraste AA, foco visible, objetivos ≥ 44dp, estado nunca solo por color.

## Tasks

| ID | Specs | Ruta | Estado | Commit |
| --- | --- | --- | --- | --- |
| T1 | S3 | delegado | pendiente | — |
| T2 | S3 | delegado | pendiente | — |
| T3 | S3, S11 | delegado | pendiente | — |
| T4 | S3, S5 | delegado | pendiente | — |
| T5 | S4 | delegado | pendiente | — |
| T6 | S6, S7, S8 | delegado | pendiente | — |
| T7 | S11 | delegado | pendiente | — |
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
