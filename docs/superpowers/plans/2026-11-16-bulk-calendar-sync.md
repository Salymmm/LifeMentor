# One-click calendar synchronization

User requests one button to sync all dated tasks and explicitly chose removing managed events after task deletion/date clearing. Continue inline on codex/task-dates.

Scope: all goals/tasks with dates, all months and completion states. App is the one-way source of truth; completed state goes in event description. First use asks calendar READ/WRITE permission and target-calendar choice; later clicks reconcile the saved target. Only writable calendars are offered. All-day dates remain UTC intervals.

Identity/safety: each installation persists an owner UUID before any provider write. Events carry CUSTOM_APP_PACKAGE and CUSTOM_APP_URI identifying owner+task ID. Query only selected calendar/package, parse only this owner's URI, and update/delete with calendar/package/owner/task/event guards. Prior manual ↗ drafts are outside batch ownership. Repeated synchronization creates no duplicates, updates changed fields, deletes stale owned events and repairs duplicate owned IDs. Other calendars/events are untouched. Changing target leaves old-calendar events in place and explains this before selection.

Read task/goal files strictly as one main-thread source snapshot before asynchronous provider work. Parse failures abort; existing forgiving display loaders must not turn corrupt JSON into a delete-all sync. Calendar I/O runs on IO dispatcher, busy state rejects duplicate taps. Compute all changes first and apply a single guarded provider batch with expected update/delete counts.

- [x] Pure model tests red→green: full source scope, idempotence, edits/date/completion, deletions, duplicate repair, missing-goal rejection and instance identity isolation.
- [x] Provider adapter, permission/writable-calendar selection, persisted target/owner, guarded batch operations and result counts.
- [x] Calendar panel: permission launcher, target picker, saved target, one-click progress/result, explicit deletion/one-way/manual-draft scope.
- [x] JVM/Python/build verification and isolated native ContentValues/ownership/permission checks without granting calendar access or writing real events. Independent read-only review, fix findings, commit own files.

No automatic background syncing, cross-device account management, task-data migration, AI modifications, or new dependencies. Actual user calendar writes require the user's permission grant, target selection and button action in the app. Tests do not grant that permission or modify existing calendars.

Verification: eight initial model tests were observed failing before implementation; a recurrence regression was also observed red before its fix. Final 100 JVM tests and 21 existing Python evaluator tests pass, and app/test APKs build. On emulator-5554, 13 bulk Android-contract checks, 28 isolated store checks (including rejecting corrupt sync input) and 11 prior intent checks pass. User task/configuration files and reminder/calendar-sync preference fingerprints remain unchanged. Latest APK installed. Packaged manifest and XML verified with aapt2.

Independent review findings addressed: exclude calendar_sync.xml from legacy backup, cloud backup and device transfer so device-local ownership/target IDs are not cloned; clear duration/recurrence metadata when restoring a single all-day event; require a currently available target in the picker; update in-memory target immediately after target persistence even if the following batch fails. Review confirmed no remaining blockers. Calendar permission result and resume metadata refresh do not compete to swallow the initial sync action.

Limits: no calendar permission was granted by the agent, and no real Calendar Provider insert/update/delete was executed. Native checks inspect actual ContentValues/operations and permission guards; actual account/provider behavior and visual UI remain to be verified after user permission/target selection. Ownership is per installation and selected calendar; old manual drafts and events in a previously selected calendar are not adopted or removed.
