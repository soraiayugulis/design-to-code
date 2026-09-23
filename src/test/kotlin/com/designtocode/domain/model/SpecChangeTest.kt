package com.designtocode.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SpecChangeTest {

    @Test
    fun shouldCalculateLowImpactForSmallDiff() {
        val change = SpecChange(
            filePath = "design/openapi.yaml",
            changeType = ChangeType.MODIFY,
            oldContent = "type: string",
            newContent = "type: string\nformat: email",
            lineNumberRange = 10..11,
            affectedSection = "components.schemas.UserDto"
        )
        val impact = change.calculateChangeImpact()
        assertTrue(impact > 0.0)
        assertTrue(impact < 5.0) // Low impact
    }

    @Test
    fun shouldCalculateCriticalImpactForStructuralChanges() {
        val change = SpecChange(
            filePath = "design/openapi.yaml",
            changeType = ChangeType.DELETE,
            oldContent = "/users:\n  get:\n    summary: Get users",
            newContent = null,
            lineNumberRange = 5..10,
            affectedSection = "paths./users"
        )
        val impact = change.calculateChangeImpact()
        assertTrue(impact >= 5.0) // High/critical impact
    }
}
