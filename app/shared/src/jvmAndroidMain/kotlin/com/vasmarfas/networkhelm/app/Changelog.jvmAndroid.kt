package com.vasmarfas.networkhelm.app

import com.vasmarfas.networkhelm.appVersion
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CHANGELOGS = "https://upgrade.mikrotik.com/routeros/"
private const val TIMEOUT_MILLIS = 6_000
private const val LIMIT_BYTES = 256 * 1024

private val versions = Regex("[0-9][0-9A-Za-z.]{0,23}")
private val fetched = ConcurrentHashMap<String, String>()

// the vendor's list of changes of one version, asked once; no answer, for whatever reason, means no list
suspend fun fetchChangelog(version: String): String? {
    if (!versions.matches(version)) return null
    fetched[version]?.let { return it }
    val text = withContext(Dispatchers.IO) {
        val connection = URI("$CHANGELOGS$version/CHANGELOG").toURL().openConnection() as? HttpURLConnection ?: return@withContext null
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "NetworkHelm/$appVersion")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readLimited(LIMIT_BYTES) } else null
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    } ?: return null
    fetched[version] = text
    return text
}

internal fun InputStream.readLimited(limit: Int): String? {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        out.write(chunk, 0, read)
        if (out.size() > limit) return null
    }
    return out.toByteArray().decodeToString()
}
