package com.parental.shared.feature.home.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.safeApiCall
import com.parental.shared.platform.Platform
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.components.CategoryResolver
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import com.parental.shared.feature.expenses.data.remote.ExpensesApi
import com.parental.shared.feature.home.domain.ChildStatus
import com.parental.shared.feature.home.domain.HomeCounts
import com.parental.shared.feature.home.domain.TodayAgenda
import com.parental.shared.feature.home.domain.computeHomeCounts
import com.parental.shared.feature.home.domain.selectChildStatuses
import com.parental.shared.feature.home.domain.selectTodayAgenda
import com.parental.shared.feature.home.domain.model.ChildProfile
import com.parental.shared.feature.home.domain.model.ComplianceResponse
import com.parental.shared.feature.home.domain.model.HomeMetrics
import com.parental.shared.feature.home.domain.model.HomeSummary
import io.ktor.client.call.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import com.parental.shared.core.network.safeApiCall
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiErrorBody(
    val statusCode: Int? = null,
    val message: String? = null,
    val error: String? = null,
)

open class HomeApi(
    private val bondsApi: BondsApi,
    private val activitiesApi: ActivitiesApi,
    private val expensesApi: ExpensesApi,
    private val categoriesApi: CategoriesApi,
    /**
     * ExpensesApi y el fetch de compliance mantienen por ahora la forma vieja de
     * origin/main (token por llamada): HomeApi no lleva token (viaja en el bearer
     * del shared HttpClient), así que lo consulta por acá en cada request.
     */
    private val tokenProvider: () -> String? = { null },
) {
    /**
     * Cliente dedicado para compliance (viene de origin/main): NO lleva el plugin
     * SessionAuth del shared HttpClient; el bearer va por header por llamada. Lazy:
     * HomeApi puede vivir sin engine hasta que se pida compliance.
     */
    private val complianceClient: io.ktor.client.HttpClient by lazy {
        io.ktor.client.HttpClient(Platform.createHttpEngine())
    }

    /**
     * El resumen del Home: los vínculos, el cumplimiento y las tres métricas.
     *
     * Cada fuente falla por su cuenta y cada una degrada por su cuenta: sin la
     * lista no hay conteos, sin el resumen de gastos no hay saldo, y sin
     * cumplimiento no hay tarjeta de cumplimiento. El Home aparece igual.
     */
    open suspend fun getHomeSummary(
        currentUserId: String? = null,
    ): ApiResult<HomeSummary> {
        return try {
            when (val bondsResult = bondsApi.getMyBonds()) {
                is ApiResult.Success -> {
                    val bonds = bondsResult.data
                    val firstBond = bonds.firstOrNull()
                    val compliance = firstBond?.let {
                        when (val complianceResult = getCompliance(it.id)) {
                            is ApiResult.Success -> complianceResult.data
                            is ApiResult.Error -> null
                        }
                    }
                    // R11: los conteos salen de la lista que ya se carga del vínculo. La
                    // MISMA lista alimenta la agenda de hoy (S2) y el estado por hijo (S5):
                    // ninguna de las dos pide un request propio.
                    val activities: List<Actividad>? = firstBond?.let { bond ->
                        when (val listResult = activitiesApi.listActividades(bond.id)) {
                            is ApiResult.Success -> listResult.data
                            is ApiResult.Error -> null
                        }
                    }
                    val counts = computeHomeCounts(activities, currentUserId)
                    // R1: el saldo lo manda el servidor, con signo. El cliente no lo recalcula.
                    val balance = firstBond?.let { bond ->
                        when (val summaryResult = expensesApi.getSummary(token = tokenProvider().orEmpty(), bondId = bond.id)) {
                            is ApiResult.Success -> summaryResult.data.saldo
                            is ApiResult.Error -> null
                        }
                    }
                    val children = homeChildren(bonds)
                    val categories = loadCategories()
                    val now = Clock.System.now()
                    val zone = TimeZone.currentSystemDefault()
                    ApiResult.Success(
                        buildHomeSummary(
                            bonds = bonds,
                            compliance = compliance,
                            counts = counts,
                            balance = balance,
                            today = selectTodayAgenda(
                                activities = activities,
                                today = now.toLocalDateTime(zone).date,
                                timeZone = zone,
                                categories = categories,
                            ),
                            childStatuses = selectChildStatuses(children, activities),
                        ),
                    )
                }
                is ApiResult.Error -> bondsResult
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }

    /**
     * GET /categories (público) → mapa uuid → info, para nombrar y pintar la categoría
     * en la agenda de hoy.
     *
     * Falla en silencio: sin categorías la fila pierde el nombre y el punto, pero la
     * agenda, el título y la hora siguen ahí. Una categoría no puede tumbar el Home.
     */
    private suspend fun loadCategories(): Map<String, CategoryInfo> =
        when (val result = categoriesApi.getCategories()) {
            is ApiResult.Success -> CategoryResolver.build(result.data)
            is ApiResult.Error -> emptyMap()
        }

    open suspend fun getCompliance(bondId: String): ApiResult<ComplianceResponse> {
        return try {
            val response = complianceClient.get("${Platform.baseUrl.trimEnd('/')}/activities/$bondId/compliance") {
                tokenProvider()?.let { header("Authorization", "Bearer $it") }
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.status.value, response.status.description)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, e.message ?: "Network error")
        }
    }
}

/**
 * Construye un [HomeSummary] a partir de los vínculos, el cumplimiento y las
 * métricas opcionales.
 *
 * Función pura y testable; la decisión de llamar a compliance (y de qué hacer con
 * un error) la toma [HomeApi.getHomeSummary].
 */
internal fun buildHomeSummary(
    bonds: List<Vinculo>,
    compliance: ComplianceResponse?,
    counts: HomeCounts = HomeCounts(assignedToMe = null, overdue = null),
    balance: Double? = null,
    today: TodayAgenda? = null,
    childStatuses: List<ChildStatus> = emptyList(),
): HomeSummary {
    val firstBond = bonds.firstOrNull()
    return HomeSummary(
        children = homeChildren(bonds),
        memberCount = firstBond?.members?.size ?: 0,
        bondId = firstBond?.id,
        compliance = compliance,
        metrics = HomeMetrics(
            assignedToMe = counts.assignedToMe,
            overdue = counts.overdue,
            balance = balance,
        ),
        today = today,
        childStatuses = childStatuses,
    )
}

/**
 * Los hijos de todos los vínculos, aplanados.
 *
 * Es la única traducción `VinculoChild` → `ChildProfile`: el resumen y el estado por
 * hijo (S5) tienen que contar sobre la MISMA lista, o un id de un lado no encontraría
 * su conteo del otro.
 */
internal fun homeChildren(bonds: List<Vinculo>): List<ChildProfile> =
    bonds.flatMap { bond ->
        bond.children.map { child ->
            ChildProfile(
                id = child.id,
                firstName = child.firstName,
                lastName = child.lastName,
                dateOfBirth = "",
            )
        }
    }
