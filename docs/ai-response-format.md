# AI Response Format Documentation

## Overview

The Design-to-Code AI Pipeline expects AI models to generate code in a specific markdown format that can be parsed and written to files securely. This document describes the expected response format from the AI model (Ollama/CodeLlama).

## Expected Format

The AI model should respond with markdown code blocks that include the file path in the code block fence. The format is:

```
```language:path/to/file.ext
<file content>
```
```

### Example

File paths must live under an allowed output root (see *Security Constraints*). For an Android app module, for example:

```kotlin:app/src/main/java/com/example/settings/SettingsScreen.kt
package com.example.settings

@Composable
fun SettingsScreen() {
    // implementation
}
```

## Format Details

- **Language**: The programming language identifier (e.g., `kotlin`, `java`, `typescript`)
- **File Path**: The relative path from the workspace root where the file should be created
- **Content**: The actual file content to be written

## Security Constraints

The parser enforces the following security constraints:

1. **Path Traversal Prevention**: File paths cannot contain `..` to prevent directory traversal attacks
2. **Absolute Path Prevention**: File paths cannot start with `/` to ensure they are relative to the workspace
3. **Workspace Boundary**: All files must be created within the workspace directory
4. **Allowed Output Roots**: When `outputValidation` is enabled, files may only be written under the resolved source roots — either `outputValidation.allowedRoots` from `pipeline.yml` or auto-detected `**/src/{main,test}/{java,kotlin}` directories

## Rejection Semantics

A file whose path violates the constraints above is **not written to disk**. Instead it is recorded in `GenerationResult.rejectedFiles` with the rejection reason. A parseable response with only rejected files still returns `success=true`; the deterministic `GeneratedOutputVerifier` then reports the failure (`OUTSIDE_SOURCE_ROOT`) and may trigger a bounded corrective retry before the pipeline fails.

## Multiple Files

The AI can generate multiple files in a single response by including multiple code blocks:

```
```kotlin:app/src/main/java/com/example/User.kt
package com.example

data class User(val id: Long, val name: String)
```

```kotlin:app/src/main/java/com/example/UserRepository.kt
package com.example

class UserRepository {
    // implementation
}
```

## File Deletion Markers

To delete a file, use the `DELETE` marker before the file path:

```
DELETE:app/src/main/java/com/example/OldScreen.kt
```

This will remove the specified file from the workspace if it exists.

## File Modification Instructions

To modify an existing file, use the `MODIFY` marker with optional line range:

```
MODIFY:app/src/main/java/com/example/settings/SettingsScreen.kt:10-20
```kotlin
// New content for lines 10-20
fun LanguageOption() {
    // implementation
}
```
```

If no line range is specified, the entire file will be replaced:

```
MODIFY:app/src/main/java/com/example/settings/SettingsScreen.kt
```kotlin
// Entire new file content
package com.example.settings

@Composable
fun SettingsScreen() {
    // new implementation
}
```

## Model Configuration

### Current Model
- **Model**: CodeLlama 13b
- **Model ID**: `codellama:13b`
- **API Endpoint**: `http://localhost:11434`

### Prompt Engineering

When prompting the AI model, include instructions to use the specified format:

```
Generate the code for a User controller. 
Please format your response using markdown code blocks with file paths:
```language:path/to/file.ext
<content>
```
```

## Implementation Details

The parsing and write-path logic lives in `GeneratedResponseParser` (used by `OllamaAdapter`). It accepts markdown code blocks, `MODIFY:` markers and `DELETE:` markers, and gates every write through workspace-boundary and allowed-roots checks before touching disk.

## Testing

To test the AI response format:

1. Ensure Ollama is running: `ollama serve`
2. Verify the model is available: `ollama list`
3. Test generation with a simple prompt that includes format instructions

## Troubleshooting

### Files Not Created
- Check that the response uses the correct format with file paths
- Verify file paths are relative and don't contain `..`
- Verify file paths live under an allowed output root (`outputValidation.allowedRoots` or auto-detected source sets)
- Check the pipeline log for `rejected file` entries with the rejection reason
- Ensure the workspace directory exists and is writable

### Parsing Errors
- The parser handles malformed responses gracefully
- Check the AI model's response for proper markdown formatting
- Verify the regex pattern matches the expected format

## Future Enhancements

Potential improvements to the response format:

- Metadata inclusion (e.g., file permissions, encoding)
