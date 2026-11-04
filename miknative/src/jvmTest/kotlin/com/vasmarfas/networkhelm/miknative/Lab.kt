package com.vasmarfas.networkhelm.miknative

import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Properties

object Lab {
    private val file: File? = System.getProperty("networkhelm.lab")?.let(::File)?.takeIf { it.isFile }
    private val properties: Properties? = file?.let { Properties().apply { it.inputStream().use(::load) } }
    val root: File? = file?.parentFile

    fun require(vararg keys: String): Map<String, String> {
        assumeTrue("lab.properties is missing", properties != null)
        val values = keys.associateWith { properties?.getProperty(it).orEmpty().trim() }
        assumeTrue("lab.properties has no ${values.filterValues(String::isEmpty).keys}", values.values.none(String::isEmpty))
        return values
    }
}
