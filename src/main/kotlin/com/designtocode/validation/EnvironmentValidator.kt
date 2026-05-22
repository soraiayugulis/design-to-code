package com.designtocode.validation

import java.io.BufferedReader
import java.io.InputStreamReader

data class ValidationResult(
    val isValid: Boolean,
    val version: String,
    val message: String = ""
)

data class EnvironmentValidationResult(
    val allValid: Boolean,
    val javaValid: Boolean,
    val gradleValid: Boolean,
    val dockerValid: Boolean,
    val gitValid: Boolean,
    val details: Map<String, ValidationResult>
)

class EnvironmentValidator {

    fun validateJavaVersion(): ValidationResult {
        return try {
            val process = ProcessBuilder("java", "-version").start()
            val reader = BufferedReader(InputStreamReader(process.errorStream))
            val output = reader.readLine()
            process.waitFor()
            
            val version = extractJavaVersion(output)
            val isValid = version.startsWith("21")
            
            ValidationResult(
                isValid = isValid,
                version = version,
                message = if (isValid) "Java 21 is installed" else "Java version must be 21, found: $version"
            )
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                version = "unknown",
                message = "Failed to validate Java: ${e.message}"
            )
        }
    }

    fun validateGradle(): ValidationResult {
        return try {
            val process = ProcessBuilder("gradle", "--version").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.useLines { lines -> lines.toList() }
            process.waitFor()
            
            val version = extractGradleVersion(output)
            val isValid = version.isNotEmpty()
            
            ValidationResult(
                isValid = isValid,
                version = version,
                message = if (isValid) "Gradle $version is installed" else "Gradle not found"
            )
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                version = "unknown",
                message = "Failed to validate Gradle: ${e.message}"
            )
        }
    }

    fun validateDocker(): ValidationResult {
        return try {
            val process = ProcessBuilder("docker", "info").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.useLines { lines -> lines.toList() }
            val exitCode = process.waitFor()
            
            val isValid = exitCode == 0
            val version = extractDockerVersion(output)
            
            ValidationResult(
                isValid = isValid,
                version = version,
                message = if (isValid) "Docker daemon is running" else "Docker daemon is not running"
            )
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                version = "unknown",
                message = "Failed to validate Docker: ${e.message}"
            )
        }
    }

    fun validateGit(): ValidationResult {
        return try {
            val process = ProcessBuilder("git", "--version").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            
            val version = extractGitVersion(output)
            val isValid = version.isNotEmpty()
            
            ValidationResult(
                isValid = isValid,
                version = version,
                message = if (isValid) "Git $version is configured" else "Git not found"
            )
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                version = "unknown",
                message = "Failed to validate Git: ${e.message}"
            )
        }
    }

    fun validateAll(): EnvironmentValidationResult {
        val javaResult = validateJavaVersion()
        val gradleResult = validateGradle()
        val dockerResult = validateDocker()
        val gitResult = validateGit()
        
        val allValid = javaResult.isValid && gradleResult.isValid && dockerResult.isValid && gitResult.isValid
        
        return EnvironmentValidationResult(
            allValid = allValid,
            javaValid = javaResult.isValid,
            gradleValid = gradleResult.isValid,
            dockerValid = dockerResult.isValid,
            gitValid = gitResult.isValid,
            details = mapOf(
                "java" to javaResult,
                "gradle" to gradleResult,
                "docker" to dockerResult,
                "git" to gitResult
            )
        )
    }

    private fun extractJavaVersion(output: String?): String {
        if (output == null) return "unknown"
        val regex = Regex("""version "(\d+\.\d+\.\d+)"""")
        val match = regex.find(output)
        return match?.groupValues?.get(1)?.split(".")?.get(0) ?: "unknown"
    }

    private fun extractGradleVersion(output: List<String>): String {
        val versionLine = output.find { it.contains("Gradle") }
        return versionLine?.split(" ")?.get(1) ?: "unknown"
    }

    private fun extractDockerVersion(output: List<String>): String {
        val versionLine = output.find { it.contains("Version:") }
        return versionLine?.split(":")?.get(1)?.trim() ?: "unknown"
    }

    private fun extractGitVersion(output: String?): String {
        if (output == null) return "unknown"
        val parts = output.split(" ")
        return if (parts.size >= 3) parts[2] else "unknown"
    }
}
