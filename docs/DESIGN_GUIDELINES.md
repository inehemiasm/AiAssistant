# Design Guidelines: High-Tech AI Assistant

Chevere uses a high-tech, privacy-first assistant aesthetic: deep surfaces, cyan activity states, precise borders, and readable typography.

## 1. Visual Philosophy

- **Depth**: Use `MaterialTheme.colorScheme.surface` plus restrained glow effects.
- **Energy**: Cyan/blue `primary` tokens signal active AI work.
- **Precision**: Prefer clean lines, small badges, and subtle borders.
- **Readability**: Keep chat text high contrast and avoid decorative clutter around message content.

## 2. Color Tokens

### Primary

- `primary`: CTAs, active model state, thinking/generation animation.
- `primaryContainer`: active fills, send button, selected tab accents.
- `onPrimary`: text/icons on primary backgrounds.

### Surfaces

- `surface`: main app background.
- `surfaceVariant`: card backgrounds and secondary containers.
- `surfaceContainerHigh`: floating input and elevated controls.
- `surfaceContainerHighest`: menus and highest-elevation surfaces.

### Feedback

- `error`: failures and destructive actions.
- `tertiary`: agent planning, warnings, and intermediate reasoning states.

## 3. Typography

| Role | Font Family | Weight | Size | Usage |
| --- | --- | --- | --- | --- |
| Display | Plus Jakarta Sans | ExtraBold | 45sp | Large headers and stats |
| Headline | Plus Jakarta Sans | Bold | 28sp | Screen titles |
| Title | Plus Jakarta Sans | Bold | 18-22sp | Section and card titles |
| Body | Inter | Normal | 14-16sp | Chat messages and descriptions |
| Label | Inter | Medium | 9-11sp | Metadata, badges, compact stats |

Use uppercase for screen titles and compact status labels when it improves scanability.

## 4. Components

### Buttons

- Shape: `12.dp`.
- Use icons for clear actions where possible.
- Disable while `state.isLoading` to avoid duplicate tool or generation calls.

### Cards

- Major cards: `20.dp`.
- Compact/chat image cards: `8-12.dp`.
- Use `1.dp` borders with low-alpha `outlineVariant`.

### Chat Input

- Pill shape around `28.dp`.
- Use `surfaceContainerHigh` with light opacity.
- Send button should use the active primary treatment only when input is valid.

### Generated Images

- Non-explicit generated images render normally.
- Explicit generated images render blurred by default with:
  - `EXPLICIT IMAGE` label
  - reveal button using visibility icon
  - hide button after reveal

## 5. Effects & Motion

### Ambient Glow

- Use `AmbientGlow` sparingly.
- Color should come from `MaterialTheme.colorScheme.primary`.
- Keep alpha low.

### Loading States

- Chat thinking: `THINKING...`.
- Tool execution: `EXECUTING: <TOOL>`.
- Direct image generation: `GENERATING IMAGE...`.
- Long-running image generation must keep the input disabled and visible activity state active.

## 6. Checklist

- [ ] Are theme tokens used instead of hardcoded colors?
- [ ] Does the screen remain readable on small devices?
- [ ] Does every long-running operation show clear feedback?
- [ ] Are explicit image masks applied only when `ChatMessage.isExplicitImage` is true?
- [ ] Can the user hide a revealed explicit image again?
