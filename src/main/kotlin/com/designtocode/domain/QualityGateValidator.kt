package com.designtocode.domain

import com.designtocode.domain.model.QualityGateResult
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

enum class CoverageType {
    LINE,
    BRANCH,
    INSTRUCTION
}

class QualityGateValidator(
    private val projectDir: File,
    private val coverageThreshold: Double = 100.0,
    private val timeoutSeconds: Long = 900L, // 15 minutes default
    private val coverageType: CoverageType = CoverageType.LINE
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
        
        val detektResult = executeDetekt()
        
        if (!detektResult.success) {
            return QualityGateResult(
                passed = false,
                buildSuccess = true,
                coveragePercentage = 0.0,
                errorMessage = detektResult.errorMessage
            )
        }
        
        val coveragePercentage = parseCoverageReport()
        val passed = coveragePercentage >= coverageThreshold
        
        return QualityGateResult(
            passed = passed,
            buildSuccess = true,
            coveragePercentage = coveragePercentage,
            lintIssues = detektResult.issueCount,
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
    
    private fun executeDetekt(): DetektResult {
        return try {
            val gradleWrapper = File(projectDir, "gradlew")
            if (!gradleWrapper.exists()) {
                return DetektResult(success = true, issueCount = 0, errorMessage = null)
            }
            
            val process = ProcessBuilder(
                gradleWrapper.absolutePath,
                "detekt",
                "--no-daemon"
            ).directory(projectDir).start()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            val errorOutput = StringBuilder()
            
            val finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                return DetektResult(success = false, issueCount = 0, errorMessage = "Detekt timed out after ${timeoutSeconds}s")
            }
            
            reader.use { it.lines().forEach { output.appendLine(it) } }
            errorReader.use { it.lines().forEach { errorOutput.appendLine(it) } }
            
            val exitCode = process.exitValue()
            
            if (exitCode == 0) {
                DetektResult(success = true, issueCount = 0, errorMessage = null)
            } else {
                val issueCount = parseDetektOutput(output.toString())
                DetektResult(success = false, issueCount = issueCount, errorMessage = "Detekt found $issueCount issues")
            }
        } catch (e: Exception) {
            DetektResult(success = false, issueCount = 0, errorMessage = "Failed to execute Detekt: ${e.message}")
        }
    }
    
    private fun parseDetektOutput(output: String): Int {
        // Parse Detekt output to count issues
        // Look for patterns like "x issues found" or "Detekt found x issues"
        val issuesFoundRegex = Regex("""(\d+) issues? found""")
        val detektFoundRegex = Regex("""Detekt found (\d+) issues""")
        
        val match = issuesFoundRegex.find(output) ?: detektFoundRegex.find(output)
        
        if (match != null) {
            return match.groupValues[1].toInt()
        }
        
        // Fallback: count error lines
        return output.lines().count { it.contains("error") || it.contains("Error") }
    }
    
    private fun parseCoverageReport(): Double {
        // Try Kover first
        val koverReport = File(projectDir, "build/reports/kover/xml/report.xml")
        if (koverReport.exists()) {
            return parseKoverReport(koverReport)
        }
        
        // Try JaCoCo
        val jacocoReport = File(projectDir, "build/reports/jacoco/test/jacocoTestReport.xml")
        if (jacocoReport.exists()) {
            return parseJaCoCoReport(jacocoReport)
        }
        
        // No coverage report found
        return 0.0
    }
    
    private fun parseKoverReport(reportFile: File): Double {
        return try {
            val content = reportFile.readText()
            val typeAttribute = when (coverageType) {
                CoverageType.LINE -> "LINE"
                CoverageType.BRANCH -> "BRANCH"
                CoverageType.INSTRUCTION -> "INSTRUCTION"
            }
            
            val regex = Regex("""<counter type="$typeAttribute" missed="(\d+)" covered="(\d+)"/>""")
            val match = regex.find(content)
            
            if (match != null) {
                val missed = match.groupValues[1].toDouble()
                val covered = match.groupValues[2].toDouble()
                val total = missed + covered
                if (total > 0) {
                    (covered / total) * 100.0
                } else {
                    0.0
                }
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
    
    private fun parseJaCoCoReport(reportFile: File): Double {
        return try {
            val content = reportFile.readText()
            val typeAttribute = when (coverageType) {
                CoverageType.LINE -> "LINE"
                CoverageType.BRANCH -> "BRANCH"
                CoverageType.INSTRUCTION -> "INSTRUCTION"
            }
            
            val regex = Regex("""<counter type="$typeAttribute" missed="(\d+)" covered="(\d+)"/>""")
            val match = regex.find(content)
            
            if (match != null) {
                val missed = match.groupValues[1].toDouble()
                val covered = match.groupValues[2].toDouble()
                val total = missed + covered
                if (total > 0) {
                    (covered / total) * 100.0
                } else {
                    0.0
                }
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
    
    private data class BuildResult(
        val success: Boolean,
        val errorMessage: String?
    )
    
    private data class DetektResult(
        val success: Boolean,
        val issueCount: Int,
        val errorMessage: String?
    )
}
