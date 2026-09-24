package com.designtocode.domain.model

enum class TechStack {
    SPRING_BOOT,
    QUARKUS,
    ANDROID,
    UNKNOWN;

    fun toFriendlyName(): String = when (this) {
        SPRING_BOOT -> "Spring Boot"
        QUARKUS -> "Quarkus"
        ANDROID -> "Android"
        UNKNOWN -> "Unknown"
    }
}
