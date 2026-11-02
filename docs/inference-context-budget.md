# Inference context budget

LiteRT-LM EngineConfig.maxNumTokens is the total input/output KV-cache capacity,
not a response-only limit. General models now use 4096 total tokens and an
explicit 1024-token ConversationConfig.maxOutputToken ceiling. Spark retains
4096 total tokens and 1536 output tokens.

Previously, general models received only 1024 total tokens while conversation
history was sized for a 4096-token window. Capability/safety instructions, the
current question, retained history, and output all competed for that small
capacity. This can cause exhaustion after only a few turns. The current fix
separates the settings and caps persistent summaries at the memory budget.

Native conversations are reset before direct-chat turns; retained text comes
from ConversationContextManager. This fix does not introduce unbounded native
history or increase the number of tool steps. The character-based history
heuristic is not an exact tokenizer: large requests, images, or token-dense
languages can still exhaust a finite window. Token-aware admission control
remains a separate improvement.

Regression checks: ModelRuntimeProfileTest, ConversationContextManagerTest,
and .maestro/context_window_validation.yaml on an English-configured device
with an active installed model. The device flow adds three test messages.

The system_server AppSearch error for android$apps-db/builtin:MobileApplication
concerns Android's installed-app search corpus. It is separate from Chevere's
Room-backed document retrieval and the local model KV cache. That log alone
does not establish the cause of a model error; do not clear app data or the
system search database as an inference fix.

SDK configuration reference:
https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt
