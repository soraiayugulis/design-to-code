package com.designtocode.domain

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class ProcessOutput(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false
)

class ProcessRunner(private val workingDir: File) {
    companion object {
        private const val OUTPUT_DRAIN_TIMEOUT_SECONDS = 30L
    }

    fun run(command: List<String>, timeoutSeconds: Long): ProcessOutput {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()

        // Drain stdout+stderr concurrently so the child never blocks on a full pipe
        val outputFuture = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ProcessOutput(-1, drainOutput(outputFuture), timedOut = true)
        }

        return ProcessOutput(process.exitValue(), drainOutput(outputFuture))
    }

    private fun drainOutput(outputFuture: CompletableFuture<String>): String {
        return try {
            outputFuture.get(OUTPUT_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            ""
        }
    }
}
