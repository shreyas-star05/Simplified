# Dusk

A private, offline calendar and voice journal for Android. No internet permission: nothing leaves the phone unless you export it yourself.

This repo currently holds **release 0, Dusk Lab**: a throwaway test build that proves the three riskiest parts on a real phone before the app itself is written.

| Tab | What it tests |
|---|---|
| Speech | Four ways to turn speech into text, scored against a script: the keyboard mic, the phone's recogniser, and the on-device recogniser in two modes |
| Reminders | Whether reminders fire on time with the phone locked, overnight and after a restart, using two scheduling methods |
| Plan | Whether a spoken or typed plan becomes the right card. Guesses are shown in amber |
| Results | Everything as text, to copy out |

## Install on your phone
1. Open **Releases** in this repo and pick the newest "Dusk Lab build".
2. Download **DuskLab.apk** on the phone (Android 13 or newer) and open it.
3. Allow "Install unknown apps" for your browser if asked.

## What is in the code
- `core/` is plain Kotlin with no Android code: the plan parser and the speech scorer, with their tests. `core/src/test/resources/corpus.tsv` holds every sentence the parser is tested against.
- `app/` is the test app (Kotlin, Jetpack Compose).
- `ci/` holds the emulator scripts. Every push runs the unit tests, builds the APK, taps through every screen on an emulator, waits for a real reminder, and restarts the emulator to check reminders survive.

## Build it yourself
`./gradlew :core:test :app:assembleRelease`
