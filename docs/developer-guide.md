# Design-to-Code AI Pipeline - Developer Guide

## Overview

This guide is for developers who want to understand, extend, or contribute to the Design-to-Code AI Pipeline platform.

## Table of Contents

1. [Architecture](#architecture)
2. [Project Structure](#project-structure)
3. [Development Setup](#development-setup)
4. [API Documentation](#api-documentation)
5. [Testing](#testing)
6. [Contributing](#contributing)

## Architecture

### System Architecture

The pipeline follows Hexagonal Architecture (Ports & Adapters) pattern for the AI engine and Event-Driven architecture for CI/CD orchestration.

```
┌─────────────────────────────────────────────────────────┐
│                   GitHub Actions                        │
│              (Trigger & Orchestration)                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              AI Engine Container                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ContextBuilder│→ │PromptConst.  │→ │  Ollama API  │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                 Quality Gates                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │  Build   │→ │  Detekt  │→ │  Tests   │→ │ Kover  │ │
│  └──────────┘  └──────────┘  └──────────┘  └────────┘ │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                Git Operations                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │  Branch  │→ │  Commit  │→ │    PR    │              │
│  └──────────┘  └──────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────┘
```

### Domain Model

#### Core Entities

- **PipelineExecution**: Represents a single pipeline run
- **SpecFile**: Represents a changed specification file
- **ProjectContext**: Represents the target microservice's technical stack
- **QualityGateResult**: Represents validation outcome
- **PipelineMetrics**: Tracks execution metrics

#### State Transitions

```
IDLE → CONTEXT_ANALYSIS → AI_GENERATION → QUALITY_VALIDATION → PR_CREATION → COMPLETED
                              ↓                   ↓
                         FAILED              FAILED
```

### Ports and Adapters

#### Ports (Interfaces)

- **GitOperationsPort**: Interface for Git operations
- **BuildSystemPort**: Interface for Gradle execution
- **AIAgentPort**: Interface for AI code generation
- **DockerRuntimePort**: Interface for container orchestration

#### Adapters (Implementations)

- **GitHubCliAdapter**: GitHub CLI implementation
- **OllamaAdapter**: Ollama LLM implementation
- **TestcontainersAdapter**: Docker container management

## Project Structure

```
design-to-code/
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── com/designtocode/
│   │           ├── cli/                 # CLI entry point
│   │           │   └── PipelineOrchestrator.kt
│   │           ├── config/              # Configuration
│   │           │   └── PipelineConfig.kt
│   │           ├── domain/              # Domain layer
│   │           │   ├── model/          # Domain models
│   │           │   ├── port/           # Ports
│   │           │   └── adapter/        # Adapters
│   │           └── Main.kt             # Application entry
│   └── test/
│       └── kotlin/
│           └── com/designtocode/
│               └── ...                 # Test files
├── docs/                              # Documentation
├── build.gradle.kts                   # Gradle build
├── detekt.yml                         # Detekt config
└── README.md
```

## Development Setup

### Prerequisites

- Java 21
- Gradle 9.4+
- Docker
- Git
- GitHub CLI (gh)

### Local Development

1. Clone the repository:
```bash
git clone https://github.com/soraiayugulis/design-to-code.git
cd design-to-code
```

2. Build the project:
```bash
./gradlew build
```

3. Run tests:
```bash
./gradlew test
```

4. Run Detekt:
```bash
./gradlew detekt
```

### IDE Setup

#### IntelliJ IDEA

1. Open the project in IntelliJ
2. Configure Java 21 SDK
3. Enable Kotlin plugin
4. Import Gradle project
5. Configure Detekt plugin

#### VS Code

1. Install Kotlin extension
2. Install Gradle extension
3. Configure Java 21
4. Open folder as workspace

## API Documentation

### PipelineOrchestrator

Main orchestrator for the pipeline execution.

```kotlin
class PipelineOrchestrator(
    private val workspacePath: String,
    private val changedFiles: List<String>,
    private val ollamaModel: String,
    private val config: PipelineConfig
) {
    fun execute(): PipelineResult
}
```

### GitOperationsPort

Interface for Git operations.

```kotlin
interface GitOperationsPort {
    fun createFeatureBranch(branchName: String): GitOperationResult
    fun commitChanges(message: String): GitOperationResult
    fun createPullRequest(
        title: String,
        description: String,
        qualityResult: QualityGateResult,
        metadata: PRMetadata
    ): GitOperationResult
}
```

### AIAgentPort

Interface for AI code generation.

```kotlin
interface AIAgentPort {
    suspend fun generate(
        prompt: String,
        workspace: File
    ): GenerationResult
}
```

### MetricsCollector

Collector for pipeline metrics.

```kotlin
class MetricsCollector {
    fun startPipeline(executionId: String): PipelineMetrics
    fun recordStage(
        pipelineMetrics: PipelineMetrics,
        stageName: String,
        block: () -> Boolean
    ): Boolean
    fun exportMetricsToString(): String
    fun exportMetricsToFile(filePath: String)
}
```

## Testing

### Unit Tests

Run unit tests:
```bash
./gradlew test
```

### Integration Tests

Integration tests use Testcontainers for Docker integration:
```bash
./gradlew integrationTest
```

### Test Coverage

Generate coverage report:
```bash
./gradlew koverHtmlReport
```

View report at: `build/reports/kover/html/index.html`

### Writing Tests

Follow TDD principles:

1. Write failing test
2. Implement minimal code to pass
3. Refactor
4. Repeat

Example:
```kotlin
@Test
fun shouldDetectSpringBootProject() {
    // Given
    val buildFile = File("test-resources/spring-boot-build.gradle.kts")
    val contextBuilder = ContextBuilder(buildFile)
    
    // When
    val context = contextBuilder.buildContext()
    
    // Then
    assertEquals(TechStack.SPRING_BOOT, context.techStack)
}
```

## Contributing

### Code Style

- Follow Kotlin coding conventions
- Use Detekt for linting: `./gradlew detekt`
- Keep functions short and focused
- Use meaningful names
- Add KDoc comments for public APIs

### Commit Messages

Follow conventional commits:
```
feat: add new feature
fix: fix bug
docs: update documentation
test: add tests
refactor: refactor code
```

### Pull Request Process

1. Create feature branch from main
2. Make changes with granular commits
3. Ensure all tests pass
4. Run Detekt and fix issues
5. Update documentation
6. Create PR with descriptive title
7. Address review feedback
8. Merge after approval

### Adding New Features

1. Update `stepbystep-todos.md` with new phase
2. Create feature branch
3. Implement following TDD
4. Add tests
5. Update documentation
6. Create PR

### Adding New AI Providers

To add a new AI provider:

1. Implement `AIAgentPort` interface
2. Add configuration to `PipelineConfig`
3. Add tests for the new adapter
4. Update documentation

Example:
```kotlin
class ClaudeAdapter(
    private val apiKey: String,
    private val model: String
) : AIAgentPort {
    override suspend fun generate(
        prompt: String,
        workspace: File
    ): GenerationResult {
        // Implementation
    }
}
```

## Performance Considerations

- Use coroutines for async operations
- Implement retry logic for transient failures
- Cache expensive operations
- Limit resource usage in containers
- Monitor pipeline metrics

## Security Considerations

- Never commit API keys
- Use environment variables for secrets
- Validate all inputs
- Sanitize AI-generated code
- Use container isolation
- Implement rate limiting

## Debugging

### Enable Debug Logging

Set log level to DEBUG in `logback.xml`:
```xml
<logger name="com.designtocode" level="DEBUG"/>
```

### Common Issues

#### Tests Fail with Docker Connection Error

Ensure Docker is running:
```bash
docker ps
```

#### Detekt Fails

Run Detekt with auto-fix:
```bash
./gradlew detekt
```

#### Build Fails with Dependency Error

Clean and rebuild:
```bash
./gradlew clean build
```

## Resources

- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Gradle Documentation](https://docs.gradle.org/)
- [Detekt Documentation](https://detekt.dev/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Ollama Documentation](https://ollama.ai/)
