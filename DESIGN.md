# Design Rules: High-Tech AI Assistant

This document defines the core UI/UX "Rules of Engagement" for the AI Assistant. All new components and screens must adhere to these standards using the provided **Design Tokens**. Avoid hardcoding hex colors.

---

## 🟢 The 10 Golden Rules

### 1. The "Void" Foundation
All main screen backgrounds must use the **`surface`** or **`background`** role (mapped to `VoidSurface`). Never use pure black or literal hex codes. This creates the depth required for the glow effects to pop.

### 2. The Cyan Pulse
**`primary`** (mapped to `CyanPrimary`) is the "heartbeat" of the app. It is reserved for:
- Primary CTA buttons.
- Active state indicators.
- AI status "Thinking" animations.

### 3. Geometric Precision (Corner Radii)
Standardized rounding to ensure a consistent silhouette:
- **Pills / Input Bars**: `28.dp` (Full rounding)
- **Primary Cards**: `20.dp`
- **Buttons / Secondary Items**: `12.dp`
- **Small Badges**: `4.dp`

### 4. Terminal Typography
- **Headers**: Use `Plus Jakarta Sans`, Bold/ExtraBold, and **UPPERCASE**.
- **Body Content**: Use `Inter`, Normal weight.
- **Letter Spacing**: Apply `1.sp` to all uppercase labels and buttons to mimic high-tech terminal readouts.

### 5. Atmospheric Depth
Every major screen should contain at least one `AmbientGlow` component.
- **Blur**: `120.dp`
- **Color**: Use **`primary`** with a very low alpha (5%).
- **Placement**: Usually top-left or bottom-right to create a non-uniform "atmospheric" lighting effect.

### 6. Subtle Outlines
Use borders instead of heavy shadows for definition.
- **Width**: Always `1.dp`.
- **Color**: **`outlineVariant`** at 20% alpha for inactive items; **`primary`** at 50% alpha for active items.

### 7. Translucency & Glassmorphism
Floating elements (like the `ChatInputBar`) should use **80% alpha** backgrounds using **`surfaceContainerHigh`**. This allows background glows to bleed through slightly.

### 8. Semantic Feedback
- **Error**: **`error`** role for failures or "Link Severed" states.
- **Agent/Reasoning**: **`tertiary`** role for when the AI is using tools or "Planning".
- **Success/On**: **`primary`** role for successful uplinks.

### 9. Component Heirarchy
- **Primary Action**: Gradient fill using **`primary`** and **`primaryContainer`**.
- **Secondary Action**: Surface fill with `1.dp` border.
- **Tertiary Action**: Icon-only or plain text with `labelSmall` styling.

### 10. Motion & Interaction
Transitions should feel "digital":
- Use **Alpha shifts** (0.4f to 1.0f) for enabled/disabled states.
- Selection states should involve both a border color change to **`primary`** and a subtle background shift to **`surfaceVariant`**.

---

## 🛠 Token Reference (Material 3 Mapping)

| Design Role | System Token | Usage |
| :--- | :--- | :--- |
| **Main Background** | `surface` | App background |
| **Deep Surface** | `surfaceContainerLow` | Subtle depth |
| **Card / Element** | `surfaceVariant` | Secondary containers |
| **Accent / Pulse** | `primary` | Interactive elements |
| **Warning / Agent** | `tertiary` | Special AI states |

## 🧪 Implementation Checklist
- [ ] Are colors referenced via `MaterialTheme.colorScheme`? (NO HEX CODES)
- [ ] Are headers in `Plus Jakarta Sans` and Uppercase?
- [ ] Does the component follow the `12/20/28dp` corner rules?
- [ ] Is `AmbientGlow` used for background atmosphere?
- [ ] If it's a card, does it have a `1.dp` border?
