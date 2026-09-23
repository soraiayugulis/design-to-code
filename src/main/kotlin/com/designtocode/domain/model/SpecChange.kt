package com.designtocode.domain.model

enum class ChangeType {
    ADD, MODIFY, DELETE
}

data class SpecChange(
    val filePath: String,
    val changeType: ChangeType,
    val oldContent: String?,
    val newContent: String?,
    val lineNumberRange: IntRange?,
    val affectedSection: String
) {
    fun calculateChangeImpact(): Double {
        return when (changeType) {
            ChangeType.DELETE -> DELETE_IMPACT
            ChangeType.ADD -> ADD_IMPACT
            ChangeType.MODIFY -> {
                val linesChanged = lineNumberRange?.let { it.last - it.first + 1 } ?: 1
                val scale = linesChanged * IMPACT_PER_LINE
                (MODIFY_BASE_IMPACT + scale).coerceAtMost(MAX_IMPACT)
            }
        }
    }

    companion object {
        private const val DELETE_IMPACT = 5.0
        private const val ADD_IMPACT = 3.0
        private const val MODIFY_BASE_IMPACT = 1.0
        private const val IMPACT_PER_LINE = 0.5
        private const val MAX_IMPACT = 10.0
    }
}
