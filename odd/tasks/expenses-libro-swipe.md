# Gastos — "Saldo vivo, libro, swipe"

Objetivo: implementar en Compose el artboard `design/expenses-ui/04-saldo-vivo-libro-swipe.html`.
Branch: `feat/expenses-libro-swipe`. Runner: `./gradlew :shared:testDebugUnitTest`.

## Specs

S1. **Saldo del vínculo como encabezado.** `summary.saldo` tal como lo manda el servidor (la
pantalla hoy no lo muestra), en serif grande, con la leyenda `A favor tuyo` si es positivo,
`En contra` si es negativo, `Al día` si es 0, y color por signo con `StatusTokens` (`Ok`/`Ko`).
Debajo, separados por una hairline: `Por pagar` (`porPagar`) y `Por cobrar` (`porCobrar`).
Ninguno de los dos filtra.

S2. **Filtros por estado con conteo.** Una fila horizontal: `Todas` (total), `Pendientes`,
`Aprobados`, `Pagados`, `Rechazados`. Los conteos salen de `summary.counts`, no se recalculan.
Cada chip alterna el filtro (`selectedStatus`); tocar el activo vuelve a `Todas`. Las
categorías dejan de ser el filtro.

S3. **La lista es un libro.** Cada fila: fecha a la izquierda (día en serif grande sobre el mes
abreviado en tres letras), concepto, `StatusTag` de estado + categoría, y a la derecha el monto
total con `Tu parte $x` debajo (`amount * splitRatioPayer / 100`, formateado con `formatMoney`).
La fila abre el detalle al tocarla.

S4. **Swipe sólo en lo que se puede decidir.** Una fila se desliza a la izquierda sólo si el
servidor dice `canApprove` o `canReject` (nunca por estado deducido en el cliente). Detrás
quedan dos acciones rectangulares que ocupan el alto completo de la fila: `Rechazar` (error) y
`Aprobar` (primary, al extremo). Aprobar llama a `viewModel.approve(id)`; rechazar a
`viewModel.reject(id)`. Para lectores de pantalla, la misma acción se expone como
`customActions` en la fila, porque un gesto no es descubrible.

S5. **Invariantes.** El saldo NO se recalcula (R1); los flags de acción son del servidor; los
estados loading / error / vacío siguen existiendo; `—`/vacío nunca se reemplaza por un 0
inventado.

## Tasks

| ID | Spec | Estado |
| --- | --- | --- |
| T1 | S3 | dominio: `payerShare` + fecha del libro |
| T2 | S4 | `ExpenseSwipeBox` |
| T3 | S3,S4 | `ExpenseRow` como fila de libro con swipe |
| T4 | S1,S2,S3 | `ExpensesScreen`: saldo, filtros, lista |
| T5 | S1-S5 | hecho · tests en los dos commits de código |

## Log

L1 (verbatim): "Implement the design artboard design/expenses-ui/04-saldo-vivo-libro-swipe.html"

L2 (decisión): `ExpenseSwipeBox` es propio de gastos, no reusa `ActivitySwipeBox` de
Actividades: aquel recibe una `Actividad` completa y resuelve la política de swipe de actividades
(`SwipeActionPolicy`), con diálogo de motivo y undo. Compartirlo obligaría a una abstracción que
hoy no tiene dos consumidores reales.

L3 (decisión): el rechazo desde el swipe va sin motivo (`reason = null`). El motivo sigue siendo
opcional en el endpoint y el detalle lo pide cuando hace falta.

L4 (ejecución): el trabajo empezó en la rama del Home por descuido; se separó con
`git branch feat/expenses-libro-swipe` en el commit de la fila y `feat/home-hoy-con-anillo`
se movió atrás a `f97c3e2`. La rama de gastos arrastra los commits del Home en su historia:
su slice de revisión es `f97c3e2..HEAD`.

L5 (frontera de slice): `eaf0278` (dominio + swipe + fila + tests, ~600 líneas) y `170b5b4`
(pantalla + contratos). El contador del gate mide la rama entera contra `origin/main`, que
incluye el historial heredado de `develop`.

L6 (verificación): `:shared:testDebugUnitTest` verde y `:androidApp:assembleDebug` verde. La
captura en dispositivo NO se pudo hacer: el teléfono se desconectó (`adb devices` vacío) justo
después de compilar. Queda pendiente ver la pantalla real antes de dar el diseño por cerrado.
