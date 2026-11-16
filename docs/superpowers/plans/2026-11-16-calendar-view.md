# Task calendar and phone calendar implementation plan

**Goal:** Monthly task calendar with day details and user-confirmed phone-calendar event creation.

**Scope/design:** User selected calendar view and phone calendar from the prior proposal. Continue inline in the existing feature branch; prior authorization to implement remains. Calendar entry points in Home and Goals open a screen with back, previous/next month and today. Monday-first day grid shows scheduled task counts, selection and today. Active-goal tasks of every tier appear if dated, including completed tasks. Unscheduled count links to Goals; calendar does not invent dates. Day list allows task completion/undo, date changes and phone-calendar export. Stored UTC date-only values stay unchanged.

**Phone-calendar approach:** ACTION_INSERT on CalendarContract.Events.CONTENT_URI creates a draft in the installed calendar app, with task title, goal description and an all-day interval from UTC midnight to exclusive next UTC midnight. User confirms, edits or cancels there. This is an add-to-calendar handoff, not two-way synchronization; returning from the calendar cannot be treated as confirmed save. No READ/WRITE_CALENDAR permissions, account selection, background event insertion, AI changes or new dependencies. Missing calendar app gives an actionable message.

**Alternatives:** Direct Calendar Provider synchronization would require permission/account/identity and update/delete tracking beyond this request; the selected user-confirmed handoff gives a reviewable first integration. A sixth bottom tab would crowd existing navigation, so existing tabs remain and expose calendar actions instead.

## Task 1: Calendar/date and event models
- [x] Add failing JUnit tests for Monday-first leap/short months, inactive/undated exclusion, completed and all-tier inclusion, stable day ordering and UTC all-day event dates, including DST/year boundary and undated rejection.
- [x] Implement pure TaskCalendar model and CalendarEventDraft; run tests and verify green.

## Task 2: Monthly UI and navigation
- [x] Add CalendarViewModel, CalendarScreen month grid/day list with stable day/row keys, saved month/day selection, completion/undo, date dialog and unscheduled link.
- [x] Add Home/Goals calendar entry points and AppRoot navigation/back handling; refresh on store revisions/resume/midnight. Keep mutations and B's AI implementation unchanged.

## Task 3: Phone calendar and verification
- [x] Add CalendarBridge intent construction and ActivityNotFound handling; UI states external confirmation and no automatic synchronization.
- [x] Add isolated native checks for actual intent extras and fallback handling without writing to any real calendar.
- [x] Run full JVM/Python regressions and app/test APK builds, verify protected AI baseline, independent read-only review. Run device/visual checks only when a device and supported UI capture are available; state remaining limits accurately.

Files: new data/TaskCalendar.kt; new ui/calendar/CalendarViewModel.kt, CalendarScreen.kt; new notify/CalendarBridge.kt; modify AppRoot.kt, HomeScreen.kt, GoalsScreen.kt; tests for model and native intent checks. No data migration.

Verification: observed six new model tests fail before implementation; final 90 JVM tests and 21 Python evaluator tests pass, and both APKs build. Studio's normal Run started the existing emulator without a competing headless instance. Updated both APKs; calendar_view passed 11 safe intent/fallback checks, goal_interactions passed 26 isolated store/VM checks including calendar date changes, completed visibility and no duplicate rewards on undo/recompletion. User files and reminder preferences had identical fingerprints before and after. Independent review found a timezone/wall-clock refresh gap; clockRevision plus system broadcasts/resume now restarts the midnight loop, and review confirmed the correction. Grid height scales with font size. Goals header XP feedback shares the existing hint line so the added calendar entry does not crowd header buttons. Protected AI files and decomposition methods remain unchanged.

Runtime limits: native window screenshots timed out, so the monthly layout has not been visually inspected. An untyped package-manager query initially returned no handlers; querying with the event MIME type correctly finds Google Calendar. The bridge now declares the type explicitly as documented by Android common intents. Actual external calendar-app UI and saving have not been tested; no real calendar was opened or written. Device checks inspect Android Intent extras and simulate handlers. User-facing fallback is included for devices without a calendar handler.
