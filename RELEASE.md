# Install and update ChilliBook

**First install or upgrade from 1.0 or 1.1.** Download the signed APK from the [latest GitHub release](https://github.com/mikeritch/ChilliBook/releases/latest), open it on an Android 8.0 or newer phone and tap **Install** or **Update**. Android may ask you to allow the app that opened the APK to install apps. Keep ChilliBook installed when updating so Android retains its private data.

**From version 1.2 onward.** Open **Settings → App updates → Check for updates**. When a release is available, choose **Download update → Install update**. The first time, Android may ask you to allow ChilliBook to install apps from this source. Return to ChilliBook and confirm **Update** in the system installer. Database migrations run automatically the next time the diary opens. Backups are optional.

**Over USB.** Enable USB debugging and authorise the computer on the phone. Place the signed APK beside [Install ChilliBook.command](<release/Install ChilliBook.command>) and double-click the command on macOS. With Android SDK Platform-Tools on your path, you can also run the following from the project root.

```sh
adb -d install -r release/ChilliBook-1.7.apk
```

## Back up or move the diary

Choose **Settings → Back up diary** and select a private destination. The JSON file contains readable health data. A cloud provider may sync it. To restore, choose **Settings → Restore diary**, select the file and review the dates and counts before choosing **Replace diary**.

## Publish a signed update

1. Set a higher default version code and version name in `app/build.gradle.kts`. Use a numeric version such as `1.3` or `1.3.1`. Make any Room migration part of the same release. Follow the upgrade rehearsal in [PROJECT_PLAN.md](PROJECT_PLAN.md).
2. Build locally with the original signing key. Set `CHILLIBOOK_SIGNING_PROPERTIES` to a private properties file containing `storeFile`, `keyAlias` and `storePassword`. The build resolves the keystore path relative to this file. On the release Mac, Gradle uses the existing signing configuration by default. Keep a recoverable copy of the key and properties outside the repository.
3. From the checked-out release commit, run the commands below with your Android SDK build tools and GitHub CLI on the path. Confirm that the package is `uk.chillibook.diary`, the version name matches the tag, the version code increased, and the signer SHA-256 is `6a55daeee50f7ae62b1c7eb7fac0476d1e68bdc402d6f03574a9063bf969eae4`.
4. Push the release commit and its tag. Publish a stable GitHub Release for that tag, attaching the signed APK with the exact filename shown. The updater uses GitHub's asset SHA-256 and verifies the downloaded APK's package, version and signer.

```sh
./gradlew :app:assembleRelease
apk=app/build/outputs/apk/release/app-release.apk
apksigner verify --print-certs "$apk"
aapt dump badging "$apk" | head -1
version=1.7  # set to the version you just built
cp "$apk" "release/ChilliBook-$version.apk"
git tag "v$version"
git push origin HEAD "v$version"
gh release create "v$version" "release/ChilliBook-$version.apk" --verify-tag --generate-notes --latest
```

## Release 1.7

Tap the date beneath Morning or Evening to open the calendar and backfill a check-in.

In Patterns, tap an activity count to see the dates, evening wellbeing and energy on the next two mornings. Completing both check-ins opens a silent flower celebration. The flower stays in the check-in header for the rest of the local day.

Evening → Rest & naps saves a separate episode each time you tap Add rest or Add nap. Time of day, duration and energy afterwards are optional. Earlier Quiet rest activities appear in the same section with their original details and edit controls. History shows the episodes individually. Patterns counts the dates on which they occurred.

The signed 1.6 → 1.7 emulator update preserved all 355 days, 673 check-ins, 157 activities and preferences in the sample diary, including an existing Quiet rest. The migration test also covered two Quiet rest records on one date, a mixed-tag entry and a saved Quiet rest setup. Room applies the update automatically. Older JSON backups remain readable.

## Release 1.6

Work now has one optional setup per day for activities, day length, effort and demands. Use the pin inside Work to save the complete setup, then apply it to another date. Household, social and Other activities each have their own pin for duration, effort, demands and worthwhile. Custom activities keep their names. The Yesterday shortcut opens the same check-in slot. History can search the whole diary for a symptom or activity.

The light theme has lavender surfaces, pastel answer scales and a smiling outline bolt. Earlier work entries keep their original fields and remain individually editable. Room migrates them on first open. Backups now include grouped Work entries and every saved activity setup.

A signed in-place update from 1.5 preserved all 354 day records, 673 check-ins, 154 activities and preferences in the synthetic diary. On the emulator, a test saved independent Work and Cooking pins, applied both to yesterday and removed the Cooking pin while retaining the Work pin and both diary entries.

## Release 1.5

The check-in uses centred, tick-free controls. Tapping a selected energy, sleep or wellbeing answer again clears it. History includes dated sections for the two previous days and the next two mornings. Patterns adds a 90-day timeline, monthly all-time charts and one-tap activity breakdowns by weekday.

The app removes symptom pinning, the separate check-in answer editor and the changes-to-plans question. The automatic migration keeps recorded symptoms, activities, historical changes to plans, appearance and reminders. Older ChilliBook backup files remain readable.

A signed 1.4 → 1.5 in-place upgrade preserved every retained field across 354 day records, 673 check-ins and 154 activities on the synthetic emulator. The release signer matched the previous APK. Check-in controls were inspected at normal and 200% font size.

## Release 1.4

Select a symptom directly in the morning or evening check-in, then optionally choose its severity. Tap its minus to remove it. A selected symptom can be saved without a rating. History and Patterns distinguish that from an explicit None and an unanswered symptom.

Check-in choices respond to a tap with a brief press effect and a persistent selected state. Illustrated headings, softer groups and graded answer colours replace repeated rules and routine save messages. Save errors still offer Retry.

**Installing 1.4 permanently removes existing crash episodes and reasons for no work.** The Work Yes/No answer, other diary records and preferences migrate automatically. Importing an older backup into 1.4 skips those two retired fields. Previously exported files remain in their original locations.

A signed 1.3 → 1.4 emulator upgrade retained all 354 day records, 673 check-ins, 154 activities and preferences in the synthetic fixture. An export comparison checked every retained field. Migration tests also exercised the version 1 database and rollback after a failed migration. The symptom flow was checked at normal and 200% text sizes.

## Release 1.3

Choose **Evening → Household tasks → Yes**, then **+ Cooking**. The selection saves and reveals its optional details. Tap the red minus to remove the activity or Undo to bring it back. Work and Socialising follow the same pattern. Other activities also accepts a name of your own. Previous diary and backup formats stay readable.

**Settings → Reset all diary data** requires two confirmations. The final confirmation permanently clears the diary and resets local preferences, including reminders. Previously exported backup files remain available at their saved locations.

The signed 1.2 → 1.3 rehearsal preserved every exported field in 354 day records, 673 check-ins, 154 multi-tag activities, three crash episodes and preferences. Focused emulator checks covered selecting and removing Cooking, restoring its duration with Undo, a custom Other activity, backup compatibility, cancelling both reset warnings and committing a reset in a separate synthetic diary.

## Release 1.2

Evening groups work, socialising and household answers with the day's activity entries. Add one activity, choose its tags and optionally add duration, effort, demands or personal value. Edit an entry directly from the same screen. The data format is compatible with 1.1.

On an isolated Android 17 emulator, a same-key rehearsal build fetched the public 1.2 release, verified the APK, opened the Android installer and updated in place. The installed app then reported it was current. Comparing the complete export to the original 1.1 fixture matched all 354 days, 673 check-ins, 154 activities, three crash episodes and preferences. A separate activity test added two entries, edited one and confirmed both retained their IDs. The inline editor was inspected at ordinary and 200% text sizes.

## Version 1.1 update

The 1.0 → 1.1 signed upgrade retained 354 day records, 673 check-ins, 154 activities, three crash episodes and existing preferences on an isolated Android 17 emulator. All fields matched after the update. Both reminder times start off. Choose morning and evening times independently in Settings. Delivery follows Android’s inexact alarm scheduling.

The check-in sheets now have fixed scroll boundaries, inset ticks and visual press feedback. Patterns includes distinct activity days and symptom severity counts by slot. Both have links to the dates behind the counts.

## Version 1.0 release verification

All four milestones were exercised with synthetic data on Android 17 / API 37 emulators on this Mac.

| Milestone | Observed result |
| --- | --- |
| Persistent check-ins | Morning took two answer taps and evening took four. A single energy answer survived a restart with sleep unanswered. A regression test also closed the screen immediately after accepting a tap and read the committed answer from the reopened diary. |
| Editable history and recovery | An empty historical date opened for editing. Correcting its energy left one check-in. A crash starting on that date stayed open on the next day. Recording recovery, undoing it and recording it again produced the expected two boundaries. The file picker restored partial entries, explicit No answers, symptoms, activities, settings and crash episodes. |
| Personal charts | The 28-day fixture showed low or very low next-morning energy on 4 of 14 usable work days and 4 of 8 usable no-work days. The work details showed 4 Low, 8 Medium and 2 Fairly high, with three additional missing follow-ups. The tests covered multi-day calendar lags, gaps, sparse groups, activity lookup and month drilldown. TalkBack focused and read a comparison count once. |
| Private installation | The release signature verified. The signed app updated in place and reopened after an emulator cold boot. Comparing exports before and after the updates, an invalid restore and a cancelled replacement confirmed identical keyed records and preferences. The year contained 354 day records, 673 check-ins, 154 activities and 3 crash episodes. The earliest signed build’s two-check-in backup also restored on the delivered APK. |

The final code passed five JVM tests and nine distinct Android instrumentation tests across the standard and 360 dp / 200% font configurations. The accessibility pass exercised every primary check-in choice, correction and scrolling in both themes. Android lint reported zero errors and nine version advisories. The release manifest has no internet permission.

[Large-text check-in](verification/screens/large-morning.png) · [TalkBack count](verification/screens/talkback-comparison.png) · [Comparison dates](verification/screens/all-levels.png) · [One-answer day after reopen](verification/screens/one-tap-reopened.png) · [Restore preview](verification/screens/restore-preview.png)

The owner's physical phone, everyday check-in times and off-Mac key recovery remain owner checks. The JSON reader and writer currently support up to 8,388,608 characters and 250,000 JSON values per backup.

[Evening activities](verification/1.2/evening.png) · [Inline editor](verification/1.2/activity-editor.png) · [Large-text editor](verification/1.2/activity-large.png) · [Update settings](verification/1.2/updates.png)
