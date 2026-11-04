package com.vasmarfas.networkhelm.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class Release(val version: String, val url: String)

@Serializable
private class Listed(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val url: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

private const val GITHUB = "https://github.com/"

private val listing = Json { ignoreUnknownKeys = true }

// a prerelease build is offered the prereleases too, a stable one only the stable releases
fun newerRelease(text: String, running: String): Release? {
    val current = Version.parse(running) ?: return null
    val listed = try {
        listing.decodeFromString(ListSerializer(Listed.serializer()), text)
    } catch (e: SerializationException) {
        return null
    }
    return listed.asSequence()
        .filter { !it.draft && (!it.prerelease || current.prerelease) && it.url.startsWith(GITHUB) }
        .mapNotNull { entry -> Version.parse(entry.tag)?.let { it to entry } }
        .filter { (version, _) -> version > current }
        .maxByOrNull { it.first }
        ?.let { (_, entry) -> Release(entry.tag.removePrefix("v"), entry.url) }
}

private class Version(private val numbers: List<Int>, private val stage: String?) : Comparable<Version> {
    val prerelease: Boolean get() = stage != null

    override fun compareTo(other: Version): Int {
        for (index in 0 until maxOf(numbers.size, other.numbers.size)) {
            val order = numbers.getOrElse(index) { 0 }.compareTo(other.numbers.getOrElse(index) { 0 })
            if (order != 0) return order
        }
        val mine = stage?.let(::stageOrder) ?: return if (other.stage == null) 0 else 1
        val theirs = other.stage?.let(::stageOrder) ?: return -1
        return compareValuesBy(mine, theirs, { it.first }, { it.second })
    }

    companion object {
        private val pattern = Regex("""^\s*v?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.]+))?\s*$""")
        private val stagePattern = Regex("""([A-Za-z]*)\.?(\d*)""")

        fun parse(text: String): Version? {
            val (major, minor, patch, stage) = pattern.matchEntire(text)?.destructured ?: return null
            val numbers = listOf(major, minor, patch.ifEmpty { "0" }).map { it.toIntOrNull() ?: return null }
            return Version(numbers, stage.ifEmpty { null })
        }

        private fun stageOrder(stage: String): Pair<String, Int> {
            val (name, number) = stagePattern.matchEntire(stage)?.destructured ?: return stage to 0
            return name to (number.toIntOrNull() ?: 0)
        }
    }
}
