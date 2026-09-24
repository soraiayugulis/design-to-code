# Fix Analysis: AI Generation Hang in CI (Read-Timeout Retry Loop)

## Summary

| | |
|---|---|
| **Status** | Fixed |
| **Severity** | High — pipeline stage blocks ~30 minutes, then fails |
| **Affected component** | `OllamaAdapter`, `PipelineOrchestrator.executeAIGeneration` |
| **Detected** | 2026-09-24, GitHub Actions run on `where-am-i` |
| **Fix branch** | `fix/ai-generation-timeout-handling` |

## Symptom

The "Run design-to-code pipeline" step appeared hung for 23+ minutes. The last log line was `Prompt length: 19018 characters` — the Ollama request was in flight with no further output. On a local machine the same prompt completed in ~260s.

## Root Cause Analysis

Four compounding defects, none of which was caught by the output-validation spec because that work scoped itself to *where generated output lands*, not *how the generation call behaves under slow inference*.

### RC1 — `withTimeout` cannot interrupt a blocking call

`generate()` wrapped `callOllamaAPI()` in `kotlinx.coroutines.withTimeout`. Coroutine cancellation is **cooperative**: it is delivered only at suspension points. `HttpURLConnection.readText()` is a blocking, non-suspending call — the timeout could never fire while the socket read was in progress. The only effective bound was `HttpURLConnection.readTimeout`, which is a **per-read** timeout, not a total-duration budget: a response delivered in trickling chunks would never trigger it.

### RC2 — Deterministic failures classified as transient

When the socket `readTimeout` (600s) fired, the error surfaced as `"Read timed out"`. `executeAIGeneration`'s `isTransientFailure` predicate matched `"timed out"`/`"timeout"` → `RetryHelper` retried — up to `maxAttempts` (3). A generation timeout is **deterministic for an identical workload**: the same prompt to the same overloaded model reproduces the same duration, so every retry was guaranteed to burn another full timeout budget. Total stall ≈ `timeoutMs × maxAttempts` (~30min at 600s/3), matching the observed 23min+.

### RC3 — CPU-only CI inference exceeds the timeout budget

The timeout was sized on a machine with Metal acceleration. A GitHub Actions runner is CPU-only; evaluating a ~5k-token prompt and generating ~3k tokens at ~10–15 tok/s crosses 600s easily. The budget was never re-derived for CI hardware.

### RC4 — Silent prompt truncation risk (`num_ctx`)

Ollama's effective context window depends on the model default/server config. A ~19k-char prompt plus a multi-thousand-token response risks silent truncation — the model would see a cut prompt and produce ungrounded output, indistinguishable from a bad generation.

## Corrections

| # | Fix | File |
|---|-----|------|
| C1 | Blocking call moved to `Dispatchers.IO`; `invokeOnCompletion` hook calls `connection.disconnect()` on cancellation, so `withTimeout` now enforces a **total** budget and aborts the in-flight socket read | `OllamaAdapter.callOllamaAPI` |
| C2 | `isTransientFailure` split: `read timed out` / `generation timeout` → **non-transient** (fail after one budget); `connection`, `connect timed out`, `network`, `502/503/504` → transient | `PipelineOrchestrator` (regex-based predicates) |
| C3 | `ai.numCtx` config option; when set, the adapter sends `options.num_ctx` in the Ollama request | `AIConfig`, `ConfigLoader`, `OllamaAdapter`, `pipeline.yml.example` |
| C4 | Per-call duration logged (`Ollama API call completed in Nms`) for observability | `OllamaAdapter.generate` |

## Verification

- `OllamaAdapterHttpTest`: a response trickled in chunks (each under the per-read timeout, total over budget) now aborts with a timeout error — fails before C1, passes after.
- `PipelineOrchestratorTest`: `"Read timed out"` → exactly 1 AI call, no retry, pipeline fails. `"connection timeout"` remains transient and is still retried.
- `ConfigLoaderTest`: `ai.numCtx` parsed; omitted → `null`, request carries no `options`.
- Full suite + Detekt green.

## Operational Guidance

- **CPU-only runners**: set `ai.timeoutMs` to cover worst-case generation (e.g. `1800000`). Timeouts now fail once instead of retrying — size the budget for a single honest attempt.
- **Large prompts**: set `ai.numCtx` explicitly (e.g. `32768`) so Ollama does not silently truncate.
- Long-term leverage remains the scoped-edit format (search/replace or line-range `MODIFY:`), which reduces output size — the dominant cost — by ~10–50× for surgical changes.

## Lessons Learned

- `withTimeout` around blocking IO is decorative; enforce cancellation via `disconnect()`/`close()` on the underlying resource, or use a client with native async support.
- "Timeout" is not a transient-failure synonym. Classify by *what* timed out: connect/gateway (transient) vs read/generation for an identical workload (deterministic).
- Timeouts sized on developer hardware do not transfer to CI runners; budgets must be derived from the slowest supported environment.
- Spec reviews should include a failure-mode pass over every blocking external call: what is the total budget, is cancellation effective, and does retry make the failure worse?
