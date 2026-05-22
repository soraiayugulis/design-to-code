package com.designtocode.domain

import com.designtocode.domain.model.QualityGateResult
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class QualityGateValidator(
    private val projectDir: File,
    private val coverageThreshold: Double = 100.0,
    private val timeoutSeconds: Long = 900L // 15 minutes default
) {

    fun validate(): QualityGateResult {
        val buildResult = executeGradleBuild()
        
        if (!buildResult.success) {
            return QualityGateResult(
                passed = false,
                buildSuccess = false,
                coveragePercentage = 0.0,
                errorMessage = buildResult.errorMessage
            )
        }
        
        val coveragePercentage = parseCoverageReport()
        val passed = coveragePercentage >= coverageThreshold
        
        return QualityGateResult(
            passed = passed,
            buildSuccess = true,
            coveragePercentage = coveragePercentage,
            errorMessage = if (!passed) "Coverage $coveragePercentage% is below threshold $coverageThreshold%" else null
        )
    }
    
    private fun executeGradleBuild(): BuildResult {
        return try {
            val gradleWrapper = File(projectDir, "gradlew")
            if (!gradleWrapper.exists()) {
                return BuildResult(false, "Gradle wrapper not found in project directory")
            }
            
            val process = ProcessBuilder(
                gradleWrapper.absolutePath,
                "clean",
                "build",
                "--no-daemon"
            ).directory(projectDir).start()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            val errorOutput = StringBuilder()
            
            val finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                return BuildResult(false, "Gradle build timed out after ${timeoutSeconds}s")
            }
            
            reader.use { it.lines().forEach { output.appendLine(it) } }
            errorReader.use { it.lines().forEach { errorOutput.appendLine(it) } }
            
            val exitCode = process.exitValue()
            
            if (exitCode == 0) {
                BuildResult(true, null)
            } else {
                val errorMessage = extractCompilationErrors(errorOutput.toString())
                BuildResult(false, "Gradle build failed with exit code $exitCode. $errorMessage")
            }
        } catch (e: Exception) {
            BuildResult(false, "Failed to execute Gradle build: ${e.message}")
        }
    }
    
    private fun extractCompilationErrors(errorOutput: String): String {
        if (errorOutput.contains("error:") || errorOutput.contains("FAILURE")) {
            val lines = errorOutput.lines()
            val errorLines = lines.filter { it.contains("error:") || it.contains("e:") }
            return if (errorLines.isNotEmpty()) {
                errorLines.take(3).joinToString("; ")
            } else {
                "Build compilation failed"
            }
        }
        return "Build failed"
    }
    
    private fun parseCoverageReport(): Double {
        // TODO: Implement actual coverage report parsing in task 1.2
        // For now, return a placeholder value
        return 0.0
    }
    
    private data class BuildResult(
        val success: Boolean,
        val errorMessage: String?
    )
}
