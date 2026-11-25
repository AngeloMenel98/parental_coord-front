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

- T1 (S1) — `ActivityCard.kt` rework: columna de asignatario, meta de una línea,
  atribución; tests de contract (source-level).
- T2 (S2, S3) — `AgendaList.kt` riel + stack con despliegue; test del modelo
  de stacks (agrupación por franja) en commonTest.
- T3 (S4, S5) — dropdowns multi-select + estado vacío de Hoy; tests del VM.
- T4 — gates: suite completa + assembleDebug; cierre del feature doc.

## Log

- L1 (verbatim pedido): "Implement the design artboard
  design/activity-card-ui/02-personas-al-frente.html" (handoff del canvas).
- L2: historial de la iteración del artboard (13 comentarios aplicados) vive en
  `design/activity-card-ui/comments.json`.
