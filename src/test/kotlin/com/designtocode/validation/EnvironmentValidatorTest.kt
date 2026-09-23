package com.designtocode.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvironmentValidatorTest {

    private val validator = EnvironmentValidator()

    @Test
    fun shouldValidateJavaVersion() {
        // Given: expected major version matches the JVM running the tests
        val expectedMajor = System.getProperty("java.version").substringBefore(".")

        // When
        val result = validator.validateJavaVersion()

        // Then
        assertNotNull(result)
        assertEquals(expectedMajor, result.version)
    }

    @Test
    fun shouldValidateGradle() {
        // When
        val result = validator.validateGradle()

        // Then
        assertNotNull(result)
        assertNotNull(result.version)
        assertTrue(result.version.isNotEmpty())
    }

    @Test
    fun shouldValidateDocker() {
        // When
        val result = validator.validateDocker()

        // Then
        assertNotNull(result)
        assertNotNull(result.version)
    }

    @Test
    fun shouldValidateGit() {
        // When
        val result = validator.validateGit()

        // Then
        assertNotNull(result)
        assertNotNull(result.version)
        assertTrue(result.version.isNotEmpty())
    }

    @Test
    fun shouldValidateAllEnvironments() {
        // When
        val result = validator.validateAll()

        // Then
        assertNotNull(result)
        assertNotNull(result.details)
        assertTrue(result.details.containsKey("java"))
        assertTrue(result.details.containsKey("gradle"))
        assertTrue(result.details.containsKey("docker"))
        assertTrue(result.details.containsKey("git"))
    }

    @Test
    fun shouldMarkJavaValidOnlyWhenMajorVersionIs21() {
        // When
        val result = validator.validateJavaVersion()

        // Then: validity must be consistent with the extracted version
        assertEquals(result.version.startsWith("21"), result.isValid)
    }

    @Test
    fun shouldExtractJavaVersionFromShortVersionString() {
        // Given: some JVMs report a bare major version
        val output = """openjdk version "21" 2024-01-16"""

        // When
        val method = validator.javaClass.getDeclaredMethod("extractJavaVersion", String::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("21", version)
    }

    @Test
    fun shouldExtractJavaVersionFromTwoPartVersionString() {
        // Given
        val output = """openjdk version "21.0" 2024-01-16"""

        // When
        val method = validator.javaClass.getDeclaredMethod("extractJavaVersion", String::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("21", version)
    }

    @Test
    fun shouldExtractJavaVersionFromOutput() {
        // Given
        val output = """openjdk version "21.0.1" 2023-10-17"""
        
        // When - Using reflection to test private method
        val method = validator.javaClass.getDeclaredMethod("extractJavaVersion", String::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("21", version)
    }

    @Test
    fun shouldExtractGradleVersionFromOutput() {
        // Given
        val output = listOf(
            "Gradle 9.4.1",
            "Build time:   2024-01-15 10:00:00 UTC"
        )
        
        // When
        val method = validator.javaClass.getDeclaredMethod("extractGradleVersion", List::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("9.4.1", version)
    }

    @Test
    fun shouldExtractDockerVersionFromOutput() {
        // Given
        val output = listOf(
            "Server: Docker/28.5.2",
            "Version: 28.5.2"
        )
        
        // When
        val method = validator.javaClass.getDeclaredMethod("extractDockerVersion", List::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("28.5.2", version)
    }

    @Test
    fun shouldExtractGitVersionFromOutput() {
        // Given
        val output = "git version 2.43.0"
        
        // When
        val method = validator.javaClass.getDeclaredMethod("extractGitVersion", String::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("2.43.0", version)
    }

    @Test
    fun shouldHandleNullJavaOutput() {
        // When
        val method = validator.javaClass.getDeclaredMethod("extractJavaVersion", String::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, null) as String

        // Then
        assertEquals("unknown", version)
    }

    @Test
    fun shouldHandleNullGitOutput() {
        // When
        val method = validator.javaClass.getDeclaredMethod("extractGitVersion", String::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, null) as String

        // Then
        assertEquals("unknown", version)
    }

    @Test
    fun shouldHandleEmptyGradleOutput() {
        // Given
        val output = emptyList<String>()
        
        // When
        val method = validator.javaClass.getDeclaredMethod("extractGradleVersion", List::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("unknown", version)
    }

    @Test
    fun shouldHandleEmptyDockerOutput() {
        // Given
        val output = emptyList<String>()
        
        // When
        val method = validator.javaClass.getDeclaredMethod("extractDockerVersion", List::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("unknown", version)
    }

    @Test
    fun shouldHandleMalformedJavaOutput() {
        // Given
        val output = "invalid output"
        
        // When
        val method = validator.javaClass.getDeclaredMethod("extractJavaVersion", String::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("unknown", version)
    }

    @Test
    fun shouldHandleMalformedGitOutput() {
        // Given
        val output = "git"
        
        // When
        val method = validator.javaClass.getDeclaredMethod("extractGitVersion", String::class.java)
        method.isAccessible = true
        val version = method.invoke(validator, output) as String

        // Then
        assertEquals("unknown", version)
    }
}
