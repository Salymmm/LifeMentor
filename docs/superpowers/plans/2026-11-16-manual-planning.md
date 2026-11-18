# Goal management and manual planning

User-requested changes: choose an optional date when adding tasks; edit goal name/domain; clarify goal-level actions; support manual hierarchy; investigate Chinese composition without changing B's AI rules.

- [x] Add tested Goal.edited and TaskItem.manual rules. Editing preserves identity/status/progress/decomposition; manual levels match milestone(1), week(2), action(3), validate same-goal immediate parent. Chinese text is accepted; dates default null.
- [x] Extend GameStore/VM with goal editing and addTask. Keep old addQuickAction API but remove its today default. Preserve past XP/domain records and B's generation/validation code.
- [x] Reuse goal dialog for creation/editing. Put edit/complete/undo/delete in header management menu; separate active/completed sections and keep delete confirmation.
- [x] Offer root milestone/independent-action creation, child weeks/actions under expanded parents, and optional shared date picker on every manual task dialog. Expand new nodes' ancestors so additions are visible.
- [x] Verify model/graph rules, Chinese JSON round trips, full JVM/Python regression, APK/test build, and independent review. Do not create a background emulator that can block Studio.

Chosen UI approach uses existing data levels and JSON storage, with no migration or new dependencies. Existing AI decomposition remains an optional goal action, hidden on completed/decomposed goals. Task completion remains independent across levels; no automatic completion cascade or retroactive XP changes are introduced.

Verification: 75 JVM tests and 21 existing Python evaluator tests pass; assembleDebug and assembleDebugAndroidTest succeed. Independent read-only review found no blocking issues. The seven new tests were observed failing before model implementation and then passing. B's AI files and the decompose/parseTree implementation remain unchanged. No device/UI execution was performed for this revision.

Chinese investigation: user sees host IME candidates but Enter does not insert into emulator. App text fields have no character filter or key-event interceptor; the AVD has hw.keyboard=yes. Android Studio and the emulator are currently closed, so host-to-emulator IME composition remains unverified. Chinese manual-text JSON round trips pass. Use the Android on-screen keyboard's Chinese layout to bypass host IME transfer; English-only AI output belongs to B and is unchanged.
