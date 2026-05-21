package com.designToCode.domain.model

enum class TechStack {
    SPRING_BOOT,
    QUARKUS,
    UNKNOWN
}

enum class DatabaseType {
    POSTGRESQL,
    MONGO,
    NONE
}

data class ProjectContext(
    val stack: TechStack,
    val database: DatabaseType,
    val frameworkVersion: String? = null
)
