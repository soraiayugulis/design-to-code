# Fix Analysis: Pipeline Fails Terminally on Uncompilable Generated Code (kapt `NonExistentClass`)

## Summary

| | |
|---|---|
| **Status** | Planned — awaiting approval |
| **Severity** | High — any LLM generation that does not compile fails the whole run with no recovery path |
| **Affected components** | `PipelineOrchestrator`, `QualityGateValidator`, `GeneratedResponseParser`, `GeneratedOutputVerifier`, `CorrectiveRetryOrchestrator` |
| **Detected** | 2026-09-24, GitHub Actions run on `where-am-i` (model `qwen2.5-coder:7b`) |
| **Fix branch** | TBD |

## Symptom

The "Run design-to-code pipeline" step failed at Stage 6 (Quality Gate Validation):

```
Gradle build failed with exit code 1:
app/build/tmp/kapt3/stubs/debug/com/whereami/presentation/settings/SettingsScreenKt.java:6:
error: incompatible types: NonExistentClass cannot be converted to Annotation
Pipeline failed: Gradle build failed with exit code 1. ...
Error: Process completed with exit code 1.
```

Generation succeeded (1 file written), output verification passed (`modifiedTargets=1/1, violations=0`), then the Gradle build failed ~26s in and the pipeline exited.

## Root Cause Analysis

The kapt error is a symptom, not a definitive diagnosis of the offending source. kapt can emit `NonExistentClass` into a Java stub when a Kotlin type cannot be resolved (including annotation types), and `javac` then fails with this message. The excerpt alone does not establish which symbol was missing or whether the generated file, dependencies, or pre-existing source caused it; the full Gradle diagnostics and generated diff are needed. The confirmed pipeline defect is architectural: **nothing between "bytes written to disk" and "full Gradle build" validates that the generated code compiles, and a build failure is a terminal dead-end.**

### RC1 — Generated output is written with zero semantic validation

`GeneratedResponseParser` extracts code blocks and writes them to disk. The only gate is path safety (`allowedRoots`, traversal). File content is never parsed, type-checked, or even sanity-checked — whatever the model emits lands in the worktree.

### RC2 — Output verification is structural only

`GeneratedOutputVerifier.verify()` checks three invariants: rejected paths, declared targets modified, unexpected files. It never compiles. A file containing garbage passes `passed=true` as long as it sits at the declared path. This is by design (the original spec scoped validation to *where* output lands), but it means the first compile check in the entire pipeline is the full `clean build`.

### RC3 — The corrective loop does not cover build failures

`CorrectiveRetryOrchestrator` feeds verification violations (wrong path, target not modified) back to the model — but it is only invoked from `OutputVerificationStage`. `PipelineOrchestrator.execute()` treats a quality-gate failure as terminal: log error → `PipelineResult(success=false)`. The dominant real-world failure mode of small local models — uncompilable output — has no retry path, despite being the failure LLMs are *best* at fixing when shown compiler errors.

### RC4 — Error extraction loses the actionable error

`QualityGateValidator.extractCompilationErrors` grabbed the javac stub error (`NonExistentClass cannot be converted to Annotation`) — an artifact, not necessarily the primary diagnosis. If the build output contains a Kotlin diagnostic (`e: file://...SettingsScreen.kt:LN:COL Unresolved reference: X`), it should be preferred. The supplied excerpt does not establish whether that diagnostic existed in the full build output. The log message lacks enough context for triage.

### RC5 — Failed runs leave broken code in the worktree

On failure the pipeline returns immediately. The uncompilable `SettingsScreen.kt` remains modified in the workspace — contaminating the next run's baseline and any manual follow-up. There is no rollback of `generatedFiles` to their pre-run state.

## Why Existing Tests Did Not Catch It

1. **All pipeline tests inject fakes.** `PipelineOrchestratorTest` uses `FakeQualityGate` and a canned `AIAgentPort`. A deterministic fake cannot reproduce "a 7B model invents a nonexistent annotation."
2. **The failure lives in the target project.** This repo's test suite has no Android/kapt toolchain and cannot compile-check code written into an external workspace.
3. **The behavior under test does not exist.** No test can assert "build failure → corrective regeneration" because that path is absent from the design. The existing suite faithfully verifies the current (terminal) contract.
4. **No e2e coverage of the real path.** There is no test wiring a real LLM response containing uncompilable code through generation → verification → quality gate.

## Corrections (Implementation Plan)

Selected options: **B + A + E** (compile-check → corrective loop → real-error extraction), plus **D** (workspace rollback) and graceful-failure logging. Implemented as five phases, each independently mergeable.

| # | Fix | Option | Files |
|---|-----|--------|-------|
| C1 | **Rich build-error extraction.** Parse kotlinc `e: file://…:line:col` lines (and javac `error:` as fallback), dedupe, cap at ~10 lines, classify failure as `COMPILATION` / `TEST` / `COVERAGE` / `LINT` / `TIMEOUT` / `UNKNOWN`. Surfaced in `QualityGateResult.errorMessage` and logs. | E | `QualityGateValidator`, `QualityGateResult` |
| C2 | **Cheap compile gate before the full build.** New config `build.compileTasks` (default derived from detected `TechStack`: `ANDROID` → `compileDebugKotlin`, `SPRING_BOOT`/`QUARKUS` → `compileKotlin`, `UNKNOWN` → skip). Runs after output verification, before `clean build`. | B | `QualityGateValidator`, `PipelineConfig`, `ConfigLoader`, `PipelineOrchestrator` |
| C3 | **Build-failure corrective loop.** On `COMPILATION` failure, feed the extracted errors into a corrective prompt (`"The generated code failed to compile: <errors>. Fix only these errors…"`) and regenerate, up to `qualityGate.maxBuildRetries` (default 2). Reuses the `CorrectiveRetryOrchestrator` pattern: early-stop on identical error signature, restore last-good file state between attempts. | A | `PipelineOrchestrator`, new `BuildFailureRetryOrchestrator` (or extend `CorrectiveRetryOrchestrator`), `PipelineConfig`, `ConfigLoader` |
| C4 | **Workspace rollback on terminal failure.** Snapshot allowed target files (content and existence) before generation; restore only paths written by this run on failure, preserving pre-existing user edits. Never run `git checkout` over user changes; report any restore failure. | D | `PipelineOrchestrator`, workspace snapshot component |
| C5 | **Graceful-failure report.** On failure, log a structured summary: failed stage, failure category, extracted errors, affected files, corrective attempts consumed — replacing the single-line `Pipeline failed: …`. | — | `PipelineOrchestrator` |

### Proposed config additions (`pipeline.yml`)

```yaml
build:
  gradleTasks: [clean, build]
  compileTasks: [compileDebugKotlin]   # optional; derived from TechStack if omitted, empty = skip

qualityGate:
  maxBuildRetries: 2                   # corrective regenerations on COMPILATION failure
```

## Verification

- `QualityGateValidatorTest`: build output containing `e: file://…/Foo.kt:12:5 Unresolved reference: Bar` → category `COMPILATION`, error message contains `Unresolved reference: Bar` (not the stub artifact).
- `ConfigLoaderTest`: `build.compileTasks` and `qualityGate.maxBuildRetries` parsed; defaults when omitted.
- `PipelineOrchestratorTest`: fake quality gate returning `COMPILATION` failure → AI agent invoked again with a prompt containing the compiler errors; identical error signature → early stop; retries exhausted → `PipelineResult(success=false)` with summary.
- Rollback test: workspace file modified by fake generation, pipeline fails → file content restored; new file → deleted.
- Compile-gate test: `compileTasks` failure skips the full `gradleTasks` build.
- Full suite + Detekt green.

## Trade-offs Accepted

- **Cost per retry**: each corrective attempt = one full LLM generation (~7min in this CI run) + one compile check (~10–30s). Bounded by `maxBuildRetries`; the cheap compile gate (C2) keeps the loop from paying for `clean build` each round.
- **Nondeterminism**: the loop reduces failure probability, not to zero — a small model may still exhaust retries. The improvement is that it *can* recover instead of never trying.
- **`compileTasks` default by `TechStack`**: convenient but opinionated; `UNKNOWN` stacks skip the check entirely (documented, overridable via config).
- **Rollback via pre-generation snapshot**: costs memory/disk for affected files but preserves user edits and avoids resetting the whole Git worktree. A failed restoration is logged explicitly.

## Lessons Learned

- A pipeline that generates code needs a **compile-feedback loop**, not just structural validation. The cheapest place to catch uncompilable output is right after generation, not at the terminal gate.
- Error extraction should prefer the *primary* diagnostic (kotlinc `e:` lines) over downstream artifacts (kapt stub javac errors); stub errors name a generated file, not the offending symbol.
- Every failure path should leave the workspace as it found it — failed automation must not leak broken state.
- Failure-mode spec reviews should ask, for every terminal gate: "is this failure recoverable, and if so, who gets the feedback?"
