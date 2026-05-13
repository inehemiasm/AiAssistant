# Google AI Edge Gallery Reference

This document is a bounded reference for architectural patterns inspired by the [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery). Chevere AI borrows patterns, not large framework code.

## Relevant Gallery Resources
The following areas of the Gallery are prioritized for reference:
- **README.md**: High-level architecture and project goals.
- **Function_Calling_Guide.md**: Standards for tool definition and execution.
- **skills/**: Implementation patterns for modular assistant capabilities.
- **Android/**: Platform-specific implementation details for LiteRT-LM.
- **DEVELOPMENT.md**: Best practices for local LLM integration and testing.

---

## 1. Tool Calling / Function Calling

### The Pattern
The Gallery uses a declarative approach where functions are defined with JSON-schema-like metadata. The LLM returns a "call" which the app executes locally.

### Why it fits this app
Chevere AI interacts with local model state, image generation, web/weather services, and Android intents. Decoupling tool definitions from implementation lets the active chat model decide what to call without coupling inference code to platform actions.

### Implementation Difference
- **Gallery**: Often uses complex DI-based plugin systems.
- **Chevere AI**: Uses Hilt multibinding for `AgentTool` and a `ToolRegistry` that produces the system tool list.

---

## 2. Agent-Skill Structure

### The Pattern
Separating the "Agent" (the LLM brain/orchestrator) from "Skills" (specific domains like Weather, Reminders, or Image Analysis).

### Why it fits this app
It prevents the inference runtime from becoming a "God Object." Tools and runtimes stay separate: chat inference, Android actions, model inspection, and image generation each have their own implementation.

### Implementation Difference
- **Gallery**: Uses a rich event bus for inter-skill communication.
- **Chevere AI**: Uses direct repository and orchestrator calls, plus MVI state in the UI. This keeps the data flow easier to debug.

---

## 3. Multimodal UX Patterns

### The Pattern
Asynchronous image processing where images are "attached" to the conversation context before the user sends the prompt.

### Why it fits this app
Chevere AI supports image attachments for multimodal chat and generated image attachments for text-to-image output.

### Implementation Difference
- **Gallery**: Standard Material 3 components.
- **Chevere AI**: Keeps high-tech styling, image previews, generated image bubbles, explicit image masking, and reactive URI state.

---

## 4. Safe Mobile Action Patterns

### The Pattern
"Human-in-the-loop" (HITL) requirements for any action that modifies user data or sends information externally.

### Why it fits this app
Local-first does not mean action-free. External sharing, app actions, and age-restricted image requests need user-visible controls.

### Implementation Difference
- **Gallery**: Complex permission negotiation.
- **Chevere AI**: Uses confirmation dialogs/cards for sensitive actions and an age-verification dialog before explicit image generation. Explicit generated images are masked by default.

---

## 5. On-device Assistant Feature Boundaries

### The Pattern
Strict boundaries between what is processed locally (privacy-first) and what requires cloud connectivity (if any).

### Why it fits this app
Privacy is a core feature. Chat and image generation should run locally when models are installed.

### Implementation Difference
- **Gallery**: Often showcases hybrid models.
- **Chevere AI**: Prioritizes local chat and local image generation. Network-backed features are explicit: model discovery/downloads, web search, weather, and any tool that opens external services.

---

## What We Explicitly Do NOT Copy
- **Heavyweight Frameworks**: Avoid importing large Gallery-specific libraries; implement the *patterns* natively.
- **Demo-Only Skills**: Avoid showcase-only features that do not support a daily-driver assistant.
- **Complex Navigation**: Keep the current MVI-based navigation and state model.
