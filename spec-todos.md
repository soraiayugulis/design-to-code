# Design-to-Code AI Pipeline - TODOs and Suggestions
>> do not commit this file yet
Status: In Planing - No code authorization yet
See @stepbystep-todos.md for implementation plan

This document organizes all TODOs found in the codebase and suggestions from the implementation thread for future development.

## Code TODOs

### Phase 3: AI Generation Integration
- **OllamaAdapter.kt** (line 78): Implement proper parsing of AI-generated file content

### Phase 4: Quality Gate Validation
- **QualityGateValidator.kt** (line 35): Implement actual Gradle build execution
- **QualityGateValidator.kt** (line 41): Implement actual coverage report parsing

### Phase 5: Git Operations & PR Creation
- **GitHubCliAdapter.kt** (line 11): Implement actual git branch creation using ProcessBuilder
- **GitHubCliAdapter.kt** (line 17): Implement actual git commit using ProcessBuilder
- **GitHubCliAdapter.kt** (line 27): Implement actual PR creation using GitHub CLI

## Implementation Suggestions

### Phase 3: AI Generation Integration
- Implement actual Gradle build execution using ProcessBuilder or Gradle Tooling API
- Implement coverage report parsing for Kover XML/HTML reports
- Add Detekt linting integration as an additional quality gate
- Add timeout handling for build execution
- Add support for multiple coverage thresholds (line, branch, instruction)

### Phase 4: Quality Gate Validation
- Implement actual Gradle build execution using ProcessBuilder or Gradle Tooling API
- Implement coverage report parsing for Kover XML/HTML reports
- Add Detekt linting integration as an additional quality gate
- Consider adding timeout handling for build execution
- Add support for multiple coverage thresholds (line, branch, instruction)

### Phase 5: Git Operations & PR Creation
- Implement actual Git operations using ProcessBuilder or JGit library
- Implement GitHub CLI integration using gh command for PR creation
- Add branch naming strategy service (feature/ai-gen-{sha}) as specified
- Add support for branch conflict detection and resolution
- Implement retry logic for GitHub API failures
- Add support for PR metadata (labels, reviewers, assignees)
- Consider adding Git repository validation before operations

### Phase 6: CLI Entry Point & GitHub Actions Integration
- Implement proper CLI argument parsing with picocli or klaxon library
- Add end-to-end integration tests with mocked external dependencies
- Implement automatic GitHub Actions triggers with branch filter `design/**` pattern, add manual trigger override
- Add support for environment variables configuration
- Implement retry logic for transient failures in pipeline stages
- Add pipeline metrics and monitoring integration
- Consider adding pipeline state persistence for resume capability (decision pending)
- Add support for custom pipeline configuration files (YAML/JSON) - **DECIDED: Use configuration files**
- Implement pipeline dry-run mode for validation without execution

## Architectural Decisions

### Configuration Strategy
**Decision:** Use configuration files (YAML/JSON) instead of CLI arguments
- Configuration files provide version controllable, reusable pipeline definitions
- Supports complex nested configurations
- Documentation-friendly and easier to maintain
- CLI arguments can be added later as overrides if needed

### GitHub Actions Triggers
**Decision:** Implement automatic triggers with branch filter `design/**` pattern
- Prevents noise on main/feature branches
- Focused on design iterations
- Manual trigger override available for flexibility

### Execution Model
**Decision:** Sequential execution (no parallel execution)
- Simpler orchestration and debugging
- Easier error handling
- Avoids race conditions
- Can be optimized later if performance becomes an issue

### CLI Commands
**Decision:** No additional commands (validate, dry-run, status) for now
- Focus on core pipeline functionality first
- Can be added based on user feedback
- Reduces initial complexity

### Open Decision
**Pipeline State Persistence:** Still evaluating advantages/disadvantages and tradeoffs
- Advantages: Resume capability, better debugging, audit trail, checkpoints
- Disadvantages: Increased complexity, storage requirements, state management overhead
- Tradeoffs: Reliability vs complexity
- Decision pending: Will implement only if pipeline stages are long-running (>5 minutes each) or if resume capability is critical

### Implementation Priorities
1. **High Priority**: Implement actual tool integrations (Gradle, Git, Ollama API)
2. **Medium Priority**: Add proper CLI argument parsing and error handling
3. **Medium Priority**: Implement automatic GitHub Actions triggers
4. **Low Priority**: Add pipeline state persistence and parallel execution
5. **Low Priority**: Add pipeline metrics and monitoring integration

## Notes
- All placeholder implementations were intentionally deferred to allow phased development
- Tests focus on interface structure rather than actual external tool execution
- Future implementations should maintain the Hexagonal Architecture pattern
- All TODOs should be implemented with proper error handling and logging
