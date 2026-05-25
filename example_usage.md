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

## Direct Image Generation

Use a slash command to bypass the chat model and call the local image backend directly:

```text
/image a white wolf under moonlight, cinematic, detailed fur
```

Supported aliases:

- `/image`
- `/img`
- `/imagine`

If no healthy image-generation model is installed, Chevere AI shows a dialog that offers to open **Models** so the user can download one.

## Agent Image Generation

When the user asks naturally, the active chat model can call the `generate_image` tool. In that path, Gemma should improve the prompt before the tool call.

```text
Can you generate an image of a white wolf?
```

## Explicit Image Generation

Explicit image generation is debug-only. Debug builds show an age-verification dialog; if verified, the generated image is masked in chat by default and can be revealed or hidden with the visibility toggle. Release builds block explicit image generation before model execution.

## Model Readiness

- The first downloaded chat model activates automatically.
- The first downloaded image-generation model becomes available immediately without replacing the active chat model.
- The chat top bar shows `CHEVERE AI` with `CHAT` and `IMAGE` readiness chips rather than a single model filename.

## Sharing Responses

Assistant responses can be shared through Android's share sheet. There is no report/flag workflow in the app yet.

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
