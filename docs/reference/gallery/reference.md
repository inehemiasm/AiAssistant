# Google AI Edge Gallery Reference

This document serves as a bounded reference for architectural patterns and features derived from the [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery). These patterns are adapted to fit the specific needs of the AI Assistant (LiteRT-LM Multimodal) project.

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
As an AI Assistant, we eventually need to interact with the Android system (e.g., checking battery, setting alarms). Decoupling the *definition* of these tools from their *implementation* allows the LLM to "reason" about which tool to use.

### Implementation Difference
- **Gallery**: Often uses complex DI-based plugin systems.
- **AiAssistant**: We will use a simple `ToolRegistry` singleton or Hilt-injected set of `Tool` interfaces to keep the logic traceable and reduce boilerplate.

---

## 2. Agent-Skill Structure

### The Pattern
Separating the "Agent" (the LLM brain/orchestrator) from "Skills" (specific domains like Weather, Reminders, or Image Analysis).

### Why it fits this app
It prevents `LlmRuntimeManager` from becoming a "God Object." Each skill can have its own prompt templates and specialized logic.

### Implementation Difference
- **Gallery**: Uses a rich event bus for inter-skill communication.
- **AiAssistant**: We will use a direct `List<Skill>` approach where the `ChatViewModel` or a `SkillOrchestrator` iterates through available skills, keeping the data flow linear and easier to debug.

---

## 3. Multimodal UX Patterns

### The Pattern
Asynchronous image processing where images are "attached" to the conversation context before the user sends the prompt.

### Why it fits this app
We already support image-based conversations. The Gallery's pattern of "Image Previews in Input" matches our "Cyberpunk" UI goals for a high-tech feel.

### Implementation Difference
- **Gallery**: Standard Material 3 components.
- **AiAssistant**: We maintain the "Cyberpunk" aesthetic (neon glows, tech-heavy borders) while adopting the Gallery's state management for image URI handling.

---

## 4. Safe Mobile Action Patterns

### The Pattern
"Human-in-the-loop" (HITL) requirements for any action that modifies user data or sends information externally.

### Why it fits this app
Safety is paramount for on-device AI. We want to ensure the AI doesn't perform actions without explicit user confirmation.

### Implementation Difference
- **Gallery**: Complex permission negotiation.
- **AiAssistant**: Simple, high-contrast confirmation cards within the chat stream for a seamless yet safe experience.

---

## 5. On-device Assistant Feature Boundaries

### The Pattern
Strict boundaries between what is processed locally (privacy-first) and what requires cloud connectivity (if any).

### Why it fits this app
Privacy is a core feature of our project.

### Implementation Difference
- **Gallery**: Often showcases hybrid models.
- **AiAssistant**: We prioritize a "Local-Only" boundary, clearly marking any feature that might require a network call (like model updates) with a distinct UI treatment.

---

## What We Explicitly Do NOT Copy
- **Heavyweight Frameworks**: Avoid importing large Gallery-specific libraries; implement the *patterns* natively.
- **Demo-Only Skills**: We will not include "Showcase" features that don't add utility to a daily-driver assistant (e.g., overly specific math-solving or poem-generating specialized agents).
- **Complex Navigation**: We stick to our MVI-based single-activity/fragment flow rather than multi-layered navigation patterns used in larger Gallery samples.
