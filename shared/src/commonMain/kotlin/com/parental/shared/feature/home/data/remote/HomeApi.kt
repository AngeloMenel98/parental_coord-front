package com.parental.shared.feature.home.data.remote

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
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
    private val client: io.ktor.client.HttpClient,
    private val baseUrl: String,
    private val bondsApi: BondsApi = BondsApi(client, baseUrl),
    private val activitiesApi: ActivitiesApi = ActivitiesApi(client, baseUrl),
    private val expensesApi: ExpensesApi = ExpensesApi(client, baseUrl),
    private val categoriesApi: CategoriesApi = CategoriesApi(client, baseUrl),
) {

    /**
     * El resumen del Home: los vínculos, el cumplimiento y las tres métricas.
     *
     * Cada fuente falla por su cuenta y cada una degrada por su cuenta: sin la
     * lista no hay conteos, sin el resumen de gastos no hay saldo, y sin
     * cumplimiento no hay tarjeta de cumplimiento. El Home aparece igual.
     */
    open suspend fun getHomeSummary(
        token: String,
        currentUserId: String? = null,
    ): ApiResult<HomeSummary> {
        return try {
            when (val bondsResult = bondsApi.getMyBonds(token)) {
                is ApiResult.Success -> {
                    val bonds = bondsResult.data
                    val firstBond = bonds.firstOrNull()
                    val compliance = firstBond?.let {
                        when (val complianceResult = getCompliance(it.id, token)) {
                            is ApiResult.Success -> complianceResult.data
                            is ApiResult.Error -> null
                        }
                    }
                    // R11: los conteos salen de la lista que ya se carga del vínculo. La
                    // MISMA lista alimenta la agenda de hoy (S2) y el estado por hijo (S5):
                    // ninguna de las dos pide un request propio.
                    val activities: List<Actividad>? = firstBond?.let { bond ->
                        when (val listResult = activitiesApi.listActividades(token, bond.id)) {
                            is ApiResult.Success -> listResult.data
                            is ApiResult.Error -> null
                        }
                    }
                    val counts = computeHomeCounts(activities, currentUserId)
                    // R1: el saldo lo manda el servidor, con signo. El cliente no lo recalcula.
                    val balance = firstBond?.let { bond ->
                        when (val summaryResult = expensesApi.getSummary(token, bond.id)) {
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

    open suspend fun getCompliance(bondId: String, token: String): ApiResult<ComplianceResponse> {
        return try {
            val response = client.get("$baseUrl/activities/$bondId/compliance") {
                header("Authorization", "Bearer $token")
            }
            if (response.status.isSuccess()) {
                ApiResult.Success(response.body())
            } else {
                val message = runCatching { response.body<ApiErrorBody>() }
                    .getOrNull()
                    ?.let { it.message ?: it.error }
                    ?: response.status.description
                ApiResult.Error(response.status.value, message)
            }
        } catch (e: Exception) {
            ApiResult.Error(0, "GET $baseUrl/activities/$bondId/compliance failed: ${e.message}")
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
