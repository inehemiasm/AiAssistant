# Documents and MCP

## Local documents

In chat, open **Add attachment → Import document** and select a UTF-8 `.txt` or `.md` file (up to 2 MB). Chevere copies text into its private local index; subsequent searches do not need continued access to the source URI. Reimporting the same URI atomically replaces its chunks.

Import displays an indexing result and prepares `/docs Summarize <filename>` in the composer. Ask `/docs What is the warranty policy?` or `Search my documents for warranty`. Search currently spans the indexed library, returns up to three chunks, and uses lexical TF-IDF matching. It does not provide semantic embeddings or PDF extraction. Name the relevant document in your question when needed.

### Connected folders

Open **Add attachment → Document folders → Add folder**. Android asks you to
choose a folder and grant access. Chevere persists read access to that tree and
indexes UTF-8 `.txt`/`.md` files recursively. No broad storage permission is
requested; only read access is persisted and source files are not modified.
Cloud-backed document providers may require network
access to supply the selected files; indexing and retrieval happen locally.

The index is a saved snapshot, not a live folder watcher. Use **Refresh index**
after editing, adding, renaming or deleting files. A successful refresh atomically
replaces only that folder's snapshot, including removal of deleted files. Failed
refreshes preserve the previous snapshot. A saved folder remains manageable after
restart or a failed refresh; if access was revoked, select it again with Add folder.

Each refresh is limited to 100 readable text files, 2 MB per file, 16 MB total,
1,000 directory entries and 8 subfolder levels. Unsupported and empty entries are
skipped; unreadable/invalid UTF-8 files and exceeded limits fail the refresh.
Provider results marked as still loading are not published as a complete snapshot.

**Disconnect** deletes that folder's indexed copies and releases its persisted
read grant. It leaves original files, other imported sources and existing chat
messages intact. Overlapping connected folders are independent sources, so a file
may remain searchable through another source. Folder permission does not load
every document into the model: `/docs` still retrieves at most three excerpts.

## Runtime prompt and grounding

This file is developer documentation, not a prompt automatically sent to the local LLM. `ChatRequestRouter` selects document routing; `ToolRegistry` supplies the tool descriptions and grounding instructions. The model calls `document_rag`, receives retrieved excerpts as a tool observation, and generates an answer. It receives at most three chunks, so a long-document summary may omit sections. Filename mentions restrict the candidate chunks to matching documents.

## Remote MCP tools

Ask `/mcp list tools at https://your-server.example/mcp`. Confirm the connection, then ask `/mcp call <tool> at https://your-server.example/mcp with arguments {...}`. The agent uses the `mcp` bridge; every remote operation requires confirmation. Tool calls verify that the server advertises the requested tool before execution.

This initial client supports HTTPS Streamable HTTP, initialization/version negotiation, session headers, JSON and SSE responses, paginated tool discovery, and tool calls. It accepts protocol versions 2025-03-26, 2025-06-18, and 2025-11-25. Endpoints are supplied per request; no credentials are stored. Servers requiring OAuth or API tokens are not supported yet, nor are stdio, resources, prompts, sampling, elicitation, resumable streams, or background subscriptions. Network failures are surfaced without automatically replaying remote actions.

Remote server output and document excerpts are untrusted content. The local model still decides how to use returned results; this implementation does not guarantee model adherence to grounding instructions.

## Validation

Focused tests: `ChatRequestRouterTest`, `DocumentRagTest`, `DocumentImportTest`, `DocumentFolderRepositoryTest`, `AndroidDocumentTreeAccessTest`, `ChatTaskActionTest`.

Picker smoke test: `.maestro/document_import_validation.yaml`. Requires a connected device with an initialized chat model. Full MCP interoperability should additionally be checked against the intended server before relying on its tools.

Folder manager/picker smoke test: `.maestro/document_folder_validation.yaml`.
It opens the permission picker and cancels without granting access. Repository
tests cover persisted metadata, scoped indexing, refresh deletion, retrieval and
disconnect isolation; provider-reader tests cover nested files, UTF-8 and limits.

Validation recorded for this change: focused unit tests and debug assembly passed; APK installation/launch and the picker smoke flow passed on ASUSAI2501C. Full on-device document retrieval and live MCP interoperability remain unverified.
