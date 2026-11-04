package com.vasmarfas.networkhelm.ui.files

import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.FileCog
import com.composables.icons.lucide.FileKey
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Usb
import com.vasmarfas.networkhelm.model.Item
import com.vasmarfas.networkhelm.ui.chart.byteCount
import com.vasmarfas.networkhelm.ui.chart.isoDate
import com.vasmarfas.networkhelm.ui.theme.IconTint
import com.vasmarfas.networkhelm.ui.theme.ThemeIcon

enum class FileKind { Folder, Disk, Mount, Script, Text, Config, Log, Image, Archive, Package, Backup, Key, Other }

private val images = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
private val scripts = setOf("rsc", "sh", "ash", "lua", "py", "local")
private val configs = setOf("json", "yaml", "yml", "conf", "cfg", "ini", "toml", "properties", "xml", "network", "wireless", "firewall", "dhcp")
private val texts = setOf("txt", "md", "csv", "html", "htm", "css", "js", "pem")
private val archives = setOf("zip", "gz", "tar", "tgz", "bz2", "xz", "7z", "rar", "lzma")
private val keys = setOf("crt", "cer", "key", "p12", "pfx", "der")

fun extensionOf(name: String): String = name.substringAfterLast('/').substringAfterLast('.', "").lowercase()

fun holdsFiles(type: String): Boolean = type == "directory" || type == "disk" || "mountpoint" in type

fun kindOf(name: String, type: String = "", directory: Boolean = false): FileKind {
    val extension = extensionOf(name)
    val file = name.substringAfterLast('/').lowercase()
    return when {
        type == "disk" -> FileKind.Disk
        "mountpoint" in type -> FileKind.Mount
        directory || type == "directory" -> FileKind.Folder
        type == "package" || extension == "npk" -> FileKind.Package
        type == "backup" || extension == "backup" -> FileKind.Backup
        extension in images -> FileKind.Image
        extension == "log" -> FileKind.Log
        extension in scripts || type == "script" -> FileKind.Script
        extension in configs || file in configs -> FileKind.Config
        extension in texts -> FileKind.Text
        extension in archives -> FileKind.Archive
        extension in keys -> FileKind.Key
        else -> FileKind.Other
    }
}

val FileKind.text: Boolean get() = this == FileKind.Script || this == FileKind.Text || this == FileKind.Config || this == FileKind.Log || this == FileKind.Other

fun FileKind.icon(open: Boolean = false): ThemeIcon = when (this) {
    FileKind.Folder -> ThemeIcon(if (open) Lucide.FolderOpen else Lucide.Folder, IconTint.Warning)
    FileKind.Disk -> ThemeIcon(Lucide.HardDrive, IconTint.Accent)
    FileKind.Mount -> ThemeIcon(Lucide.Usb, IconTint.Accent)
    FileKind.Script -> ThemeIcon(Lucide.FileCode, IconTint.Accent)
    FileKind.Text -> ThemeIcon(Lucide.FileText, null)
    FileKind.Config -> ThemeIcon(Lucide.FileCog, IconTint.Accent)
    FileKind.Log -> ThemeIcon(Lucide.ScrollText, IconTint.Muted)
    FileKind.Image -> ThemeIcon(Lucide.Image, IconTint.Success)
    FileKind.Archive -> ThemeIcon(Lucide.Archive, IconTint.Warning)
    FileKind.Package -> ThemeIcon(Lucide.Package, IconTint.Accent)
    FileKind.Backup -> ThemeIcon(Lucide.ArchiveRestore, IconTint.Warning)
    FileKind.Key -> ThemeIcon(Lucide.FileKey, IconTint.Success)
    FileKind.Other -> ThemeIcon(Lucide.File, IconTint.Muted)
}

data class Entry(val id: String?, val path: String, val directory: Boolean, val size: Long?, val modified: String, val type: String) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/', "")
    val kind: FileKind get() = kindOf(path, type, directory)
}

fun entryOf(item: Item): Entry? {
    val path = item["name"]?.trim('/')?.takeIf { it.isNotEmpty() } ?: return null
    val type = item["type"].orEmpty()
    return Entry(item.id, path, holdsFiles(type), item["size"]?.toLongOrNull(), cleanTime(item["last-modified"] ?: item["creation-time"].orEmpty()), type)
}

private val duration = Regex("(\\d+w)?(\\d+d)?(\\d+h)?(\\d+m)?(\\d+s)?(\\d+ms)?")
private val isoYear = Regex("^(\\d{4})-\\d{2}-\\d{2}")
private val oldYear = Regex("^[A-Za-z]{3}/\\d{2}/(\\d{4})")

// RouterOS gives a date of 1970 while its clock is unset, and a span like 3h23s for a folder it made at boot
fun cleanTime(raw: String): String {
    val text = raw.trim()
    if (text.isEmpty() || duration.matches(text)) return ""
    val year = (isoYear.find(text) ?: oldYear.find(text))?.groupValues?.get(1)?.toIntOrNull()
    return if (year != null && year < 2001) "" else isoDate(text)
}

fun crumbs(folder: String): List<Pair<String, String>> {
    val parts = folder.split('/').filter { it.isNotEmpty() }
    return listOf("" to "") + parts.indices.map { parts[it] to parts.take(it + 1).joinToString("/") }
}

enum class FileColumn { Name, Size, Modified, Type }

fun sizeText(size: Long?): String = size?.let { byteCount(it.toDouble()) }.orEmpty()

fun utcText(epochSeconds: Long): String {
    val days = epochSeconds.floorDiv(86_400L)
    val rest = epochSeconds - days * 86_400L
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val dayOfEra = z - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val shifted = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * shifted + 2) / 5 + 1
    val month = if (shifted < 10) shifted + 3 else shifted - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    fun two(value: Long) = value.toString().padStart(2, '0')
    return "${year.toString().padStart(4, '0')}-${two(month)}-${two(day)} ${two(rest / 3_600)}:${two(rest % 3_600 / 60)}:${two(rest % 60)}"
}
