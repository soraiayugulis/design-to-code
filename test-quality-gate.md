# Phase 4: Quality Gate Validation - TDD Scenarios

## Test Scenarios

### Test: Execute Gradle build successfully
**Given**: Valid Kotlin source files with no compilation errors
**When**: CommandExecutor.execute() runs `./gradlew clean build`
**Then**: Build should complete with exit code 0
**And**: Build output should indicate BUILD SUCCESSFUL

```kotlin
@Test
fun `should execute Gradle build successfully`() {
    // Setup valid Kotlin files
    setupValidKotlinProject(tempDir)
    
    val result = CommandExecutor.execute(
        listOf("./gradlew", "clean", "build"),
        tempDir
    )
    
    assertEquals(0, result.exitCode)
}
```

### Test: Execute Detekt linting
**Given**: Kotlin source files with potential style violations
**When**: CommandExecutor.execute() runs `./gradlew detekt`
**Then**: Detekt should analyze code and report violations
**And**: Exit code should be 0 if no violations, non-zero if violations exist

```kotlin
@Test
fun `should execute Detekt linting`() {
    setupValidKotlinProject(tempDir)
    
    val result = CommandExecutor.execute(
        listOf("./gradlew", "detekt"),
        tempDir
    )
    
    // Should succeed if code follows style guide
    assertEquals(0, result.exitCode)
}
```

### Test: Execute Kover coverage verification
**Given**: Kotlin source files with corresponding test files
**When**: CommandExecutor.execute() runs `./gradlew koverVerify`
**Then**: Kover should generate coverage report
**And**: Verification should pass if coverage meets threshold

```kotlin
@Test
fun `should execute Kover coverage verification`() {
    setupProjectWithTests(tempDir)
    
    val result = CommandExecutor.execute(
        listOf("./gradlew", "koverVerify"),
        tempDir
    )
    
    assertEquals(0, result.exitCode)
}
```

### Test: Parse coverage report for 100% threshold
**Given**: Kover coverage report XML file
**When**: CoverageParser.parseReport() is called
**Then**: Should return coverage percentage
**And**: Should correctly identify if 100% threshold is met

```kotlin
@Test
fun `should parse coverage report for 100% threshold`() {
    val coverageReport = File(tempDir, "build/reports/kover/report.xml")
    coverageReport.writeText("""
        <?xml version="1.0" encoding="UTF-8"?>
        <report>
            <counter type="INSTRUCTION" covered="100" missed="0"/>
            <counter type="LINE" covered="50" missed="0"/>
        </report>
    """.trimIndent())
    
    val coverage = CoverageParser.parseReport(coverageReport)
    
    assertEquals(100.0, coverage.percentage, 0.01)
}
```

### Test: Fail pipeline when coverage < 100%
**Given**: Coverage report showing 85% coverage
**When**: QualityGateValidator.validate() is called with 100% threshold
**Then**: Should throw CoverageGateException
**And**: Exception should contain actual coverage percentage

```kotlin
@Test
fun `should fail pipeline when coverage below 100%`() {
    val coverageReport = File(tempDir, "build/reports/kover/report.xml")
    coverageReport.writeText("""
        <?xml version="1.0" encoding="UTF-8"?>
        <report>
            <counter type="INSTRUCTION" covered="85" missed="15"/>
        </report>
    """.trimIndent())
    
    val exception = assertThrows<CoverageGateException> {
        QualityGateValidator.validate(coverageReport, threshold = 100.0)
    }
    
    assertTrue(exception.message!!.contains("85"))
    assertTrue(exception.message!!.contains("100"))
}
```

### Test: Fail pipeline when compilation fails
**Given**: Kotlin source files with compilation errors
**When**: CommandExecutor.execute() runs `./gradlew compileKotlin`
**Then**: Build should fail with non-zero exit code
**And**: Error should indicate compilation failure

```kotlin
@Test
fun `should fail pipeline when compilation fails`() {
    val sourceFile = File(tempDir, "src/main/kotlin/Broken.kt")
    sourceFile.writeText("""
        class Broken {
            fun invalidMethod( {
        }
    """.trimIndent())
    
    val exception = assertThrows<RuntimeException> {
        CommandExecutor.execute(
            listOf("./gradlew", "compileKotlin"),
            tempDir
        )
    }
    
    assertTrue(exception.message!!.contains("compilation") || exception.message!!.contains("error"))
}
```

### Test: Validate Detekt violations fail quality gate
**Given**: Kotlin code with Detekt violations
**When**: CommandExecutor.execute() runs `./gradlew detekt`
**Then**: Should fail if violations exceed threshold
**And**: Should report violation details

```kotlin
@Test
fun `should validate Detekt violations fail quality gate`() {
    val sourceFile = File(tempDir, "src/main/kotlin/Violation.kt")
    sourceFile.writeText("""
        class Violation {
            val badName = "violates naming convention"
        }
    """.trimIndent())
    
    val exception = assertThrows<RuntimeException> {
        CommandExecutor.execute(
            listOf("./gradlew", "detekt"),
            tempDir
        )
    }
    
    assertTrue(exception.message!!.contains("detekt") || exception.message!!.contains("violation"))
}
```

### Test: Execute Testcontainers integration tests
**Given**: Test files using Testcontainers
**When**: CommandExecutor.execute() runs `./gradlew test`
**Then**: Tests should run with real containers
**And**: All tests should pass

```kotlin
@Test
fun `should execute Testcontainers integration tests`() {
    setupProjectWithTestcontainers(tempDir)
    
    val result = CommandExecutor.execute(
        listOf("./gradlew", "test"),
        tempDir
    )
    
    assertEquals(0, result.exitCode)
}
```

### Test: Validate coverage only on generated classes
**Given**: Coverage report for entire project
**When**: QualityGateValidator.validateGeneratedClasses() is called
**Then**: Should only check coverage on AI-generated classes
**And**: Should exclude existing manual code from coverage requirement

```kotlin
@Test
fun `should validate coverage only on generated classes`() {
    val coverageReport = File(tempDir, "build/reports/kover/report.xml")
    coverageReport.writeText("""
        <?xml version="1.0" encoding="UTF-8"?>
        <report>
            <package name="com.example.generated">
                <counter type="LINE" covered="100" missed="0"/>
            </package>
            <package name="com.example.manual">
                <counter type="LINE" covered="50" missed="50"/>
            </package>
        </report>
    """.trimIndent())
    
    val coverage = QualityGateValidator.validateGeneratedClasses(
        coverageReport,
        generatedPackage = "com.example.generated",
        threshold = 100.0
    )
    
    assertEquals(100.0, coverage, 0.01)
}
```

### Test: Handle missing coverage report file
**Given**: Kover report file does not exist
**When**: CoverageParser.parseReport() is called
**Then**: Should throw FileNotFoundException
**And**: Error should indicate report not found

```kotlin
@Test
fun `should handle missing coverage report file`() {
    val missingReport = File(tempDir, "build/reports/kover/report.xml")
    
    val exception = assertThrows<FileNotFoundException> {
        CoverageParser.parseReport(missingReport)
    }
    
    assertTrue(exception.message!!.contains("report") || exception.message!!.contains("not found"))
}
```

### Test: Validate all quality gates in sequence
**Given**: Valid project with all quality checks passing
**When**: Pipeline executes complete quality gate sequence
**Then**: All gates should pass in order: compile, lint, test, coverage
**And**: Pipeline should proceed to PR creation

```kotlin
@Test
fun `should validate all quality gates in sequence`() {
    setupValidProjectWithFullCoverage(tempDir)
    
    val validator = QualityGateValidator(tempDir)
    
    // Execute all gates in sequence
    validator.validateCompilation()
    validator.validateLinting()
    validator.validateTests()
    validator.validateCoverage(threshold = 100.0)
    
    // If no exceptions thrown, all gates passed
    assertTrue(true)
}
```

### Test: Fail fast on first quality gate failure
**Given**: Project with compilation errors
**When**: Pipeline executes quality gate sequence
**Then**: Should fail at compilation gate
**And**: Should not execute subsequent gates

```kotlin
@Test
fun `should fail fast on first quality gate failure`() {
    setupProjectWithCompilationErrors(tempDir)
    
    val validator = QualityGateValidator(tempDir)
    
    val exception = assertThrows<QualityGateException> {
        validator.validateCompilation()
        validator.validateLinting() // Should not reach here
    }
    
    assertTrue(exception.stage == QualityGateStage.COMPILATION)
}
```
