# Design Rules: High-Tech AI Assistant

This document defines the UI/UX rules for Chevere. All new components should use the shared design system and Material theme tokens.

## Layout & Keyboard Rules

### 1. Inset Consumption

The app uses nested Scaffold structures. To avoid double-padding around system bars and the keyboard:

- Use `Modifier.consumeWindowInsets(innerPadding)` on screen roots when a child handles system insets.
- Chat input should stay above the navigation bar and IME without adding a visible gap.

### 2. Background Foundation

Main screens use `MaterialTheme.colorScheme.surface`. Do not use pure black or hardcoded background hex values.

### 3. Smart Bottom Padding

For chat inputs, use the max/union of IME and navigation-bar insets:

- Keyboard open: trust resize/IME handling.
- Keyboard closed: keep controls above the system navigation pill.

## Visual Identity Rules

### 4. The Cyan Pulse

`primary` is the AI identity color. Reserve it for CTAs, active states, model activity, and thinking/generation indicators.

### 5. Geometric Precision

- Pills/inputs: `28.dp`
- Cards: `20.dp` for major cards, `12.dp` for compact cards
- Buttons: `12.dp`
- Chat image corners: `8.dp`

### 6. Terminal Typography

- Headers: Plus Jakarta Sans, bold, uppercase, light letter spacing.
- Body: Inter, normal weight.
- Metadata badges: compact, uppercase where useful.

### 7. Atmospheric Depth

Major screens should use subtle `AmbientGlow` with theme tokens. Avoid hardcoded decorative blobs.

### 8. Subtle Outlines

Prefer `1.dp` outlines over shadows. Use `outlineVariant` at low alpha for inactive states.

## Chat UX Rules

### 9. Top Bar Identity

The chat top bar should represent Chevere and local capability readiness, not a single model filename. Use the `CHEVERE` brand title with compact readiness chips such as `CHAT` and `IMAGE`. Model selection and model details belong in the Models screen.

### 10. Generation Feedback

Image generation can take minutes on device. Any image-generation path, including slash commands, must set `SendState.GeneratingImage` so the chat shows `GENERATING IMAGE...` and disables duplicate sends.

If no healthy image-generation model is installed, image-generation requests should show a clear download prompt instead of silently failing or producing a generic assistant apology.

Attached images are multimodal chat inputs. They should route to chat/vision inference, not text-to-image generation, unless a future explicit image-editing flow is added.

### 11. Attachment Preview

Selected image thumbnails should be large enough to inspect before sending. Use a neutral circular remove button over the thumbnail; avoid oversized red/destructive controls for routine removal.

### 12. Assistant Message Actions

Assistant message controls should reflect available behavior. Use a share icon for Android share-sheet actions. Do not show report or flag controls until there is an actual reporting workflow.

### 13. Explicit Image Mask

Explicit generated images must render masked by default:

- Blur the generated image.
- Show a compact `EXPLICIT IMAGE` label.
- Provide a visibility icon button to reveal the image.
- When visible, provide the inverse toggle so the user can hide it again.
- Non-explicit images should not show mask controls.

### 14. Age Verification Dialog

Debug builds may use a dialog rather than an inline chat-only prompt for explicit image requests. Release builds should block explicit image generation before model execution.

### 15. Launcher And Splash

Launcher icon and launch animation should use a shared robot-head/cyan identity so the app drawer, Android splash, and in-app launch screen feel like the same product.

## Token Reference

| Design Role | System Token | Usage |
| --- | --- | --- |
| Main background | `surface` | App background |
| Element background | `surfaceVariant` / containers | Cards and secondary surfaces |
| Accent | `primary` | Interactive elements |
| Agent state | `tertiary` | Reasoning, planning, warnings |
| Error | `error` | Failures and destructive actions |

## Implementation Checklist

- [ ] Are colors referenced via `MaterialTheme.colorScheme`?
- [ ] Are headers using the app typography?
- [ ] Does long work show an activity indicator?
- [ ] Are explicit image masks present only for explicit generated images?
- [ ] Is `AmbientGlow` subtle and token-based?
