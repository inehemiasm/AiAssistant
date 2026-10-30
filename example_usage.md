# Example Usage

## Chat

1. Install or push a `.litertlm` model.
2. Select it in **Models**.
3. Ask normal chat questions from the **Chat** tab.

## Multimodal Chat

Attach an image from the gallery or camera and ask about it:

```text
What is in this image?
```

If the text field is empty, Chevere AI sends a default `Describe this image.` prompt. Attached images route through chat/vision inference, not text-to-image generation.

## Document Search and MCP

Import a UTF-8 `.txt` or `.md` file (up to 2 MB) through **Add attachment → Import document**, then ask:

```text
/docs What is the warranty policy in policy.md?
```

Chevere searches its local document index and supplies up to three excerpts to the local model. This uses keyword matching, not semantic embeddings. PDF extraction and whole-document summaries are not implemented.

To discover tools on a remote MCP server, supply its HTTPS endpoint in chat:

```text
/mcp list tools at https://your-server.example/mcp
```

Replace the example endpoint with your server. Confirm the connection, then request a discovered tool by name and confirm the call. Remote MCP operations send requests to that server; the local document index is not automatically uploaded. This initial bridge has no saved connections or OAuth/API-token setup.

See [RAG/MCP usage and limitations](docs/rag-mcp.md) for supported behavior and validation coverage.

## Image Generation (`:image-generation` Module)

Local ONNX Stable Diffusion (`OnnxLocalDiffusionEngine`) is packaged in the standalone `:image-generation` Gradle module and is currently decoupled from `:app`. While decoupled, asking to generate an image or using `/image`, `/img`, or `/imagine` returns a friendly message explaining that local image generation is not enabled in the current build.

## Model Readiness

- The first downloaded chat model activates automatically.
- The chat top bar shows `CHEVERE AI` with the `CHAT` readiness chip rather than a single model filename.

## Sharing & Reporting Responses

- **Share**: Assistant responses can be shared through Android's native share sheet via the Share icon on any assistant message bubble.
- **Report AI Content**: Users can flag inaccurate, offensive, or unsafe AI responses via the Flag icon on any assistant message bubble (`ReportAiContentDialog`), which submits a PII-scrubbed report to Firestore (`ai_content_reports`) and Firebase Analytics (`ai_content_reported`).

## Local Tasks Management

Users can manage their local checklists in two ways:
1. **Interactive Checklist Screen**: Go to the **Tasks** tab to manually add, delete, or toggle pending/completed tasks.
2. **Natural Language / Agent Control**: Ask the assistant to update the checklist:
   ```text
   Add "Buy groceries tomorrow morning" to my todo list
   ```
   The agent will parse the command and use the `task_registry` tool to create it. You can also ask:
   ```text
   What are my active tasks?
   ```
   The agent will read and list them for you.

## Performance Benchmarking

To measure how fast models run on your specific device:
1. Navigate to **Settings** (gear icon in Chat or bottom bar).
2. Tap on the **Performance Benchmark** card.
3. Tap **RUN BENCHMARK**.
4. The screen will report warmup load speed, Time-to-First-Token (TTFT), tokens-per-second generation rate (TPS), total elapsed time, and show device specs (physical RAM and CPU/GPU acceleration status).

## Manual ONNX Bundle Layout

The app expects extracted ONNX diffusion bundles to look like:

```text
sd_1_5_onnx/
  text_encoder/model.ort
  tokenizer/vocab.json
  tokenizer/merges.txt
  unet/model.ort
  vae_decoder/model.ort
```
