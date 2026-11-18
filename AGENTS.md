# ChilliBook

Build a private, offline Android diary for one person with ME/CFS. A check-in must save even if the user answers just one question.

## Start here

Read [PROJECT_PLAN.md](PROJECT_PLAN.md) and open the [screen reference](UI_REFERENCE.png) before assigning UI work. In Goal mode, use the user's stated finish line. For the full release, work through all four milestones, using each as a checkpoint.

## Delegate coding

- The primary agent owns shared interfaces, the Compose theme, integration and verification. Delegate bounded coding tasks to sub-agents at **low reasoning effort** when supported. Give each a plan section, files to own, expected behaviour and a check. Give UI workers the matching screen reference.
- Parallelise independent edits. Handle tiny changes directly. If delegation is unavailable, continue locally.
- Workers report files changed, checks run and open issues. Review the combined diff. Raise effort for a concrete problem such as a migration that could lose diary entries.

## Keep it small

- Follow existing Kotlin and Compose patterns. Add a layer, dependency or setting only when the current slice needs it. Keep unrelated cleanup out of the diff.
- Preserve partial entries, explicit “no” answers and gaps in history. Keep real diary data, backups and signing secrets out of the repo and logs.
- Run the smallest check that exercises the change. Add a focused test where a wrong result could silently lose or misrepresent data. Compare changed screens to the reference on a running emulator or phone, including large text for changed controls. The owner can open Android Studio on this Mac if needed.
- Finish with what works, how it was checked and anything still unverified.

## Updating an installed diary

- Keep the production application ID, database filename and signing identity stable. Increment the APK version code. User backups are optional. An ordinary update must migrate on first database open without a file picker.
- Treat shipped Room schema JSON as immutable. For a schema change, increment the Room version, export the new schema and register a complete migration path. Use Room auto-migrations for unambiguous additions and explicit migrations for transformations. Preserve nulls, explicit No, IDs, timestamps, local dates, context and settings. Release 1.4 explicitly retires crash episodes and the reason for no work. Preserve the actual work answer. Release 1.5 removes the symptom pin preference. Keep recorded symptoms and historical changes to plans even though their prompt was retired. New grouped Work entries are opt-in per date. Preserve older individual activities, including mixed tags and different details on the same day.
- Exercise production database opening from populated released schemas. Check the resulting values and a subsequent edit and reopen. A failed migration must leave the old database intact and retryable. Never enable destructive fallback or use reinstall, database deletion or an empty diary as a recovery path.
- Version backup changes separately. Keep previous exports readable, reject unsupported versions before replacing data and give new optional preferences safe defaults.
- Rehearse signed in-place updates on an isolated emulator with synthetic entries. Compare the pre- and post-update records using the upgrade check in PROJECT_PLAN.md. Keep the previous signed artifact available for rehearsal.

- Keep network access confined to user-initiated release checks and APK downloads. Use the official GitHub repository, verify release integrity and signing identity, and hand installation to Android for confirmation.
- Publish only source, public design or synthetic verification material, and the signed APK. Keep production signing local. The release tag, version name and asset filename must match the contract in RELEASE.md.
