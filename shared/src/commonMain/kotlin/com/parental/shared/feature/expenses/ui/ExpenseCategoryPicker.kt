package com.parental.shared.feature.expenses.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.parental.shared.core.ui.components.CategoryInfo
import com.parental.shared.core.ui.theme.Spacing

/**
 * Selector simple de categoría para el alta de gastos (S6/T27).
 *
 * El usuario elige por NOMBRE; el id se guarda en el formulario. Sólo muestra
 * categorías activas, siguiendo la política del alta de actividades.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoryPicker(
    categories: Map<String, CategoryInfo>,
    selectedId: String?,
    error: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Categoría",
) {
    var expanded by remember { mutableStateOf(false) }
    val activeCategories = remember(categories) {
        categories.filterValues { it.isActive }
    }
    val selected = activeCategories[selectedId]

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.name ?: "",
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Seleccionar categoría") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                activeCategories.entries.forEach { (id, info) ->
                    DropdownMenuItem(
                        text = { Text(info.name) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
