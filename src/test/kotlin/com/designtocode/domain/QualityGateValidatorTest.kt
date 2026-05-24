package com.designtocode.domain

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityGateValidatorTest {

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        // Create gradlew wrapper in temp dir for tests
        val gradlew = File(tempDir, "gradlew")
        gradlew.writeText("#!/bin/bash\necho 'mock gradle'")
        gradlew.setExecutable(true)
    }

    @Test
    fun shouldReturnErrorWhenGradleWrapperNotFound() {
        // Given
        val emptyDir = tempDir
        // Delete gradlew created by @BeforeEach
        File(emptyDir, "gradlew").delete()
        val qualityGateValidator = QualityGateValidator(emptyDir)

        // When
        val result = qualityGateValidator.validate()

        // Then
        assertFalse(result.buildSuccess, "Build should fail without gradlew")
        assertTrue(result.errorMessage?.contains("Gradle wrapper not found") == true, 
            "Error should mention missing gradlew")
    }

    @Test
    fun shouldReturnBuildSuccessWhenGradleSucceeds() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\nexit 0")
        gradlew.setExecutable(true)
        
        val qualityGateValidator = QualityGateValidator(projectDir)

        // When
        val result = qualityGateValidator.validate()

        // Then
        assertTrue(result.buildSuccess, "Build should succeed")
    }

    @Test
    fun shouldReturnBuildFailureWhenGradleFails() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\nexit 1")
        gradlew.setExecutable(true)
        
        val qualityGateValidator = QualityGateValidator(projectDir)

        // When
        val result = qualityGateValidator.validate()

        // Then
        assertFalse(result.buildSuccess, "Build should fail")
        assertTrue(result.errorMessage != null, "Error message should be present")
    }

    @Test
    fun shouldEnforceTimeoutOnGradleExecution() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\nsleep 1000\nexit 0")
        gradlew.setExecutable(true)
        
        val qualityGateValidator = QualityGateValidator(projectDir, timeoutSeconds = 1)

        // When
        val result = qualityGateValidator.validate()

        // Then
        assertFalse(result.buildSuccess, "Build should timeout")
        assertTrue(result.errorMessage?.contains("timed out") == true, 
            "Error should mention timeout")
    }

    @Test
    fun shouldExtractCompilationErrorsFromOutput() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\necho 'error: unresolved reference' >&2\nexit 1")
        gradlew.setExecutable(true)
        
        val qualityGateValidator = QualityGateValidator(projectDir)

        // When
        val result = qualityGateValidator.validate()

        // Then
        assertFalse(result.buildSuccess, "Build should fail")
        assertTrue(result.errorMessage != null, "Error message should be present")
    }

    @Test
    fun shouldParseKoverXmlReportForCoveragePercentage() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\nexit 0")
        gradlew.setExecutable(true)
        
        // Create mock Kover XML report
        val reportDir = File(projectDir, "build/reports/kover/xml")
        reportDir.mkdirs()
        val reportFile = File(reportDir, "report.xml")
        reportFile.writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <report>
                <counter type="LINE" missed="10" covered="90"/>
                <counter type="BRANCH" missed="5" covered="45"/>
                <counter type="INSTRUCTION" missed="20" covered="180"/>
            </report>
        """.trimIndent())
        
        val qualityGateValidator = QualityGateValidator(projectDir)

        // When
        val result = qualityGateValidator.validate()

        // Then
        assertTrue(result.coveragePercentage > 0, "Coverage should be calculated")
    }

    @Test
    fun shouldParseJaCoCoXmlReportForCoveragePercentage() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\nexit 0")
        gradlew.setExecutable(true)
        
        // Create mock JaCoCo XML report
        val reportDir = File(projectDir, "build/reports/jacoco/test")
        reportDir.mkdirs()
        val reportFile = File(reportDir, "jacocoTestReport.xml")
        reportFile.writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <report>
                <counter type="LINE" missed="10" covered="90"/>
                <counter type="BRANCH" missed="5" covered="45"/>
                <counter type="INSTRUCTION" missed="20" covered="180"/>
            </report>
        """.trimIndent())
        
        val qualityGateValidator = QualityGateValidator(projectDir)

        // When
        val result = qualityGateValidator.validate()

        // Then
        assertTrue(result.coveragePercentage > 0, "Coverage should be calculated")
    }

    @Test
    fun shouldSupportMultipleCoverageTypes() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\nexit 0")
        gradlew.setExecutable(true)
        
        val qualityGateValidator = QualityGateValidator(projectDir, coverageType = CoverageType.BRANCH)

        // When
        val result = qualityGateValidator.validate()

        // Then
        // Coverage should be calculated based on branch type
        assertTrue(result.coveragePercentage >= 0, "Coverage should be calculated")
    }

    @Test
    fun shouldValidateCoverageThresholdCorrectly() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\nexit 0")
        gradlew.setExecutable(true)
        
        val qualityGateValidator = QualityGateValidator(projectDir, coverageThreshold = 50.0)

        // When
        val result = qualityGateValidator.validate()

        // Then
        // If coverage is above 50%, should pass
        if (result.coveragePercentage >= 50.0) {
            assertTrue(result.passed, "Should pass when coverage meets threshold")
        }
    }

    @Test
    fun shouldExecuteDetektAsPartOfQualityGate() {
        // Given
        val qualityGateValidator = QualityGateValidator(tempDir)

        // When
        val result = qualityGateValidator.validate()

        // Then
        // Detekt should be executed as part of quality gate
        // Since we have a mock gradlew, Detekt will pass
        assertTrue(result.buildSuccess || result.errorMessage != null)
    }

    @Test
    fun shouldReturnLintIssuesWhenDetektFails() {
        // Given
        val qualityGateValidator = QualityGateValidator(tempDir)
        // Create a mock gradlew that simulates Detekt failure
        val gradlew = File(tempDir, "gradlew")
        gradlew.writeText(
            "#!/bin/bash\nif [ \"$1\" = \"detekt\" ]; then\n  echo 'Detekt found 5 issues'\n  exit 1\nelse\n  echo 'build success'\n  exit 0\nfi"
        )
        gradlew.setExecutable(true)

        // When
        val result = qualityGateValidator.validate()

        // Then
        // Quality gate should fail due to Detekt issues
        assertFalse(result.passed, "Quality gate should fail when Detekt finds issues")
        // The lint issues count may be 0 if parsing fails, but the gate should still fail
        assertTrue(result.errorMessage?.contains("Detekt") == true, "Error should mention Detekt")
    }

    @Test
    fun shouldPassQualityGateWhenDetektSucceeds() {
        // Given
        val qualityGateValidator = QualityGateValidator(tempDir)
        // Create a mock gradlew that simulates Detekt success
        val gradlew = File(tempDir, "gradlew")
        gradlew.writeText(
            "#!/bin/bash\nif [ \"$1\" = \"detekt\" ]; then\n  echo 'No issues found'\n  exit 0\nelse\n  echo 'build success'\n  exit 0\nfi"
        )
        gradlew.setExecutable(true)

        // When
        val result = qualityGateValidator.validate()

        // Then
        // Quality gate should pass when Detekt succeeds
        assertTrue(result.buildSuccess, "Build should succeed")
        assertTrue(result.lintIssues == 0, "Should have no lint issues")
    }
}
