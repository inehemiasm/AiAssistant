# Design Guidelines: High-Tech AI Assistant

This document outlines the UI/UX principles, design tokens, and component rules for the AI Assistant project. The aesthetic is defined as **"Cyber-Tech"** or **"High-Tech AI"**, characterized by deep surfaces, cyan glows, and clean typography.

---

## 1. Visual Philosophy
- **Depth & Dimension**: Use **`MaterialTheme.colorScheme.surface`** (mapped to Void Surface) combined with glowing primary elements to create a sense of digital depth.
- **Energy**: Cyan and Blue tones (**`primary`**) represent the "pulse" of the AI.
- **Precision**: Clean lines, subtle borders (1.dp), and geometric shapes.
- **Readability**: High contrast for text on dark backgrounds using the Inter font family.

---

## 2. Color Palette (Material 3 Tokens)

### Primary "Glow" Colors
Used for primary actions, active states, and AI identity.
- **`primary`**: Base interactive color (Cyan).
- **`primaryContainer`**: Secondary glow/fill color.
- **`onPrimary`**: Text/Icons on primary backgrounds.

### Surface & Backgrounds
- **`surface`**: Deepest background (The "Void").
- **`surfaceVariant`**: Card backgrounds and secondary containers.
- **`surfaceContainerHigh`**: Floating elements and elevated components.
- **`surfaceContainerHighest`**: Used for menus and highest-elevation surfaces.

### Feedback & Special
- **`error`**: Failures and destructive actions.
- **`tertiary`**: Agent reasoning, planning states, and warnings.

---

## 3. Typography

| Role | Font Family | Weight | Size | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Display** | Plus Jakarta Sans | ExtraBold | 45sp | Hero headers, Large stats |
| **Headline** | Plus Jakarta Sans | Bold | 28sp | Screen titles |
| **Title** | Plus Jakarta Sans | Bold | 18-22sp | Section headers, Card titles |
| **Body** | Inter | Normal | 14-16sp | Chat messages, Descriptions |
| **Label** | Inter | Medium | 11sp | Metadata, Small stats |

*Note: Titles and Buttons should often use **UPPERCASE** to reinforce the high-tech terminal aesthetic.*

---

## 4. Component Rules

### Buttons (`HighTechPrimaryButton`)
- **Shape**: Rounded corners (12.dp).
- **Background**: Linear gradient from **`primary`** to **`primaryContainer`**.
- **Text**: Bold, Uppercase, 1sp letter spacing.
- **State**: Use **`surfaceVariant`** for disabled states.

### Cards (`ModelSelectorCard`, `StatCard`)
- **Corner Radius**: 20.dp for major cards, 12.dp for stat items.
- **Borders**: 1.dp width. Use **`outlineVariant`** at 20% alpha for inactive cards.
- **Active State**: 1.dp **`primary`** border at 50% alpha and a background shift to **`surfaceVariant`**.

### Input Bar (`ChatInputBar`)
- **Shape**: Pill-shaped (28.dp height/radius).
- **Opacity**: Use 80% alpha on **`surfaceContainerHigh`** for a semi-translucent look.
- **Icons**: 24.dp standard size. Send button should use **`primaryContainer`** when active.

---

## 5. Effects & Motion

### Ambient Glows
- Use the `AmbientGlow` component for atmospheric lighting.
- **Blur**: 120.dp
- **Color**: **`primary`** with low alpha (0.05f).
- **Placement**: Usually placed behind main content or at the edges of the screen.

### Gradients
- Use linear gradients at a 45-degree angle (from top-left to bottom-right) using **`primary`** family tokens for interactive elements.

---

## 6. Implementation Checklist
- [ ] Are all colors referenced via `MaterialTheme.colorScheme`? (Strictly **NO** hex codes in UI code).
- [ ] Are headers using `Plus Jakarta Sans`?
- [ ] Is the background using the **`surface`** token?
- [ ] Do primary buttons have the **`primary`** to **`primaryContainer`** gradient?
- [ ] Are cards using the 20.dp corner radius?
- [ ] Is `AmbientGlow` applied with the **`primary`** token to add atmosphere?
