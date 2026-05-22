package com.designtocode.domain.model

enum class DatabaseType {
    POSTGRESQL,
    MONGODB,
    UNKNOWN;

    fun toFriendlyName(): String = when (this) {
        POSTGRESQL -> "PostgreSQL"
        MONGODB -> "MongoDB"
        UNKNOWN -> "Unknown"
    }
}
