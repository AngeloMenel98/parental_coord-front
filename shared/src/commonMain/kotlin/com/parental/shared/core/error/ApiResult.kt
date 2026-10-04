package com.parental.shared.core.error

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * @param code    HTTP status. `0` = la petición ni siquiera llegó al servidor.
     * @param message mensaje para mostrar, legible por una persona.
     * @param errorCode **código estable** del backend (`CodedException.code`, p. ej.
     *   `"ACTIVITY_ALREADY_PAST"`), o `null` si el endpoint no lo mandó.
     *
     * R8 · El móvil NO deduce el significado de la acción a partir del status HTTP.
     * Un 409 puede ser "ya pasó" o "se pasó la ventana de deshacer" y la UI reacciona
     * de forma distinta a cada uno, así que sin este campo el cliente tendría que
     * adivinarlo desde un texto que puede cambiar. Es `null` —no un string vacío—
     * cuando no hay código, para que `null` signifique "este endpoint no codifica
     * errores" y no "el backend envió un código vacío".
     *
     * Es un parámetro con valor por defecto a propósito: los 37 `ApiResult.Error(...)`
     * existentes siguen compilando sin tocarlos.
     */
    data class Error(
        val code: Int,
        val message: String,
        val errorCode: String? = null,
    ) : ApiResult<Nothing>()
}
