package com.designToCode.infra

import com.designToCode.domain.model.DatabaseType
import com.designToCode.domain.model.ProjectContext
import com.designToCode.domain.model.TechStack
import java.io.File

object ContextBuilder {
    fun analyzeProject(workspace: File): ProjectContext {
        val buildFile = File(workspace, "build.gradle.kts")
        
        if (!buildFile.exists()) {
            return ProjectContext(TechStack.UNKNOWN, DatabaseType.NONE)
        }

        val content = buildFile.readText()

        val stack = detectTechStack(content)
        val database = detectDatabase(content)

        return ProjectContext(stack, database)
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
            content.contains("postgresql", ignoreCase = true) -> DatabaseType.POSTGRESQL
            content.contains("mongodb", ignoreCase = true) -> DatabaseType.MONGO
            else -> DatabaseType.NONE
        }
    }
}
