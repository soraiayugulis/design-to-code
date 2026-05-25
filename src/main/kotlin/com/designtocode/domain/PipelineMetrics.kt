package com.designtocode.domain

import java.time.Duration
import java.time.Instant

data class StageMetrics(
    val stageName: String,
    val startTime: Instant,
    val endTime: Instant,
    val success: Boolean,
    val errorMessage: String? = null
) {
    val duration: Duration
        get() = Duration.between(startTime, endTime)
}

data class PipelineMetrics(
    val executionId: String,
    val startTime: Instant,
    var endTime: Instant? = null,
    val stages: MutableList<StageMetrics> = mutableListOf(),
    var overallSuccess: Boolean? = null
) {
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it) }
    
    val successRate: Double
        get() = if (stages.isEmpty()) 0.0 else stages.count { it.success }.toDouble() / stages.size
    
    val failureCount: Int
        get() = stages.count { !it.success }
    
    fun addStage(stage: StageMetrics) {
        stages.add(stage)
    }
    
    fun complete(success: Boolean) {
        overallSuccess = success
        endTime = Instant.now()
    }
    
    fun getStageMetrics(stageName: String): StageMetrics? {
        return stages.find { it.stageName == stageName }
    }
}

class MetricsCollector {
    private val metricsHistory = mutableListOf<PipelineMetrics>()
    
    companion object {
        private const val PERCENTAGE_MULTIPLIER = 100
    }
    
    fun startPipeline(executionId: String): PipelineMetrics {
        val metrics = PipelineMetrics(
            executionId = executionId,
            startTime = Instant.now()
        )
        metricsHistory.add(metrics)
        return metrics
    }
    
    fun recordStage(
        pipelineMetrics: PipelineMetrics,
        stageName: String,
        block: () -> Boolean
    ): Boolean {
        val startTime = Instant.now()
        val success = try {
            block()
        } catch (e: Exception) {
            false
        }
        val endTime = Instant.now()
        
        val stageMetrics = StageMetrics(
            stageName = stageName,
            startTime = startTime,
            endTime = endTime,
            success = success,
            errorMessage = if (!success) "Stage failed" else null
        )
        
        pipelineMetrics.addStage(stageMetrics)
        return success
    }
    
    fun completePipeline(pipelineMetrics: PipelineMetrics, success: Boolean) {
        pipelineMetrics.complete(success)
    }
    
    fun getMetrics(executionId: String): PipelineMetrics? {
        return metricsHistory.find { it.executionId == executionId }
    }
    
    fun getAllMetrics(): List<PipelineMetrics> {
        return metricsHistory.toList()
    }
    
    fun getSuccessRate(): Double {
        if (metricsHistory.isEmpty()) return 0.0
        return metricsHistory.count { it.overallSuccess == true }.toDouble() / metricsHistory.size
    }
    
    fun clearHistory() {
        metricsHistory.clear()
    }
    
    fun exportMetricsToFile(filePath: String) {
        val file = java.io.File(filePath)
        file.writeText(exportMetricsToString())
    }
    
    fun exportMetricsToString(): String {
        return buildString {
            appendLine("=== Pipeline Metrics Export ===")
            appendLine("Total Pipelines: ${metricsHistory.size}")
            appendLine("Overall Success Rate: ${(getSuccessRate() * PERCENTAGE_MULTIPLIER).toInt()}%")
            appendLine()
            
            metricsHistory.forEach { metrics ->
                appendLine("Pipeline ID: ${metrics.executionId}")
                appendLine("  Start Time: ${metrics.startTime}")
                appendLine("  End Time: ${metrics.endTime ?: "In Progress"}")
                appendLine("  Duration: ${metrics.duration?.toSeconds() ?: "N/A"}s")
                appendLine("  Overall Success: ${metrics.overallSuccess}")
                appendLine("  Stage Success Rate: ${(metrics.successRate * PERCENTAGE_MULTIPLIER).toInt()}%")
                appendLine("  Stages:")
                metrics.stages.forEach { stage ->
                    appendLine("    - ${stage.stageName}: ${if (stage.success) "✅" else "❌"} (${stage.duration.toSeconds()}s)")
                }
                appendLine()
            }
        }
    }
}
