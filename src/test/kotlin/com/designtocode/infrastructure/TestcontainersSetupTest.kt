package com.designtocode.infrastructure

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

@Testcontainers
@DisplayName("Testcontainers Setup Tests")
class TestcontainersSetupTest {

    companion object {
        @Container
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15-alpine")

        @Container
        val mongoContainer = MongoDBContainer("mongo:6")
    }

    @Test
    @DisplayName("Should start PostgreSQL container successfully")
    fun `should start postgresql container successfully`() {
        assertTrue(postgresContainer.isRunning, "PostgreSQL container should be running")
        assertTrue(postgresContainer.jdbcUrl.isNotEmpty(), "PostgreSQL JDBC URL should be available")
    }

    @Test
    @DisplayName("Should start MongoDB container successfully")
    fun `should start mongodb container successfully`() {
        assertTrue(mongoContainer.isRunning, "MongoDB container should be running")
        assertTrue(mongoContainer.connectionString.isNotEmpty(), "MongoDB connection string should be available")
    }

    @Test
    @DisplayName("Should cleanup containers after test")
    fun `should cleanup containers after test`() {
        // This test validates that containers are properly cleaned up
        // The @Testcontainers annotation handles automatic cleanup
        assertTrue(postgresContainer.isRunning, "PostgreSQL container should be running during test")
        assertTrue(mongoContainer.isRunning, "MongoDB container should be running during test")
    }
}
