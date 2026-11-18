# Integrate main redesign into system integration branch

User authorizes resolving PR #3 conflicts. Merge origin/main (139239d) into feat/system-integration, preserving both histories. Retain teammate scrapbook styling, mentor avatars and English UI while reconnecting task dates, editing/deletion, arbitrary subtasks, agenda/reminders, calendar and categorized settings. Preserve free-form Chinese input/data. Receive protected AI changes exactly from origin/main; do not independently modify prompts, parsers, protocols, clients or mentor generation.

- Create a local pre-merge safety ref; merge without committing. Preserve unrelated untracked IDE files and current project path.
- Resolve six conflicting files and inspect automatically merged files for lost functionality or incompatible signatures. Reuse new Paper/MentorAvatar components and English labels for integrated/new controls.
- Verify protected AI files/methods against incoming main, scan conflict markers, run JVM/Python tests and app/test APK builds. Adapt only feature tests affected by deliberate incoming localization.
- Independent review of integration and required fixes; run available isolated device checks without clearing user data or granting permissions.
- Commit and update existing feature PR if authorized by the active integration task; do not merge main or rewrite remote history.

Verification: app and Android test APKs build successfully; 100 JVM unit tests and 21 Python evaluator tests pass. On the existing emulator, 28 isolated goal interaction, 13 calendar provider contract and 11 calendar intent checks pass. Hashes of user files and reminder/calendar preferences are unchanged after reinstall and checks. Protected AI files and goal decomposition/parser match incoming main. No real calendar writes were performed during automated verification.
