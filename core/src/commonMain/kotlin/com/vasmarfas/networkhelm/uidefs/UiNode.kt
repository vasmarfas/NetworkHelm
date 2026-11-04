package com.vasmarfas.networkhelm.uidefs

class UiNode(val kind: String?, val attrs: Map<String, JgValue>, val children: List<UiNode>) {
    fun text(key: String): String? = (attrs[key] as? JgString)?.text

    fun number(key: String): Long? = (attrs[key] as? JgNumber)?.value?.toLong()

    fun flag(key: String): Boolean = when (val value = attrs[key]) {
        is JgFlag -> value.on
        is JgNumber -> value.value != 0.0
        else -> false
    }

    val name: String? get() = text("name")

    val label: String? get() = text("title") ?: name
}

fun uiNodes(value: JgValue): List<UiNode> = (value as? JgList)?.items.orEmpty().mapNotNull { (it as? JgObject)?.let(::uiNode) }

private fun uiNode(obj: JgObject): UiNode =
    UiNode((obj.fields["type"] as? JgString)?.text, obj.fields, (obj.fields["c"] as? JgList)?.items.orEmpty().mapNotNull { (it as? JgObject)?.let(::uiNode) })
