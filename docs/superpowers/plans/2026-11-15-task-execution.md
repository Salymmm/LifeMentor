# Task Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

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

- [x] Write failing task grouping and completion tests, then implement the smallest pure rules.
- [x] Connect agenda groups and date actions to the store; complete remains idempotent.
- [x] Expose manual action creation before AI decomposition and render root-level actions.
- [x] Add lifecycle and midnight refresh.

### Task 2: Notifications

- [x] Write and run failing notification policy tests.
- [x] Add opt-in permission UI, background daily and snooze work, idempotent action receiver and reset/cancel behavior.
- [x] Integrate settings and storage reset/date-change hooks.

### Task 3: Verify

- [x] Run full JVM and existing Python tests, assembleDebug and diff checks.
- [x] Attempt emulator flow; preserve existing user state and report unavailable checks.
- [x] Independent read-only review; fix material findings, rerun affected checks, and retain local branch.

## Verification

- 64 JVM tests and 21 existing Python tests passed. Debug and Android test APKs built successfully; diff whitespace checks passed.
- Temporary read-only Android emulator: PASS, 20 native task/notification checks. Covered offline creation, date save/clear, actual notification posting, one-hour work enqueue, duplicate completion/XP, screen refresh signal, stale-date rejection, goal completion cancellation and data reset.
- Smoke runner refuses execution without the explicit disposable-device flag. Its notification repost test waits for the notification to exist before simulating the next user action.
- Independent review identified a midnight timer issue after clock/zone changes; fixed with resume/time broadcast reanchoring and a failing-then-passing DST/timezone regression. Task screens also observe persisted task changes.
- Protected AI code, prompts, parsers, mentor chat and evaluation scripts have no diff.
- Manual visual layout and actual overnight/one-hour delivery were not timed; WorkManager delivery can be delayed by OS policy. A timezone change missed while force-stopped can retain the old request until its first execution.

## Try it

Run the app from Android Studio. Create a goal and choose 添加行动 without configuring an API key. Use the home date groups and date actions. In 设置, enable 每日行动提醒 and grant notifications; 发送测试提醒 lets you try notification actions.
