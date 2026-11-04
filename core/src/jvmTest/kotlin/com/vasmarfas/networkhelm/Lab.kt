package com.vasmarfas.networkhelm

import org.junit.Assume.assumeTrue
import org.junit.AssumptionViolatedException
import java.io.File
import java.util.Properties

object Lab {
    private val file: File? = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
    private val properties: Properties? = file?.let { Properties().apply { it.inputStream().use(::load) } }

    fun file(path: String): File = File(path).takeIf { it.isAbsolute } ?: File(file?.parentFile, path)

    fun require(vararg keys: String): Map<String, String> {
        assumeTrue("lab.properties is missing", properties != null)
        val values = keys.associateWith { properties?.getProperty(it).orEmpty().trim() }
        assumeTrue("lab.properties has no ${values.filterValues(String::isEmpty).keys}", values.values.none(String::isEmpty))
        return values
    }

    fun optional(key: String): String? = properties?.getProperty(key)?.trim()?.ifEmpty { null }

    fun forEachOpenWrt(vararg keys: String, block: (Map<String, String>) -> Unit) {
        val routers = listOf("openwrt", "openwrt24").filter { properties?.getProperty("$it.host").orEmpty().isNotBlank() }
        assumeTrue("lab.properties has no OpenWrt", routers.isNotEmpty())
        for (prefix in routers) {
            val fields = require(*keys.map { "$prefix.$it" }.toTypedArray()).mapKeys { it.key.removePrefix("$prefix.") }
            try {
                block(fields)
            } catch (e: AssumptionViolatedException) {
                throw e
            } catch (e: Throwable) {
                throw AssertionError("on $prefix: $e", e)
            }
        }
    }
}
