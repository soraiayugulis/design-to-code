# Design-to-Code AI Pipeline - Step-by-Step Implementation Plan
## Status: In Planning
## Commit Authorization: Not Authorized
## Specification: @docs/adr/spec-incremental-code-generation.md
## Agent Instructions: use @workflow:new-feature-sdd and @rules:cascade-response-spec

This document provides a detailed, phased implementation plan for adding incremental code generation capability to the Design-to-Code AI Pipeline. Instead of regenerating entire codebases, the system will parse specification diffs and generate only the affected code, reducing execution time, minimizing merge conflicts, and preserving manually written code.

---

## Implementation Phases Overview
See @docs/adr/spec-incremental-code-generation.md for detailed phase descriptions.

## Phase 0: Diff Parsing & Change Detection

### 0.1 Create SpecDiffParser Domain Service
**File:** `src/main/kotlin/com/designtocode/domain/SpecDiffParser.kt`

**Steps:**
1. Write failing test for SpecDiffParser initialization
2. Define SpecDiffParser class with git integration
3. Implement method to parse git diff for spec files
4. Extract added, modified, and deleted sections
5. Create SpecChange data class to represent changes
6. Implement change type detection (endpoint addition, DTO modification, etc.)
7. Make test pass
8. Write tests for various diff scenarios
9. Refactor for clean code

**Tests:**
- Test: SpecDiffParser parses git diff correctly
- Test: SpecDiffParser identifies added sections
- Test: SpecDiffParser identifies modified sections
- Test: SpecDiffParser identifies deleted sections
- Test: SpecDiffParser detects change types
- Test: SpecDiffParser handles multiple file changes
- Test: SpecDiffParser handles empty diffs

**Commits:**
- test: add SpecDiffParser initialization tests
- feat: implement SpecDiffParser class
- feat: add git diff parsing logic
- feat: add change extraction logic
- feat: add change type detection
- refactor: clean up SpecDiffParser code

### 0.2 Create SpecChange Model
**File:** `src/main/kotlin/com/designtocode/domain/model/SpecChange.kt`

**Steps:**
1. Write failing test for SpecChange creation
2. Define SpecChange data class
3. Add fields: filePath, changeType, oldContent, newContent, lineNumber
4. Define ChangeType enum (ADD, MODIFY, DELETE)
5. Add method to calculate change impact
6. Make test pass
7. Write tests for change impact calculation
8. Refactor for clean code

**Tests:**
- Test: SpecChange creates valid data structure
- Test: SpecChange handles ADD change type
- Test: SpecChange handles MODIFY change type
- Test: SpecChange handles DELETE change type
- Test: SpecChange calculates impact correctly

**Commits:**
- test: add SpecChange creation tests
- feat: implement SpecChange data class
- feat: add ChangeType enum
- feat: add impact calculation method
- refactor: clean up SpecChange model

### 0.3 Update PipelineOrchestrator to Use Diff Parsing
**File:** `src/main/kotlin/com/designtocode/cli/PipelineOrchestrator.kt`

**Steps:**
1. Write failing test for PipelineOrchestrator with diff parsing
2. Add SpecDiffParser dependency to PipelineOrchestrator
3. Update context analysis to include diff parsing
4. Pass spec changes to subsequent stages
5. Make test pass
6. Write tests for diff parsing integration
7. Refactor for clean code

**Tests:**
- Test: PipelineOrchestrator uses SpecDiffParser
- Test: PipelineOrchestrator passes spec changes to prompt construction
- Test: PipelineOrchestrator handles empty diffs
- Test: PipelineOrchestrator handles multiple spec changes

**Commits:**
- test: add PipelineOrchestrator diff parsing tests
- feat: integrate SpecDiffParser in PipelineOrchestrator
- feat: update context analysis with diff parsing
- feat: pass spec changes to prompt construction
- refactor: clean up PipelineOrchestrator code

---

## Phase 1: Impact Analysis Engine

### 1.1 Create ImpactAnalyzer Domain Service
**File:** `src/main/kotlin/com/designtocode/domain/ImpactAnalyzer.kt`

**Steps:**
1. Write failing test for ImpactAnalyzer initialization
2. Define ImpactAnalyzer class with project context
3. Implement method to map spec changes to affected code files
4. Create mapping rules (endpoint change → controller, DTO change → model, etc.)
5. Implement dependency graph traversal
6. Create ImpactReport data class
7. Make test pass
8. Write tests for impact analysis scenarios
9. Refactor for clean code

**Tests:**
- Test: ImpactAnalyzer maps endpoint changes to controllers
- Test: ImpactAnalyzer maps DTO changes to models
- Test: ImpactAnalyzer maps service changes to use cases
- Test: ImpactAnalyzer traverses dependency graph
- Test: ImpactAnalyzer generates comprehensive impact report
- Test: ImpactAnalyzer handles cascading changes

**Commits:**
- test: add ImpactAnalyzer initialization tests
- feat: implement ImpactAnalyzer class
- feat: add spec-to-code mapping rules
- feat: add dependency graph traversal
- feat: add impact report generation
- refactor: clean up ImpactAnalyzer code

### 1.2 Create ImpactReport Model
**File:** `src/main/kotlin/com/designtocode/domain/model/ImpactReport.kt`

**Steps:**
1. Write failing test for ImpactReport creation
2. Define ImpactReport data class
3. Add fields: affectedFiles, changeType, impactLevel, dependencies
4. Define ImpactLevel enum (LOW, MEDIUM, HIGH, CRITICAL)
5. Add method to prioritize changes
6. Make test pass
7. Write tests for impact prioritization
8. Refactor for clean code

**Tests:**
- Test: ImpactReport creates valid data structure
- Test: ImpactReport calculates impact level correctly
- Test: ImpactReport prioritizes changes appropriately
- Test: ImpactReport includes all affected files
- Test: ImpactReport includes dependency chain

**Commits:**
- test: add ImpactReport creation tests
- feat: implement ImpactReport data class
- feat: add ImpactLevel enum
- feat: add impact prioritization logic
- refactor: clean up ImpactReport model

### 1.3 Integrate ImpactAnalyzer in PipelineOrchestrator
**File:** `src/main/kotlin/com/designtocode/cli/PipelineOrchestrator.kt`

**Steps:**
1. Write failing test for PipelineOrchestrator with impact analysis
2. Add ImpactAnalyzer dependency to PipelineOrchestrator
3. Update prompt construction to use impact report
4. Log impact analysis results
5. Make test pass
6. Write tests for impact analysis integration
7. Refactor for clean code

**Tests:**
- Test: PipelineOrchestrator uses ImpactAnalyzer
- Test: PipelineOrchestrator passes impact report to prompt construction
- Test: PipelineOrchestrator logs impact analysis results
- Test: PipelineOrchestrator handles no-impact scenarios

**Commits:**
- test: add PipelineOrchestrator impact analysis tests
- feat: integrate ImpactAnalyzer in PipelineOrchestrator
- feat: update prompt construction with impact report
- feat: add impact analysis logging
- refactor: clean up PipelineOrchestrator code

---

## Phase 2: Incremental Prompt Construction

### 2.1 Update PromptConstructor for Incremental Generation
**File:** `src/main/kotlin/com/designtocode/domain/PromptConstructor.kt`

**Steps:**
1. Write failing test for incremental prompt construction
2. Update PromptConstructor to accept ImpactReport
3. Implement method to construct prompt for specific files only
4. Add context preservation (existing code snippets)
5. Implement selective instruction generation
6. Make test pass
7. Write tests for incremental prompt scenarios
8. Refactor for clean code

**Tests:**
- Test: PromptConstructor constructs incremental prompt
- Test: PromptConstructor includes existing code context
- Test: PromptConstructor generates selective instructions
- Test: PromptConstructor handles file-specific prompts
- Test: PromptConstructor preserves code structure

**Commits:**
- test: add incremental prompt construction tests
- feat: update PromptConstructor for incremental generation
- feat: add context preservation logic
- feat: add selective instruction generation
- refactor: clean up PromptConstructor code

### 2.2 Create CodeContextPreserver Service
**File:** `src/main/kotlin/com/designtocode/domain/CodeContextPreserver.kt`

**Steps:**
1. Write failing test for CodeContextPreserver initialization
2. Define CodeContextPreserver class
3. Implement method to extract existing code context
4. Implement method to preserve manually written code sections
5. Add markers for preserved sections
6. Make test pass
7. Write tests for context preservation scenarios
8. Refactor for clean code

**Tests:**
- Test: CodeContextPreserver extracts existing code
- Test: CodeContextPreserver preserves manually written sections
- Test: CodeContextPreserver adds preservation markers
- Test: CodeContextPreserver handles partial file updates
- Test: CodeContextPreserver maintains code structure

**Commits:**
- test: add CodeContextPreserver initialization tests
- feat: implement CodeContextPreserver class
- feat: add code extraction logic
- feat: add preservation marker logic
- refactor: clean up CodeContextPreserver code

### 2.3 Integrate CodeContextPreserver in PromptConstructor
**File:** `src/main/kotlin/com/designtocode/domain/PromptConstructor.kt`

**Steps:**
1. Write failing test for PromptConstructor with context preservation
2. Add CodeContextPreserver dependency to PromptConstructor
3. Update prompt construction to use preserved context
4. Make test pass
5. Write tests for context preservation integration
6. Refactor for clean code

**Tests:**
- Test: PromptConstructor uses CodeContextPreserver
- Test: PromptConstructor includes preserved context in prompt
- Test: PromptConstructor handles missing context gracefully

**Commits:**
- test: add PromptConstructor context preservation tests
- feat: integrate CodeContextPreserver in PromptConstructor
- feat: update prompt construction with preserved context
- refactor: clean up PromptConstructor code

---

## Phase 3: Selective Code Generation

### 3.1 Update AIAgentPort for Selective Generation
**File:** `src/main/kotlin/com/designtocode/domain/port/AIAgentPort.kt`

**Steps:**
1. Write failing test for selective generation method
2. Add generateSelective method signature to AIAgentPort
3. Update GenerationResult to include modified files list
4. Make test pass
5. Write tests for selective generation interface
6. Refactor for clean code

**Tests:**
- Test: AIAgentPort has generateSelective method
- Test: GenerationResult includes modified files
- Test: GenerationResult tracks selective changes

**Commits:**
- test: add AIAgentPort selective generation tests
- feat: add generateSelective method to AIAgentPort
- feat: update GenerationResult with modified files
- refactor: clean up AIAgentPort interface

### 3.2 Implement Selective Generation in OllamaAdapter
**File:** `src/main/kotlin/com/designtocode/domain/adapter/OllamaAdapter.kt`

**Steps:**
1. Write failing test for OllamaAdapter.generateSelective
2. Implement generateSelective method
3. Apply AI-generated changes to specific files only
4. Preserve code sections marked for preservation
5. Implement selective file writing
6. Make test pass
7. Write tests for selective generation scenarios
8. Refactor for clean code

**Tests:**
- Test: OllamaAdapter generates selective changes
- Test: OllamaAdapter preserves marked code sections
- Test: OllamaAdapter modifies only affected files
- Test: OllamaAdapter handles partial file updates
- Test: OllamaAdapter maintains file structure

**Commits:**
- test: add OllamaAdapter selective generation tests
- feat: implement generateSelective method
- feat: add selective change application
- feat: add code preservation logic
- feat: add selective file writing
- refactor: clean up OllamaAdapter code

### 3.3 Update PipelineOrchestrator for Selective Generation
**File:** `src/main/kotlin/com/designtocode/cli/PipelineOrchestrator.kt`

**Steps:**
1. Write failing test for PipelineOrchestrator with selective generation
2. Update AI generation to use generateSelective
3. Add configuration for incremental vs. full generation
4. Log selective generation results
5. Make test pass
6. Write tests for selective generation integration
7. Refactor for clean code

**Tests:**
- Test: PipelineOrchestrator uses selective generation when enabled
- Test: PipelineOrchestrator falls back to full generation when disabled
- Test: PipelineOrchestrator logs selective generation results
- Test: PipelineOrchestrator handles selective generation failures

**Commits:**
- test: add PipelineOrchestrator selective generation tests
- feat: update AI generation to use generateSelective
- feat: add incremental generation configuration
- feat: add selective generation logging
- refactor: clean up PipelineOrchestrator code

---

## Phase 4: Conflict Detection & Resolution

### 4.1 Create ConflictDetector Domain Service
**File:** `src/main/kotlin/com/designtocode/domain/ConflictDetector.kt`

**Steps:**
1. Write failing test for ConflictDetector initialization
2. Define ConflictDetector class
3. Implement method to detect conflicts between AI changes and existing code
4. Create Conflict data class
5. Implement conflict severity classification
6. Make test pass
7. Write tests for conflict detection scenarios
8. Refactor for clean code

**Tests:**
- Test: ConflictDetector detects code conflicts
- Test: ConflictDetector classifies conflict severity
- Test: ConflictDetector handles merge conflicts
- Test: ConflictDetector handles dependency conflicts
- Test: ConflictDetector handles no-conflict scenarios

**Commits:**
- test: add ConflictDetector initialization tests
- feat: implement ConflictDetector class
- feat: add conflict detection logic
- feat: add conflict severity classification
- refactor: clean up ConflictDetector code

### 4.2 Create ConflictResolver Service
**File:** `src/main/kotlin/com/designtocode/domain/ConflictResolver.kt`

**Steps:**
1. Write failing test for ConflictResolver initialization
2. Define ConflictResolver class
3. Implement resolution strategies (AI-priority, Manual-priority, Merge)
4. Implement method to apply resolution
5. Create ConflictResolutionResult data class
6. Make test pass
7. Write tests for resolution strategies
8. Refactor for clean code

**Tests:**
- Test: ConflictResolver applies AI-priority resolution
- Test: ConflictResolver applies Manual-priority resolution
- Test: ConflictResolver applies Merge resolution
- Test: ConflictResolver handles unresolvable conflicts
- Test: ConflictResolver logs resolution decisions

**Commits:**
- test: add ConflictResolver initialization tests
- feat: implement ConflictResolver class
- feat: add resolution strategies
- feat: add resolution application logic
- refactor: clean up ConflictResolver code

### 4.3 Integrate Conflict Detection in Pipeline
**File:** `src/main/kotlin/com/designtocode/cli/PipelineOrchestrator.kt`

**Steps:**
1. Write failing test for PipelineOrchestrator with conflict detection
2. Add ConflictDetector and ConflictResolver dependencies
3. Update pipeline to detect conflicts after generation
4. Apply automatic resolution when possible
5. Fail pipeline for unresolvable conflicts
6. Make test pass
7. Write tests for conflict detection integration
8. Refactor for clean code

**Tests:**
- Test: PipelineOrchestrator detects conflicts after generation
- Test: PipelineOrchestrator applies automatic resolution
- Test: PipelineOrchestrator fails on unresolvable conflicts
- Test: PipelineOrchestrator logs conflict resolution

**Commits:**
- test: add PipelineOrchestrator conflict detection tests
- feat: integrate ConflictDetector in PipelineOrchestrator
- feat: integrate ConflictResolver in PipelineOrchestrator
- feat: add automatic resolution logic
- feat: add conflict logging
- refactor: clean up PipelineOrchestrator code

---

## Phase 5: CI/CD Integration & Configuration

### 5.1 Add Incremental Generation Configuration
**File:** `src/main/kotlin/com/designtocode/config/PipelineConfig.kt`

**Steps:**
1. Write failing test for incremental generation configuration
2. Add IncrementalGenerationConfig nested class
3. Add fields: enabled, conflictResolutionStrategy, preserveManualCode
4. Update YAML parsing for incremental configuration
5. Make test pass
6. Write tests for configuration parsing
7. Refactor for clean code

**Tests:**
- Test: PipelineConfig includes IncrementalGenerationConfig
- Test: IncrementalGenerationConfig has all required fields
- Test: PipelineConfig parses incremental configuration from YAML
- Test: IncrementalGenerationConfig has sensible defaults

**Commits:**
- test: add incremental generation configuration tests
- feat: add IncrementalGenerationConfig nested class
- feat: add incremental generation fields
- feat: update YAML parsing for incremental config
- refactor: clean up configuration code

### 5.2 Update GitHub Actions Workflow
**File:** `.github/workflows/design-to-code-pipeline.yml`

**Steps:**
1. Write test for workflow YAML syntax
2. Add workflow input for incremental generation enabled flag
3. Add workflow input for conflict resolution strategy
4. Update pipeline job to pass incremental configuration
5. Add step to upload conflict reports
6. Update PR summary to include incremental generation info
7. Make test pass
8. Validate workflow syntax
9. Refactor for clean workflow

**Tests:**
- Test: Workflow YAML is valid
- Test: Workflow includes incremental generation inputs
- Test: Workflow passes incremental configuration to pipeline
- Test: Workflow uploads conflict reports
- Test: PR summary includes incremental generation information

**Commits:**
- test: add workflow YAML syntax tests
- feat: add incremental generation enabled input to workflow
- feat: add conflict resolution strategy input to workflow
- feat: update pipeline job to pass incremental config
- feat: add conflict reports upload step
- feat: update PR summary with incremental generation info
- refactor: clean up workflow YAML

### 5.3 Update Documentation
**File:** `README.md`

**Steps:**
1. Write test for documentation completeness
2. Add section on incremental code generation
3. Document configuration options
4. Document conflict resolution strategies
5. Add examples of incremental generation scenarios
6. Update architecture diagram to include incremental flow
7. Make test pass
8. Validate documentation links
9. Refactor for clear documentation

**Tests:**
- Test: Documentation includes incremental generation section
- Test: Documentation describes configuration options
- Test: Documentation describes conflict resolution strategies
- Test: Documentation includes examples
- Test: Architecture diagram includes incremental flow
- Test: Documentation links are valid

**Commits:**
- test: add documentation completeness tests
- docs: add incremental code generation section
- docs: document configuration options
- docs: document conflict resolution strategies
- docs: add incremental generation scenario examples
- docs: update architecture diagram
- refactor: clean up documentation

### 5.4 Add Integration Tests for Incremental Generation
**File:** `src/test/kotlin/com/designtocode/integration/IncrementalGenerationIntegrationTest.kt`

**Steps:**
1. Write failing integration test for incremental generation
2. Set up test project with existing code
3. Make spec changes (add endpoint, modify DTO)
4. Run pipeline with incremental generation enabled
5. Verify only affected files are modified
6. Verify existing code is preserved
7. Verify pipeline succeeds
8. Make test pass
9. Write integration test for conflict resolution
10. Write integration test for full generation fallback
11. Refactor for clean test code

**Tests:**
- Test: Integration test for successful incremental generation
- Test: Integration test for conflict resolution
- Test: Integration test for full generation fallback
- Test: Integration test verifies selective file modification
- Test: Integration test verifies code preservation

**Commits:**
- test: add incremental generation integration test
- test: add conflict resolution integration test
- test: add full generation fallback integration test
- test: add selective modification verification
- test: add code preservation verification
- refactor: clean up integration test code

---

## Implementation Notes

### Branch Strategy
- Each phase is implemented in a separate feature branch
- Branch naming convention: `feature/{description}`
- After phase completion, use @workflow:pepare-pr to create PR and merge to main
- Do not use feature branch after merge (per global rules)
- Start next phase from updated main branch

### Commit Strategy
- Follow TDD: Write failing test first, then implementation using @skills:tdd-expert
- Make granular commits for each sub-task using @workflow:pre-commit
- Use conventional commit format as in @workflow:commit-message-convention
- Each commit should be independently testable
- Commit frequently to avoid large diffs and avoid big commits

### Testing Strategy
- Write failing test before implementation (TDD) @skills:tdd-expert
- Unit tests for individual components
- Integration tests for cross-component interactions
- End-to-end tests for complete pipeline execution
- Use Testcontainers for integration testing
- All tests must pass before commit
- All linting must pass before commit
- Before commit, invoke @workflow:pre-commit

### Code Quality Standards
- Maintain Hexagonal Architecture pattern
- Follow Kotlin idiomatic practices using @skills:kotlin-spring-dev
- Ensure all tests pass before committing
- Use conventional commit format as in @workflow:commit-message-convention
- Update documentation with each phase
- Follow global rules for branch management
- Always follow @rules:cascade-response-spec

### Dependencies Between Phases
- Phase 0: No dependencies (prerequisite)
- Phase 1: Depends on Phase 0 (SpecDiffParser, SpecChange)
- Phase 2: Depends on Phase 1 (ImpactAnalyzer, ImpactReport)
- Phase 3: Depends on Phase 2 (PromptConstructor updates, CodeContextPreserver)
- Phase 4: Depends on Phase 3 (Selective generation)
- Phase 5: Depends on Phase 4 (Conflict detection/resolution)

### Risk Mitigation
- Implement features incrementally
- Maintain backward compatibility with full generation mode
- Provide fallback to full generation on failures
- Monitor for regressions after each phase
- Keep phases small and focused
- Add feature flags to enable/disable incremental generation
- Implement conflict detection to prevent data loss
- Add manual review step for unresolvable conflicts

### Success Criteria Check
- [ ] Diff parsing correctly identifies specification changes
- [ ] Impact analysis maps changes to affected code files
- [ ] Incremental prompts preserve existing code context
- [ ] Selective generation modifies only affected files
- [ ] Conflict detection identifies merge conflicts
- [ ] Conflict resolution applies appropriate strategies
- [ ] Configuration options allow enabling/disabling incremental generation
- [ ] Detailed logging provides visibility into incremental generation
- [ ] CI/CD pipeline integrates incremental generation seamlessly
- [ ] Documentation clearly explains incremental generation behavior
- [ ] Integration tests validate end-to-end incremental flow
- [ ] All tests pass (unit, integration, end-to-end)
- [ ] All linting passes (Detekt)
- [ ] Code coverage remains at 100%

---

## Authorization Required

This implementation plan is not ready for review. Please authorize before beginning implementation of Phase 0.
