# Tarjeta de actividades — Personas al frente

**Objetivo**: implementar en Compose la tarjeta de actividades del artboard
`design/activity-card-ui/02-personas-al-frente.html` con todo el flujo aprobado:
persona asignataria al frente (columna de avatar), hora/límite en una línea,
hijos como chips, riel con punto por categoría + dot-contador en stacks,
stack con fantasmas abajo-izquierda y despliegue, mapa de densidad con
dropdowns multi-select (checks, Todos excluyente) y botón Hoy.

**Rama**: `feature/thirdparty-identity` (continúa, mismos límites de riesgo que la feature origen).
**Delivery**: commits unitarios (~400 líneas), tests y docs con el comportamiento.
**Test runner**: `./gradlew :shared:testDebugUnitTest`; gate de build `:androidApp:assembleDebug`.

## Specs

- **S1** — la tarjeta muestra: asignatario en columna de 40dp (avatar + rol),
  título + pill, meta en UNA línea (hora mono + separador + límite, rojo si vencido),
  hijos como chips, categoría con dot en el pie, atribución de tercero vía
  `ActivityAttribution` (S7 de la feature origen: el shader decide el nombre).
- **S2** — riel a la izquierda de la lista con punto del COLOR DE LA CATEGORÍA
  centrado verticalmente en cada tarjeta; anillo punteado rojo si vencida.
- **S3** — mujeres apiladas ("stack"): tarjeta compuesta con exactamente una
  estructura card-normal + fantasmas abajo-izquierda (2 capas) + dot-contador EN
  EL RIEL con el color de la categoría de la primera actividad; tocar despliega
  las apiladas a ancho completo y desaparecen fantasmas/badge.
- **S4** — mapa de densidad como hoy (MonthDensityMap existente) + dropdowns
  multi-select con check en caja teal; "Todos" excluyente (elegir uno lo despeja,
  desmarcar la última vuelve a Todos); label del chip muestra las selecciones.
- **S5** — botón "Hoy" vuelve al día actual; día sin actividades muestra
  "No hay actividades el <fecha>".
- **S6** — degradación: back viejo sin attribution/identidad no rompe nada
  (el modelo origen ya con defaults); el stack sin datos apila igual.

## Tasks

- T1 (S1) DONE `6157988` — ActivityCard rework (lead column, meta 1 línea,
  atribución).
- T2 (S2,S3) DONE `6157988` — riel + stack (8+7 tests; suite 78/773).
- T3 (S4,S5) DONE `4f68f39` — dropdowns multi-select (ChildrenDropdown/
  ActivityTypeDropdown), vacío con fecha; contract Android retargeted;
  ChildrenDropdownLabelTest (4); suite 79/777.
- T4 — suite final 79/777 verde, assembleDebug OK; cierre abajo.

## Log

- L1 (verbatim pedido): "Implement the design artboard
  design/activity-card-ui/02-personas-al-frente.html" (handoff del canvas).
- L2: historial de la iteración del artboard (13 comentarios aplicados) vive en
  `design/activity-card-ui/comments.json`.

- L3 (cierre): implementación completa de T1-T3 + gates. Deuda menor: (a) `AgendaTimeline`
  sigue INERTO para el usuario — `ActivitiesScreen` monta el riel preexistente
  (`RailAnchoredTimeline`), no la Agenda nueva; conectar la vista es el paso pendiente
  que activa todo (tarjeta, riel, stack) en la app; (b) un test en dispositivo podría
  reasentar el offset del dot 1-2dp; (c) el import cruzado `initialsOf`
  (activities→thirdparty) convendría reubicarlo a un paquete común.
