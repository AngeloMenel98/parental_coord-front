package com.parental.shared.feature.activities.domain

import com.parental.shared.feature.activities.domain.model.ActividadAttribution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * S5 (thirdparty-identity): la policy de atribución es UNA y ambos casos del S3
 * quedan fijados acá — "en las cards de activities tiene que mostrarse el nombre del
 * terciario para el que lo autorizo, para los demas solo muestra quien lo autorizo".
 *
 * La visibilidad NO se decide acá: el servidor manda `name` sólo a quien puede verlo
 * (S7). La policy sólo traduce el payload a línea de UI.
 */
class AttributionPolicyTest {
    @Test
    fun `el autorizante ve el nombre real del terciario`() {
        val line = attributionLine(
            ActividadAttribution(kind = "third_party", name = "Pablo Charles"),
        )
        assertEquals("Pablo Charles", line, "S3: quien lo autorizó ve 'Pablo Charles'.")
    }

    @Test
    fun `los demás ven Autorizado por el autorizante`() {
        val line = attributionLine(
            ActividadAttribution(
                kind = "third_party",
                name = null,
                authorizedByName = "Angelo",
            ),
        )
        assertEquals(
            "Autorizado por Angelo",
            line,
            "S3: la ex-mujer ve 'Autorizado por Angelo', nunca el nombre del terciario.",
        )
    }

    @Test
    fun `lo de un miembro no agrega línea`() {
        assertNull(
            attributionLine(ActividadAttribution(kind = "member", name = "Angelo")),
            "S5: actividades de miembros siguen pintándose como siempre.",
        )
        assertNull(attributionLine(null), "S5: back viejo sin attribution → sin línea (S6).")
    }

    @Test
    fun `sin names resolvibles el hueco es honesto`() {
        assertNull(
            attributionLine(ActividadAttribution(kind = "third_party")),
            "S3: grant muerto sin nombres → nada, ni nombre filtrado ni frase inventada.",
        )
    }

    @Test
    fun `blancos cuentan como ausentes`() {
        assertNull(
            attributionLine(
                ActividadAttribution(kind = "third_party", name = "   ", authorizedByName = ""),
            ),
            "S5: espacios no son un nombre.",
        )
    }
}
