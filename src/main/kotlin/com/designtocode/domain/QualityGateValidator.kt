package com.designtocode.domain

import com.designtocode.domain.model.QualityGateResult
import com.designtocode.domain.port.QualityGatePort
import org.slf4j.LoggerFactory
import java.io.File

enum class CoverageType {
    LINE,
    BRANCH,
    INSTRUCTION
}

class QualityGateValidator(
    private val projectDir: File,
    private val coverageThreshold: Double = 90.0,
    private val timeoutSeconds: Long = 900L, // 15 minutes default
    private val coverageType: CoverageType = CoverageType.LINE,
    private val gradleTasks: List<String> = listOf("clean", "build")
) : QualityGatePort {
    private val logger = LoggerFactory.getLogger(QualityGateValidator::class.java)

    override fun validate(): QualityGateResult {
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

        // Generate the coverage report (no-op failure if the project lacks the task)
        generateCoverageReport()

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
    
    private fun runProcess(command: List<String>): ProcessOutput {
        return ProcessRunner(projectDir).run(command, timeoutSeconds)
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
            val result = runProcess(listOf(gradleWrapper.absolutePath) + gradleTasks + "--no-daemon")

            if (result.timedOut) {
                logger.error("Gradle build timed out after ${timeoutSeconds}s")
                return BuildResult(false, "Gradle build timed out after ${timeoutSeconds}s")
            }

            logger.debug("Gradle build exit code: ${result.exitCode}")

            if (result.exitCode == 0) {
                logger.info("Gradle build completed successfully")
                BuildResult(true, null)
            } else {
                val errorMessage = extractCompilationErrors(result.output)
                logger.error("Gradle build failed with exit code ${result.exitCode}: $errorMessage")
                BuildResult(false, "Gradle build failed with exit code ${result.exitCode}. $errorMessage")
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
            val result = runProcess(listOf(gradleWrapper.absolutePath, "detekt", "--no-daemon"))

            if (result.timedOut) {
                logger.error("Detekt timed out after ${timeoutSeconds}s")
                return DetektResult(success = false, issueCount = 0, errorMessage = "Detekt timed out after ${timeoutSeconds}s")
            }

            logger.debug("Detekt exit code: ${result.exitCode}")

            if (result.exitCode == 0) {
                logger.info("Detekt completed successfully with no issues")
                DetektResult(success = true, issueCount = 0, errorMessage = null)
            } else {
                val issueCount = parseDetektOutput(result.output)
                logger.warn("Detekt found $issueCount issues")
                DetektResult(success = false, issueCount = issueCount, errorMessage = "Detekt found $issueCount issues")
            }
        } catch (e: Exception) {
            logger.error("Failed to execute Detekt: ${e.message}", e)
            DetektResult(success = false, issueCount = 0, errorMessage = "Failed to execute Detekt: ${e.message}")
        }
    }

    private fun generateCoverageReport() {
        logger.debug("Generating coverage report")
        try {
            val gradleWrapper = File(projectDir, "gradlew")
            if (!gradleWrapper.exists()) return

            val result = runProcess(listOf(gradleWrapper.absolutePath, "koverXmlReport", "--no-daemon"))
            if (result.exitCode != 0) {
                logger.debug("koverXmlReport not available or failed (exit ${result.exitCode}), falling back to existing reports")
            }
        } catch (e: Exception) {
            logger.debug("Could not run koverXmlReport: ${e.message}")
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
