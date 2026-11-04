package com.vasmarfas.networkhelm.ui.files

import com.vasmarfas.networkhelm.ui.table.SortKey
import com.vasmarfas.networkhelm.ui.table.naturalOrder

class TreeRow(val entry: Entry, val depth: Int, val open: Boolean, val count: Int?, val size: Long?)

class FileTree(entries: List<Entry>, private val partial: Boolean = false) {
    private val byPath = LinkedHashMap<String, Entry>()
    private val kids = HashMap<String, MutableList<Entry>>()
    private val counts = HashMap<String, Int>()
    private val sizes = HashMap<String, Long>()

    init {
        for (entry in entries) byPath[entry.path] = entry
        for (entry in entries) {
            var parent = entry.parent
            while (parent.isNotEmpty() && parent !in byPath) {
                byPath[parent] = Entry(null, parent, true, null, "", "directory")
                parent = parent.substringBeforeLast('/', "")
            }
        }
        for (entry in byPath.values) {
            kids.getOrPut(entry.parent) { ArrayList() } += entry
            var parent = entry.parent
            while (true) {
                counts[parent] = (counts[parent] ?: 0) + 1
                if (!entry.directory && entry.size != null) sizes[parent] = (sizes[parent] ?: 0L) + entry.size
                if (parent.isEmpty()) break
                parent = parent.substringBeforeLast('/', "")
            }
        }
    }

    val folders: Set<String> = byPath.values.filter { it.directory }.mapTo(HashSet()) { it.path }

    fun countIn(path: String): Int = counts[path] ?: 0

    fun entry(path: String): Entry? = byPath[path]

    fun children(path: String): List<Entry> = kids[path].orEmpty()

    fun foldersBelow(path: String): List<String> = children(path).filter { it.directory }.flatMap { listOf(it.path) + foldersBelow(it.path) }

    fun rows(root: String, expanded: Set<String>, sort: SortKey?, filter: String = ""): List<TreeRow> {
        val text = filter.trim()
        val matches = if (text.isEmpty()) emptySet<String>() else matching(root, text)
        val way = if (text.isEmpty()) emptySet<String>() else matches.flatMapTo(HashSet<String>()) { ancestors(it, root) }
        val out = ArrayList<TreeRow>()
        fun walk(parent: String, depth: Int) {
            for (entry in ordered(children(parent), sort)) {
                if (text.isNotEmpty() && entry.path !in matches && entry.path !in way) continue
                val open = entry.directory && if (text.isEmpty()) entry.path in expanded else entry.path in way
                out += TreeRow(entry, depth, open, if (entry.directory && !partial) counts[entry.path] ?: 0 else null, if (entry.directory && !partial) sizes[entry.path] ?: 0L else null)
                if (open) walk(entry.path, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    private fun matching(root: String, text: String): Set<String> {
        val prefix = if (root.isEmpty()) "" else "$root/"
        return byPath.values.filter { it.path.startsWith(prefix) && it.name.contains(text, ignoreCase = true) }.mapTo(HashSet()) { it.path }
    }

    private fun ancestors(path: String, root: String): List<String> {
        val found = ArrayList<String>()
        var parent = path.substringBeforeLast('/', "")
        while (parent.isNotEmpty() && parent != root) {
            found += parent
            parent = parent.substringBeforeLast('/', "")
        }
        return found
    }

    private fun ordered(list: List<Entry>, sort: SortKey?): List<Entry> {
        val column = sort?.let { columnOf(it.column) }
        if (sort == null || column == null) return list.sortedWith(compareBy<Entry> { !it.directory }.thenBy(naturalOrder) { it.name })
        val ascending = sort.ascending
        val ordered = list.sortedWith(compareBy<Entry, String>(naturalOrder) { entry ->
            val lead = if (entry.directory == ascending) '0' else '1'
            lead + when (column) {
                FileColumn.Name -> entry.name
                FileColumn.Size -> (if (entry.directory) sizes[entry.path] else entry.size)?.toString()?.padStart(20, '0').orEmpty()
                FileColumn.Modified -> entry.modified
                FileColumn.Type -> entry.type
            }
        })
        return if (ascending) ordered else ordered.asReversed()
    }

    private fun columnOf(key: String): FileColumn? = when (key) {
        "name" -> FileColumn.Name
        "size" -> FileColumn.Size
        "last-modified" -> FileColumn.Modified
        "type" -> FileColumn.Type
        else -> null
    }
}
