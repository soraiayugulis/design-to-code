# [SPRECIFICATION-PHASES]Design-to-Code AI Pipeline Platform - Implementation Shell Phases
**Status:** DONE
**Specification:** @docs/spec/spec-context.md
**Instructions:** Always use `@workflow:new-feature-sdd` and `@rules:cascade-response-spec`

## Phase 1: Context Analysis & Detection
**Objective**: Implement core context detection logic to analyze project build configuration and identify technical stack.

### Scope
- Detect Spring Boot project from build.gradle.kts
- Detect Quarkus project from build.gradle.kts
- Detect PostgreSQL database dependency
- Detect MongoDB database dependency
- Handle unknown stack gracefully
- Parse changed files from git diff output

### Deliverables
- ContextBuilder domain service
- ProjectContext domain model
- TechStack enum (SPRING_BOOT, QUARKUS, UNKNOWN)
- DatabaseType enum (POSTGRESQL, MONGODB, UNKNOWN)
- Unit tests for all scenarios
- Integration with build.gradle.kts parsing

### Test Scenarios
1. Detect Spring Boot from `implementation("org.springframework.boot:spring-boot-starter")`
2. Detect Quarkus from `implementation("io.quarkus:quarkus-core")`
3. Detect PostgreSQL from `runtimeOnly("org.postgresql:postgresql")`
4. Detect MongoDB from `implementation("org.mongodb:mongodb-driver-sync")`
5. Return UNKNOWN for unrecognized dependencies
6. Parse git diff output to extract changed spec file paths

---

## Phase 2: Prompt Construction
**Objective**: Build prompt engineering logic to merge global rules, project context, and specifications.

### Scope
- Merge global rules with project context
- Inject framework-specific instructions for Spring Boot
- Inject framework-specific instructions for Quarkus
- Construct prompt with multiple spec files
- Handle empty spec files list

### Deliverables
- PromptConstructor domain service
- Global rules loader from mounted directory
- Framework-specific instruction templates
- Unit tests for prompt construction scenarios

---

## Phase 3: AI Generation Integration
**Objective**: Integrate with AI agent port for code generation using Ollama.

### Scope
- Invoke Ollama API successfully
- Handle Ollama connection errors
- Handle AI timeout scenarios
- Validate AI modifies correct files
- Handle model unavailability

### Deliverables
- AIAgentPort interface
- OllamaAdapter implementation
- Code generation orchestration service
- Error handling and retry logic
- Unit tests for AI integration

---

## Phase 4: Quality Gate Validation
**Objective**: Implement quality gate enforcement through Gradle build system.

### Scope
- Execute Gradle build successfully
- Execute Detekt linting
- Execute Kover coverage verification
- Parse coverage report for 100% threshold
- Fail pipeline when coverage < 100%
- Fail pipeline when compilation fails

### Deliverables
- QualityGateValidator service
- Gradle build adapter
- Coverage report parser
- Quality gate result domain model
- Unit tests for validation scenarios

---

## Phase 5: Git Operations & PR Creation
**Objective**: Implement Git operations for branch creation, commits, and PR management.

### Scope
- Create feature branch from current HEAD
- Commit generated code changes
- Create Pull Request via GitHub CLI
- Handle branch already exists scenario
- Handle GitHub API authentication failures

### Deliverables
- GitOperationsPort interface
- GitHub CLI adapter implementation
- Branch naming strategy (feature/ai-gen-{sha})
- PR creation with validation summary
- Unit tests for Git operations

---

## Phase 6: CLI Entry Point & GitHub Actions Integration
**Objective**: Build CLI interface and GitHub Actions workflow for pipeline orchestration.

### Scope
- Parse command-line arguments (changedFiles, workspacePath, ollamaModel)
- Orchestrate pipeline stages
- Structured logging with stage markers
- GitHub Actions reusable workflow
- Docker container configuration

### Deliverables
- Kotlin main entry point
- Pipeline orchestrator service
- GitHub Actions workflow YAML
- Dockerfile for AI engine container
- End-to-end integration tests
