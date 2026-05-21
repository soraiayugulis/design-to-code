# Phase 2: Prompt Construction - TDD Scenarios

## Test Scenarios

### Test: Merge global rules with project context
**Given**: Global Clean Architecture rules file exists
**And**: ProjectContext with SPRING_BOOT stack and POSTGRESQL database
**When**: PromptConstructor.buildPrompt() is called
**Then**: Prompt should contain global architecture rules
**And**: Prompt should include project-specific context (stack and database)

```kotlin
@Test
fun `should merge global rules with project context`() {
    val globalRules = File("/opt/ai-platform/rules/clean-architecture.md")
    globalRules.writeText("Use Clean Architecture pattern with domain, application, and infrastructure layers.")
    
    val projectContext = ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL)
    val specContent = "User API specification"
    
    val prompt = PromptConstructor.buildPrompt(globalRules, projectContext, listOf(specContent))
    
    assertTrue(prompt.contains("Clean Architecture"))
    assertTrue(prompt.contains("SPRING_BOOT"))
    assertTrue(prompt.contains("POSTGRESQL"))
}
```

### Test: Inject framework-specific instructions for Spring Boot
**Given**: ProjectContext with SPRING_BOOT stack
**When**: PromptConstructor.buildPrompt() is called
**Then**: Prompt should include Spring Boot specific annotations
**And**: Prompt should mention @RestController, @Service, @Repository

```kotlin
@Test
fun `should inject Spring Boot specific instructions`() {
    val projectContext = ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL)
    
    val prompt = PromptConstructor.buildPrompt(File("rules.md"), projectContext, listOf("spec"))
    
    assertTrue(prompt.contains("@RestController"))
    assertTrue(prompt.contains("@Service"))
    assertTrue(prompt.contains("@Repository"))
    assertTrue(prompt.contains("Spring Boot"))
}
```

### Test: Inject framework-specific instructions for Quarkus
**Given**: ProjectContext with QUARKUS stack
**When**: PromptConstructor.buildPrompt() is called
**Then**: Prompt should include Quarkus specific annotations
**And**: Prompt should mention @Path, @ApplicationScoped, @Inject

```kotlin
@Test
fun `should inject Quarkus specific instructions`() {
    val projectContext = ProjectContext(TechStack.QUARKUS, DatabaseType.POSTGRESQL)
    
    val prompt = PromptConstructor.buildPrompt(File("rules.md"), projectContext, listOf("spec"))
    
    assertTrue(prompt.contains("@Path"))
    assertTrue(prompt.contains("@ApplicationScoped"))
    assertTrue(prompt.contains("@Inject"))
    assertTrue(prompt.contains("Quarkus"))
}
```

### Test: Construct prompt with multiple spec files
**Given**: Multiple changed spec files (user-api.yaml, payment-flow.md)
**When**: PromptConstructor.buildPrompt() is called
**Then**: Prompt should include content from all spec files
**And**: Each spec file should be clearly labeled with its path

```kotlin
@Test
fun `should construct prompt with multiple spec files`() {
    val spec1 = SpecFile("openapi/user-api.yaml", SpecType.OPENAPI, "User API content")
    val spec2 = SpecFile("docs/payment-flow.md", SpecType.MARKDOWN, "Payment flow content")
    
    val prompt = PromptConstructor.buildPrompt(
        File("rules.md"),
        ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL),
        listOf(spec1, spec2)
    )
    
    assertTrue(prompt.contains("File: openapi/user-api.yaml"))
    assertTrue(prompt.contains("User API content"))
    assertTrue(prompt.contains("File: docs/payment-flow.md"))
    assertTrue(prompt.contains("Payment flow content"))
}
```

### Test: Handle empty spec files list
**Given**: Empty list of changed spec files
**When**: PromptConstructor.buildPrompt() is called
**Then**: Should throw IllegalArgumentException
**Or**: Should return prompt with empty spec section

```kotlin
@Test
fun `should handle empty spec files list`() {
    val exception = assertThrows<IllegalArgumentException> {
        PromptConstructor.buildPrompt(
            File("rules.md"),
            ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL),
            emptyList()
        )
    }
    
    assertTrue(exception.message!!.contains("No spec files provided"))
}
```

### Test: Include Testcontainers configuration in prompt
**Given**: ProjectContext with POSTGRESQL database
**When**: PromptConstructor.buildPrompt() is called
**Then**: Prompt should include Testcontainers PostgreSQL configuration
**And**: Prompt should mention @Testcontainers annotation

```kotlin
@Test
fun `should include Testcontainers configuration for PostgreSQL`() {
    val projectContext = ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL)
    
    val prompt = PromptConstructor.buildPrompt(File("rules.md"), projectContext, listOf("spec"))
    
    assertTrue(prompt.contains("Testcontainers"))
    assertTrue(prompt.contains("PostgreSQLContainer"))
    assertTrue(prompt.contains("@Testcontainers"))
}
```

### Test: Include Testcontainers configuration for MongoDB
**Given**: ProjectContext with MONGO database
**When**: PromptConstructor.buildPrompt() is called
**Then**: Prompt should include Testcontainers MongoDB configuration
**And**: Prompt should mention MongoDBContainer

```kotlin
@Test
fun `should include Testcontainers configuration for MongoDB`() {
    val projectContext = ProjectContext(TechStack.QUARKUS, DatabaseType.MONGO)
    
    val prompt = PromptConstructor.buildPrompt(File("rules.md"), projectContext, listOf("spec"))
    
    assertTrue(prompt.contains("Testcontainers"))
    assertTrue(prompt.contains("MongoDBContainer"))
}
```

### Test: Include JUnit 5 test instructions
**Given**: Any ProjectContext
**When**: PromptConstructor.buildPrompt() is called
**Then**: Prompt should include JUnit 5 testing instructions
**And**: Prompt should mention @Test, @BeforeEach, @TestInstance

```kotlin
@Test
fun `should include JUnit 5 test instructions`() {
    val projectContext = ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL)
    
    val prompt = PromptConstructor.buildPrompt(File("rules.md"), projectContext, listOf("spec"))
    
    assertTrue(prompt.contains("JUnit 5"))
    assertTrue(prompt.contains("@Test"))
    assertTrue(prompt.contains("@BeforeEach"))
}
```

### Test: Handle missing global rules file
**Given**: Global rules file does not exist
**When**: PromptConstructor.buildPrompt() is called
**Then**: Should throw FileNotFoundException
**Or**: Should use default architecture rules

```kotlin
@Test
fun `should handle missing global rules file`() {
    val missingRules = File("/opt/ai-platform/rules/non-existent.md")
    
    val exception = assertThrows<FileNotFoundException> {
        PromptConstructor.buildPrompt(
            missingRules,
            ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL),
            listOf("spec")
        )
    }
    
    assertEquals("Global architecture rules file not found", exception.message)
}
```

### Test: Validate prompt structure completeness
**Given**: Valid inputs for prompt construction
**When**: PromptConstructor.buildPrompt() is called
**Then**: Prompt should have all required sections in order
**And**: Sections should be: Global Rules, Project Context, Specifications, Framework Instructions

```kotlin
@Test
fun `should validate prompt structure completeness`() {
    val projectContext = ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL)
    
    val prompt = PromptConstructor.buildPrompt(File("rules.md"), projectContext, listOf("spec"))
    
    val sections = listOf("Global Architecture Rules", "Project Context", "Specifications", "Framework Instructions")
    sections.forEach { section ->
        assertTrue(prompt.contains(section), "Prompt should contain section: $section")
    }
}
```
