package com.example.engine

class NewPipeEngine(
    private val ytDlpEngine: YtDlpEngine = YtDlpEngine()
) : ExtractionEngine {

    override val name: String = "yt-dlp"
    override var version: String = ytDlpEngine.version
    override val isBundled: Boolean = true

    override suspend fun supportsUrl(url: String): Boolean {
        return ytDlpEngine.supportsUrl(url)
    }

    override suspend fun extract(url: String): ExtractionResult {
        return ytDlpEngine.extract(url)
    }

    override suspend fun runSelfTest(): Boolean {
        return ytDlpEngine.runSelfTest()
    }
}
