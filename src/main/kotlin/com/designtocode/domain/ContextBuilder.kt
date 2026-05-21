package com.designtocode.domain

import com.designtocode.domain.model.DatabaseType
import com.designtocode.domain.model.ProjectContext
import com.designtocode.domain.model.TechStack
import java.io.File

class ContextBuilder(private val buildFile: File) {

    fun buildContext(): ProjectContext {
        val content = buildFile.readText()
        val techStack = detectTechStack(content)
        val database = detectDatabase(content)
        val frameworkVersion = extractFrameworkVersion(content, techStack)

        return ProjectContext(
            techStack = techStack,
            database = database,
            frameworkVersion = frameworkVersion
        )
    }

    private fun detectTechStack(content: String): TechStack {
        return when {
            content.contains("org.springframework.boot") -> TechStack.SPRING_BOOT
            content.contains("io.quarkus") -> TechStack.QUARKUS
            else -> TechStack.UNKNOWN
        }
    }

    private fun detectDatabase(content: String): DatabaseType {
        return when {
            content.contains("org.postgresql") -> DatabaseType.POSTGRESQL
            content.contains("org.mongodb") -> DatabaseType.MONGODB
            else -> DatabaseType.UNKNOWN
        }
    }

    private fun extractFrameworkVersion(content: String, techStack: TechStack): String {
        return when (techStack) {
            TechStack.SPRING_BOOT -> extractVersion(content, "org.springframework.boot:spring-boot-starter")
            TechStack.QUARKUS -> extractVersion(content, "io.quarkus:quarkus-core")
            TechStack.UNKNOWN -> "unknown"
        }
    }

    private fun extractVersion(content: String, dependency: String): String {
        val pattern = """"$dependency:([^"]+)"""".toRegex()
        val match = pattern.find(content)
        return match?.groupValues?.get(1) ?: "unknown"
    }

    fun parseChangedFiles(gitDiffOutput: String): List<String> {
        return gitDiffOutput
            .lines()
            .filter { it.isNotBlank() }
            .map { it.trim() }
    }
}
