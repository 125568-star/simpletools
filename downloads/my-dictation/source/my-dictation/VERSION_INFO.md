# My Dictation — Version Information

This file records the Android app's development history. Versions are listed in release order. The version number is the Android app's `versionName`, not a separate Windows release.

| Version | Changes in this version |
| --- | --- |
| **1.0** — 9 Nov 2026 | Initial Android app. Dark-themed note and folder screens; record a voice note; transcription attempt; create to-do and shopping lists with checklists; folder management, note colours, import of exported notes, and safeguards around previously saved audio. Early transcription could fail on some Android speech providers. |
| **1.1** — 9 Nov 2026 | Changed new dictation to Android's live microphone speech-recognition service rather than injecting a pre-recorded PCM stream. Added continuous listening/restart and partial-result handling. Legacy PCM retry remained available but provider-dependent. |
| **1.2** — 10 Nov 2026 | Moved **Unfiled** and **All Notes** to the bottom of the folder list; added automatic saving instead of a **Save Text** button; black, bordered note editor; centered red **REC** indicator; title generation and conservative automatic folder classification; stop recording when leaving a note. |
| **1.3** — 10 Nov 2026 | Added long-press selection of listed notes and multi-select **Move**/**Delete**, with confirmation before deletion. Opening notes no longer started recording automatically. Introduced a single manual Record/Stop toggle. |
| **1.4** — 11 Nov 2026 | Replaced the single toggle with separate tape-recorder-style **Record** and **Stop** buttons. Recording starts only when requested. Stopping inserts transcribed text at the saved editor cursor/selection position, rather than always appending at the end. Notes continue to save automatically. |

## Current status

**Latest documented Android version:** 1.4 (`versionCode 5`), package `com.myapps.mydictation`. The Android debug APK was built successfully in GitHub Actions. Installation on a specific phone is not implied by a successful build.

**Not part of v1.4:** Offline Whisper integration in the customised app; the requested three-second *silence-detection* recording stop; system-wide Windows dictation; and verified automatic import of all notes from other apps. These are requested or experimental work, not released features. The Android implementation still uses the platform's `SpeechRecognizer` and may require a working speech service.

## Source and distribution

- The `my-dictation/` source tree contains the Android app.
- This document must be included in every source release ZIP and alongside the APK in download artifacts.
- Source code is available under the MIT License in `LICENSE`.
- No personal notes, raw voice recordings or private Android app data are included in the public source release.

For the current source and build instructions, see `README.md`.
