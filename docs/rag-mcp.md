# Documents and MCP

## Local documents

In chat, open **Add attachment → Import document** and select a UTF-8 `.txt` or `.md` file (up to 2 MB). Chevere copies text into its private local index; subsequent searches do not need continued access to the source URI. Reimporting the same URI atomically replaces its chunks.

Import displays an indexing result and prepares `/docs Summarize <filename>` in the composer. Ask `/docs What is the warranty policy?` or `Search my documents for warranty`. Search currently spans the indexed library, returns up to three chunks, and uses lexical TF-IDF matching. It does not provide semantic embeddings or PDF extraction. Name the relevant document in your question when needed.

## Runtime prompt and grounding

This file is developer documentation, not a prompt automatically sent to the local LLM. `ChatRequestRouter` selects document routing; `ToolRegistry` supplies the tool descriptions and grounding instructions. The model calls `document_rag`, receives retrieved excerpts as a tool observation, and generates an answer. It receives at most three chunks, so a long-document summary may omit sections. Filename mentions restrict the candidate chunks to matching documents.

## Remote MCP tools

Ask `/mcp list tools at https://your-server.example/mcp`. Confirm the connection, then ask `/mcp call <tool> at https://your-server.example/mcp with arguments {...}`. The agent uses the `mcp` bridge; every remote operation requires confirmation. Tool calls verify that the server advertises the requested tool before execution.

This initial client supports HTTPS Streamable HTTP, initialization/version negotiation, session headers, JSON and SSE responses, paginated tool discovery, and tool calls. It accepts protocol versions 2025-03-26, 2025-06-18, and 2025-11-25. Endpoints are supplied per request; no credentials are stored. Servers requiring OAuth or API tokens are not supported yet, nor are stdio, resources, prompts, sampling, elicitation, resumable streams, or background subscriptions. Network failures are surfaced without automatically replaying remote actions.

Remote server output and document excerpts are untrusted content. The local model still decides how to use returned results; this implementation does not guarantee model adherence to grounding instructions.

## Validation

Focused tests: `ChatRequestRouterTest`, `DocumentRagTest`, `DocumentImportTest`, `ChatTaskActionTest`.

Picker smoke test: `.maestro/document_import_validation.yaml`. Requires a connected device with an initialized chat model. Full MCP interoperability should additionally be checked against the intended server before relying on its tools.

Validation recorded for this change: focused unit tests and debug assembly passed; APK installation/launch and the picker smoke flow passed on ASUSAI2501C. Full on-device document retrieval and live MCP interoperability remain unverified.
