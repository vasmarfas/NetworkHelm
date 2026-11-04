package com.vasmarfas.networkhelm.session

interface TextStore {
    suspend fun read(name: String): String?

    suspend fun write(name: String, text: String)
}
