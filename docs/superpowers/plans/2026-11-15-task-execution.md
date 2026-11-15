# Task Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the local task execution flow from manual creation through agenda and notification actions.

**Architecture:** Pure date/grouping and completion logic feed the existing JSON store and Compose screens. An independent notify module owns permission, WorkManager and notification actions.

**Tech Stack:** Kotlin, Compose, JSON, JUnit, WorkManager.

**Spec:** `docs/superpowers/specs/2026-11-15-task-execution-design.md`

## Global Constraints

- B's AI implementation and evaluation scripts remain unchanged.
- Dates retain UTC-midnight encoding and are compared as calendar dates in the device's local day.
- No remote writes. Preserve unrelated untracked Studio files.

## Review Focus

- Completed/deleted/inactive tasks must not surface in the agenda or notifications.
- Manual actions without milestone/week parents must remain visible after later AI decomposition.
- Repeated completion and notification delivery must not double-reward or undo completion.
- Notification revocation, disabling, data reset and date changes suppress pending reminders.
- Home must refresh after notification actions and at midnight.

### Task 1: Task data and UI

- [ ] Write failing task grouping and completion tests, then implement the smallest pure rules.
- [ ] Connect agenda groups and date actions to the store; complete remains idempotent.
- [ ] Expose manual action creation before AI decomposition and render root-level actions.
- [ ] Add lifecycle and midnight refresh.

### Task 2: Notifications

- [ ] Write and run failing notification policy tests.
- [ ] Add opt-in permission UI, background daily and snooze work, idempotent action receiver and reset/cancel behavior.
- [ ] Integrate settings and storage reset/date-change hooks.

### Task 3: Verify

- [ ] Run full JVM and existing Python tests, assembleDebug and diff checks.
- [ ] Attempt emulator flow; preserve existing user state and report unavailable checks.
- [ ] Independent read-only review; fix material findings, rerun affected checks, and retain local branch.
