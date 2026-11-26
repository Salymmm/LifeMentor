# Android Mentor and Task Workflows Implementation Plan

> **For agentic workers:** Implement and verify each task against the approved design. Steps use checkbox syntax for tracking.

**Goal:** Bring task details into mentor conversations and require confirmation for AI task changes and chat deletion.

**Architecture:** Keep date and pagination rules in the task-query module. The chat screen and view model call it on demand, and the goal editor keeps decomposition results in a draft until confirmation.

**Tech stack:** Kotlin, Jetpack Compose, JUnit, existing JSON storage and model client.

**Spec:** `docs/superpowers/specs/2026-11-26-android-mentor-task-design.md`

## Global constraints

Android only. Preserve existing storage compatibility and English UI copy. Model output cannot mutate tasks directly. Device calendar synchronization and reminder scheduling retain their existing policies.

## Review focus

- Large sets report the number of matches and page through the results.
- A completion around midnight uses local time while due dates retain UTC date encoding.
- Cancelled or stale decomposition drafts leave the original tasks intact.
- Chat actions affect only the selected mentor and completed replies.
- A blank suggestion or a stale goal cannot create a task.

### Task 1 · Task query

- [x] Add `MentorTaskQueryTest` for date classifications, goal filters, paging, and ordinary chat detection.
- [x] Verify red with `gradlew.bat :app:testDebugUnitTest --tests "*MentorTaskQueryTest"`.
- [x] Implement `MentorTaskQuery` with `TaskQueryScope`, `TaskQueryRequest`, and `TaskQueryPage`. Read current records in the chat integration and supply a bounded JSON page with matching counts to the chat prompt.
- [x] Repeat the test and verify no results are sent for ordinary unscoped chat.

### Task 2 · Goal decomposition preview

- [x] Add regression coverage for pruning branches and confirming a valid draft only once.
- [x] Separate the draft from persistence in `GoalsViewModel` and show an editable preview from `GoalsScreen`. Validate the active goal again upon confirmation and preserve prior manual tasks.
- [x] Verify draft edits and persistence boundaries, including recovery after an interrupted save.

### Task 3 · Chat integration

- [x] Extend `ChatViewModelTest` to assert scoped context, confirmation boundaries, and task-suggestion extraction behavior.
- [x] In `ChatViewModel` and `ChatScreen`, wire task filters and pagination, confirm clearing the current mentor chat, and extract one task from a completed reply for editing before save.
- [x] Run the chat tests and verify that suggestions use the existing task creation path.

### Task 4 · Integration

- [x] Run `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` and review the full diff. All 118 unit tests passed.
- [x] Install both APKs on an isolated emulator and run `TaskExecutionSmoke` with `goal_interactions`. All 37 checks passed, including the storage and calendar checks for confirmed decomposition.
- [ ] Exercise the complete mentor chat UI against a configured model on a phone. Chat workflow tests used a controlled model response.
