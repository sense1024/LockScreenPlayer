# LockScreenPlayer

Android local video player: **audio continues on lock screen**, **wake screen restores video** (Media3 ExoPlayer + `MediaSessionService`).

## Requirements

| Item | Requirement |
|------|-------------|
| **Minimum Android** | **8.0 (API 26)** |
| Target SDK | Android 15 (API 35) |
| Recommended testing | Physical devices on Android 12–15 (lock screen / notifications) |

## Documentation

| Document | Description |
|----------|-------------|
| [docs/FUNCTIONAL_SPEC.md](docs/FUNCTIONAL_SPEC.md) | Functional spec, state machines, flowcharts |
| [docs/PROJECT_SKELETON.md](docs/PROJECT_SKELETON.md) | Project layout, lifecycle rules, build notes |

## Quick start

1. Open this folder in **Android Studio** (AGP 9.1.x supported) → **Sync Project with Gradle Files**.
2. Connect a **physical device** (recommended for lock screen / notification behavior).
3. Run `app` → grant notification permission (Android 13+) → pick a video → verify lock / wake.

```powershell
.\gradlew.bat installDebug
```

## Tech stack

- Kotlin 2.3.21 (AGP built-in) · **minSdk 26** · targetSdk 35 · compileSdk 36
- AGP 9.1.1 · Gradle 9.3.1
- [AndroidX Media3](https://developer.android.com/media/media3) 1.10.1 (`media3-exoplayer` + `media3-session` + `media3-ui`)

## Features

- Open local videos from the system picker (`video/*`).
- Audio continues when locked; waking the screen restores `PlayerView` on the lock screen.
- Lock screen / notification **MediaStyle** controls (play / pause, artwork).
- **Play once** / **Repeat** (preference persisted; written only by `PlaybackService`).
- Home screen **Resume playback** (URI / title persisted; shown after process restart if permission still valid).
- Home screen **gear → Interface language**: Traditional Chinese, English, Japanese, or system default.

## String resources

| Language | Directory |
|----------|-----------|
| English (default) | `res/values/` |
| Traditional Chinese | `res/values-zh-rTW/` |
| Japanese | `res/values-ja/` |

## License

Project skeleton — add your own license as needed.
