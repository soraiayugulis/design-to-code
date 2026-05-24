package com.designtocode.domain

import com.designtocode.domain.model.QualityGateResult
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(QualityGateValidator::class.java)

    fun validate(): QualityGateResult {
        logger.info("Starting quality gate validation")
        logger.info("Project directory: ${projectDir.absolutePath}")
        logger.info("Coverage threshold: $coverageThreshold%, type: $coverageType")
        logger.info("Timeout: ${timeoutSeconds}s")
        
        val buildResult = executeGradleBuild()
        
        if (!buildResult.success) {
            logger.error("Gradle build failed: ${buildResult.errorMessage}")
            return QualityGateResult(
                passed = false,
                buildSuccess = false,
                coveragePercentage = 0.0,
                errorMessage = buildResult.errorMessage
            )
        }
        logger.info("Gradle build succeeded")
        
        val detektResult = executeDetekt()
        
        if (!detektResult.success) {
            logger.error("Detekt failed: ${detektResult.errorMessage}")
            logger.error("Detekt found ${detektResult.issueCount} issues")
            return QualityGateResult(
                passed = false,
                buildSuccess = true,
                coveragePercentage = 0.0,
                lintIssues = detektResult.issueCount,
                errorMessage = detektResult.errorMessage
            )
        }
        logger.info("Detekt passed with ${detektResult.issueCount} issues")
        
        val coveragePercentage = parseCoverageReport()
        logger.info("Coverage percentage: $coveragePercentage%")
        
        val passed = coveragePercentage >= coverageThreshold
        if (!passed) {
            logger.warn("Coverage $coveragePercentage% is below threshold $coverageThreshold%")
        } else {
            logger.info("Coverage meets threshold")
        }
        
        return QualityGateResult(
            passed = passed,
            buildSuccess = true,
            coveragePercentage = coveragePercentage,
            lintIssues = detektResult.issueCount,
            errorMessage = if (!passed) "Coverage $coveragePercentage% is below threshold $coverageThreshold%" else null
        )
    }
    
    private fun executeGradleBuild(): BuildResult {
        logger.debug("Executing Gradle build")
        return try {
            val gradleWrapper = File(projectDir, "gradlew")
            logger.debug("Gradle wrapper path: ${gradleWrapper.absolutePath}")
            
            if (!gradleWrapper.exists()) {
                logger.error("Gradle wrapper not found at: ${gradleWrapper.absolutePath}")
                return BuildResult(false, "Gradle wrapper not found in project directory")
            }
            
            logger.debug("Starting Gradle build with timeout: ${timeoutSeconds}s")
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
                logger.error("Gradle build timed out after ${timeoutSeconds}s")
                process.destroyForcibly()
                return BuildResult(false, "Gradle build timed out after ${timeoutSeconds}s")
            }
            
            reader.use { it.lines().forEach { output.appendLine(it) } }
            errorReader.use { it.lines().forEach { errorOutput.appendLine(it) } }
            
            val exitCode = process.exitValue()
            logger.debug("Gradle build exit code: $exitCode")
            
            if (exitCode == 0) {
                logger.info("Gradle build completed successfully")
                BuildResult(true, null)
            } else {
                val errorMessage = extractCompilationErrors(errorOutput.toString())
                logger.error("Gradle build failed with exit code $exitCode: $errorMessage")
                BuildResult(false, "Gradle build failed with exit code $exitCode. $errorMessage")
            }
        } catch (e: Exception) {
            logger.error("Failed to execute Gradle build: ${e.message}", e)
            BuildResult(false, "Failed to execute Gradle build: ${e.message}")
        }
    }
    
    companion object {
        private const val PERCENTAGE_MULTIPLIER = 100.0
        private const val MAX_ERROR_LINES = 3
    }

    private fun extractCompilationErrors(errorOutput: String): String {
        if (errorOutput.contains("error:") || errorOutput.contains("FAILURE")) {
            val lines = errorOutput.lines()
            val errorLines = lines.filter { it.contains("error:") || it.contains("e:") }
            return if (errorLines.isNotEmpty()) {
                errorLines.take(MAX_ERROR_LINES).joinToString("; ")
            } else {
                "Build compilation failed"
            }
        }
        return "Build failed"
    }
    
    private fun executeDetekt(): DetektResult {
        logger.debug("Executing Detekt")
        return try {
            val gradleWrapper = File(projectDir, "gradlew")
            logger.debug("Gradle wrapper path: ${gradleWrapper.absolutePath}")
            
            if (!gradleWrapper.exists()) {
                logger.warn("Gradle wrapper not found, skipping Detekt")
                return DetektResult(success = true, issueCount = 0, errorMessage = null)
            }
            
            logger.debug("Starting Detekt with timeout: ${timeoutSeconds}s")
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
                logger.error("Detekt timed out after ${timeoutSeconds}s")
                process.destroyForcibly()
                return DetektResult(success = false, issueCount = 0, errorMessage = "Detekt timed out after ${timeoutSeconds}s")
            }
            
            reader.use { it.lines().forEach { output.appendLine(it) } }
            errorReader.use { it.lines().forEach { errorOutput.appendLine(it) } }
            
            val exitCode = process.exitValue()
            logger.debug("Detekt exit code: $exitCode")
            
            if (exitCode == 0) {
                logger.info("Detekt completed successfully with no issues")
                DetektResult(success = true, issueCount = 0, errorMessage = null)
            } else {
                val issueCount = parseDetektOutput(output.toString())
                logger.warn("Detekt found $issueCount issues")
                DetektResult(success = false, issueCount = issueCount, errorMessage = "Detekt found $issueCount issues")
            }
        } catch (e: Exception) {
            logger.error("Failed to execute Detekt: ${e.message}", e)
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
                    (covered / total) * PERCENTAGE_MULTIPLIER
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
                    (covered / total) * PERCENTAGE_MULTIPLIER
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
