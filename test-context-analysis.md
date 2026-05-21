# Phase 1: Context Analysis & Detection - TDD Scenarios

## Test Scenarios

### Test: Detect Spring Boot project from build.gradle.kts
**Given**: A build.gradle.kts file containing `org.springframework.boot` dependency
**When**: ContextBuilder.analyzeProject() is called
**Then**: ProjectContext.stack should be TechStack.SPRING_BOOT
**And**: ProjectContext.database should be detected based on database dependencies

```kotlin
@Test
fun `should detect Spring Boot project`() {
    val buildFile = File(tempDir, "build.gradle.kts")
    buildFile.writeText("""
        plugins {
            id("org.springframework.boot") version "3.2.0"
            kotlin("jvm") version "1.9.20"
        }
        dependencies {
            implementation("org.springframework.boot:spring-boot-starter-web")
        }
    """.trimIndent())
    
    val context = ContextBuilder.analyzeProject(tempDir)
    
    assertEquals(TechStack.SPRING_BOOT, context.stack)
}
```

### Test: Detect Quarkus project from build.gradle.kts
**Given**: A build.gradle.kts file containing `io.quarkus` dependency
**When**: ContextBuilder.analyzeProject() is called
**Then**: ProjectContext.stack should be TechStack.QUARKUS

```kotlin
@Test
fun `should detect Quarkus project`() {
    val buildFile = File(tempDir, "build.gradle.kts")
    buildFile.writeText("""
        plugins {
            id("io.quarkus")
        }
        dependencies {
            implementation("io.quarkus:quarkus-resteasy-reactive")
        }
    """.trimIndent())
    
    val context = ContextBuilder.analyzeProject(tempDir)
    
    assertEquals(TechStack.QUARKUS, context.stack)
}
```

### Test: Detect PostgreSQL database dependency
**Given**: A build.gradle.kts file containing `postgresql` dependency
**When**: ContextBuilder.analyzeProject() is called
**Then**: ProjectContext.database should be DatabaseType.POSTGRESQL

```kotlin
@Test
fun `should detect PostgreSQL database`() {
    val buildFile = File(tempDir, "build.gradle.kts")
    buildFile.writeText("""
        dependencies {
            runtimeOnly("org.postgresql:postgresql")
        }
    """.trimIndent())
    
    val context = ContextBuilder.analyzeProject(tempDir)
    
    assertEquals(DatabaseType.POSTGRESQL, context.database)
}
```

### Test: Detect MongoDB database dependency
**Given**: A build.gradle.kts file containing `mongodb` dependency
**When**: ContextBuilder.analyzeProject() is called
**Then**: ProjectContext.database should be DatabaseType.MONGO

```kotlin
@Test
fun `should detect MongoDB database`() {
    val buildFile = File(tempDir, "build.gradle.kts")
    buildFile.writeText("""
        dependencies {
            implementation("org.mongodb:mongodb-driver-kotlin-sync")
        }
    """.trimIndent())
    
    val context = ContextBuilder.analyzeProject(tempDir)
    
    assertEquals(DatabaseType.MONGO, context.database)
}
```

### Test: Handle unknown stack gracefully
**Given**: A build.gradle.kts file without Spring Boot or Quarkus dependencies
**When**: ContextBuilder.analyzeProject() is called
**Then**: ProjectContext.stack should be TechStack.UNKNOWN
**And**: ProjectContext.database should be DatabaseType.NONE

```kotlin
@Test
fun `should handle unknown stack`() {
    val buildFile = File(tempDir, "build.gradle.kts")
    buildFile.writeText("""
        plugins {
            kotlin("jvm")
        }
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib")
        }
    """.trimIndent())
    
    val context = ContextBuilder.analyzeProject(tempDir)
    
    assertEquals(TechStack.UNKNOWN, context.stack)
    assertEquals(DatabaseType.NONE, context.database)
}
```

### Test: Parse changed files from git diff output
**Given**: Git diff output showing changed .yaml and .md files
**When**: Pipeline extracts changed files
**Then**: Only .yaml, .yml, and .md files should be included
**And**: Other file types should be filtered out

```kotlin
@Test
fun `should parse changed files from git diff`() {
    val gitDiffOutput = """
        openapi/user-api.yaml
        src/main/kotlin/UserController.kt
        docs/architecture/spec/payment-flow.md
        README.md
        build.gradle.kts
    """.trimIndent()
    
    val changedFiles = gitDiffOutput.lines()
        .filter { it.matches(Regex(".*\\.(yaml|yml|md)$")) }
    
    assertEquals(2, changedFiles.size)
    assertTrue(changedFiles.contains("openapi/user-api.yaml"))
    assertTrue(changedFiles.contains("docs/architecture/spec/payment-flow.md"))
}
```

### Test: Handle missing build.gradle.kts file
**Given**: A workspace without build.gradle.kts
**When**: ContextBuilder.analyzeProject() is called
**Then**: Should return ProjectContext with UNKNOWN stack and NONE database

```kotlin
@Test
fun `should handle missing build file`() {
    val context = ContextBuilder.analyzeProject(tempDir)
    
    assertEquals(TechStack.UNKNOWN, context.stack)
    assertEquals(DatabaseType.NONE, context.database)
}
```

### Test: Detect Spring Boot with PostgreSQL combination
**Given**: A build.gradle.kts with Spring Boot and PostgreSQL dependencies
**When**: ContextBuilder.analyzeProject() is called
**Then**: ProjectContext should have SPRING_BOOT stack and POSTGRESQL database

```kotlin
@Test
fun `should detect Spring Boot with PostgreSQL`() {
    val buildFile = File(tempDir, "build.gradle.kts")
    buildFile.writeText("""
        plugins {
            id("org.springframework.boot") version "3.2.0"
        }
        dependencies {
            implementation("org.springframework.boot:spring-boot-starter-data-jpa")
            runtimeOnly("org.postgresql:postgresql")
        }
    """.trimIndent())
    
    val context = ContextBuilder.analyzeProject(tempDir)
    
    assertEquals(TechStack.SPRING_BOOT, context.stack)
    assertEquals(DatabaseType.POSTGRESQL, context.database)
}
```

### Test: Detect Quarkus with MongoDB combination
**Given**: A build.gradle.kts with Quarkus and MongoDB dependencies
**When**: ContextBuilder.analyzeProject() is called
**Then**: ProjectContext should have QUARKUS stack and MONGO database

```kotlin
@Test
fun `should detect Quarkus with MongoDB`() {
    val buildFile = File(tempDir, "build.gradle.kts")
    buildFile.writeText("""
        plugins {
            id("io.quarkus")
        }
        dependencies {
            implementation("io.quarkus:quarkus-mongodb-client")
        }
    """.trimIndent())
    
    val context = ContextBuilder.analyzeProject(tempDir)
    
    assertEquals(TechStack.QUARKUS, context.stack)
    assertEquals(DatabaseType.MONGO, context.database)
}
```
