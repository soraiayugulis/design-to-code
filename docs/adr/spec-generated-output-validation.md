# Spec: Generated Output Grounding & Placement Validation

## Status: Implemented — `feature/design2code-generated-output-validation` (commits `7bb303b`…`300c14a`)
## Related: `docs/adr/spec-incremental-code-generation.md` (broader initiative — this spec extracts and delivers the incident-driven chain independently)

This spec defines the minimal, deterministic chain that prevents the pipeline from producing a "harmless but useless" diff: generated files must land inside configured source roots, spec-declared target files must be grounded in the prompt and verified as modified, and violations trigger a bounded corrective retry before failing the run.

---

## 1. Origin — Incident (change-language-setting-color)

Observed on `where-am-i`, spec `design/change-language-setting-color.yaml`, branch `feature/ai-gen-fc0dfece`:

- The spec declared `target.file: app/src/main/java/com/whereami/presentation/settings/SettingsScreen.kt`, but the model emitted `src/main/kotlin/com/whereami/presentation/settings/SettingsScreen.kt` — outside the `:app` module's source set. The file was never compiled, the real target was left untouched, `testDebugUnitTest` passed vacuously, and a PR was opened with a useless diff.
- `PromptConstructor.appendResponseFormat` hardcodes `src/main/kotlin/com/example/UserController.kt`, priming the model toward Maven-style roots that may not exist in the target project.
- `OllamaAdapter.isPathSafe` only blocks `..`, absolute paths and out-of-workspace paths — any in-workspace path is silently accepted.
- The spec's `target.file`/`target.composable` fields were never read; the target file's current content was never injected into the prompt, so the model rewrote the screen from hallucination instead of editing it.

## 2. Scope

**In scope:**
- Extract `target` declarations from spec YAML files
- Ground the prompt: allowed output roots, target file content, placement instructions
- Enforce allowed roots when writing generated files (code blocks, `MODIFY:`, `DELETE:`)
- Post-generation verification: placement, declared-target modification, unexpected files
- Bounded corrective retry with violation-specific feedback
- `ANDROID` tech stack detection + bundled default `android-rules.md`

**Out of scope:**
- Impact analysis / dependency-graph traversal (`ImpactAnalyzer`, `ImpactReport`)
- Selective/partial generation (`generateSelective`, `CodeContextPreserver`)
- Conflict detection & resolution
- CI/CD workflow changes (inputs, step summary, PR annotations)
- Symbol-scoped diff verification (`target.composable` is a prompt hint only — no AST/hunk analysis)
- `MODIFY:` line-range semantics (unchanged; full-file rewrite is the guided path)

## 3. Locked Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | `allowedRoots` configured in target-workspace `pipeline.yml` under `outputValidation`; fallback auto-detection of existing `**/src/{main,test}/{java,kotlin}` dirs | Explicit > implicit; fallback covers unconfigured projects |
| D2 | `UNEXPECTED_FILE` violations are report-only by default (`strictMode: false`) | Avoid false positives on legitimate companion files |
| D3 | Corrective retry reuses the original prompt + appended feedback section, conditional on violation type | Simplest effective correction; keeps model context |
| D4 | Response parseable but 100% rejected → `GenerationResult(success=true, generatedFiles=[], rejectedFiles=[...])`; verifier produces the deterministic failure | Single, explained failure point instead of split semantics |
| D5 | `android-rules.md` bundled as classpath resource; workspace `rules/android-rules.md` takes precedence | Every Android target gets sane conventions without repo setup |
| D6 | `target.composable`/`<symbol>` used only as prompt hint | Symbol-level diff verification is disproportionately complex for the value |
| D7 | Verification compares workspace state via `git status --porcelain` snapshot taken at pipeline start — never `git.baseRef` | `baseRef` describes the spec diff, not the generation baseline; also captures untracked files |
| D8 | Declared `target.file` outside `allowedRoots` fails fast at extraction — before any AI call | Model cannot satisfy it; retrying wastes tokens |

## 4. Contracts & Schemas

### 4.1 `pipeline.yml` (target workspace)

```yaml
outputValidation:
  enabled: true               # false skips extraction, enforcement, verification and retry
  strictMode: false           # true → UNEXPECTED_FILE fails the run
  maxCorrectiveRetries: 2     # corrective attempts after the first generation
  allowedRoots:               # optional; auto-detected when absent/empty
    - app/src/main/java
    - app/src/test/java
```

`PipelineConfig` gains `val outputValidation: OutputValidationConfig = OutputValidationConfig()`.
`OutputValidationConfig(enabled: Boolean, strictMode: Boolean, maxCorrectiveRetries: Int, allowedRoots: List<String>)`.

### 4.2 `SpecTarget` (new model)

```kotlin
data class SpecTarget(
    val filePath: String,      // normalized, workspace-relative
    val symbol: String?,       // first of target.composable / target.class / target.function
    val exists: Boolean        // resolved against workspace at extraction time
)
```

Spec `target:` is a single map: `file` (required when `target` present) + optional `composable`/`class`/`function`.

### 4.3 `GenerationResult` (port change)

```kotlin
data class GenerationResult(
    val success: Boolean,
    val generatedFiles: List<String>,
    val errorMessage: String? = null,
    val rejectedFiles: List<RejectedFile> = emptyList()
)

data class RejectedFile(val filePath: String, val reason: String)
```

### 4.4 `OutputVerificationResult` (new model)

```kotlin
data class OutputVerificationResult(
    val passed: Boolean,
    val writtenFiles: List<String>,
    val rejectedFiles: List<RejectedFile>,
    val expectedTargetFiles: List<String>,
    val modifiedTargetFiles: List<String>,
    val violations: List<Violation>
)

data class Violation(val type: ViolationType, val filePath: String, val message: String)

enum class ViolationType { OUTSIDE_SOURCE_ROOT, TARGET_NOT_MODIFIED, UNEXPECTED_FILE }
```

### 4.5 Prompt layout (new sections, in order)

1. *(existing)* global rules, framework rules, project context, spec files, spec changes
2. `## Allowed Output Roots` — verbatim list of resolved roots
3. `## Target Files` — for each `SpecTarget` with `exists=true`: path + current content, truncated at 20 000 chars with `// ... [truncated]` marker; absent targets listed as `(new file — create at this path)`
4. `## Response Format` — example path built from first allowed root (e.g. `app/src/main/java/com/example/Feature.kt`); falls back to the generic example when no roots resolved
5. Placement instruction: existing target → "apply the change via `MODIFY:<target.file>` with the complete new file content"; absent target → "emit a ` ```<lang>:<target.file>` block"; "do not create files outside Allowed Output Roots; do not create files the spec does not declare"
6. `target.symbol`, when present → "the change must be scoped inside `<symbol>`"

## 5. Facts (observable behavior)

- **F1.** Given a spec declaring `target.file` that exists in the workspace, when the prompt is built, then it contains the Allowed Output Roots section, the file's current content, and an in-place `MODIFY` instruction naming that path.
- **F2.** Given a spec without `target`, when the prompt is built, then no Target Files section is emitted and prompt construction succeeds.
- **F3.** Given a generated path outside allowed roots (code block, `MODIFY:` or `DELETE:`), when the response is applied, then the file is not written/deleted and a `RejectedFile` with reason is recorded and logged with the allowed roots.
- **F4.** Given a generated path inside an allowed root, when the response is applied, then the file is written and appears in `generatedFiles`.
- **F5.** Given a parseable response where every file is rejected, when `generate` returns, then `success=true`, `generatedFiles` is empty and `rejectedFiles` is populated (D4).
- **F6.** Given a declared existing `target.file` absent from the post-generation change set, when verification runs, then a `TARGET_NOT_MODIFIED` violation is produced and verification fails.
- **F7.** Given a declared `target.file` that did not exist pre-run and was created by generation, when verification runs, then the target counts as satisfied.
- **F8.** Given a written file that is not a declared target, when `strictMode=false`, then an `UNEXPECTED_FILE` violation is recorded and verification still passes; when `strictMode=true`, verification fails.
- **F9.** Given verification failure, when corrective retry is enabled and attempts remain, then the original prompt plus a violation-specific feedback section is sent again; when the identical violation signature repeats, then retry stops early.
- **F10.** Given verification failure after retries are exhausted, when the pipeline continues, then Git Operations/PR creation is skipped and the pipeline result is failed.
- **F11.** Given `outputValidation.enabled=false`, when the pipeline runs, then extraction, root enforcement, verification and corrective retry are all skipped — behavior equals current main.
- **F12.** Given a spec whose declared `target.file` is outside all allowed roots, when extraction runs, then the pipeline fails fast with a spec/configuration error before any AI call (D8).
- **F13.** Given no `allowedRoots` configured, when the pipeline starts, then roots are auto-detected from existing `**/src/{main,test}/{java,kotlin}` directories; if none resolve, validation degrades to workspace-containment with a warning.
- **F14.** Given a workspace without `rules/android-rules.md` and `TechStack.ANDROID` detected, when the prompt is built, then the bundled default android rules are included.
- **F15.** Given the `change-language-setting-color` scenario re-run end-to-end, then the produced diff touches the real `SettingsScreen.kt` or the run fails loudly before PR creation.

## 6. Rules (deterministic)

- **R1 — Path normalization:** declared paths are normalized (`./` stripped, `\` → `/`, canonicalized) before comparison; containment uses `Path.startsWith`, never string-prefix on raw paths.
- **R2 — Root resolution:** `allowedRoots` config → else auto-detect → else workspace root with WARN log.
- **R3 — Enforcement point:** every write/delete inside `GeneratedResponseParser` (extracted from `OllamaAdapter`) passes `SourceRootValidator`; rejections never touch disk.
- **R4 — Target modification check:** target is "modified" iff it appears in the post-generation `git status --porcelain` delta vs the pipeline-start snapshot (modified, added, or untracked).
- **R5 — Feedback section:** `OUTSIDE_SOURCE_ROOT` → rejected paths + allowed roots; `TARGET_NOT_MODIFIED` → expected target list; both → both blocks.
- **R6 — Retry bounds:** at most `maxCorrectiveRetries` corrective attempts; identical sorted `(type, filePath)` violation signature between consecutive attempts → early stop.
- **R7 — Retry separation:** `RetryHelper` handles transient transport failures only; `CorrectiveRetryOrchestrator` handles deterministic violations only.
- **R8 — Verifier determinism:** `GeneratedOutputVerifier` performs no AI calls.
- **R9 — Stage order:** Output Verification runs after AI Generation and before Quality Gate; failure skips Git Operations and marks the pipeline failed.

## 7. Edge Cases

- Declared target under a symlinked path → canonical resolution decides containment.
- Nested/overlapping allowed roots (`app/src` and `app/src/main/java`) → longest-prefix match; both valid.
- Model emits `MODIFY:` for a nonexistent file → ignored (existing behavior); if that path is a declared absent target, F6/F7 governs.
- File written then deleted within one response → net effect evaluated on final workspace state.
- Retry attempt writes a subset of attempt 1's files → verification evaluates final workspace state; leftovers count as `UNEXPECTED_FILE` (report-only by default).
- `target:` present without `file:` → extraction error (malformed spec), fail fast.
- Multiple spec files each declaring `target` → all collected; all must satisfy the modification check.

## 8. Error Conditions

| Condition | Behavior |
|-----------|----------|
| `target.file` outside allowed roots | Fail fast pre-generation (D8) |
| Malformed spec `target` section | Fail fast with spec error |
| All generated files rejected | `success=true` + `rejectedFiles` → verifier fails → corrective retry → pipeline fail |
| Verification failure, retries exhausted | Skip git ops/PR, `PipelineResult(success=false)` with violations in message/log |
| No allowed roots resolvable | Degrade to workspace containment + WARN (F13) |
| `enabled=false` | Feature fully inert (F11) |

## 9. Tasks

Dependency-ordered; each task = objective + file + acceptance criteria. TDD per project rules: behavior-level failing test first; no tautological tests.

- **A1 — `OutputValidationConfig`** — `config/PipelineConfig.kt`, `config/ConfigLoader.kt`, `pipeline.yml.example`. Parses §4.1 schema with documented defaults. *Blocks: A3, A5, A7.*
- **A2 — `SpecTarget` + `SpecTargetExtractor`** — `domain/model/SpecTarget.kt`, `domain/SpecTargetExtractor.kt`. Parses `target` map (snakeyaml), resolves paths vs workspace, enforces D8 and malformed-spec errors.
- **A3 — `SourceRootValidator`** — `validation/SourceRootValidator.kt`. R1/R2 containment + boundary checks (`..`, absolute, canonical). Depends: A1.
- **A4 — Write-path enforcement** — `domain/adapter/OllamaAdapter.kt`, `domain/port/AIAgentPort.kt`. Applies A3 to code blocks/`MODIFY:`/`DELETE:`; §4.3 `rejectedFiles`; per-rejection logging (F3–F5). Depends: A3.
- **A5 — Prompt grounding** — `domain/PromptConstructor.kt`. §4.5 sections, target truncation, conditional MODIFY/create instruction, allowed-root example path. Depends: A1, A2.
- **A6 — `OutputVerificationResult` + `Violation`** — `domain/model/OutputVerificationResult.kt`. §4.4 models.
- **A7 — `GeneratedOutputVerifier`** — `validation/GeneratedOutputVerifier.kt`. R4/R8; consumes A2 targets + A4 results + start snapshot. Depends: A1, A2, A6.
- **A8 — `CorrectiveRetryOrchestrator`** — `domain/CorrectiveRetryOrchestrator.kt`. R5–R7: feedback builder, bounded attempts, identical-signature early exit, per-attempt results. Depends: A7.
- **A9 — Orchestrator integration** — `cli/PipelineOrchestrator.kt`. Capture `git status --porcelain` snapshot at start; insert Output Verification stage (R9); wire retry; record metrics; honor `enabled`. Depends: A4, A7, A8.
- **A10 — Android support** — `domain/model/TechStack.kt`, `domain/ContextBuilder.kt`, `domain/PromptConstructor.kt`, `src/main/resources/rules/android-rules.md`. Detect `com.android.application|library`; bundled rules as workspace fallback (F14). Independent — may run parallel to A3–A9.
- **A11 — Docs** — `docs/ai-response-format.md` (rooted example + rejection semantics), `README.md` (outputValidation config). Depends: A1–A9.

### Implementation deltas (as-built vs spec)

- **A4** — the write-path gate was extracted into `GeneratedResponseParser` (`domain/adapter/`): `OllamaAdapter` keeps HTTP concerns only, and the parser is unit-testable without reflection.
- **A8** — the corrective `generate` is `suspend (String) -> GenerationResult`, not `AIAgentPort`: the pipeline wraps each corrective call in `RetryHelper` transient backoff, keeping R7 without duplicating retry code. Bundles `CorrectionRequest` (A8) and `OutputVerificationRequest` (A9) were added to satisfy parameter-count limits.
- **A9** — a dedicated `OutputVerificationStage` (`validation/`) owns root resolution, target extraction, baseline capture and the verify + corrective-retry sequence; `PipelineOrchestrator` calls a single `verify(request, generate)`. This keeps the orchestrator within size limits and the concern cohesive.
- **A10** — `ContextBuilder` additionally scans module `*/build.gradle.kts` files (sorted, ignoring `build`/`out`/`.gradle`/`.git`/`node_modules`) when the root file does not declare the stack — required for multi-module Android projects such as where-am-i, whose plugin lives in `app/build.gradle.kts`. The bundled-rules mechanism is generic (`/rules/<file>` classpath fallback for any framework); only `android-rules.md` is bundled today.

## 10. Test Cases

| Fact | Test |
|------|------|
| F1/F2 | PromptConstructor: roots section, embedded target, absent-target path, no-target spec |
| F3/F4 | OllamaAdapter: reject out-of-root block/`MODIFY:`/`DELETE:`; accept in-root |
| F5 | GenerationResult aggregation on all-rejected response |
| F6/F7/F8 | Verifier: unmodified target fails; created target passes; unexpected file report-only vs strict |
| F9 | CorrectiveRetryOrchestrator: feedback content per violation type; early exit on identical signature |
| F10/F11 | PipelineOrchestrator: no PR on verification failure; stage skipped when disabled |
| F12 | SpecTargetExtractor: out-of-root declared target fails fast |
| F13 | Root auto-detection and degrade-to-workspace fallback |
| F14 | Bundled android rules used when workspace lacks the file |
| F15 | Integration: `change-language-setting-color` replay (documented manual scenario) |

## 11. Success Criteria

- [x] Generated file outside allowed roots is never written; rejection reported with reason
- [x] Declared `target.file` unmodified → run fails, no branch push, no PR
- [x] Corrective retry regenerates with violation-specific feedback, bounded, early exit on repeat
- [x] New-file specs pass verification when the target is created
- [x] `enabled=false` reproduces current behavior exactly
- [x] `change-language-setting-color` replay produces a diff on the real `SettingsScreen.kt` or fails loudly
- [x] All unit tests pass; Detekt clean; no tautological tests

### F15 — E2E replay result (2026-09-23, `--dry-run`, `qwen2.5-coder:7b`, 294s)

- Roots auto-detected: `app/src/main/java`, `app/src/test/java`; stack detected: `ANDROID` via `app/build.gradle.kts`; bundled rules injected; target extracted (`SettingsScreen.kt`, symbol `LanguageOption`).
- Generation wrote exactly the declared target — the incident path was not reproduced. `modifiedTargets=1/1`, 0 violations, `testDebugUnitTest` green, Detekt clean, git ops skipped by dry-run.
- **Known limitation:** the verifier proves the target *changed*, not that the change is *correct* — the model emitted `White` instead of the spec'd `DarkBlue`. Semantic verification remains out of scope (no AI-in-the-loop verification); candidates: diff-token assertions in specs or screenshot/golden tests.

## 12. Risks & Mitigations

- **Model ignores grounding anyway** → enforcement at write path (R3) + verifier (R9) is model-independent.
- **Auto-detected roots too broad/narrow** → explicit config wins (R2); roots logged at startup.
- **Corrective loop burns tokens** → bounded retries + identical-signature early exit (R6).
- **Snapshot drift (external writes during run)** → delta is evaluated on spec targets and generated files only; unrelated changes are `UNEXPECTED_FILE` at worst.
- **False UNEXPECTED_FILE noise** → report-only default (D2); strict mode opt-in.

## 13. Open Questions

- ~~Should the corrective feedback include the rejected file's *content* (to salvage intent) or paths only?~~ Resolved: paths only (as built).
- Prompt size guard for multiple/large targets beyond the 20 000-char per-file cap: left unbounded as built (per-file cap only).
- Should verification gain an optional semantic layer (e.g. spec-declared diff assertions) to catch correct-placement/wrong-change outputs like the E2E replay produced?
