package com.vasmarfas.networkhelm.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.vasmarfas.networkhelm.ui.kit.IconButton
import com.vasmarfas.networkhelm.ui.kit.Select
import com.vasmarfas.networkhelm.ui.kit.TextField
import com.vasmarfas.networkhelm.ui.theme.Theme
import org.jetbrains.compose.resources.stringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.filter_add
import networkhelm.app.shared.generated.resources.filter_contains
import networkhelm.app.shared.generated.resources.filter_contains_not
import networkhelm.app.shared.generated.resources.filter_in
import networkhelm.app.shared.generated.resources.filter_is
import networkhelm.app.shared.generated.resources.filter_is_not
import networkhelm.app.shared.generated.resources.filter_remove

@Stable
class FilterLine(column: String) {
    var column by mutableStateOf(column)
    var operator by mutableStateOf(FilterOperator.Is)
    val value = TextFieldState()

    fun condition() = Condition(column, operator, value.text.toString().trim())
}

@Composable
fun FilterBar(lines: SnapshotStateList<FilterLine>, columns: List<String>, label: (String) -> String, sample: (String) -> String?) {
    Column(
        Modifier.fillMaxWidth().background(Theme.palette.raised).padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        lines.forEachIndexed { index, line ->
            key(line) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Select(line.column, columns, { label(it) }, { column ->
                        line.column = column
                        if (line.operator !in operatorsFor(sample(column))) line.operator = FilterOperator.Is
                    }, Modifier.width(170.dp).testTag("filter-column"))
                    Select(line.operator, operatorsFor(sample(line.column)), { operatorLabel(it) }, { line.operator = it }, Modifier.width(120.dp))
                    TextField(line.value, Modifier.width(180.dp).testTag("filter-value"))
                    IconButton(Lucide.Minus, stringResource(Res.string.filter_remove), { lines.remove(line) })
                    IconButton(Lucide.Plus, stringResource(Res.string.filter_add), { lines.add(index + 1, FilterLine(line.column)) })
                }
            }
        }
    }
}

@Composable
private fun operatorLabel(operator: FilterOperator) = stringResource(
    when (operator) {
        FilterOperator.Is -> Res.string.filter_is
        FilterOperator.IsNot -> Res.string.filter_is_not
        FilterOperator.Contains -> Res.string.filter_contains
        FilterOperator.ContainsNot -> Res.string.filter_contains_not
        FilterOperator.In -> Res.string.filter_in
    },
)
