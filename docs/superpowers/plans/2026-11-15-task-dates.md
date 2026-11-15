# Manual Task Dates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users persist, edit and clear a calendar date on weekly and daily tasks.

**Architecture:** Extend the existing TaskItem JSON contract with an optional date. Keep GameStore as the persistence boundary and add a Material 3 date picker in the existing goal task row. Keep B's AI decomposition unchanged.

**Tech Stack:** Existing Kotlin, Compose Material 3, org.json, JUnit 4; no added dependencies.

**Spec:** `docs/superpowers/specs/2026-11-15-task-dates-design.md`

## Global Constraints

- `dueDate: Long? = null` is appended to TaskItem; UTC midnight encodes a date, not a notification instant.
- Old JSON without dueDate remains readable. No automatic date assignment.
- Only level 2 and level 3 tasks expose date editing.
- No changes to prompts, AI parsing, Python evaluation, XP, or home filtering.
- Work in the existing fresh clone on `codex/task-dates`; no remote writes.

## Review Focus

- Legacy tasks retain title, hierarchy, completion and XP when a date is saved.
- UTC dates display correctly in positive and negative device time zones.
- Cancel leaves the original date unchanged; clear persists null.
- Long task text does not displace the date action.
- Date changes survive process restart without changing other tasks.

### Task 1: Persist and edit task dates

**Files:** Modify GameModels.kt, GameStore.kt, GoalsViewModel.kt and GoalsScreen.kt in their existing directories. Add `app/src/test/java/com/lifementor/app/data/TaskItemDateTest.kt`.

**Interfaces:**
- Consumes: `GameStore.loadTasks(): MutableList<TaskItem>` and `saveTasks(List<TaskItem>)`.
- Produces: `TaskItem.dueDate: Long?`, `TaskItem.dueDateText(): String?`, `GameStore.setTaskDueDate(taskId: Long, dueDate: Long?)`, `GoalsViewModel.setDueDate(taskId: Long, dueDate: Long?)`.

- [ ] Write tests using hand-built legacy JSON and dated JSON; run them against the original model and observe lost dates.
- [ ] Append dueDate, preserve it in JSON read/write, then rerun the tests.
- [ ] Add tests for clear/copy and stable ISO display under UTC-8 and UTC+14; implement `dueDateText()` using UTC.
- [ ] Add the store update, ViewModel action and date dialog. Save only on explicit confirmation; cancel changes nothing. Give the title remaining row width and retain the date button.
- [ ] Run `gradlew.bat testDebugUnitTest assembleDebug --console=plain` from a temporary ASCII drive mapping, `python -B -m unittest tools.test_evaluate_decomposition -q`, and `git diff --check`.
- [ ] In a disposable/read-only emulator run, verify select, change, cancel, clear and restart persistence; record any unavailable checks.
- [ ] Review the diff, commit the completed change locally, and remove the temporary drive mapping.
