# Phase 3: AI Generation Integration - TDD Scenarios

## Test Scenarios

### Test: Invoke AI Agent Port successfully
**Given**: AI Agent Port implementation configured (OllamaAdapter by default)
**And**: Valid prompt constructed
**When**: AiAgentPort.generate() is called
**Then**: Request should execute successfully
**And**: Generated files should be present in workspace

```kotlin
@Test
fun `should invoke AI Agent Port successfully`() {
    val prompt = "Generate Kotlin code for User API"
    val workspace = tempDir
    val aiAgentPort: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
    
    val result = aiAgentPort.generate(prompt, workspace)
    
    assertTrue(result.success)
    assertTrue(File(workspace, "src/main/kotlin/UserController.kt").exists())
}
```

### Test: Switch AI Agent implementation at runtime
**Given**: Multiple AI Agent implementations available
**When**: Configuration specifies different adapter
**Then**: System should use configured implementation without code changes

```kotlin
@Test
fun `should switch AI Agent implementation at runtime`() {
    val prompt = "Generate Kotlin code"
    val workspace = tempDir
    
    // Test with OllamaAdapter
    val ollamaAdapter: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
    val result1 = ollamaAdapter.generate(prompt, workspace)
    assertTrue(result1.success)
    
    // Test with ClaudeAdapter (configuration change only)
    val claudeAdapter: AiAgentPort = ClaudeAdapter(apiKey = "test-key", model = "claude-3-5-sonnet")
    val result2 = claudeAdapter.generate(prompt, workspace)
    assertTrue(result2.success)
}
```

### Test: Handle AI Agent connection errors
**Given**: AI Agent service not running or unreachable
**When**: AiAgentPort.generate() attempts to connect
**Then**: Should throw AiAgentConnectionException
**And**: Error message should indicate connection failure

```kotlin
@Test
fun `should handle AI Agent connection errors`() {
    val prompt = "Generate Kotlin code"
    val workspace = tempDir
    val aiAgentPort: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
    
    // Simulate service not running
    mockOllamaServiceUnavailable()
    
    val exception = assertThrows<AiAgentConnectionException> {
        aiAgentPort.generate(prompt, workspace)
    }
    
    assertTrue(exception.message!!.contains("connection") || exception.message!!.contains("unreachable"))
}
```

### Test: Handle AI timeout scenarios
**Given**: AI generation takes longer than configured timeout
**When**: AiAgentPort.generate() monitors request
**Then**: Request should be cancelled after timeout
**And**: Should throw AiAgentTimeoutException

```kotlin
@Test
fun `should handle AI timeout scenarios`() {
    val prompt = "Generate extremely complex code taking 20 minutes"
    val workspace = tempDir
    val aiAgentPort: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMinutes = 15)
    
    // Mock slow response
    mockOllamaSlowResponse()
    
    val exception = assertThrows<AiAgentTimeoutException> {
        aiAgentPort.generate(prompt, workspace)
    }
    
    assertTrue(exception.message!!.contains("timeout"))
    assertTrue(exception.message!!.contains("15 minutes"))
}
```

### Test: Validate AI modifies correct files
**Given**: Changed spec files: openapi/user-api.yaml
**When**: AI generation completes
**Then**: AI should only modify files related to user API
**And**: Should not modify unrelated files like README.md or build.gradle.kts

```kotlin
@Test
fun `should validate AI modifies correct files`() {
    val specFile = File(tempDir, "openapi/user-api.yaml")
    specFile.writeText("User API specification")
    
    val initialReadme = File(tempDir, "README.md")
    initialReadme.writeText("Original README")
    val initialReadmeHash = initialReadme.readText().hashCode()
    
    val aiAgentPort: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
    aiAgentPort.generate("Generate User API", tempDir)
    
    val modifiedReadme = File(tempDir, "README.md")
    assertEquals(initialReadmeHash, modifiedReadme.readText().hashCode(), "README should not be modified")
    
    assertTrue(File(tempDir, "src/main/kotlin/UserController.kt").exists())
}
```

### Test: Handle model unavailability
**Given**: Requested model not available in AI Agent
**When**: AiAgentPort.generate() attempts to use model
**Then**: Should throw ModelUnavailableException
**And**: Error should indicate which model is missing

```kotlin
@Test
fun `should handle model unavailability`() {
    val prompt = "Generate code"
    val workspace = tempDir
    val aiAgentPort: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "nonexistent-model")
    
    val exception = assertThrows<ModelUnavailableException> {
        aiAgentPort.generate(prompt, workspace)
    }
    
    assertTrue(exception.message!!.contains("model") && exception.message!!.contains("not found"))
}
```

### Test: Validate generated code compiles
**Given**: AI generated Kotlin source files
**When**: Gradle compileKotlin is executed
**Then**: Compilation should succeed with exit code 0
**And**: No compilation errors should be present

```kotlin
@Test
fun `should validate generated code compiles`() {
    // Simulate AI generation
    val controllerFile = File(tempDir, "src/main/kotlin/UserController.kt")
    controllerFile.writeText("""
        package com.example.controller
        
        import org.springframework.web.bind.annotation.*
        
        @RestController
        class UserController {
            @GetMapping("/users")
            fun getUsers(): List<String> = listOf()
        }
    """.trimIndent())
    
    val result = CommandExecutor.execute(
        listOf("./gradlew", "compileKotlin"),
        tempDir
    )
    
    assertEquals(0, result.exitCode)
}
```

### Test: Handle AI generation with syntax errors
**Given**: AI generates invalid Kotlin code
**When**: Gradle compileKotlin is executed
**Then**: Compilation should fail
**And**: Pipeline should report compilation error details

```kotlin
@Test
fun `should handle AI generation with syntax errors`() {
    val controllerFile = File(tempDir, "src/main/kotlin/UserController.kt")
    controllerFile.writeText("""
        package com.example.controller
        
        @RestController
        class UserController {
            // Syntax error: missing import and invalid syntax
            fun brokenMethod( {
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

### Test: Validate AI respects Clean Architecture
**Given**: AI generates code
**When**: Generated files are inspected
**Then**: Controllers should be in infrastructure/adapters layer
**And**: Use cases should be in domain/application layer
**And**: No circular dependencies between layers

```kotlin
@Test
fun `should validate AI respects Clean Architecture`() {
    val aiAgentPort: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
    aiAgentPort.generate("Generate User API with Clean Architecture", tempDir)
    
    val controllerPath = "src/main/kotlin/com/example/infrastructure/adapters/UserController.kt"
    val useCasePath = "src/main/kotlin/com/example/domain/usecases/CreateUserUseCase.kt"
    
    assertTrue(File(tempDir, controllerPath).exists(), "Controller should be in infrastructure/adapters")
    assertTrue(File(tempDir, useCasePath).exists(), "Use case should be in domain/usecases")
}
```

### Test: Handle missing workspace directory
**Given**: Invalid workspace path that does not exist
**When**: CommandExecutor.execute() is called
**Then**: Should throw IllegalArgumentException
**And**: Error message should indicate workspace not found

```kotlin
@Test
fun `should handle missing workspace directory`() {
    val nonExistentDir = File(tempDir, "non-existent")
    
    val aiAgentPort: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
    val exception = assertThrows<IllegalArgumentException> {
        aiAgentPort.generate("prompt", nonExistentDir)
    }
    
    assertTrue(exception.message!!.contains("workspace") || exception.message!!.contains("not found"))
}
```

### Test: Capture AI execution logs
**Given**: AI generation in progress
**When**: CommandExecutor.execute() runs
**Then**: All stdout and stderr should be captured
**And**: Logs should include stage markers for debugging

```kotlin
@Test
fun `should capture AI execution logs`() {
    val logFile = File(tempDir, "ai-execution.log")
    
    val aiAgentPort: AiAgentPort = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
    aiAgentPort.generate("Generate code", tempDir, logOutput = logFile)
    
    assertTrue(logFile.exists())
    val logs = logFile.readText()
    assertTrue(logs.contains("AI Generation") || logs.contains("AI Agent"))
}
```
