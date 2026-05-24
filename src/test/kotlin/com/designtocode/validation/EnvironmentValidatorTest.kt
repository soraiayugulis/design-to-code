package com.designtocode.validation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertTrue

@DisplayName("Environment Validator Tests")
class EnvironmentValidatorTest {

    @Test
    @DisplayName("Should validate Java version is 21")
    fun `should validate java version is 21`() {
        val validator = EnvironmentValidator()
        val result = validator.validateJavaVersion()
        assertTrue(result.isValid, "Java version should be valid")
        assertTrue(result.version.startsWith("21"), "Java version should start with 21, found: ${result.version}")
    }

    @Test
    @DisplayName("Should validate Gradle is installed")
    fun `should validate gradle is installed`() {
        val validator = EnvironmentValidator()
        val result = validator.validateGradle()
        assertTrue(result.isValid, "Gradle should be installed")
        assertTrue(result.version.isNotEmpty(), "Gradle version should not be empty")
    }

    @Test
    @DisplayName("Should validate Docker daemon is running")
    fun `should validate docker daemon is running`() {
        val validator = EnvironmentValidator()
        val result = validator.validateDocker()
        assertTrue(result.isValid, "Docker daemon should be running")
        assertTrue(result.version.isNotEmpty(), "Docker version should not be empty")
    }

    @Test
    @DisplayName("Should validate Git is configured")
    fun `should validate git is configured`() {
        val validator = EnvironmentValidator()
        val result = validator.validateGit()
        assertTrue(result.isValid, "Git should be configured")
        assertTrue(result.version.isNotEmpty(), "Git version should not be empty")
    }

    @Test
    @DisplayName("Should validate all environment requirements")
    fun `should validate all environment requirements`() {
        val validator = EnvironmentValidator()
        val result = validator.validateAll()
        assertTrue(result.allValid, "All environment requirements should be valid")
        assertTrue(result.javaValid, "Java should be valid")
        assertTrue(result.gradleValid, "Gradle should be valid")
        assertTrue(result.dockerValid, "Docker should be valid")
        assertTrue(result.gitValid, "Git should be valid")
    }
}
