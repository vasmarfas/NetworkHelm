package com.vasmarfas.networkhelm.ui.table

import com.vasmarfas.networkhelm.search.span

enum class FilterOperator { Is, IsNot, Contains, ContainsNot, In }

data class Condition(val column: String, val operator: FilterOperator, val value: String) {

    fun matches(cell: String?): Boolean {
        if (value.isEmpty()) return true
        val text = cell.orEmpty()
        return when (operator) {
            FilterOperator.Is -> text == value
            FilterOperator.IsNot -> text != value
            FilterOperator.Contains -> text.contains(value, ignoreCase = true)
            FilterOperator.ContainsNot -> !text.contains(value, ignoreCase = true)
            FilterOperator.In -> inSubnet(text, value)
        }
    }
}

fun operatorsFor(sample: String?): List<FilterOperator> =
    if (sample != null && span(sample) != null) FilterOperator.entries else FilterOperator.entries - FilterOperator.In

private fun inSubnet(cell: String, subnet: String): Boolean {
    val outer = span(subnet) ?: return false
    return span(cell)?.within(outer) == true
}
