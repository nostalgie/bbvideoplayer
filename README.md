# Multiki (bbvideoplayer)

An Android video player for kids with parent controls and a kiosk mode.

**Package:** `com.dima.bbvideoplayer` · **Min SDK:** 26 (Android 8.0) · **Target SDK:** 34

## Overview

| Mode | Screen | Description | Access |
|------|--------|-------------|--------|
| Kid | `KidPlayerScreen` | Fullscreen libVLC player, video navigation, kiosk | Default |
| Parent | `ParentDashboardScreen` | Playlist management, manual playback | Long-press the gear 3 s → PIN |
| File picker | `FilePickerScreen` | On-device file browser | From the parent screen |

## Project structure

```
bbvideoplayer/
├── app/                          # The application
└── README.md
```

```
app/src/main/java/com/dima/bbvideoplayer/
├── MainActivity.kt               # Entry point: Compose, kiosk lifecycle, immersive UI
├── BbVideoPlayerApp.kt           # Application (singleton VideoPlayerManager)
├── AppState.kt                   # Shared state (managers, lock-task flag)
│
├── admin/
│   ├── LockTaskManager.kt        # Kiosk: start/stop Lock Task, Device Owner policies
│   └── MyDeviceAdminReceiver.kt  # Device Admin Receiver
│
├── data/
│   ├── VideoRepository.kt        # DataStore: watched folders, selection, expanded folders
│   └── PlaybackStateRepository.kt  # SharedPreferences: playback position
│
├── navigation/
│   └── AppNavHost.kt             # kid_player ↔ parent_dashboard ↔ file_picker
│
├── player/
│   ├── VideoPlayerManager.kt     # libVLC: playlist, next/prev, seek
│   └── SeekAccelerator.kt        # Progressive seek acceleration on long-press
│
├── ui/
│   ├── components/               # BounceButton, SeekButton, PinDialog, PinValidator
│   ├── screens/
│   │   ├── KidPlayerScreen.kt
│   │   ├── ParentDashboardScreen.kt
│   │   ├── FilePickerScreen.kt
│   │   ├── dashboard/VideoListGrouping.kt
│   │   ├── kidplayer/            # PlayerControlsOverlay, SecretDoorGesture
│   │   └── filepicker/
│   └── theme/
│
└── utils/
    ├── HuaweiStorageHelper.kt
    ├── VideoPathUtils.kt
    └── StoragePermissionHelper.kt
```

### Where to look

| Task | File |
|------|------|
| Kiosk | `admin/LockTaskManager.kt` |
| Kiosk auto-start | `navigation/AppNavHost.kt` |
| Parent PIN | `ui/components/PinDialog.kt` |
| Secret door | `ui/screens/kidplayer/SecretDoorGesture.kt` |
| Video selection | `ui/screens/FilePickerScreen.kt` |
| Honor/Huawei SD card | `utils/HuaweiStorageHelper.kt` |
| Video library | `data/VideoRepository.kt` |
| Playback | `player/VideoPlayerManager.kt` (libVLC) |

## Architecture

```
FilePickerScreen / ParentDashboardScreen
  → VideoRepository (DataStore)
  → KidPlayerScreen → VideoPlayerManager (libVLC)
  → PlaybackStateRepository (resume from position)
```

## Build and tests

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest          # unit tests (Robolectric)
./gradlew connectedDebugAndroidTest  # Compose UI tests (requires an emulator)
```

## Tech stack

| Component | Version |
|-----------|---------|
| Kotlin | 1.9.22 |
| Jetpack Compose BOM | 2024.01.00 |
| libVLC | 3.6.2 |
| Navigation Compose | 2.7.6 |
| DataStore Preferences | 1.1.1 |
| AGP | 8.2.2 |

## Implemented

- [x] Kiosk (Lock Task Mode) with Device Owner policies
- [x] libVLC playback of local videos (AVI/XVID/AC3 included)
- [x] File picker with batch folder selection
- [x] Playback position persistence
- [x] PIN-protected parent area with a configurable PIN
- [x] Unit tests for repositories, path utils, PIN, file system

## Roadmap

- [ ] UI for Device Admin provisioning
