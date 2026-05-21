package com.designToCode.infra

import com.designToCode.domain.model.DatabaseType
import com.designToCode.domain.model.ProjectContext
import com.designToCode.domain.model.TechStack
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.io.path.createTempDirectory

class ContextBuilderTest {
    
    @TempDir
    lateinit var tempDir: File

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
        
        assertThat(context.stack).isEqualTo(TechStack.SPRING_BOOT)
    }

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
        
        assertThat(context.stack).isEqualTo(TechStack.QUARKUS)
    }

    @Test
    fun `should detect PostgreSQL database`() {
        val buildFile = File(tempDir, "build.gradle.kts")
        buildFile.writeText("""
            dependencies {
                runtimeOnly("org.postgresql:postgresql")
            }
        """.trimIndent())
        
        val context = ContextBuilder.analyzeProject(tempDir)
        
        assertThat(context.database).isEqualTo(DatabaseType.POSTGRESQL)
    }

    @Test
    fun `should detect MongoDB database`() {
        val buildFile = File(tempDir, "build.gradle.kts")
        buildFile.writeText("""
            dependencies {
                implementation("org.mongodb:mongodb-driver-kotlin-sync")
            }
        """.trimIndent())
        
        val context = ContextBuilder.analyzeProject(tempDir)
        
        assertThat(context.database).isEqualTo(DatabaseType.MONGO)
    }

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
        
        assertThat(context.stack).isEqualTo(TechStack.UNKNOWN)
        assertThat(context.database).isEqualTo(DatabaseType.NONE)
    }

    @Test
    fun `should handle missing build file`() {
        val context = ContextBuilder.analyzeProject(tempDir)
        
        assertThat(context.stack).isEqualTo(TechStack.UNKNOWN)
        assertThat(context.database).isEqualTo(DatabaseType.NONE)
    }

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
        
        assertThat(context.stack).isEqualTo(TechStack.SPRING_BOOT)
        assertThat(context.database).isEqualTo(DatabaseType.POSTGRESQL)
    }

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
        
        assertThat(context.stack).isEqualTo(TechStack.QUARKUS)
        assertThat(context.database).isEqualTo(DatabaseType.MONGO)
    }
}
