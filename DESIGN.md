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

### 9. Generation Feedback

Image generation can take minutes on device. Any image-generation path, including slash commands, must set `SendState.GeneratingImage` so the chat shows `GENERATING IMAGE...` and disables duplicate sends.

### 10. Explicit Image Mask

Explicit generated images must render masked by default:

- Blur the generated image.
- Show a compact `EXPLICIT IMAGE` label.
- Provide a visibility icon button to reveal the image.
- When visible, provide the inverse toggle so the user can hide it again.
- Non-explicit images should not show mask controls.

### 11. Age Verification Dialog

Explicit image requests should use a dialog rather than an inline chat-only prompt. The dialog should be direct and neutral: it verifies age before proceeding with age-restricted image content.

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
