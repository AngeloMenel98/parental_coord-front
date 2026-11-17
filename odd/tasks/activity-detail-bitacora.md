# Actividad — detalle con bitácora visual

Objetivo: implementar el artboard `design/activity-detail-ui/04-bitacora-visual.html` en el
detalle de actividad real (`ActividadDetailSheet.kt`): riel de 5 etapas del ciclo de vida,
bloque "Qué falta" con medidor y checklist, y bitácora de eventos derivada de los datos que el
DTO ya expone. Sin dependencias nuevas, sin backend nuevo.

- Branch: `ft/admin-reports-ui` (árbol con cambios ajenos sin commitear: NO se commitea este trabajo).
- Delivery: implementación inline (una sola unidad de comportamiento), sin commits.
- Test runner: `./gradlew :shared:testDebugUnitTest`.

## Specs

S1. "Implement it in the project's real stack. Use the artboard as the visual spec: map its
parts to the project's existing components and styles, and do not copy the artboard HTML
verbatim."

S2. "Take colors, typography, spacing, radius and shadows from design/system.md, the project's
token source." En este repo los tokens viven en `core/ui/theme/` (`Spacing`, `AgendaGeometry`,
`ParentalShapes`, `MaterialTheme.typography/colorScheme`); no se agregan literales `.dp` ni hex.

S3. El detalle debe mostrar: (a) riel de 5 etapas Creada → Asignada → Confirmada → En progreso →
Finalizada; (b) bloque "Qué falta" con medidor y checklist de pasos pendientes; (c) bitácora de
eventos con fecha, hora, autor y contexto del hijo.

S4. "Me referia a los botones de cambio de estado, por ejemplo: Asignar, Asistir/No asistir,
Finalizar.. etc" — el pie queda con acciones de cambio de estado; NO se le agregan acciones de
evidencia nuevas. El gate de cada botón sigue viniendo del servidor (`canAssign`, `canReassign`,
`canConfirm`, `canComplete`, `canDecline`): el cliente no deriva la política.

S5. "Bien la bitacora pero me gustaria que tenga mas info directa de a q hijo se le asigno, la
descripcion, etc." — la bitácora lleva el nombre del hijo al que refiere cada evento.

S6. Veracidad: ningún hito se inventa. Solo se derivan eventos de `createdAt`, `confirmedAt`,
`completedAt` y `declinedReason`; el paso "Turno" usa `isPast` del servidor; el paso
"Comprobantes" usa la lista de adjuntos ya cargada.

S7. Fuera de alcance por falta de dato: la tira de densidad de eventos por día (necesita un
endpoint de eventos; con 2–4 timestamps reales el gráfico nace vacío) y el evento "Asignada a"
(no existe `assignedAt` en el DTO).

## Tasks

- T1 · S3/S6 · inline · derivación pura `BitacoraDerivation.kt` + `BitacoraDerivationTest.kt`
- T2 · S1/S2/S4/S5 · inline · `ActividadDetailSheet.kt`: tarjeta Estado (riel + checklist) y tarjeta Bitácora

## Log

L1 (pedido del humano, verbatim): "Implement the design artboard
design/activity-detail-ui/04-bitacora-visual.html ... Implement it in the project's real stack.
Use the artboard as the visual spec: map its parts to the project's existing components and
styles, and do not copy the artboard HTML verbatim. Take colors, typography, spacing, radius and
shadows from design/system.md, the project's token source."

L2 (comentario de diseño previo, verbatim): "Me referia a los botones de cambio de estado, por
ejemplo: Asignar, Asistir/No asistir, Finalizar.. etc" · "Me gusta lo de Estado, pero el quedan x
hs para tal, no me convence, cambialo por otra cosa" · "Bien la bitacora pero me gustaria que
tenga mas info directa de a q hijo se le asigno, la descripcion, etc."

L3 (decisión): se descarta el contador "queda 1 día 8 h" del artboard; el bloque pasa a ser
"Qué falta" con checklist derivada de datos reales.

L4 (decisión): "Cargar comprobante" se queda en el PIE FIJO (`FR-9`, documentado en
`ActividadDetailSheet.kt`), no baja a la tarjeta de comprobantes como en el artboard: moverlo
rompe el requisito de que la CTA no se pierda al scrollear. Es una desviación consciente del
dibujo.

L5 (evidencia de cierre, T1+T2):
- RED observado: `BitacoraDerivationTest` no compilaba (`Unresolved reference 'BitacoraKind'`,
  `activityStages`, `detailChecklist`) antes de escribir `BitacoraDerivation.kt`.
- GREEN final: `./gradlew :shared:testDebugUnitTest` → **558 tests, 0 fallos**:
  `BitacoraDerivationTest` 34 + `BitacoraStagesMatrixTest` 6.
  `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL, exit 0.
  (El host OOM-killeó la primera corrida con el heap por defecto: usar
  `-Dorg.gradle.jvmargs=-Xmx1536M --max-workers=2`.)
- Dos fallos propios corregidos en el camino, ambos de la implementación y no de la regla:
  (a) `when` usado como nombre de parámetro lambda (palabra reservada) en `detailChecklist`;
  (b) una aserción del test fijaba `"límite 6 ago 2026"` y `formatDeadline` omite el año cuando
  coincide con el año en curso — el test pasó a fijar formato y día, no el año del reloj.
- `git diff --numstat`: `ActividadDetailSheet.kt` +209/−0, `Spacing.kt` +22/−0. Cero borrados ⇒
  `DetailFooter`, sus gates de servidor y los items previos del `LazyColumn` intactos.
- Archivos: `BitacoraDerivation.kt` (nuevo), `BitacoraDerivationTest.kt` (nuevo),
  `BitacoraStagesMatrixTest.kt` (nuevo), `ActividadDetailSheet.kt`, `core/ui/theme/Spacing.kt`.
- Sin commits: el árbol trae cambios ajenos (`AuthApi.kt`, `LoginViewModel.kt`) en una rama
  que no es de esta feature; commitear aquí los mezclaría.

L6 (verificación independiente, `gentle-ai-verify`, dos rondas):
- Ronda 1 → FAIL con dos defectos reales: **B1** `assisting` ausente del mapa de estados (caía
  en "Creada") y **B2** el modelo de índice único pintaba "Confirmada" alcanzada en una
  actividad cerrada sin marca de confirmación. Más una observación de accesibilidad: el estado
  hecho/pendiente de cada paso no entraba en el árbol de semántica.
- Corrección aplicada: `activityStages` pasó de índice único a CONJUNTO de etapas alcanzadas
  (el hueco en "Confirmada" queda visible, `current` = la más alta), `ASSISTING` agrupado con
  `IN_PROGRESS`, el tramo del riel se pinta sólo si AMBOS extremos se alcanzaron, y cada
  `CheckRow` anuncia "Hecho"/"Pendiente" con su detalle. 8 tests nuevos en la ronda.
- Ronda 2 → PASS en los 8 puntos revisados (B1, B2, ausencia de nuevas afirmaciones falsas,
  ningún consumidor dependía de la semántica de prefijo, cero regresiones, disciplina de tokens,
  verde, cobertura). Observaciones abiertas, todas de menor entidad y anotadas allí:
  `cancelled`/`not_assisting` se ubican en "Asignada" aunque nunca se hayan asignado, y
  `created` + `assignedConfirmed` deja un hueco en "Asignada": ambos son sub-afirmaciones de
  estados raros del servidor, no defectos del riel.
- La propiedad central de B2 (riel NO contiguo, p. ej. `11011`) no la fijaba ningún test: se
  agregó `BitacoraStagesMatrixTest` con la matriz de estados y el `mask` de 5 bits.
