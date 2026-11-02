# Chat reliability and evidence

## Context and token usage

`PromptBudget` budgets the complete policy and current request before selecting
background text. It reserves the configured output ceiling, 256 tokens for
template overhead, and a conservative 1,536-token allowance for an attached image.
History can shrink; the current request and policy are never silently truncated.
Oversized requests fail with a localized, actionable message. Spark retains its
4,096/1,536 context/output limits; general models use 4,096/1,024.

Admission is a conservative, language-aware **estimate**, not exact tokenization.
The installed LiteRT-LM 0.19 Kotlin API exposes post-generation counts and
benchmarks but no public preflight tokenizer. Unusual tokenization or image
encoding can still exceed a finite window. Do not describe this guard as an exact
token counter or guarantee against native exhaustion.

The runtime enables SDK benchmark counts. New text responses show measured input
and output counts only when available; unknown counts are omitted. Agent-response
counts sum controller model passes, not the isolated suggestion pass. Historical
estimates retain a `~` label. These counts are usage totals, not a live KV-cache
fill gauge.

Retained memory is bounded to twelve capped messages plus a 1,000-character
capsule. Compaction keeps excerpts, not a guaranteed semantic summary. The former
background LLM summarization job is removed, avoiding extra inference and native
session contention between requests. Native history resets at every controller
inference step because the bounded prompt already supplies retained context.

## Document answers

Document-routed requests retrieve once in the controller before an answer-only
model pass. The model is not offered search tools during synthesis, preventing a
repeat-search loop for a simple lookup. No matches produce a deterministic,
localized response instead of a model-invented document answer.

The data layer returns up to three typed excerpts with filename, chunk number and
text. The presentation layer maps these into expandable **Retrieved excerpts**.
They show what was retrieved, not independently verified citations or proof that
the model used every character. Bounded context may omit some earlier background.
Source paths/URIs are not included in the model's search observations.

Evidence, terminal outcome and measured-vs-estimated count metadata persist in a
versioned data-layer envelope within the existing message text column. Plain
history and the existing v1 task-checklist envelope remain compatible. Malformed
metadata does not render raw JSON. Room schema remains unchanged.

## Runtime and agent status

Chat distinguishes choosing a model, downloading, loading, engine-ready,
generating, awaiting confirmation and failure. A healthy downloaded file alone
does not mark the engine ready. Recovery routes through MVI: reload a failed
model, retry the last response, or open Models for download recovery.

Activity labels describe observed tools and results rather than intermediate
model reasoning. Cancellation and step exhaustion have distinct terminal states;
unresolved tool failures require review. A final answer is labeled response-ready,
not independent verification that an external app completed an action. Pending
confirmation disables submitting another request until it is confirmed/cancelled.
Cancellation cannot undo earlier side effects.

## Validation

Focused coverage: `PromptBudgetTest`, `ConversationContextManagerTest`,
`ChatMessageMetadataCodecTest`, `ChatModelStatusTest`, `AgentOrchestratorTest`,
`ChatRepositoryImplTest`, and `ChatTaskActionTest`. Full debug tests and both debug
and release lint/builds are part of the recorded review.

For `.maestro/document_sources_validation.yaml`, connect a folder containing
`policy.txt` with this UTF-8 fixture, in an English-configured app with a ready model:

```text
The amber otter warranty covers defects for 41 days. The support color is copper. Reliability fixture: Amber Otter.
```

The flow checks retrieved text expansion, history restoration, and no-match
presentation, and rejects a step-limit fallback. It creates a new chat without
deleting existing history. `.maestro/mcp_confirmation_validation.yaml` checks
confirmation/cancellation without confirming the remote operation. Test fixtures
should be disconnected and deleted after testing.

Release readiness is tracked separately in `release-readiness.json`. Run
`python scripts/check_release_readiness.py` for the declaration report, and add
`--strict` before any intended Play release. A green evidence-validation CI job
means the ledger is valid, not that its open gates are closed. No policy/terms,
backend intake, or Play upload is published by these checks.
