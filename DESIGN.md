# Design Rules: High-Tech AI Assistant

This document defines the core UI/UX "Rules of Engagement". All new components must adhere to these standards using the provided **Design Tokens**.

---

## 🟢 Layout & Keyboard Rules

### 1. Inset Consumption (The "No Gap" Rule)
The app uses a nested Scaffold structure (Outer `AiAssistantApp` + Inner Screen Scaffolds). To avoid the "40dp gap" or double-padding:
- **Always** use `Modifier.consumeWindowInsets(innerPadding)` on the top-level container of a screen.
- This tells the system that system bars (Nav/Status) are already handled, preventing children from "double-padding" when the keyboard opens.

### 2. The "Void" Foundation
All main screen backgrounds must use the **`surface`** role (mapped to `VoidSurface` #10141A). Never use pure black or hardcoded hex codes.

### 3. Smart Bottom Padding
For chat inputs, use the **union/max** of `ime` and `navigationBars` insets.
- **Keyboard Open**: Use 0 extra padding (trust `adjustResize`).
- **Keyboard Closed**: Use `navigationBarsPadding` to stay above the system pill.

---

## 🟢 Visual Identity Rules

### 4. The Cyan Pulse
**`primary`** (Cyan) is the AI identity. Reserved for CTAs, active states, and thinking animations.

### 5. Geometric Precision
- **Pills/Inputs**: `28.dp`
- **Cards**: `20.dp`
- **Buttons**: `12.dp`

### 6. Terminal Typography
- **Headers**: `Plus Jakarta Sans`, Bold, **UPPERCASE**, `1.sp` letter spacing.
- **Body**: `Inter`, Normal weight.

### 7. Atmospheric Depth
Every major screen should contain at least one `AmbientGlow` (Primary color, 5% alpha, 120dp blur) to create digital depth.

### 8. Subtle Outlines
Use `1.dp` borders instead of shadows. Use `outlineVariant` at 20% alpha for inactive states.

---

## 🛠 Token Reference

| Design Role | System Token | Usage |
| :--- | :--- | :--- |
| **Main Background** | `surface` | App background |
| **Element BG** | `surfaceVariant` | Cards / Secondary containers |
| **Accent** | `primary` | Interactive elements |
| **Agent State** | `tertiary` | Reasoning / Planning |

## 🧪 Implementation Checklist
- [ ] Is `consumeWindowInsets` applied to the screen root?
- [ ] Are colors referenced via `MaterialTheme.colorScheme`?
- [ ] Are headers in `Plus Jakarta Sans` and Uppercase?
- [ ] Is `AmbientGlow` used for atmosphere?
