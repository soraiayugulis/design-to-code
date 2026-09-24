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
- **Quality Gate Enforcement**: Build, Detekt and configurable coverage validation (90% default for generated code; 80% Kover gate on this project)
- **Automated Pull Request Creation**: For human review and approval

## Features

- **Automated Code Generation**: Transforms OpenAPI/Markdown specs to Kotlin code
- **Generated Output Validation**: Writes restricted to allowed source roots; declared spec targets are verified after generation; bounded corrective retries on violations; the pipeline fails before Git/PR operations when targets are untouched
- **Spec Change Analysis**: Parses the git diff of design specs into structured changes (section, change type, line ranges) to focus the prompt
- **Hexagonal Architecture**: Clean separation of concerns with ports and adapters
- **Quality Gates**: Configurable coverage threshold (default 90%), linting, and compilation checks
- **Git Operations**: Automated branch creation, commits, push, and PR creation
- **Monitoring & Metrics**: Pipeline execution tracking and optional metrics export (`--metrics-file`)
- **Branch Naming Strategy**: SHA-based branch naming for traceability
- **PR Metadata Support**: Labels, reviewers, and assignees for pull requests

## Technology Stack

### Core Technologies

- **Language**: Kotlin 1.9+
- **Build Tool**: Gradle 9.4+
- **Java**: JDK 21

### Quality & Testing

- **Linting**: Detekt (Kotlin static analysis)
- **Code Coverage**: Kover (80% minimum verified on this project)
- **Integration Testing**: Testcontainers (optional — tests are skipped when Docker is unavailable)
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
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │ContextBuilder│→ │SpecChangeAnalyzer│→ │  Ollama API  │  │
│  │              │  │PromptConstructor │  │              │  │
│  └──────────────┘  └──────────────────┘  └──────────────┘  │
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
IDLE → CONTEXT_ANALYSIS → SPEC_CHANGE_ANALYSIS → PROMPT_CONSTRUCTION → AI_GENERATION → QUALITY_VALIDATION → PR_CREATION → COMPLETED
                                                                      ↓                   ↓
                                                                 FAILED              FAILED
```

### Ports and Adapters

#### Ports (Interfaces)

- **AIAgentPort**: Interface for AI code generation
- **GitOperationsPort**: Interface for Git operations
- **QualityGatePort**: Interface for quality gate validation

#### Adapters (Implementations)

- **OllamaAdapter**: Ollama LLM implementation
- **GitHubCliAdapter**: Git + GitHub CLI implementation
- **QualityGateValidator**: Gradle/Detekt/Kover quality gate implementation

## Getting Started

For detailed installation, configuration, and usage instructions, see the [User Guide](docs/guides/user-guide.md).

### Quick Start

```bash
git clone https://github.com/soraiayugulis/design-to-code.git
cd design-to-code
./gradlew build
```

### CLI Usage

```bash
./gradlew run --args="[options] <workspacePath> [changedFiles]"
```

| Option | Description |
|---|---|
| `-c`, `--config` | Path to `pipeline.yml` (defaults to `<workspace>/pipeline.yml`; see `pipeline.yml.example`) |
| `-m`, `--model` | Ollama model override (default `codellama:13b`) |
| `-b`, `--base-ref` | Git base ref for spec diff analysis (default `HEAD~1`, or `git.baseRef` in config) |
| `--metrics-file` | Export pipeline metrics to a file |

`changedFiles` accepts a comma-separated list of design spec paths (e.g. `design/api.yaml,design/model.md`).

### Output Validation

The `outputValidation` section of `pipeline.yml` controls generated-output grounding and verification:

```yaml
outputValidation:
  enabled: true              # verify generated output before Git/PR operations
  strictMode: false          # treat unexpected generated files as fatal violations
  maxCorrectiveRetries: 2    # bounded corrective attempts with violation feedback
  allowedRoots: []           # relative source roots; empty = auto-detect **/src/{main,test}/{java,kotlin}
```

Specs may declare `target.file` (plus an optional symbol like `composable`) to state which file the change must land in; the pipeline fails before branch/commit/PR when a declared target is not modified. See `pipeline.yml.example` and `docs/adr/spec-generated-output-validation.md`.

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

