# Task interaction improvements

User-approved scope: goal completion can be reversed; deletion requires confirmation; completed goals cannot invoke AI decomposition; long task trees/text can fold; Home offers arbitrary dates instead of tomorrow shortcuts.

- [x] Add tested preorder projection for independently collapsed task branches. Retain manual root tasks and append new manual children next to their actual parent.
- [x] Add persisted goal reopen operation and UI button; preserve task progress and XP. Add delete confirmation including goal title. Show AI entry only for active, undecomposed goals; do not modify generation/schema/prompts.
- [x] Render milestone/week fold controls and two-line text previews with full-text expansion. Expand ancestors when a manual action is added, so it is visible immediately.
- [x] Share existing date dialog between Goals and Home. Replace Home quick-date controls with arbitrary-date picker, preserving cancel/clear and UTC encoding.
- [x] Run meaningful new tests, full existing regression, APK/test build, and independent read-only review. Preserve unrelated Studio files and avoid stopping the user's emulator or changing API settings.

UI choices: completed goals show 撤回完成; deletion dialog defaults to cancellation; nested branches initially collapsed; reversing goal completion changes only goal status. B's AI implementation remains unchanged.

## Verification

- TaskTreeTest observed RED:4 failures, then GREEN. Full suite:68 JVM tests passed;21 existing Python tests passed. Debug app and Android test APK builds passed.
- Independent read-only review found no actionable production defects. AI decompose/parseTree bodies, LLM prompts/parsers, mentor chat and evaluator remain unchanged.
- New safe goal-only device checks use isolated files and negative fixture IDs. AGP did not package the additional manifest runner, so the checks now use the existing runner with -e goal_interactions true. This mode never resets real reminder state.
- Native goal checks were not completed: emulator/Studio closed before retry. They are compiled but are not reported as passed. Existing app-file fingerprints were unchanged after the first attempted run.
- App APK was updated on the emulator while it was available, preserving application data. Unrelated Studio-generated files are untouched.
