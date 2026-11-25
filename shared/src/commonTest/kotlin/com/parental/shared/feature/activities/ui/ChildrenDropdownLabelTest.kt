package com.parental.shared.feature.activities.ui

import com.parental.shared.feature.bonds.domain.model.VinculoChild
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T3 (activity-card-stack S4): el label del dropdown de hijos muestra las
 * selecciones; sin selección el chip dice "Hijo" (el default "no muestra nada",
 * decisión del humano) y con dos seleccionadas el count va en el chip por separado.
 *
 * "Todos" excluyente NO se prueba acá: vive en el ViewModel
 * (`toggleChildFilter`/`clearChildFilter`, ya cubiertos por
 * `ActivitiesViewModelChildrenFilterTest`).
 */
class ChildrenDropdownLabelTest {
    private fun child(id: String, first: String, last: String? = null) =
        VinculoChild(id = id, firstName = first, lastName = last ?: "")

    private val children = listOf(
        child("c1", "Sofía", "García"),
        child("c2", "Tomás", "García"),
    )

    @Test
    fun `sin seleccion el label queda vacio - el icono solo lleva el chip`() {
        // S4 llave por ícono: el chip YA no dice "Hijo" cuando no hay nada seleccionado.
        assertEquals("", childrenDropdownLabel(children, emptySet()))
    }

    @Test
    fun `un hijo activo muestra su nombre`() {
        assertEquals(
            "Sofía García",
            childrenDropdownLabel(children, setOf("c1")),
        )
    }

    @Test
    fun `dos hijos activos se enumeran en el label`() {
        assertEquals(
            "Sofía García, Tomás García",
            childrenDropdownLabel(children, setOf("c1", "c2")),
        )
    }

    @Test
    fun `un hijo sin nombre degrada al id`() {
        val nameless = listOf(VinculoChild(id = "x", firstName = "", lastName = ""))
        assertEquals("x", childrenDropdownLabel(nameless, setOf("x")))
    }
}
