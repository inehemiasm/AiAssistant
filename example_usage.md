# Example Usage

## Chat

1. Install or push a `.litertlm` model.
2. Select it in **Models**.
3. Ask normal chat questions from the **Chat** tab.

## Direct Image Generation

Use a slash command to bypass the chat model and call the local image backend directly:

```text
/image a white wolf under moonlight, cinematic, detailed fur
```

Supported aliases:

- `/image`
- `/img`
- `/imagine`

## Agent Image Generation

When the user asks naturally, the active chat model can call the `generate_image` tool. In that path, Gemma should improve the prompt before the tool call.

```text
Can you generate an image of a white wolf?
```

## Explicit Image Generation

Explicit image prompts show an age-verification dialog. If verified, the generated image is masked in chat by default and can be revealed or hidden with the visibility toggle.

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
