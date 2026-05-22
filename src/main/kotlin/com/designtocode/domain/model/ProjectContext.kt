package com.designtocode.domain.model

data class ProjectContext(
    val techStack: TechStack,
    val database: DatabaseType,
    val frameworkVersion: String
)
