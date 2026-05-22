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

```kotlin:src/main/kotlin/com/example/UserController.kt
package com.example

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController {
    @GetMapping
    fun getAllUsers(): List<User> {
        // implementation
    }
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

## Multiple Files

The AI can generate multiple files in a single response by including multiple code blocks:

```
```kotlin:src/main/kotlin/com/example/User.kt
package com.example

data class User(val id: Long, val name: String)
```

```kotlin:src/main/kotlin/com/example/UserController.kt
package com.example

@RestController
class UserController {
    // implementation
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

The parsing logic is implemented in `OllamaAdapter.kt`:

```kotlin
private fun parseGeneratedFiles(content: String, workspace: File): List<String> {
    val codeBlockRegex = Regex("""```(\w+):([^\n]+)\n([\s\S]*?)```""")
    val matches = codeBlockRegex.findAll(content)
    
    for (match in matches) {
        val filePath = match.groupValues[2]
        val fileContent = match.groupValues[3]
        
        if (isPathSafe(filePath, workspace)) {
            val file = File(workspace, filePath)
            file.parentFile?.mkdirs()
            file.writeText(fileContent)
            generatedFiles.add(filePath)
        }
    }
}
```

## Testing

To test the AI response format:

1. Ensure Ollama is running: `ollama serve`
2. Verify the model is available: `ollama list`
3. Test generation with a simple prompt that includes format instructions

## Troubleshooting

### Files Not Created
- Check that the response uses the correct format with file paths
- Verify file paths are relative and don't contain `..`
- Ensure the workspace directory exists and is writable

### Parsing Errors
- The parser handles malformed responses gracefully
- Check the AI model's response for proper markdown formatting
- Verify the regex pattern matches the expected format

## Future Enhancements

Potential improvements to the response format:

- Support for file deletion markers
- Support for file modification instructions
- Metadata inclusion (e.g., file permissions, encoding)
