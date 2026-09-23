package com.designtocode.domain

import com.designtocode.domain.model.ChangeType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpecDiffParserTest {

    private val parser = SpecDiffParser()

    @Test
    fun shouldParseAddedSectionsInDiff() {
        // Given
        val diffOutput = """
            diff --git a/design/openapi.yaml b/design/openapi.yaml
            index 8384920..9283748 100644
            --- a/design/openapi.yaml
            +++ b/design/openapi.yaml
            @@ -10,6 +10,12 @@ paths:
               /users:
                 get:
                   summary: Get all users
            +    post:
            +      summary: Create a user
            +      requestBody:
            +        content:
            +          application/json:
            +            schema:
            +              ${'$'}ref: '#/components/schemas/UserDto'
        """.trimIndent()

        // When
        val changes = parser.parseDiff(diffOutput)

        // Then
        assertEquals(1, changes.size)
        val change = changes[0]
        assertEquals("design/openapi.yaml", change.filePath)
        assertEquals(ChangeType.ADD, change.changeType)
        assertEquals("paths./users.post", change.affectedSection)
        assertTrue(change.newContent?.contains("post:") == true)
        assertTrue(change.newContent?.contains("Create a user") == true)
        assertEquals(13..19, change.lineNumberRange)
    }

    @Test
    fun shouldParseModifiedSectionsInDiff() {
        // Given
        val diffOutput = """
            diff --git a/design/openapi.yaml b/design/openapi.yaml
            index 8384920..9283748 100644
            --- a/design/openapi.yaml
            +++ b/design/openapi.yaml
            @@ -45,5 +45,5 @@ components:
                 UserDto:
                   type: object
                   properties:
            -        name: string
            +        fullName: string
                     email: string
        """.trimIndent()

        // When
        val changes = parser.parseDiff(diffOutput)

        // Then
        assertEquals(1, changes.size)
        val change = changes[0]
        assertEquals("design/openapi.yaml", change.filePath)
        assertEquals(ChangeType.MODIFY, change.changeType)
        assertEquals("components.schemas.UserDto", change.affectedSection)
        assertTrue(change.oldContent?.contains("name: string") == true)
        assertTrue(change.newContent?.contains("fullName: string") == true)
    }

    @Test
    fun shouldParseDeletedSectionsInDiff() {
        // Given
        val diffOutput = """
            diff --git a/design/openapi.yaml b/design/openapi.yaml
            index 8384920..9283748 100644
            --- a/design/openapi.yaml
            +++ b/design/openapi.yaml
            @@ -25,10 +25,0 @@ paths:
            -  /orders:
            -    get:
            -      summary: Get all orders
            -      responses:
            -        '200':
            -          description: OK
        """.trimIndent()

        // When
        val changes = parser.parseDiff(diffOutput)

        // Then
        assertEquals(1, changes.size)
        val change = changes[0]
        assertEquals("design/openapi.yaml", change.filePath)
        assertEquals(ChangeType.DELETE, change.changeType)
        assertEquals("paths./orders", change.affectedSection)
        assertTrue(change.oldContent?.contains("/orders") == true)
    }

    @Test
    fun shouldReturnEmptyListForEmptyDiff() {
        // Given
        val diffOutput = ""

        // When
        val changes = parser.parseDiff(diffOutput)

        // Then
        assertTrue(changes.isEmpty())
    }

    @Test
    fun shouldIgnoreNonSpecChanges() {
        // Given
        val diffOutput = """
            diff --git a/src/main/kotlin/com/designtocode/domain/SpecDiffParser.kt b/src/main/kotlin/com/designtocode/domain/SpecDiffParser.kt
            index 1234567..7654321 100644
            --- a/src/main/kotlin/com/designtocode/domain/SpecDiffParser.kt
            +++ b/src/main/kotlin/com/designtocode/domain/SpecDiffParser.kt
            @@ -1,3 +1,4 @@
             package com.designtocode.domain
            +
             class SpecDiffParser {
        """.trimIndent()

        // When
        val changes = parser.parseDiff(diffOutput)

        // Then
        assertTrue(changes.isEmpty())
    }

    @Test
    fun shouldParseMultipleFilesAndSections() {
        // Given
        val diffOutput = """
            diff --git a/design/openapi.yaml b/design/openapi.yaml
            index 8384920..9283748 100644
            --- a/design/openapi.yaml
            +++ b/design/openapi.yaml
            @@ -10,4 +10,6 @@ paths:
               /users:
            +    post:
            +      summary: Create user
            diff --git a/design/messages.md b/design/messages.md
            index 1111111..2222222 100644
            --- a/design/messages.md
            +++ b/design/messages.md
            @@ -5,2 +5,2 @@
            -Welcome to API
            +Welcome to the API version 2
        """.trimIndent()

        // When
        val changes = parser.parseDiff(diffOutput)

        // Then
        assertEquals(2, changes.size)
        assertEquals("design/openapi.yaml", changes[0].filePath)
        assertEquals(ChangeType.ADD, changes[0].changeType)
        assertEquals("design/messages.md", changes[1].filePath)
        assertEquals(ChangeType.MODIFY, changes[1].changeType)
    }
}
