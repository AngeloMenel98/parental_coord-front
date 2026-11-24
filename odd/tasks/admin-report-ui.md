# Admin reportes: implementar el reporte según design 04 y arreglar el detalle de actividades

Objective: implementar la pestaña Reportes de Admin siguiendo el diseño
`design/admin-ui/04-reporte-desplegable.html` y que el detalle de actividades
nunca muestre el mensaje de "no está disponible" cuando la carga falla.
Branch: `ft/third-party-access` (repo front). Test runner: `./gradlew :shared:testDebugUnitTest`
(fetch por clase con `--tests`; `allTests`/`jvmTest` no existen en este proyecto) y
`./gradlew :androidApp:compileDebugKotlinAndroid` para el app.

## Specs

S1: «en la parte de reportes no veo bien implementado el reporte en si»
S2: «fijate el design como esta hecho y hacelo aparte cuando voy al detalle de actividades me dice que no esta disponible»
S3: mensaje observado en la app: «El detalle de actividades todavía no está disponible. Cargá el reporte del período y volvé a entrar.» (AdminActivitiesDetail.kt:193)

## Tasks

T1 (S2,S3, inline/delegated, commit propio): detalle de actividades — distinguir
error de lista vacía, botón Reintentar cuando falla la carga, y texto del mensaje
vacío correcto (el reporte SÍ está cargado; falta la lista). El fetch debe
reintentarse al abrir aunque `selectReportBond` haya limpiado la lista.
Archivos: `shared/src/commonMain/kotlin/com/parental/shared/feature/admin/ui/AdminActivitiesDetail.kt`,
`AdminViewModel.kt` + test en commonTest.
Risk: item 3 (AdminActivitiesDetail consume el ViewModel compartido con la pestaña
Reportes; un cambio de estado afecta vistas no pedidas).
Estado 2026-11-23: implementado — `ActivitiesDetailState` (LOADING/LOAD_ERROR/EMPTY/CONTENT),
`activitiesError` en `AdminUiState`, `retryReportActivities()`, fetch extraído a
`fetchReportActivities()`, limpieza del error en `selectReportBond`, test
`AdminActivitiesDetailStateTest` (5 casos, RED observado, GREEN, suite verde).
Commit diferido: el working tree arrastra el avance previo delAdmin report sin commitear;
el corte de commits se decide con el usuario.

T2 (S1,S2, inline, commit propio): reporte según design 04 — CTA «Ver las N actividades»
con la cuenta, tracks por progenitor como barras de progreso, copy del artboard.
barras de progreso, copy del artboard. Ya cubierto por el avance previo: chips de estado y
split pagado/pendiente.
Archivos: `AdminScreen.kt`, `AdminReportCounts.kt` (+ tests en
`AdminReportCountsTest.kt`, `AdminDetailTest.kt` si aplica).
Risk: item 3 (ActivityCard/ExpensesCard/MembersCard comparten helpers y estado).
Estado 2026-11-23: implementado inline (la delegación al worker fue rechazada por el runtime
por tamaño; no se reintentó) — `adminActivityDetailCta()` con test (RED observado, GREEN),
CTA del card con la cuenta del período, `LinearProgressIndicator` por progenitor.
Suite `:shared:testDebugUnitTest` completa y `:androidApp:compileDebugKotlinAndroid` verdes.
Commit diferido junto con T1.

T3 (S1,S2, inline, sin commit): observe RED antes de cada fix (tests nuevos),
suite `:shared:testDebugUnitTest` al cierre de cada task. Hecho para T1 y T2.

T4 (S1-S3, inline, sin commit): rebuild APK local (`-PlocalBuild=true`,
`-PapiBaseUrl=http://192.168.0.17:3000/api`, `-PwsUrl=ws://192.168.0.17:3000/realtime`),
instalar por USB al teléfono Q4EER8PRM7ONT4OZ, copia a `/sdcard/Download/',
verificación del dex. Verificación funcional final la hace el usuario.

## Log

L1: «podes instalar el .apk por depuracion inalambrica a este ip:192.168.0.11:42383 cod:583933» — instalado debug build; conexión final por USB Q4EER8PRM7ONT4OZ.
L2: «pero instalaste el .apk local?» — verificado URL embebida http://192.168.0.17:3000/api.
L3: «porq sigue el view viejo en Admin.» — diagnóstico: el APK sí contenía el Admin nuevo (las 14:11 strings con tilde verificadas con grep -a, la prueba anterior con `strings` era inválida por tildes); la vista nueva es overlay en AdminScreen.
L4: «porq no tenes cuota disponible?» — modelo del gentle-ai-explore sin cuota; repuesto y reejecutado con éxito.
L5: «lo elimine ahora quiero que me pases por usb al gestor de archivos el .aok del android.local» — push a /sdcard/Download.
L6: «me instalate el de produccion» — el rebuild de las 14:33 se lanzó sin -PlocalBuild=true; corregido: rebuild local instalado y copiado como parental-android-local.apk.
L7: «en la parte de reportes no veo bien implementado el reporte en si, fijate el design como esta hecho y hacelo aparte cuando voy al detalle de actividades me dice que no esta disponible» — causa raíz del mensaje confirmada: el backend local en Docker (dist de 12:35) no tiene el endpoint B6/S16
GET /admin/bonds/:id/activities (404; trend/report dan 401). Rebuild+restart de api/worker hecho;
endpoint verificado con 401 (ruta viva). T1 implementado y verificado; T2 implementado inline
(la delegación al worker fue rechazada por el runtime por tamaño).
