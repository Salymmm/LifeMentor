# Compact task controls and readable mentor messages

User authorizes task controls as icons, collapsible home sections, growth below personal information, and proper rendering of mentor formatting. Continue in the existing codex/task-dates feature workspace.

- Calendar and plus controls share the task title row. Preserve date labels when set, long-title expansion by tapping, and nested folding. Give icon controls descriptive accessibility labels.
- Home agenda date controls move to the task row. Make all four agenda groups and growth collapsible with saved expansion state. Today/overdue start expanded; future/unscheduled start collapsed. Growth remains expanded immediately below the profile card.
- Reuse MarkdownText for assistant bubbles; user messages remain literal text. Add a non-scrolling mode because bubbles live inside a LazyColumn and a nested vertical scroller would receive infinite height. Do not change reply text, storage, ChatViewModel, prompts, parser, protocol, evaluator or mentor logic. ChatScreen display-only modification is explicitly in scope for the user's formatting request.
- Verify JVM/Python regression tests and app/test APK builds; independent review of scrolling constraints, count changes preserving fold state, and narrow task rows. Existing isolated native smoke tests can run on an available emulator without resetting user data.

No new dependencies or task data changes. These are display changes; do not add tests that mirror UI wiring. Visual runtime verification depends on the available native capture tools.

Completed: 78 JVM tests, 21 existing Python evaluator tests, both APK builds, and 14 isolated emulator data checks passed. Both APKs updated on emulator-5554; user files had identical fingerprints before/after. Independent static review caught the standalone renderer's 40 dp footer being applied to each bubble; non-scrolling mode now omits that footer. Final build/tests pass after the fix. Protected AI generation and validation code is unchanged. Native preview was unavailable while Studio was minimized, so no visual runtime pass is claimed.
