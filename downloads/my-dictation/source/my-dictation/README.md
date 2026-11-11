# My Dictation — Android (open-source)

My Dictation is a free Android voice-notes, folders and checklists app. This repository contains the **original Android implementation**, version **1.4**, under the [MIT License](LICENSE).

## Features in v1.4

- Dark interface, black bordered text editor, and autosave without a Save Text button.
- Folders, per-note colours, to-do and shopping checklists.
- **Unfiled** and **All Notes** at the bottom of the folder listing.
- Long-press note selection, multi-select move and confirmed deletion.
- New and existing notes do **not** record on opening. Separate **Record** and **Stop** buttons at the bottom.
- A small red **REC** label is visible only while actively recording.
- Stop requests transcription and inserts the recognised words at the saved text caret/selection position.
- Titles and conservative category assignment based on dictated words.
- Import exported TXT/Markdown/simple JSON notes from other apps.

## Limitations (important)

- The customised v1.4 app still uses Android's `SpeechRecognizer` service. It does **not** include the offline Whisper engine in the current public source.
- Some speech providers can fail to transcribe or can be slow; results depend on Android's speech service. Live dictation does not record a new recoverable audio file. An older PCM retry workflow is still present, but not reliable across providers.
- The requested **three seconds of silence to stop automatically** is not implemented in v1.4.
- Windows-wide dictation and automatic import of another Android app's private notes are not included.
- This project has not been verified as ready for general consumer release. Public APK signing and end-to-end device validation are separate release tasks.

## Version history

See [VERSION_INFO.md](VERSION_INFO.md) for a two-column version-by-version change table, v1.0 through v1.4.

## Build

Requires Java 17 and Android SDK 35 (Android Studio or Gradle). From this directory:

```sh
gradle assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds a debug APK and a **source ZIP containing VERSION_INFO.md and LICENSE**. Package ID: `com.myapps.mydictation`. Minimum Android: 13 / API 33.

## Privacy and permissions

The Android app stores notes in app-private storage. It does not access other apps' private data. Public source archives intentionally contain **no user notes, audio recordings, passwords or private keys**.

## License

MIT; see [LICENSE](LICENSE). Any future integration using GPL-licensed upstream Whisper/Notely code must follow the applicable upstream license and may need different distribution terms.
