# Arul Remind Me — v1.4 (Sessions 1–4 + 5A)

**உங்கள் நினைவூட்டல் உதவியாளர்**

Kotlin + Jetpack Compose reminder app. This build contains **Session 1** (data layer,
architecture, full reminder CRUD UI) and **Session 2** (offline Tamil / English / Tanglish
natural-language parsing) and **Session 3** (real AlarmManager alarms, lock-screen
notifications, full-screen alert, snooze) and **Session 4** (reboot rescheduling, recurring
reminders, battery/Doze guidance) and **Session 5A** (voice input via Android
SpeechRecognizer). See *Roadmap* below for what remains.

---

## What works in this build

| Feature | Status |
|---|---|
| Home screen (header, subtitle, Today / Upcoming sections) | ✅ |
| Add Reminder screen with Material 3 date + time pickers | ✅ |
| Confirmation card — "இந்த Reminder சரியா?" before anything is saved | ✅ |
| Room database, survives app restart and device reboot | ✅ |
| Edit reminder | ✅ |
| Mark as Done | ✅ |
| Delete (soft delete + Undo snackbar) | ✅ |
| Completed screen with Restore | ✅ |
| Overdue badge on past-due pending reminders | ✅ |
| Tamil / English string resources (`values/`, `values-ta/`) | ✅ |
| Natural-language entry (Tamil / English / Tanglish) | ✅ Session 2 |
| AM/PM clarification instead of guessing | ✅ Session 2 |
| Reminder text separated from the date/time expression | ✅ Session 2 |
| Microphone button | Placeholder, disabled, labelled "Coming Soon" |
| Exact alarm at the scheduled time (AlarmManager) | ✅ Session 3 |
| Lock-screen notification with Done / Snooze / Open | ✅ Session 3 |
| Full-screen alarm-style alert screen | ✅ Session 3 (where Android permits) |
| Snooze 5 / 10 / 30 min | ✅ Session 3 |
| Notification + exact-alarm permission status and guidance | ✅ Session 3 |
| Reschedule after reboot / app update / timezone change | ✅ Session 4 |
| Recurring reminders — daily, weekly, monthly | ✅ Session 4 |
| Natural-language recurring phrases | ✅ Session 4 |
| Reminder Reliability screen with battery guidance | ✅ Session 4 |
| Voice input — Tamil / English speech to reminder | ✅ Session 5A |
| Launcher shortcut "Voice Reminder" | ✅ Session 5A |
| Assistant / App Actions capability declared | ⚠️ Declared, needs Play Console setup |
| Custom "Arul Remind Me" wake word while locked | ❌ **Not possible for a normal app — see below** |
| Voice input — Tamil / English speech to reminder | ✅ Session 5A |
| Launcher shortcut "Voice Reminder" | ✅ Session 5A |
| Assistant / App Actions capability declared | ⚠️ Declared, needs Play Console setup |
| Custom "Arul Remind Me" wake word while locked | ❌ **Not possible for a normal app — see below** |

> **Reboot:** alarms are rebuilt from the database on `BOOT_COMPLETED`, app update and
> time/timezone change. A one-time reminder whose time passed while the phone was off is
> *not* fired late and *not* given a past alarm — it stays pending and shows in Today with
> the overdue chip. A recurring one jumps to its next future occurrence.
>
> **Voice:** the microphone runs only while the voice sheet is open, after an explicit tap.
> No audio is stored or uploaded by this app; recognition uses the on-device / Play services
> recogniser already on the phone. `RECORD_AUDIO` is requested on first mic tap, never at
> startup.
>
> **There is no always-listening wake word, and there cannot be one.** A normal third-party
> Android app cannot register an arbitrary phrase like "Arul Remind Me" for detection while
> the phone is locked. The supported routes are the launcher shortcut, the Assistant App
> Action, and opening the app — all three are wired up. Anything else would mean a hidden
> microphone service, which this project does not ship.
>
> **Direct boot is not supported:** the database is credential-encrypted, so nothing can be
> read before the first unlock after a restart. Alarms are re-armed at that point.
>
> **Exact alarms need a permission on Android 12+.** If "Alarms & reminders" is off, the app
> falls back to an inexact Doze-aware alarm (which the OS may delay), records that honestly
> in `isAlarmScheduled`, and shows a card on Home linking to the right Settings screen. It
> never pretends an exact alarm was armed.

---

## Requirements

* Android Studio Ladybug (2024.2) or newer
* JDK 17 (bundled with Android Studio)
* Android SDK Platform 35
* minSdk 26 (Android 8.0) — chosen so `java.time` works natively without desugaring
* An Android 8.0+ device or emulator

---

## Build in Android Studio

1. `File → Open…` → select the `ArulRemindMe` folder.
2. Let Gradle sync (first sync downloads AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01).
3. If prompted, install SDK Platform 35 and Build Tools.
4. Press **Run ▶** with a device connected.

## Build from the command line

```
# Windows
gradlew.bat assembleDebug

# APK lands in:
app\build\outputs\apk\debug\app-debug.apk
```

Release APK (signed with the debug key for now — swap in a real keystore before Play Store):

```
gradlew.bat assembleRelease
```

## Build in the cloud (no local Android SDK needed)

`.github/workflows/build.yml` builds debug + release APKs on every push to `main` and uploads
them as workflow artifacts. Push this folder to a GitHub repo and open the **Actions** tab.

## Tests

```
gradlew.bat testDebugUnitTest              # domain model, entity mapping, NL parser, alarm logic
gradlew.bat connectedDebugAndroidTest      # Room CRUD (needs a device/emulator)
```

`ReminderRepositoryTest` covers the Session 1 acceptance list: create, read, update, complete,
restore, soft-delete + undo, hard delete.

---

## Architecture

```
ui/            Compose screens + ViewModels (StateFlow only, no Room access)
  navigation/  single NavHost, one Activity
  components/  ReminderCard, confirmation dialog, logo, empty states
  home/ editor/ completed/
alarm/         AlarmIds, AlarmScheduleRules and RecurrenceRules are pure Kotlin
               (JVM-testable decision logic: request codes, snooze, recurrence, reboot);
               AlarmRescheduler + BootCompletedReceiver rebuild alarms after boot;
               AlarmManagerReminderScheduler, ReminderIntents and ReminderAlarmReceiver hold
               the platform calls
notification/  ReminderNotifier — channel, lock-screen notification, Done/Snooze/Open actions
ui/alert/      ReminderAlertActivity — full-screen alarm screen (showWhenLocked, never unlocks)
ui/permissions/ status card + official Settings intents
ui/reliability/ Reminder Reliability screen — notification, exact alarm and battery status
voice/         SpeechRecognitionService (Android SpeechRecognizer wrapper, mic scoped to
               one flow collection), VoiceInputSheet (Compose UI), VoiceSettings, and
               VoiceLanguage + VoiceTranscriptProcessor which are pure Kotlin and carry the
               JVM-tested speech-to-parser boundary
nlp/           pure Kotlin parser — no Android types, so it runs under plain JVM unit tests
  ReminderParser          orchestrates: date -> mask -> time -> reminder text
  DateExpressionParser    relative days, weekdays + qualifiers, month/day, numeric dates
  TimeExpressionParser    explicit AM/PM, part-of-day words, 24h, ambiguity detection
  TamilDateTerms / EnglishDateTerms / TanglishDateTerms   all vocabulary, one place
  ParsedReminderInput / ParserResult   typed result (LocalDate / LocalTime, never strings)
domain/        pure Kotlin — Reminder model, repository interface, scheduler interface
data/
  local/       Room entity, DAO, database
  mapper/      entity <-> domain (all java.time, no string date maths)
  repository/  ReminderRepositoryImpl — the only place persistence and alarms are kept in sync
di/            AppContainer, hand-rolled DI created in ArulRemindMeApp
util/          display-only date/time formatters
```

**Key design decisions**

* `ReminderScheduler` is an interface with a deliberate no-op implementation. The repository
  already calls `schedule()` / `cancel()` on every create, update, complete and delete, so
  Session 3 only has to write `AlarmManagerReminderScheduler` and change one line in
  `DefaultAppContainer`.
* Date/time is stored as `scheduledAtEpochMillis` + IANA `zoneId`. `scheduledDateEpochDay` and
  `scheduledTimeSecondOfDay` are numeric helpers for grouping — no date is ever stored as a
  display string.
* Schema v1 already contains the columns later sessions need (`repeatMode`, `repeatInterval`,
  `snoozedUntilEpochMillis`, `isAlarmScheduled`, `soundEnabled`, `vibrationEnabled`,
  `originalInput`), so recurring reminders and snooze will not need a destructive migration.
* Deletes are soft, which gives Undo now and an audit trail for alarm cancellation later.
* The parser takes a `java.time.Clock`, so relative dates ("வரும் சனிக்கிழமை") are unit-testable
  against a fixed date and stay correct in any future year. Nothing reads the clock statically.
* The parser never guesses AM/PM. A bare hour with no part-of-day word returns an
  `AmbiguousTime` and the UI asks — a silently wrong 5 AM is worse than one extra tap.
* Natural language and the manual pickers write to the *same* editor fields and save through
  the same repository call, so there is only one save path to maintain.
* Alarm request codes are derived from the reminder id, never random. A random code would
  make `AlarmManager.cancel()` silently miss and leave a duplicate alarm after every edit.
* `schedule()` returns whether an *exact* alarm was really armed, and the repository stores
  that in `isAlarmScheduled` — the UI reports what the OS actually did, not what was asked.
* The receiver re-reads the row before notifying. An alarm is a message from the past: by
  the time it lands the reminder may have been completed, deleted or snoozed again.
* No foreground service and no `Handler`/`Timer`/coroutine delay anywhere in the alarm path —
  none of those survive process death, which is the whole requirement.
* `isAlarmScheduled` is a record of what was asked for, never trusted after a reboot. The
  boot sweep rebuilds every alarm from the database rows themselves.
* Recurring reminders needed **no schema change**: the weekday for WEEKLY and the day of
  month for MONTHLY both come from the reminder's own `scheduledAt`. Room is still at
  version 1 and no migration was written.
* A recurring series advances when the alarm *fires*, not when the user taps Done — so it
  still fires tomorrow even if today's notification is ignored completely. Snooze writes
  only `snoozedUntilEpochMillis`, which is why a snooze never drags the series off 8:00.
* Voice adds no second parser. `SpeechRecognizer` → transcript → `VoiceTranscriptProcessor`
  → the same `ReminderParser` a typed sentence uses → the same confirmation card → the same
  repository call. The tests assert that a spoken sentence and a typed one produce identical
  reminders.
* Month-end policy for monthly repeats: the day is clamped to the target month's length
  (31st → 30 Nov, 28/29 Feb) and the anchor is never rewritten, so the 31st comes back in
  months that have one.
* No runtime permissions are requested in Session 1. Each permission is added in the session
  that introduces the feature needing it.

---

## Roadmap

| Session | Scope |
|---|---|
| 2 | ✅ **Done** — Tamil / English / Tanglish natural-language date-time parsing |
| 3 | ✅ **Done** — AlarmManager exact alarms, BroadcastReceiver, notifications, full-screen alert, snooze |
| 4 | ✅ **Done** — BOOT_COMPLETED rescheduling, recurring reminders, Doze / battery guidance |
| 5A | ✅ **Done** — Tamil / English speech input, launcher shortcut, App Action declaration, minimal voice settings |
| 5B | Remaining: Assistant verification on a real Play track, optional home-screen widget, final settings screen, full device test pass |

---

Version 1.4 (Sessions 1–4 + 5A) · Built for Arul Sundaresan

---

## Assistant / App Actions — what is still required

`res/xml/shortcuts.xml` declares the `actions.intent.CREATE_REMINDER` capability. That
declaration is **inert until it is verified through Google's tooling**. To activate it:

1. Upload a build to any Play Console track (internal testing is enough) using the same
   `applicationId` and signing key.
2. Install the **App Actions Test Tool** plugin in Android Studio and sign in with the
   Play Console account.
3. Run *Tools → App Actions → App Actions Test Tool*, create a preview for
   `actions.intent.CREATE_REMINDER`, and test on a device signed in with the same account.

Until step 1 is done, saying "create a reminder in Arul Remind Me" to Assistant will not
reach the app. The launcher shortcut works immediately with no setup at all.
