package com.designtocode.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContextBuilderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldDetectSpringBootProjectFromBuildGradleKts() {
        // Given
        val buildFile = File(tempDir, "build.gradle.kts")
        val springBootContent = """
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter:3.2.0")
            }
        """.trimIndent()
        buildFile.writeText(springBootContent)
        val contextBuilder = ContextBuilder(buildFile)

        // When
        val context = contextBuilder.buildContext()

        // Then
        assertEquals(TechStack.SPRING_BOOT, context.techStack)
    }

    @Test
    fun shouldDetectQuarkusProjectFromBuildGradleKts() {
        // Given
        val buildFile = File(tempDir, "build.gradle.kts")
        val quarkusContent = """
            dependencies {
                implementation("io.quarkus:quarkus-core:3.6.0")
            }
        """.trimIndent()
        buildFile.writeText(quarkusContent)
        val contextBuilder = ContextBuilder(buildFile)

        // When
        val context = contextBuilder.buildContext()

        // Then
        assertEquals(TechStack.QUARKUS, context.techStack)
    }

    @Test
    fun shouldDetectPostgreSQLDatabaseDependency() {
        // Given
        val buildFile = File(tempDir, "build.gradle.kts")
        val postgresContent = """
            dependencies {
                runtimeOnly("org.postgresql:postgresql:42.7.1")
            }
        """.trimIndent()
        buildFile.writeText(postgresContent)
        val contextBuilder = ContextBuilder(buildFile)

        // When
        val context = contextBuilder.buildContext()

        // Then
        assertEquals(DatabaseType.POSTGRESQL, context.database)
    }

    @Test
    fun shouldDetectMongoDBDatabaseDependency() {
        // Given
        val buildFile = File(tempDir, "build.gradle.kts")
        val mongoContent = """
            dependencies {
                implementation("org.mongodb:mongodb-driver-sync:4.11.0")
            }
        """.trimIndent()
        buildFile.writeText(mongoContent)
        val contextBuilder = ContextBuilder(buildFile)

        // When
        val context = contextBuilder.buildContext()

        // Then
        assertEquals(DatabaseType.MONGODB, context.database)
    }

    @Test
    fun shouldHandleUnknownStackGracefully() {
        // Given
        val buildFile = File(tempDir, "build.gradle.kts")
        val unknownContent = """
            dependencies {
                implementation("some.unknown:library:1.0.0")
            }
        """.trimIndent()
        buildFile.writeText(unknownContent)
        val contextBuilder = ContextBuilder(buildFile)

        // When
        val context = contextBuilder.buildContext()

        // Then
        assertEquals(TechStack.UNKNOWN, context.techStack)
        assertEquals(DatabaseType.UNKNOWN, context.database)
    }

    @Test
    fun shouldParseChangedFilesFromGitDiffOutput() {
        // Given
        val buildFile = File(tempDir, "build.gradle.kts")
        buildFile.writeText("")
        val contextBuilder = ContextBuilder(buildFile)
        val gitDiffOutput = """
            openapi/user-service.yaml
            docs/spec/order-api.md
            design/product.yaml
        """.trimIndent()

        // When
        val changedFiles = contextBuilder.parseChangedFiles(gitDiffOutput)

        // Then
        assertEquals(3, changedFiles.size)
        assertEquals("openapi/user-service.yaml", changedFiles[0])
        assertEquals("docs/spec/order-api.md", changedFiles[1])
        assertEquals("design/product.yaml", changedFiles[2])
    }
}
