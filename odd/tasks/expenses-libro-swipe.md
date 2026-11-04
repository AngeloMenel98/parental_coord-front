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
total con `Tu parte $x` debajo. `$x` es la parte del usuario que consulta: si pidió el gasto,
`amount * splitRatioPayer / 100`; si es la contraparte, `amount * splitRatioOther / 100`. Si la
identidad del usuario todavía no se conoce, la línea se omite en vez de mostrar la parte de
otro. El saldo y los conteos siguen viniendo del servidor; la fila abre el detalle al tocarla.

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
| T6 | S1,S2,S3,S4 | done · delegated · `412771c` |
| T7 | S3 | done · delegated · viewer-specific share · `412771c` |

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
después de compilar y no volvió a aparecer. Queda pendiente ver la pantalla real antes de dar el
diseño por cerrado.

L7: sin dispositivo, la revisión del gesto encontró dos defectos que la captura tampoco habría
mostrado bien: la fila no tenía fondo y las acciones del swipe se veían siempre detrás, y el
drag lanzaba una corrutina por evento de puntero. Los dos corregidos antes de cerrar.

L8 (verbatim): "look for the design i asked you to do for expenses view, but i saw that you did not do it correctly. Fix it"

L9 (corrección): la comparación contra el artboard elegido y los comentarios_canvas
encontró que la implementación Compose había perdido la tarjeta de saldo, agrupado la lista
en una superficie redondeada sin separadores, añadido la leyenda temporal no respaldada
"actualizado hoy", y dejado las acciones del swipe sin una garantía estructural de ocupar
todo el alto de la fila. T6 corrige sólo esos desvíos y vuelve a abrir la verificación visual.

L10 (defecto semántico descubierto al revisar T6): S3 decía siempre
`amount * splitRatioPayer / 100` bajo la etiqueta "Tu parte", pero `splitRatioPayer` pertenece a
quien SOLICITÓ el gasto. Para la contraparte, "tu parte" es `splitRatioOther`; con una sesión
legacy cuyo `userId` todavía no se conoce, esa línea no se puede determinar y debe omitirse.
T7 corrige S3 y lo fija con pruebas deterministas; no recalcula el saldo ni los conteos del
servidor.

L11 (entrega de la corrección): T6 y T7 quedaron implementados en el mismo work unit. T6
restauró la tarjeta de saldo, quitó la copia temporal sin respaldo, aplanó el libro en
ítems virtuales separados por hairline y midió el riel del swipe contra la fila completa.
T7 corrigió la parte del viewer. RED: cuatro contratos nuevos fallaron (tarjeta de saldo,
copy temporal, libro plano y alto del swipe); GREEN: los contratos focalizados y la suite
completa pasaron. Verificación final: 53 suites / 492 tests, 0 fallos / 0 errores / 0 skips;
`:androidApp:assembleDebug` verde; APK SHA-256
`b4f018cf2c9f7328a95f8e1a5f0184ee4665bf24727a7ab9852454f1b7f23e98`; `git diff --check`
verde; self-check del artboard (sin recursos remotos, landmark `main`, foco visible, sin
copy obsoleto) verde. `gentle_review assess` clasificó el candidato como riesgo medio,
`reviewDue=false` (`under_budget`), por lo que no se abrió una revisión nativa y no se
requirió verificador independiente. La captura en dispositivo sigue pendiente: `adb devices`
no lista ningún dispositivo.

L12 (cierre): el handoff del artboard elegido queda implementado. La prueba en dispositivo
sigue siendo la única prueba faltante y no se presenta como hecha. Work unit: `412771c`
(`fix(expenses): align the ledger view with the approved design`).
