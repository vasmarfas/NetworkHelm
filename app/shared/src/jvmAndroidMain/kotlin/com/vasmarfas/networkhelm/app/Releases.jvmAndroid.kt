package com.vasmarfas.networkhelm.app

import com.vasmarfas.networkhelm.appVersion
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LISTING = "https://api.github.com/repos/vasmarfas/networkhelm/releases?per_page=20"
private const val TIMEOUT_MILLIS = 6_000
private const val LIMIT_BYTES = 2 * 1024 * 1024

// no answer, for whatever reason, means no notice
suspend fun fetchReleases(): String? = withContext(Dispatchers.IO) {
    val connection = URI(LISTING).toURL().openConnection() as? HttpURLConnection ?: return@withContext null
    try {
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "NetworkHelm/$appVersion")
        if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readLimited(LIMIT_BYTES) } else null
    } catch (e: IOException) {
        null
    } finally {
        connection.disconnect()
    }
}
