# Gasto — "Expediente" (detalle)

Objetivo: implementar en Compose el artboard `design/expense-detail-ui/01-expediente.html`.
Branch: `feat/expenses-libro-swipe`. Runner: `./gradlew :shared:testDebugUnitTest`; build `:androidApp:assembleDebug`.

## Specs

S1. **Encabezado del gasto.** Tarjeta sobre `surfaceVariant` (la misma superficie de la card de
   cumplimiento) con el chip de estado y el de categoría (con color y **sin ícono**), el concepto,
   el monto en serif y `Tu parte` con la parte del viewer (`viewerShare`). Debajo, una barra de
   división de dos tramos con la leyenda `Pagador {splitRatioPayer}%` / `Otro {splitRatioOther}%`.

S2. **Datos del gasto.** Una tarjeta con cuatro pares etiqueta/valor en grilla: `Fecha`,
   `Solicitó`, `Contraparte`, `Hijos`. Un dato que el contrato no trae se muestra `—`, nunca un
   texto inventado. `Hijos` es el pedido del usuario: "que sea el nombre del hijo/hijos al que fue
   el gasto"; el contrato actual no expone los hijos del gasto, así que hoy queda en `—`.

S3. **Descripción.** Tarjeta propia con la descripción del gasto.

S4. **Documento.** Si `hasDocument` es falso: aviso `Sin comprobante adjunto` con el texto "El
   gasto se registró sin documento de respaldo." y un botón `Adjuntar` con ícono de clip que abre
   el selector, sube el archivo y confirma el adjunto. Si existe: `Comprobante adjunto`.

S5. **Trazabilidad desplegable.** Un solo control plegable (encabezado `Trazabilidad` con
   chevron, sin triángulo nativo) que envuelve una **línea temporal continua**. Los pasos sólo
   existen cuando ocurrieron: `Gasto creado` siempre; `Aprobado` si hay `approvedAt`; `Pago` si
   hay `paidAt`; `Rechazado` si el estado es rechazado. Si el gasto sigue esperando, se suma un
   paso actual (`Esperando aprobación` o `Esperando pago`), nunca un paso futuro con guion.

S6. **Acciones al pie.** Sólo con los flags del servidor: `Aprobar`/`Rechazar` (contraparte en
   pendiente), `Marcar como pagado`, `Editar`/`Cancelar` (solicitante en pendiente). En estado
   terminal, un aviso de cierre. Sin botones inventados.

S7. **Invariantes.** Tokens del sistema (`design/system.md`); categoría sin ícono; `—` para
   faltantes; ningún dato derivado se inventa en el cliente; el saldo no se recalcula.
   El texto "Los próximos pasos aparecen a medida que ocurren" del artboard **no** se implementa.

## Tasks

| ID | Spec | Ruta | Estado |
| --- | --- | --- | --- |
| T1 | S5 | dominio: `expenseTimeline` + tests | done |
| T2 | S1,S2,S3,S4 | encabezado, datos, descripción y documento | done |
| T3 | S5 | trazabilidad plegable | done |
| T4 | S4,S6 | adjuntar comprobante (VM) y acciones al pie | done |
| T5 | S1-S7 | contratos de fuente, verificación y dispositivo | done (dispositivo bloqueado) |

## Log

L1 (verbatim): "Implement the design artboard design/expense-detail-ui/01-expediente.html"

L2 (comentario pendiente, verbatim): "Este dato, no hace falta" sobre "Los próximos pasos aparecen
a medida que ocurren." → no se implementa ese texto.

L3 (decisión): `Hijos` se muestra con `—` porque `ExpenseResponseDto` no expone los hijos y el
gasto no trae `activityId`; inventar un nombre sería peor que el hueco. Queda anotado como deuda
de contrato (el servidor tendría que resolver activity → children).

L4 (implementación): T1–T4 entregados en un solo work unit. `ExpenseTimeline.kt` resuelve la
línea temporal pura (creado; aprobado si hay `approvedAt`; pago si hay `paidAt`; rechazo si el
estado es rechazado; paso actual de espera cuando corresponde) y `ExpenseTimelineTest` fija los
cuatro estados. `ExpenseDetailScreen` se reescribió con el expediente: encabezado sobre
`surfaceVariant` con chips de estado/categoría (categoría sin ícono), monto serif, `Tu parte` del
viewer y barra de división con leyendas; grilla `Fecha/Solicitó/Contraparte/Hijos` con `—` en
Hijos; descripción; documento con `Adjuntar` (clip) conectado a `ExpensesViewModel.attachDocument`
(comprime, presigna, sube con PUT y confirma); trazabilidad en un único bloque plegable; y barra
de acciones fija que sólo usa los flags del servidor. `ExpensesScreen` pasa `currentUserId` al
detalle para calcular `Tu parte`.

L5 (verificación): `ExpenseDetailExpedienteContractTest` (6 casos) + `ExpenseTimelineTest` (7)
RED antes de escribir la UI (el dominio no compilaba; los contratos de diseño se escribieron
contra la fuente final). Suite completa **55 suites / 507 tests, 0 fallos**;
`:androidApp:assembleDebug` verde. `ExpensesScreenContractTest` se ajustó para leer el nombre de
quien aprobó en el dominio de la trazabilidad, conservando su intención.

L6 (dispositivo, bloqueado): el APK quedó instalado, pero la pantalla del teléfono entró en
`Dozing` y MIUI rechaza `adb shell input` con `INJECT_EVENTS`; no pude despertarla desde adb
(`KEYCODE_WAKEUP` bloqueado, `monkey` no la despierta). La captura del detalle queda pendiente
para cuando el teléfono esté despierto.
