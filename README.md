# Design-to-Code AI Pipeline

![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-indigo?logo=kotlin)
![Gradle](https://img.shields.io/badge/Gradle-9.4+-indigo?logo=gradle)
![JDK](https://img.shields.io/badge/JDK-21-indigo?logo=openjdk)
![JUnit](https://img.shields.io/badge/JUnit-5-indigo?logo=junit5)
![Testcontainers](https://img.shields.io/badge/Testcontainers-Integration%20Tests-indigo?logo=testcontainers)
![Kover](https://img.shields.io/badge/Kover-Code%20Coverage-indigo?logo=kotlin)

![Ollama](https://img.shields.io/badge/Ollama-Local%20LLM-blueviolet?logo=ollama)
![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-blueviolet?logo=github-actions)
![Docker](https://img.shields.io/badge/Docker-Container-blueviolet?logo=docker)
![GitHub CLI](https://img.shields.io/badge/GitHub%20CLI-gh-blueviolet?logo=github)
![Internal Developer Platform](https://img.shields.io/badge/Internal%20Developer%20Platform-IDP-blueviolet)

![Spec-Driven Development](https://img.shields.io/badge/Spec--Driven%20Development-SDD-blueviolet)
![Event-Driven Development](https://img.shields.io/badge/Event--Driven%20Development-EDD-blueviolet)
![Hexagonal Architecture](https://img.shields.io/badge/Hexagonal%20Architecture-Ports%20%26%20Adapters-blueviolet)
![Test-Driven Development](https://img.shields.io/badge/Test--Driven%20Development-TDD-blueviolet)

An automated platform that transforms design specifications (OpenAPI/Markdown) into production-ready Kotlin code using AI.

## Overview

The Design-to-Code AI Pipeline is a distributed Internal Developer Platform (IDP) that orchestrates automated code generation from design specifications to production-ready Kotlin implementations. The system operates as a centralized CI/CD infrastructure composed of:

- **GitHub Actions Workflow**: Triggers on design branch merges
- **Kotlin-based AI Orchestration Engine**: Analyzes project context and executes code generation
- **Quality Gate Enforcement**: Testcontainers-based integration testing and 100% code coverage validation
- **Automated Pull Request Creation**: For human review and approval

## Features

- **Automated Code Generation**: Transforms OpenAPI/Markdown specs to Kotlin code
- **Hexagonal Architecture**: Clean separation of concerns with ports and adapters
- **Quality Gates**: Enforces 100% code coverage, linting, and compilation checks
- **Git Operations**: Automated branch creation, commits, and PR creation
- **Monitoring & Metrics**: Pipeline execution tracking and metrics export
- **Branch Naming Strategy**: SHA-based branch naming for traceability
- **Conflict Detection**: Automatic detection of merge conflicts
- **PR Metadata Support**: Labels, reviewers, and assignees for pull requests

## Technology Stack

### Core Technologies

- **Language**: Kotlin 1.9+
- **Build Tool**: Gradle 9.4+
- **Java**: JDK 21

### Quality & Testing

- **Linting**: Detekt (Kotlin static analysis)
- **Code Coverage**: Kover (Kotlin coverage tool)
- **Integration Testing**: Testcontainers (Docker-based testing)
- **Testing Framework**: JUnit 5

### AI & Code Generation

- **AI Model**: Ollama (local LLM runtime)
- **Model**: CodeLlama 13B (default)
- **API**: Ollama REST API

### Git & CI/CD

- **Version Control**: Git
- **GitHub Integration**: GitHub CLI (gh)
- **CI/CD**: GitHub Actions
- **Container Runtime**: Docker

### Architecture Patterns

- **Hexagonal Architecture**: Ports & Adapters pattern
- **Event-Driven Architecture**: CI/CD orchestration
- **Clean Architecture**: Domain-driven design principles

## Documentation

- [User Guide](docs/guides/user-guide.md) - Installation, configuration, and usage
- [Developer Guide](docs/guides/developer-guide.md) - Architecture, API documentation, and contribution guide
- [Specifications](docs/spec/) - Technical specifications and implementation phases

## Architecture

The pipeline follows Hexagonal Architecture (Ports & Adapters) pattern for the AI engine and Event-Driven architecture for CI/CD orchestration.

### System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   GitHub Actions                        │
│              (Trigger & Orchestration)                  │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────┐
│              AI Engine Container                           │
│  ┌──────────────┐  ┌─────────────────┐  ┌──────────────┐   │
│  │ContextBuilder│→ │PromptConstructor│→ │  Ollama API  │   │
│  └──────────────┘  └─────────────────┘  └──────────────┘   │
└────────────────────┬───────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                 Quality Gates                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐   │
│  │  Build   │→ │  Detekt  │→ │  Tests   │→ │ Kover  │   │
│  └──────────┘  └──────────┘  └──────────┘  └────────┘   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                Git Operations                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │  Branch  │→ │  Commit  │→ │    PR    │               │
│  └──────────┘  └──────────┘  └──────────┘               │
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

## Getting Started

For detailed installation, configuration, and usage instructions, see the [User Guide](docs/guides/user-guide.md).

### Quick Start

```bash
git clone https://github.com/soraiayugulis/design-to-code.git
cd design-to-code
./gradlew build
```

## Development

See the [Developer Guide](docs/guides/developer-guide.md) for development setup and contribution guidelines.

### Running Tests

```bash
./gradlew test
```

### Running Detekt

```bash
./gradlew detekt
```

### Building

```bash
./gradlew build
```

## Development Approach

This project was implemented and is maintained using **Spec-Driven Development (SDD)** combined with **AI-Augmented Development**. Features are implemented based on detailed technical specifications, with AI (Ollama/CodeLlama) automating code generation from **executable OpenAPI/Markdown specifications**. The development process adheres to **Test-Driven Development (TDD)** principles and **Hexagonal Architecture** patterns, with granular incremental implementation phases ensuring continuous delivery and validation, with the specifications serving as **Living Documentation** and following **Contract-Driven Development**.

## Contributing

1. Create a feature branch from main
2. Implement following TDD principles
3. Ensure all tests pass
4. Run Detekt and fix issues
5. Update documentation
6. Create PR with @soraiayugulis as reviewer
7. Address review feedback
8. Merge after approval

See the [Developer Guide](docs/guides/developer-guide.md) for detailed contribution guidelines.


## Contact

- GitHub: [@_sysout](https://github.com/soraiayugulis)
- LinkedIn: [soraia-yugulis](https://www.linkedin.com/in/soraia-yugulis-47a622b1/)

