package com.parental.shared.feature.activities.ui

import com.parental.shared.core.error.ApiResult
import com.parental.shared.core.network.CategoriesApi
import com.parental.shared.core.network.CategoryDto
import com.parental.shared.feature.activities.data.remote.ActivitiesApi
import com.parental.shared.feature.activities.domain.model.Actividad
import com.parental.shared.feature.activities.domain.model.attachments.ActivityAttachmentDto
import com.parental.shared.feature.activities.domain.model.attachments.ConfirmAttachmentReq
import com.parental.shared.feature.activities.domain.model.attachments.PresignAttachmentReq
import com.parental.shared.feature.activities.domain.model.attachments.PresignAttachmentRes
import com.parental.shared.feature.bonds.data.remote.BondsApi
import com.parental.shared.feature.bonds.domain.model.Vinculo
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Las cuatro mutaciones de adjuntos hacían `return@launch` mudo ante cualquier
 * error. Desde la pantalla eso es indistinguible de un botón roto: el usuario elige
 * el archivo, no pasa nada y no hay forma de saber por qué.
 *
 * Estos tests fijan que el fallo llegue al mismo canal que ya usa el resto de la
 * pantalla (`snackbarMessage`) y que NO se invente un adjunto que el servidor nunca
 * confirmó.
 */
class ActivitiesViewModelAttachmentsTest {

    private val token = "t-1"
    private val bondId = "b-1"
    private val userId = "u-self"
    private val activityId = "act-1"

    private class FakeActivitiesApi : ActivitiesApi(UNUSED_CLIENT, "http://unused") {
        var presignResult: ApiResult<PresignAttachmentRes> = ApiResult.Error(500, "boom")
        var presignError: Throwable? = null
        var confirmResult: ApiResult<ActivityAttachmentDto> = ApiResult.Error(500, "boom")
        var deleteResult: ApiResult<Unit> = ApiResult.Error(500, "boom")

        var presignCalls = 0
        var confirmCalls = 0

        override suspend fun listActividades(token: String, bondId: String): ApiResult<List<Actividad>> =
            ApiResult.Success(emptyList())

        override suspend fun presignAttachment(
            token: String,
            activityId: String,
            req: PresignAttachmentReq,
        ): ApiResult<PresignAttachmentRes> {
            presignCalls++
            presignError?.let { throw it }
            return presignResult
        }

        override suspend fun confirmAttachment(
            token: String,
            activityId: String,
            req: ConfirmAttachmentReq,
        ): ApiResult<ActivityAttachmentDto> {
            confirmCalls++
            return confirmResult
        }

        override suspend fun deleteAttachment(
            token: String,
            activityId: String,
            attachmentId: String,
        ): ApiResult<Unit> = deleteResult
    }

    private class FakeCategoriesApi : CategoriesApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getCategories(): ApiResult<List<CategoryDto>> =
            ApiResult.Success(listOf(CategoryDto(id = "cat", name = "Salud", criticality = "medium")))
    }

    private class FakeBondsApi : BondsApi(UNUSED_CLIENT, "http://unused") {
        override suspend fun getMyBonds(token: String): ApiResult<List<Vinculo>> =
            ApiResult.Success(
                listOf(
                    Vinculo(
                        id = "b-1",
                        title = "Familia",
                        agreementType = "custody",
                        members = emptyList(),
                        children = emptyList(),
                    ),
                ),
            )
    }

    private val viewModels = mutableListOf<ActividadesViewModel>()

    private fun build(): Pair<ActividadesViewModel, FakeActivitiesApi> {
        val api = FakeActivitiesApi()
        val vm = ActividadesViewModel(
            activitiesApi = api,
            categoriesApi = FakeCategoriesApi(),
            bondsApi = FakeBondsApi(),
            token = token,
            bondId = bondId,
            currentUserId = userId,
            dispatcher = UnconfinedTestDispatcher(),
            clock = { 0L },
        )
        viewModels += vm
        return vm to api
    }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.destroy() }
        viewModels.clear()
    }

    @Test
    fun `un presign fallido avisa y no encadena confirm`() {
        val (vm, api) = build()
        api.presignResult = ApiResult.Error(413, "El archivo supera el tamaño máximo")

        // PDF: `ImageCompressor` lo deja pasar sin tocar BitmapFactory, así que este
        // test corre en JVM sin gráficos de Android.
        vm.addAttachment(activityId, "comprobante.pdf", "application/pdf", ByteArray(4))

        assertEquals("El archivo supera el tamaño máximo", vm.uiState.value.snackbarMessage)
        assertEquals(0, api.confirmCalls, "sin presign válido no hay nada que confirmar")
        assertTrue(vm.attachments.value.isEmpty(), "no se agrega un adjunto que nunca se confirmó")
    }

    @Test
    fun `una excepcion de red avisa en vez de escapar del launch`() {
        val (vm, api) = build()
        api.presignError = RuntimeException("socket reset")

        // Antes, una excepción de red escapaba del `launch`: ni snackbar ni adjunto,
        // indistinguible de un botón roto.
        vm.addAttachment(activityId, "comprobante.pdf", "application/pdf", ByteArray(4))

        assertEquals("No se pudo adjuntar el archivo: socket reset", vm.uiState.value.snackbarMessage)
        assertEquals(0, api.confirmCalls)
        assertTrue(vm.attachments.value.isEmpty())
    }

    @Test
    fun `un delete fallido avisa`() {
        val (vm, api) = build()
        api.deleteResult = ApiResult.Error(403, "Cannot delete this attachment")

        vm.deleteAttachment(activityId, "att-1")

        assertEquals("Cannot delete this attachment", vm.uiState.value.snackbarMessage)
    }

    private companion object {
        val UNUSED_CLIENT = HttpClient { }
    }
}
