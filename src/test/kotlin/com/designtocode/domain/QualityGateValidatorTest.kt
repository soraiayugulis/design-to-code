package com.designtocode.domain

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
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

        // Then: LINE coverage = 90 / (90 + 10) = 90%
        assertEquals(90.0, result.coveragePercentage, 0.01, "LINE coverage should be 90%")
        assertTrue(result.passed, "90% coverage meets the default 90% threshold")
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

        // Then: JaCoCo fallback LINE coverage = 90 / (90 + 10) = 90%
        assertEquals(90.0, result.coveragePercentage, 0.01, "JaCoCo LINE coverage should be 90%")
    }

    @Test
    fun shouldSupportMultipleCoverageTypes() {
        // Given
        val projectDir = tempDir
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText("#!/bin/bash\nexit 0")
        gradlew.setExecutable(true)

        val reportDir = File(projectDir, "build/reports/kover/xml")
        reportDir.mkdirs()
        File(reportDir, "report.xml").writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <report>
                <counter type="LINE" missed="50" covered="50"/>
                <counter type="BRANCH" missed="5" covered="45"/>
            </report>
        """.trimIndent())

        // When: BRANCH coverage = 45 / (45 + 5) = 90%, while LINE is only 50%
        val result = QualityGateValidator(projectDir, coverageType = CoverageType.BRANCH).validate()

        // Then
        assertEquals(90.0, result.coveragePercentage, 0.01, "BRANCH coverage should be 90%")
    }

    @Test
    fun shouldPassWhenCoverageMeetsThresholdAndFailWhenBelow() {
        // Given: deterministic 90% coverage report
        val projectDir = tempDir
        File(projectDir, "gradlew").apply {
            writeText("#!/bin/bash\nexit 0")
            setExecutable(true)
        }
        val reportDir = File(projectDir, "build/reports/kover/xml")
        reportDir.mkdirs()
        File(reportDir, "report.xml").writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <report>
                <counter type="LINE" missed="10" covered="90"/>
            </report>
        """.trimIndent())

        // When / Then
        assertTrue(
            QualityGateValidator(projectDir, coverageThreshold = 50.0).validate().passed,
            "90% coverage must pass a 50% threshold"
        )
        val failing = QualityGateValidator(projectDir, coverageThreshold = 95.0).validate()
        assertFalse(failing.passed, "90% coverage must fail a 95% threshold")
        assertTrue(failing.errorMessage?.contains("below threshold") == true)
    }

    @Test
    fun shouldFailGateWhenNoCoverageReportExists() {
        // Given: mock gradlew succeeds for every task, but no coverage report is generated
        val qualityGateValidator = QualityGateValidator(tempDir)

        // When
        val result = qualityGateValidator.validate()

        // Then
        assertTrue(result.buildSuccess, "Build should succeed")
        assertFalse(result.passed, "Gate must fail when no coverage report exists")
        assertEquals(0.0, result.coveragePercentage)
        assertTrue(result.errorMessage?.contains("below threshold") == true)
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
        assertFalse(result.passed, "Quality gate should fail when Detekt finds issues")
        assertEquals(5, result.lintIssues, "Should parse the issue count from Detekt output")
        assertTrue(result.errorMessage?.contains("Detekt") == true, "Error should mention Detekt")
    }

    @Test
    fun shouldParseRealDetektWeightedIssuesOutput() {
        // Given: real Detekt 1.23 output format
        val gradlew = File(tempDir, "gradlew")
        gradlew.writeText(
            "#!/bin/bash\nif [ \"$1\" = \"detekt\" ]; then\n  echo 'Analysis failed with 7 weighted issues.'\n  exit 1\nfi\nexit 0"
        )
        gradlew.setExecutable(true)

        // When
        val result = QualityGateValidator(tempDir).validate()

        // Then
        assertFalse(result.passed)
        assertEquals(7, result.lintIssues, "Should parse the weighted issues count from real Detekt output")
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
