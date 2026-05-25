# Design-to-Code AI Pipeline - User Guide

## Overview

The Design-to-Code AI Pipeline is an automated platform that transforms design specifications (OpenAPI/Markdown) into production-ready Kotlin code. This guide helps users understand how to use the pipeline effectively.

## Table of Contents

1. [Installation](#installation)
2. [Configuration](#configuration)
3. [Usage](#usage)
4. [Troubleshooting](#troubleshooting)

## Installation

### Prerequisites

- Java 21 or higher
- Gradle 9.4 or higher
- Docker (for Testcontainers integration)
- Git
- GitHub CLI (gh) for PR creation

### Setup

1. Clone the repository:
```bash
git clone https://github.com/soraiayugulis/design-to-code.git
cd design-to-code
```

2. Build the project:
```bash
./gradlew build
```

3. Configure the pipeline (see [Configuration](#configuration))

## Configuration

### Pipeline Configuration

The pipeline uses a configuration file to control behavior. Create a `pipeline-config.yaml` in your project root:

```yaml
ai:
  host: localhost
  port: 11434
  model: codellama:13b

qualityGate:
  coverageThreshold: 100
  coverageType: LINE
  maxLintIssues: 0

git:
  branchPrefix: feature/ai-gen

retry:
  maxAttempts: 3
  initialDelayMs: 1000
  maxDelayMs: 10000
  multiplier: 2.0
```

### Environment Variables

- `OLLAMA_HOST`: Ollama API host (default: localhost)
- `OLLAMA_PORT`: Ollama API port (default: 11434)
- `OLLAMA_MODEL`: Model to use for code generation
- `GITHUB_TOKEN`: GitHub token for PR creation (optional)

## Usage

### Running the Pipeline

The pipeline is triggered automatically when design specifications are merged to the `design/**` branch.

### Design Specification Format

#### OpenAPI Specification

Place your OpenAPI specifications in `openapi/*.yaml`:

```yaml
openapi: 3.0.0
info:
  title: User API
  version: 1.0.0
paths:
  /users:
    get:
      summary: List all users
      responses:
        '200':
          description: Successful response
```

#### Markdown Specification

Place your markdown specifications in `docs/spec/*.md`:

```markdown
# User Management Feature

## Requirements
- Create user
- Update user
- Delete user
- List users

## API Endpoints
- POST /api/users
- PUT /api/users/{id}
- DELETE /api/users/{id}
- GET /api/users
```

### Workflow

1. **Create Design Branch**: Create a branch from `main` with your design changes
2. **Add Specifications**: Add or modify specification files in `openapi/` or `docs/spec/`
3. **Commit Changes**: Commit your specification changes
4. **Push to Design Branch**: Push to a branch under `design/**`
5. **Automatic Execution**: The pipeline automatically triggers and generates code
6. **Review PR**: Review the generated code in the created Pull Request
7. **Merge**: Merge the PR if the generated code meets requirements

### Generated Code Structure

The pipeline generates code following Clean Architecture principles:

```
src/main/kotlin/com/yourproject/
├── domain/
│   ├── model/          # Domain models
│   ├── port/           # Ports and interfaces
│   └── adapter/        # Adapters and implementations
├── application/        # Application layer
└── infrastructure/     # Infrastructure layer
```

## Troubleshooting

### Common Issues

#### Pipeline Fails with "Ollama Connection Error"

**Problem**: Cannot connect to Ollama API

**Solution**:
1. Verify Ollama is running: `docker ps` or `ollama list`
2. Check configuration: Ensure `ai.host` and `ai.port` are correct
3. Test connection: `curl http://localhost:11434/api/tags`

#### Quality Gate Fails with "Coverage Below 100%"

**Problem**: Generated code doesn't meet coverage threshold

**Solution**:
1. Review the generated code in the PR
2. Add missing tests manually if needed
3. Adjust `coverageThreshold` in configuration if appropriate

#### Branch Already Exists Error

**Problem**: Pipeline fails because the feature branch already exists

**Solution**:
1. Check if the branch exists: `git branch -a | grep feature/ai-gen`
2. Delete the existing branch if it's stale: `git branch -D feature/ai-gen-<sha>`
3. Re-run the pipeline

#### PR Creation Fails with Authentication Error

**Problem**: GitHub CLI authentication failed

**Solution**:
1. Authenticate with GitHub CLI: `gh auth login`
2. Verify authentication: `gh auth status`
3. Ensure you have permissions to create PRs in the repository

### Getting Help

If you encounter issues not covered here:

1. Check the [Developer Guide](developer-guide.md) for technical details
2. Review the [Operations Guide](operations-guide.md) for deployment issues
3. Open an issue in the GitHub repository with:
   - Pipeline execution logs
   - Configuration file
   - Specification files that triggered the issue
   - Error messages

## Best Practices

1. **Keep Specifications Clear**: Write clear, detailed specifications for better code generation
2. **Review Generated Code**: Always review the generated code before merging
3. **Use Version Control**: Keep design specifications in version control
4. **Test Thoroughly**: Ensure generated code passes all quality gates
5. **Document Changes**: Update documentation when adding new features

## Next Steps

- Read the [Developer Guide](developer-guide.md) to understand how to extend the pipeline
- Read the [Operations Guide](operations-guide.md) for deployment and monitoring
- Review the [Architecture Documentation](developer-guide.md#architecture) for system design
