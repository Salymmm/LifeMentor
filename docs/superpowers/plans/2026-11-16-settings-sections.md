# Settings categories and detail pages

User requests a settings index with small entries that open detailed content. Continue inline on the existing feature branch.

- Five entries: personal information, career profile, reminders/notifications, AI interface, data/privacy. Index displays static summaries and no input forms or credentials.
- Selected category is saveable across rotation; page header and Android back return to settings index. Keep index scroll position, start newly entered detail pages at top.
- Extract existing fields/buttons into private detail composables. Preserve SettingsViewModel, ProfileViewModel, API presets/requests/validation, reminder behavior and wipe confirmation. VM drafts survive category navigation; API Key visibility resets when its detail page is disposed. Wipe confirmation exists only inside data/privacy.
- Use the existing Compose/runtime dependencies and styles. Do not add UI-wiring tests for this reversible display-only change; run current regressions/builds and request independent review of navigation, state and preserved callbacks.
- No AI generation, protocol, evaluator, data storage, permissions, or calendar-sync changes.

Completed: 100 JVM tests, 21 existing Python evaluator tests and app/test APK builds pass. Compared extracted role/profile/API bodies and wipe dialog against the previous commit (ignoring indentation and moved headers/local UI state); field/button callbacks are preserved exactly. Independent static review found no blockers in index/detail routing, Android back, draft state, masking or wipe-dialog scope. Updated APK on emulator-5554 with unchanged user-file and reminder/sync-preference fingerprints. Native Studio screenshot capture timed out, so the actual layout has not been visually inspected.
