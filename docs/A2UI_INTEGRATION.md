# A2UI in Chevere AI

Chevere uses the official AndroidX Compose A2UI runtime and renderer, pinned to
`1.0.0-alpha01` in `gradle/libs.versions.toml`. AndroidX currently documents support
for A2UI specification 0.9.1 (wire version `v0.9`). This first integration renders
interactive local task checklists inside assistant messages.

## Flow

1. A user asks to list tasks. The agent invokes `task_registry(action=list)`.
2. The tool returns a versioned Chevere envelope containing a plain-text summary,
   a stable surface ID, and up to 50 actual Room task IDs.
3. The orchestrator returns the structured tool result immediately. The model does
   not need to generate a JSON layout or run another inference pass.
4. ChatViewModel decodes the envelope and observes the existing Room task flow.
5. `TaskChecklistSurface` creates an AndroidX `A2uiMessageProcessor` with the
   versioned Chevere catalog and emits standard typed `createSurface` and
   `updateComponents` messages. The renderer validates the component properties.
6. The catalog wraps the same native `TaskRowItem` used by the Tasks screen.
7. A tap dispatches an A2UI `set_task_completed` event. The adapter accepts only
   this event for the current surface/root and routes it to `ChatIntent.SetTaskCompleted`.
   The ViewModel rechecks surface membership before an atomic Room status update.

The Chevere tool/storage envelope is not an A2UI wire message. It bridges the local
agent's existing string-result API to the official renderer's typed protocol API.
There is no remote UI server or arbitrary model-authored layout ingestion in this PR.

## State and persistence

Room is the source of task titles, descriptions, and status. Changes from the Tasks
screen update all visible chat cards. Deleted tasks disappear; newly created tasks
appear in the next requested checklist. Empty checklists still render. Pending
writes disable the matching row across cards. Reads and writes expose actionable
fallbacks, and failed writes do not optimistically change completion state.

Structured metadata is encoded into the existing conversation message text column
and decoded by ChatHistoryRepositoryImpl. No Room schema change or destructive
migration is necessary. User messages are never decoded as UI envelopes. Sharing,
text-to-speech, and conversation compression receive the readable summary instead
of protocol metadata. Old text messages remain compatible.

Processors are scoped to the composed card; leaving the card or switching
conversations cancels their processing coroutines. History restoration recreates
surfaces against current Room data. Envelopes reject unsupported versions, duplicate
or invalid IDs, invalid surface IDs, and oversized payloads.

## Extending and upgrading

- Keep AndroidX types in `ui/chat/a2ui`; domain content remains renderer-independent.
- Add supported content to the sealed `AgentUiContent` contract and a catalog
  component that wraps an existing native composable.
- Register and validate each new action through MVI rather than interpreting arbitrary
  action names as tool calls. Do not bypass permission or confirmation flows.
- Change the catalog ID when its schema changes incompatibly. Add an envelope
  version/decoder for incompatible persisted-content changes.
- Upgrade the single `a2ui` dependency version, then run the native rendering and
  persistence tests. AndroidX owns protocol processing and schema validation;
  Chevere still owns its components and application actions. The current alpha
  APIs may require adapter changes before stable release.

## Verification

The integration tests cover tool results, bounded/invalid envelopes, history
round-trips, native row rendering, action dispatch, live completion/deletion updates,
and loading/error states. The orchestrator test verifies that a checklist result
terminates the agent turn without another model pass.

Manual device check: create a task in Tasks, ask "List my tasks", tap its status in
chat, check Tasks, reopen the conversation from history, and delete the task in Tasks.
Verify that the restored card uses current data and removes the deleted row.

## Results for this PR

- 32 selected unit/Robolectric tests passed (new UI, tool, envelope, history and MVI
  tests plus existing orchestrator and chat repository regression tests).
- The native checklist was captured and visually inspected at phone chat width.
- `:app:assembleDebug` passed.
- `:app:lintDebug` remains blocked by 75 pre-existing errors: 72 missing translations,
  two vibration permission checks, and one non-observable locale read. New strings
  are translated into German, Spanish and French; no new adapter lint findings.
- ADB reported no connected device/emulator, so install/launch verification remains
  pending.
- The existing attachment-routing regression test exposed an incorrect route for
  image-generation wording with an attached image. The routing guard now always
  sends attachments through direct vision inference, as required by AGENTS.md.

## References

- https://developer.android.com/develop/ui/compose/agentic
- https://developer.android.com/develop/ui/compose/agentic/manage-catalogs
- https://developer.android.com/develop/ui/compose/agentic/render-surfaces
- https://developer.android.com/jetpack/androidx/releases/a2ui-compose
