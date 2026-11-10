Resolución PRDs prd_improvement + PRD_expense (parental-coordination)
Revisión 4. Incluye las correcciones del usuario L5–L8 y las nuevas: un único RESPONSE_TIMEOUT para todos los estados de espera (L9), anticipación mínima de 48 h al crear y asignación irreversible (L10).
Repos: Front parental_coord-front (Kotlin Multiplatform Compose) · Back parental_coordination_back (NestJS + TypeORM + Postgres + Supabase Storage).
Objetivo: unificar los dos PRDs con el código que existe, resolver conflictos y preguntas abiertas, y dejar un backlog ordenado del más barato al más caro. Este documento no cambia código.
Fuentes: prd_improvement.md (UX/UI), PRD_expense.md (Gastos) y docs/PRD.md del back. Cada decisión se apoya en un archivo leído.
Tests: front ./gradlew :shared:testDebugUnitTest; back pnpm test. Las estimaciones son horas/días-persona, solo para ordenar.
Decisiones
R1 — Un saldo firmado, dos vistas (resuelve OQ2 de prd_improvement y OQ3 / Issue #4 de PRD_expense)
Los PRDs piden el mismo número con nombres y signos opuestos: H-5 "Saldo gastos" (verde si positivo, rojo si negativo) y FR-3 "Por pagar". El Issue #4 registra que la UI muestra $16.80 cuando lo pendiente suma $480 + $16.80.
Decisión. Un único saldo por vínculo, calculado en el servidor, firmado desde el punto de vista del usuario actual, sobre gastos en estado approved o paid (pending no es deuda hasta que se acuerda; rejected está muerto).
myShare = amount _ split_ratio[me]
otherShare = amount _ split_ratio[other]
saldo = Σ (myShare - otherShare) // > 0: me deben. < 0: debo.
Superficie
Etiqueta
Valor
Signo / color
Tarjeta Home (H-5)
Saldo gastos
saldo con signo
verde si > 0, rojo si < 0
Cabecera Gastos (FR-3)
Por pagar
abs(min(saldo, 0)) (lo que debo)
ámbar, siempre ≥ 0
Cabecera Gastos
Por cobrar (nueva)
abs(max(saldo, 0)) (lo que me deben)
verde
Regla: nunca se muestra un número con signo bajo una etiqueta que oculte el signo. El endpoint devuelve { saldo, porPagar, porCobrar, counts: {pendientes, aprobados, pagados, rechazados} } y ningún cliente recalcula.
R2 — Una sola taxonomía de categorías (resuelve FR-12 de PRD_expense y D-15 de prd_improvement)
D-15 pide preseleccionar la categoría del gasto desde la actividad, lo que solo es posible si comparten taxonomía.
Decisión. La tabla category existente (src/categories/entities/category.entity.ts: name, color, icon, criticality, ya enlazada a activity.category_id) es la taxonomía única. Los gastos reciben category_id FK a la misma tabla.
• No hay tabla expense_category ni segundo sistema de iconos o colores.
• FR-12 (categorías configurables después) se cubre con category.is_active.
• Si el seed no cubre Salud/Educación/Recreación, se extiende. Las categorías de actividades no se renombran.
R3 — Los nueve estados son la especificación; las transiciones son del motor (resuelve D-7…D-10)
prd_improvement §7.3 propone una matriz sobre cinco estados. El dominio tiene nueve: created, assigned, assisting, in_progress, verify, done, overdue, not_assisting, cancelled (activity.entity.ts), gobernados por un catálogo cerrado de transiciones (activity-transition.map.ts) más un motor de timers.
Decisión. Se mantienen los nueve estados. La matriz de cinco estados del PRD se descarta, no se traduce ("Reprogramar", "Reabrir" y "Editar" nombran transiciones que el motor no tiene).
Principio de espera (L9). RESPONSE_TIMEOUT_HOURS (default 36, activities.service.ts:304) es la única constante. Todo estado donde se espera una acción tiene una ancla propia; si la acción no llega a las 36 h desde su ancla, la actividad pasa a overdue.
Matriz autoritativa (la UI implementa esto):
Estado
Quién actúa
Botón
→
Si pasan 36 h sin acción
created
cualquier miembro
Asignar
assigned
→ overdue (nuevo)
assigned
asignado
Confirmar
assisting (T5, confirmedAt)
→ overdue (ya existe, T7)
assigned
asignado
No asiste
not_assisting (T6, declinedAt)

assisting
nadie
ninguno (espera automática)
in_progress al scheduledStart (T10)

in_progress
sistema

verify al fin (scheduledEnd ?? scheduledStart) (nuevo, hoy salta a done)

verify
miembro
Completado
done (nuevo)
→ overdue (nuevo)
overdue
asignado
Confirmar / No asiste
assisting / not_assisting (T8, aceptación tardía)
ver punto abierto 1
not_assisting
cualquier miembro
Reasignar
assigned (T9, reinicia la ventana)

done

ninguno
terminal

cancelled

ninguno
terminal

Camino feliz: created → Asignar → assigned → Confirmar → assisting → inicio → in_progress → fin → verify → Completado → done.
Eliminada (L10): la transición T4 (assigned → created, "Desasignar"). Una vez asignada, la actividad no se puede desasignar. Se borra T4 de RULES, desaparece el botón y canUnassign deja de existir (o queda en false un release por compatibilidad). El único camino para cambiar de asignado es T9 (Reasignar), que exige que el asignado haya rechazado. Si la asignación ya no sirve, queda Cancelar (creador, con motivo).
cancelled es un estado con dos puertas que nunca se fusionan en un botón:
Vía
Endpoint
Columnas
Visible después
Motivo
Eliminar (soft delete)
DELETE /activities/:id
status = cancelled, deleted_at = now
oculta (deleted_at IS NULL la filtra)
ninguno; restaurable 5000 ms (POST /:id/restore)
Cancelar
POST /activities/:id/cancel
status = cancelled, cancelled_at = now
visible como "Cancelada"
obligatorio, 3–200 caracteres, solo el creador
Ambas usan la misma política (canDelete / canCancel = creador && no pasada). D-9 (confirmación en acciones destructivas) cubre las dos.
Dos consecuencias:

1. D-7 queda superado. "Finalizar solo visible en IN_PROGRESS" pasa a: Completado es visible en in_progress y en verify, y en ningún otro estado.
2. Los botones los decide el servidor. Hoy el front muestra "Finalizar" en todos los estados salvo done (ActividadDetailSheet.kt:378) y deriva "Confirmar" de una regla local que ignora el estado (canConfirmAssignment, ActivitiesModels.kt:105). Ambos provocan 409. La matriz llega como flags del servidor, como ya hacen canDelete/canCancel/canDecline.
   Flag
   Vale en
   canAssign
   created
   canConfirm, canDecline
   assigned y overdue (solo el asignado)
   canReassign
   not_assisting
   canComplete
   in_progress y verify
   canUnassign
   eliminado (siempre false un release)
   R4 — "Confirmada" es un dato derivado por persona (resuelve A-1)
   El backend ya eliminó el booleano assigned_confirmed (migración DropActivityAssignedConfirmed) y lo deriva: assignedConfirmed = status === assisting && confirmedAt != null (activity-response.dto.ts). El chip aún sale en la tarjeta (ActivityCard.kt:276-298).
   Decisión. Se quita el chip de la tarjeta (duplica la píldora de estado y es información por asignado en una superficie por actividad). El flag derivado se muestra en el detalle, como línea por asignado. No cambia el esquema.
   R5 — Los textos de notificaciones viven en el backend (resuelve N-4)
   La fila de notificación la escribe el servidor (src/notifications/fan-out/activity-notification.map.ts).
   Decisión. El catálogo de textos es un módulo del backend: un mapa tipado de evento a { title, body } en español. El front renderiza notification.title y notification.body tal cual.
   • title === body hoy; debe separarse: N-2 exige título ≤ 40 y descripción ≤ 100 caracteres.
   • Las 8 plantillas actuales están en inglés y se reemplazan por las españolas de N-3.
   • El "archivo i18n" del front pasa a ser una superficie de revisión del catálogo del backend.
   R6 — Un adjunto convertido comparte el objeto de Storage y tiene su fila de gasto (resuelve D-11…D-18)
   D-13/D-18: tras elegir un archivo se pregunta "¿Es un gasto?"; si sí, se abre un gasto prellenado; si el usuario cancela, el archivo queda en la actividad.
   Decisión.
   • Un objeto de Storage, dos filas: act_attachment (existente) y expense_attachment (nueva) apuntan a la misma key de Supabase. No se copian bytes.
   • expense.activity_id (FK nueva) registra el origen (D-17).
   • La actividad conserva su adjunto (D-18).
   • Si existe expense_attachment, el flag sin doc. no se marca (D-15). Se deriva como "no hay fila expense_attachment", nunca se guarda como booleano.
   R7 — Montos y split
   amount es el total del gasto; la obligación de cada parte es amount \* ratio. En v1 el split es fijo 50/50 (ya asumido en PRD_expense §5.3). Las columnas split_ratio_payer y split_ratio_other (expense.entity.ts) se conservan para splits personalizados (v1.2) sin migración. La UI muestra primero el total y secundariamente la parte del usuario.
   R8 — Notificaciones de gastos por el pipeline existente
   Los eventos expense.created, expense.approved, expense.rejected, expense.paid y expense.stale (pendiente > 7 días) usan el outbox → fan-out → socket notification.created ya existente. El recordatorio de 7 días es un scheduler, siguiendo notifications-retention.scheduler.ts. No se construye un segundo transporte.
   R9 — "Tiempo real" es el socket, no polling
   Los cambios de estado de gastos emiten un frame expense.changed por el mismo gateway y el front refresca saldo y conteos al recibirlo. El polling queda solo para el badge de notificaciones, que ya lo tiene.
   R10 — Soft delete de notificaciones por usuario
   Se agrega notification.deleted_at (nullable) con índice parcial, como el precedente de actividades. Se filtra deleted_at IS NULL en todas las rutas de lectura: lista, conteo de no leídas, marcar leída, marcar todas y selección de retención (el riesgo de fuga de conteos ya está en el PRD). El usuario nunca borra la fila físicamente; la retención puede purgarla por antigüedad.
   R11 — Contadores del Home desde datos que el cliente ya tiene
   GET /activities/bond/:bondId no admite filtros. "Asignadas" y "Vencidas" se calculan en el cliente sobre la lista ya cargada (por vínculo, pequeña). Solo "Saldo gastos" requiere endpoint, porque el dinero nunca se recalcula en el cliente (R1).
   R12 — Secuencia: Gastos antes que la tarjeta del Home
   La tarjeta "Saldo gastos" depende del endpoint de balance, parte del módulo de gastos. Orden: Fase 1 de prd_improvement (barra superior, copy y soft delete de notificaciones, tarjeta de actividad) en paralelo y primero → MVP de PRD_expense (T14…T27) → tarjetas del Home (T20) → flujo adjunto → gasto (T30).
   R13 — Fuera de alcance
   OCR del monto (OQ6, T34); pasarelas de pago y multi-guardián (PRD_expense §3/§12); vista mensual de la agenda (OQ5); visibilidad del historial de gastos para terceros/abogados (OQ6); restaurar notificaciones borradas (OQ7; el snackbar de deshacer de N-8 cubre los 5 s).
   R14 — Una constante, tres anclas: la ventana de espera de created, assigned y verify
   verify está declarado (enum TS, enum Postgres, TimelineFormatters.kt:152, Color.kt:176) pero ningún camino entra ni sale de él. activity-transition.map.spec.ts:139-140 afirma que [VERIFY, DONE] y [VERIFY, CANCELLED] son inválidos y :215 que timerOpsForTransition(VERIFY) es vacío. Hoy el motor va in_progress → done solo (activity-timers.processor.ts: targetStatusFor(END) = DONE; activity-timers.sweep.ts: dueTargetFor).
   Decisión. Una sola constante (RESPONSE_TIMEOUT_HOURS, 36) con tres anclas:
   Ventana
   Estado
   Ancla
   Vence
   Transición
   Asignación
   created
   created_at
   created_at + 36h
   created → overdue (nueva)
   Aceptación
   assigned
   momento en que entró a assigned (también tras T9)
   +36h
   assigned → overdue (T7, ya implementada)
   Verificación
   verify
   fin de la actividad (scheduledEnd ?? scheduledStart)
   fin + 36h
   verify → overdue (nueva)
   Línea de tiempo:
   t0 created, deadline = created_at + 36h (RESPONSE_TIMEOUT armado)
   Asignar → assigned, deadline = t + 36h (irreversible, sin Desasignar)
   Confirmar → assisting (T5/T8)
   t2 scheduledStart → in_progress (timer START)
   t3 scheduledEnd → verify (timer END, retargeteado de done)
   t4 t3 + 36h → overdue (VERIFY_TIMEOUT, nuevo)
   Completado en [t3, t4] → done (miembro)
   • deadline se generaliza, no se duplica. Pasa a ser "el vencimiento de la ventana en la que espera la actividad": created_at + 36h en created, now + 36h al entrar a assigned, (scheduledEnd ?? scheduledStart) + 36h al entrar a verify, y se limpia en el resto. Sin columna nueva ni migración.
   • Reglas nuevas del catálogo cerrado: in_progress → verify (sistema, T12), verify → done (miembro, T13), verify → overdue (sistema, T14) y created → overdue (sistema, id nuevo a asignar). Se elimina T4.
   • Timers: END pasa a apuntar a verify. RESPONSE_TIMEOUT también se arma al crear (ya no solo al asignar). Nuevo tipo VERIFY_TIMEOUT (fuente verify → overdue).
   • El sweep gana cláusulas. findDueForTransition (activities.repository.ts:248-270) solo barre assigned, assisting e in_progress. Hay que sumar created y verify tanto en la query como en dueTargetFor; sin eso, un timer perdido nunca se autorrepara. Quedan cinco estados barridos.
   • Specs que se revierten a propósito: activity-transition.map.spec.ts:139-140 y :215; activity-timers.processor.spec.ts:55,87; activity-timers.sweep.spec.ts:61,67,97. Se invierte además la aserción de T4. Es el único lugar del programa donde se deshace una decisión previamente afirmada.
   • Eventos nuevos (el catálogo cerrado pasa de 9 a 12): activity.verify, activity.verify_expired y un evento para created → overdue ("Actividad sin asignar"), con plantillas y reglas de fan-out.
   • Inconsistencia a corregir de paso: activity-attachments.service.ts:70 trata overdue como terminal (rechaza done, cancelled y overdue), mientras el mapa lo trata como recuperable (T8).
   • Flujo tardío (para decidir): con el ancla literal, una actividad que terminó hace más de 36 h entra a verify ya vencida y el siguiente sweep (≤ 15 min) la pasa a overdue. La alternativa de una línea es max(fin, ahora) + 36h.
   R15 — Cumplimiento: mes calendario, solo done, cancelled excluido (resuelve H-6 y OQ1)
   La implementación difiere del PRD:
   • Período: getComplianceForBond (activities.service.ts:581-602) usa el mes calendario actual; el PRD pide 90 días.
   • Numerador: COUNT(a.id) FILTER (WHERE a.status = 'done') (bond-members.repository.ts:76).
   • Denominador: toda actividad asignada al miembro en el período (a.assigned_to = bm.user_id), incluidas canceladas y soft-deleted, porque la query solo filtra por asignado, vínculo y rango de fechas.
   Decisión.
   • Período: mes calendario, como está. Se enmienda el PRD, no el código: el 82% de H-6 pasa a ser "el porcentaje de este mes" y la UI dice "Este mes" / "Octubre 2026", nunca "últimos 90 días". Hoy la tarjeta muestra el string crudo "2026-10".
   • Denominador: excluir cancelled. cancelled es el estado del soft delete: delete escribe cancelled + deleted_at; cancel escribe cancelled + cancelled_at + motivo. Ninguna es una obligación incumplida. Esto corrige una inconsistencia viva: una actividad eliminada con "Eliminar" seguía contando en contra del porcentaje del creador.
   • Numerador: solo done. verify no cuenta: una actividad esperando confirmación no es obligación cumplida. Tras R14 el porcentaje se atrasa hasta 36 h por actividad; el costo de "confirmado" se declara en el copy junto al número.
   • overdue cuenta en el denominador y no en el numerador, también un overdue que nunca tuvo asignado.
   • Barra secundaria: todos los miembros del vínculo, incluido el usuario actual (el endpoint ya devuelve una fila por miembro; HomeScreen → ComplianceCard → ComplianceMemberRow). Responde OQ1.
   • La fórmula se documenta en el repo junto al endpoint: cumplimiento(usuario, mes) = done(asignadas al usuario en el mes) / (asignadas al usuario en el mes − cancelled).
   R16 — Anticipación mínima de 48 h al crear una actividad (L10)
   Una actividad no se puede crear con scheduledStart a menos de 48 h de now.
   • Backend (fuente de verdad): validación en create-activity.dto.ts / activities.service.ts. Si scheduledStart < now + 48h, responde 400: "La actividad debe programarse con al menos 48 horas de anticipación".
   • Constante propia: MIN_LEAD_TIME_HOURS = 48, configurable y separada de RESPONSE_TIMEOUT_HOURS. Son cosas distintas aunque hoy valgan 48 y 36.
   • Front: el selector de fecha y hora (T04, CreateDateConversion.kt) deshabilita lo anterior a now + 48h y explica el motivo. Es solo UX; el servidor valida igual.
   • Alcance: solo la creación. Falta decidir si aplica al editar la fecha (punto abierto 2).
   • Efecto positivo: como scheduledStart ≥ created_at + 48h y la ventana de created dura 36 h, una actividad sin asignar siempre vence (created → overdue) antes de su inicio. No hace falta min(created_at + 36h, scheduledStart).
   • Datos existentes: las actividades ya creadas con menos de 48 h no se tocan (la regla valida al crear). Hay que comprobar que ninguna ya asignada dependa de T4.
