package com.example.engine

interface ExtractionEngine {
    val name: String
    val version: String
    val isBundled: Boolean

    suspend fun supportsUrl(url: String): Boolean
    suspend fun extract(url: String): ExtractionResult
    suspend fun runSelfTest(): Boolean
}
