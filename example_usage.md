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

If the text field is empty, Chevere sends a default `Describe this image.` prompt. Attached images route through chat/vision inference, not text-to-image generation.

## Direct Image Generation

Use a slash command to bypass the chat model and call the local image backend directly:

```text
/image a white wolf under moonlight, cinematic, detailed fur
```

Supported aliases:

- `/image`
- `/img`
- `/imagine`

If no healthy image-generation model is installed, Chevere shows a dialog that offers to open **Models** so the user can download one.

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
- The chat top bar shows `CHEVERE` with `CHAT` and `IMAGE` readiness chips rather than a single model filename.

## Sharing Responses

Assistant responses can be shared through Android's share sheet. There is no report/flag workflow in the app yet.

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
