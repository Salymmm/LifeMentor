# Task interaction improvements

User-approved scope: goal completion can be reversed; deletion requires confirmation; completed goals cannot invoke AI decomposition; long task trees/text can fold; Home offers arbitrary dates instead of tomorrow shortcuts.

- [ ] Add tested preorder projection for independently collapsed task branches. Retain manual root tasks and append new manual children next to their actual parent.
- [ ] Add persisted goal reopen operation and UI button; preserve task progress and XP. Add delete confirmation including goal title. Show AI entry only for active, undecomposed goals; do not modify generation/schema/prompts.
- [ ] Render milestone/week fold controls and two-line text previews with full-text expansion. Expand ancestors when a manual action is added, so it is visible immediately.
- [ ] Share existing date dialog between Goals and Home. Replace Home quick-date controls with arbitrary-date picker, preserving cancel/clear and UTC encoding.
- [ ] Run meaningful new tests, full existing regression, APK/test build, and independent read-only review. Preserve unrelated Studio files and avoid stopping the user's emulator or changing API settings.

UI choices: completed goals show 撤回完成; deletion dialog defaults to cancellation; nested branches initially collapsed; reversing goal completion changes only goal status. B's AI implementation remains unchanged.
