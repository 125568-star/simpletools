# My Dictation — Version Information

This file records the Android app's development history. Versions are listed in release order. The version number is the Android app's `versionName`, not a separate Windows release.

| Version | Changes in this version |
| --- | --- |
| **1.0** — 9 Nov 2026 | Initial Android app. Dark-themed note and folder screens; record a voice note; transcription attempt; create to-do and shopping lists with checklists; folder management, note colours, import of exported notes, and safeguards around previously saved audio. Early transcription could fail on some Android speech providers. |
| **1.1** — 9 Nov 2026 | Changed new dictation to Android's live microphone speech-recognition service rather than injecting a pre-recorded PCM stream. Added continuous listening/restart and partial-result handling. Legacy PCM retry remained available but provider-dependent. |
| **1.2** — 10 Nov 2026 | Moved **Unfiled** and **All Notes** to the bottom of the folder list; added automatic saving instead of a **Save Text** button; black, bordered note editor; centered red **REC** indicator; title generation and conservative automatic folder classification; stop recording when leaving a note. |
| **1.3** — 10 Nov 2026 | Added long-press selection of listed notes and multi-select **Move**/**Delete**, with confirmation before deletion. Opening notes no longer started recording automatically. Introduced a single manual Record/Stop toggle. |
| **1.4** — 11 Nov 2026 | Replaced the single toggle with separate tape-recorder-style **Record** and **Stop** buttons. Recording starts only when requested. Stopping inserts transcribed text at the saved editor cursor/selection position, rather than always appending at the end. Notes continue to save automatically. |
| 1.5 | Offline Whisper, WebRTC speech detection, automatic stop after three seconds of silence, and recoverable audio when transcription fails. Adds a Windows dictation helper. |
| 1.5.1 | Black text on light blue buttons and light note cards. Keeps the editor black and removes unused Android speech recognition code and extra native binaries. |
| 1.5.2 | Stop cancels Whisper transcription. Record can replace a pending take or redo the most recent transcript in the open editor. |
| 1.5.3 | Adds a Delete note button in the editor with confirmation. |
| 1.5.4 | Record appends each new transcript to the end of the note, preserving all saved text. |
| 1.5.5 | Dictate directly into the title, append body recordings as separate paragraphs, show a full-screen hourglass during transcription, and save and close the note with the floppy disk button. |

## Source and distribution

The combined v1.5 distribution uses GPLv3. The original MIT notice is retained in LICENSE-MIT.
