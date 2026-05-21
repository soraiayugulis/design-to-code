package com.designToCode.infra

import java.io.File

enum class QualityGateStage {
    COMPILATION,
    LINTING,
    TESTS,
    COVERAGE
}

data class QualityGateResult(
    val stage: QualityGateStage,
    val success: Boolean,
    val errorMessage: String? = null,
    val coveragePercentage: Double? = null
)

class QualityGateException(
    val stage: QualityGateStage,
    message: String
) : RuntimeException(message)

object QualityGateValidator {
    fun validateAll(workspace: File, coverageThreshold: Double = 100.0): List<QualityGateResult> {
        val results = mutableListOf<QualityGateResult>()

        try {
            results.add(validateCompilation(workspace))
            results.add(validateLinting(workspace))
            results.add(validateTests(workspace))
            results.add(validateCoverage(workspace, coverageThreshold))
        } catch (e: QualityGateException) {
            // Fail fast on first error
            results.add(QualityGateResult(e.stage, false, e.message))
            return results
        }

        return results
    }

    fun validateCompilation(workspace: File): QualityGateResult {
        return try {
            val process = ProcessBuilder("./gradlew", "compileKotlin")
                .directory(workspace)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                QualityGateResult(QualityGateStage.COMPILATION, true)
            } else {
                val output = process.inputStream.bufferedReader().readText()
                QualityGateResult(QualityGateStage.COMPILATION, false, "Compilation failed: $output")
            }
        } catch (e: Exception) {
            QualityGateResult(QualityGateStage.COMPILATION, false, "Error running compilation: ${e.message}")
        }
    }

    fun validateLinting(workspace: File): QualityGateResult {
        return try {
            val process = ProcessBuilder("./gradlew", "detekt")
                .directory(workspace)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                QualityGateResult(QualityGateStage.LINTING, true)
            } else {
                val output = process.inputStream.bufferedReader().readText()
                QualityGateResult(QualityGateStage.LINTING, false, "Linting failed: $output")
            }
        } catch (e: Exception) {
            QualityGateResult(QualityGateStage.LINTING, false, "Error running linting: ${e.message}")
        }
    }

    fun validateTests(workspace: File): QualityGateResult {
        return try {
            val process = ProcessBuilder("./gradlew", "test")
                .directory(workspace)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                QualityGateResult(QualityGateStage.TESTS, true)
            } else {
                val output = process.inputStream.bufferedReader().readText()
                QualityGateResult(QualityGateStage.TESTS, false, "Tests failed: $output")
            }
        } catch (e: Exception) {
            QualityGateResult(QualityGateStage.TESTS, false, "Error running tests: ${e.message}")
        }
    }

    fun validateCoverage(workspace: File, threshold: Double): QualityGateResult {
        return try {
            val process = ProcessBuilder("./gradlew", "koverVerify")
                .directory(workspace)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                QualityGateResult(QualityGateStage.COVERAGE, true, coveragePercentage = threshold)
            } else {
                val output = process.inputStream.bufferedReader().readText()
                QualityGateResult(QualityGateStage.COVERAGE, false, "Coverage threshold not met: $output")
            }
        } catch (e: Exception) {
            QualityGateResult(QualityGateStage.COVERAGE, false, "Error checking coverage: ${e.message}")
        }
    }
}
