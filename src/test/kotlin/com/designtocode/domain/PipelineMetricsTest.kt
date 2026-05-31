package com.designtocode.domain

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Duration
import java.time.Instant

class PipelineMetricsTest {

    @Test
    fun shouldCalculateStageDuration() {
        // Given
        val startTime = Instant.now()
        val endTime = startTime.plusSeconds(5)
        val stageMetrics = StageMetrics(
            stageName = "Test Stage",
            startTime = startTime,
            endTime = endTime,
            success = true
        )

        // When
        val duration = stageMetrics.duration

        // Then
        assertEquals(Duration.ofSeconds(5), duration)
    }

    @Test
    fun shouldCreatePipelineMetricsWithDefaults() {
        // Given
        val executionId = "test-123"
        val startTime = Instant.now()

        // When
        val metrics = PipelineMetrics(
            executionId = executionId,
            startTime = startTime
        )

        // Then
        assertEquals(executionId, metrics.executionId)
        assertEquals(startTime, metrics.startTime)
        assertNull(metrics.endTime)
        assertTrue(metrics.stages.isEmpty())
        assertNull(metrics.overallSuccess)
    }

    @Test
    fun shouldCalculatePipelineDurationWhenCompleted() {
        // Given
        val startTime = Instant.now()
        val endTime = startTime.plusSeconds(10)
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = startTime,
            endTime = endTime
        )

        // When
        val duration = metrics.duration

        // Then
        assertEquals(Duration.ofSeconds(10), duration)
    }

    @Test
    fun shouldReturnNullDurationWhenNotCompleted() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )

        // When
        val duration = metrics.duration

        // Then
        assertNull(duration)
    }

    @Test
    fun shouldCalculateSuccessRateForEmptyStages() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )

        // When
        val successRate = metrics.successRate

        // Then
        assertEquals(0.0, successRate)
    }

    @Test
    fun shouldCalculateSuccessRateForAllSuccessfulStages() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )
        metrics.addStage(StageMetrics("Stage 1", Instant.now(), Instant.now(), true))
        metrics.addStage(StageMetrics("Stage 2", Instant.now(), Instant.now(), true))
        metrics.addStage(StageMetrics("Stage 3", Instant.now(), Instant.now(), true))

        // When
        val successRate = metrics.successRate

        // Then
        assertEquals(1.0, successRate)
    }

    @Test
    fun shouldCalculateSuccessRateForMixedStages() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )
        metrics.addStage(StageMetrics("Stage 1", Instant.now(), Instant.now(), true))
        metrics.addStage(StageMetrics("Stage 2", Instant.now(), Instant.now(), false))
        metrics.addStage(StageMetrics("Stage 3", Instant.now(), Instant.now(), true))

        // When
        val successRate = metrics.successRate

        // Then
        assertEquals(2.0 / 3.0, successRate, 0.001)
    }

    @Test
    fun shouldCountFailures() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )
        metrics.addStage(StageMetrics("Stage 1", Instant.now(), Instant.now(), true))
        metrics.addStage(StageMetrics("Stage 2", Instant.now(), Instant.now(), false))
        metrics.addStage(StageMetrics("Stage 3", Instant.now(), Instant.now(), false))

        // When
        val failureCount = metrics.failureCount

        // Then
        assertEquals(2, failureCount)
    }

    @Test
    fun shouldAddStage() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )
        val stage = StageMetrics("Test Stage", Instant.now(), Instant.now(), true)

        // When
        metrics.addStage(stage)

        // Then
        assertEquals(1, metrics.stages.size)
        assertEquals(stage, metrics.stages[0])
    }

    @Test
    fun shouldCompletePipeline() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )

        // When
        metrics.complete(true)

        // Then
        assertTrue(metrics.overallSuccess == true)
        assertNotNull(metrics.endTime)
    }

    @Test
    fun shouldGetStageMetricsByName() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )
        val stage = StageMetrics("Test Stage", Instant.now(), Instant.now(), true)
        metrics.addStage(stage)

        // When
        val foundStage = metrics.getStageMetrics("Test Stage")

        // Then
        assertNotNull(foundStage)
        assertEquals(stage, foundStage)
    }

    @Test
    fun shouldReturnNullForNonExistentStage() {
        // Given
        val metrics = PipelineMetrics(
            executionId = "test-123",
            startTime = Instant.now()
        )

        // When
        val foundStage = metrics.getStageMetrics("Non-existent")

        // Then
        assertNull(foundStage)
    }

    @Test
    fun shouldIncludeErrorMessageInStageMetrics() {
        // Given
        val errorMessage = "Stage failed due to error"
        val stage = StageMetrics(
            stageName = "Test Stage",
            startTime = Instant.now(),
            endTime = Instant.now(),
            success = false,
            errorMessage = errorMessage
        )

        // When
        val retrievedError = stage.errorMessage

        // Then
        assertEquals(errorMessage, retrievedError)
    }
}

class MetricsCollectorTest {

    @Test
    fun shouldStartPipeline() {
        // Given
        val collector = MetricsCollector()
        val executionId = "test-123"

        // When
        val metrics = collector.startPipeline(executionId)

        // Then
        assertNotNull(metrics)
        assertEquals(executionId, metrics.executionId)
        assertNotNull(metrics.startTime)
    }

    @Test
    fun shouldRecordStage() = runBlocking {
        // Given
        val collector = MetricsCollector()
        val metrics = collector.startPipeline("test-123")

        // When
        val success = collector.recordStage(metrics, "Test Stage") {
            true
        }

        // Then
        assertTrue(success)
        assertEquals(1, metrics.stages.size)
        assertEquals("Test Stage", metrics.stages[0].stageName)
    }

    @Test
    fun shouldRecordFailedStage() = runBlocking {
        // Given
        val collector = MetricsCollector()
        val metrics = collector.startPipeline("test-123")

        // When
        val success = collector.recordStage(metrics, "Test Stage") {
            throw IllegalStateException("Test error")
        }

        // Then
        assertFalse(success)
        assertEquals(1, metrics.stages.size)
        assertFalse(metrics.stages[0].success)
    }

    @Test
    fun shouldCompletePipeline() {
        // Given
        val collector = MetricsCollector()
        val metrics = collector.startPipeline("test-123")

        // When
        collector.completePipeline(metrics, true)

        // Then
        assertTrue(metrics.overallSuccess == true)
        assertNotNull(metrics.endTime)
    }

    @Test
    fun shouldGetMetricsById() {
        // Given
        val collector = MetricsCollector()
        val executionId = "test-123"
        collector.startPipeline(executionId)

        // When
        val retrievedMetrics = collector.getMetrics(executionId)

        // Then
        assertNotNull(retrievedMetrics)
        assertEquals(executionId, retrievedMetrics.executionId)
    }

    @Test
    fun shouldReturnNullForNonExistentMetrics() {
        // Given
        val collector = MetricsCollector()

        // When
        val retrievedMetrics = collector.getMetrics("non-existent")

        // Then
        assertNull(retrievedMetrics)
    }

    @Test
    fun shouldGetAllMetrics() {
        // Given
        val collector = MetricsCollector()
        collector.startPipeline("test-1")
        collector.startPipeline("test-2")
        collector.startPipeline("test-3")

        // When
        val allMetrics = collector.getAllMetrics()

        // Then
        assertEquals(3, allMetrics.size)
    }

    @Test
    fun shouldCalculateSuccessRate() {
        // Given
        val collector = MetricsCollector()
        val metrics1 = collector.startPipeline("test-1")
        collector.completePipeline(metrics1, true)
        val metrics2 = collector.startPipeline("test-2")
        collector.completePipeline(metrics2, false)
        val metrics3 = collector.startPipeline("test-3")
        collector.completePipeline(metrics3, true)

        // When
        val successRate = collector.getSuccessRate()

        // Then
        assertEquals(2.0 / 3.0, successRate, 0.001)
    }

    @Test
    fun shouldReturnZeroSuccessRateForEmptyHistory() {
        // Given
        val collector = MetricsCollector()

        // When
        val successRate = collector.getSuccessRate()

        // Then
        assertEquals(0.0, successRate)
    }

    @Test
    fun shouldClearHistory() {
        // Given
        val collector = MetricsCollector()
        collector.startPipeline("test-1")
        collector.startPipeline("test-2")

        // When
        collector.clearHistory()
        val allMetrics = collector.getAllMetrics()

        // Then
        assertTrue(allMetrics.isEmpty())
    }

    @Test
    fun shouldExportMetricsToString() {
        // Given
        val collector = MetricsCollector()
        val metrics = collector.startPipeline("test-123")
        metrics.addStage(StageMetrics("Stage 1", Instant.now(), Instant.now(), true))
        collector.completePipeline(metrics, true)

        // When
        val exported = collector.exportMetricsToString()

        // Then
        assertNotNull(exported)
        assertTrue(exported.contains("test-123"))
        assertTrue(exported.contains("Stage 1"))
    }

    @Test
    fun shouldExportMetricsToFile() {
        // Given
        val collector = MetricsCollector()
        val metrics = collector.startPipeline("test-123")
        metrics.addStage(StageMetrics("Stage 1", Instant.now(), Instant.now(), true))
        collector.completePipeline(metrics, true)
        val tempFile = java.io.File.createTempFile("metrics", ".txt")

        // When
        collector.exportMetricsToFile(tempFile.absolutePath)

        // Then
        assertTrue(tempFile.exists())
        val content = tempFile.readText()
        assertTrue(content.contains("test-123"))
        tempFile.delete()
    }
}
