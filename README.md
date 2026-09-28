# Multiki (bbvideoplayer)

An Android video player for kids with parent controls and a kiosk mode.
Ships as two apps built from a shared core: the phone kiosk edition and an
Android TV edition (D-pad, no PIN gate, bottom controls bar).

**Phone package:** `com.dima.bbvideoplayer` · **TV package:** `com.dima.bbvideoplayer.tv` ·
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 34

## Overview

| Mode | Screen | Description | Access |
|------|--------|-------------|--------|
| Kid | `KidPlayerScreen` (phone) / `TvPlayerScreen` (TV) | Fullscreen libVLC player, video navigation | Default |
| Parent | `ParentDashboardScreen` | Playlist management, manual playback | Phone: secret door → PIN · TV: «Родителям» button (no PIN) |
| File picker | `FilePickerScreen` | On-device file browser | From the parent screen |

The phone edition is a kiosk (Lock Task Mode HOME launcher); the TV edition is
a regular Leanback app — same library and folder selection, no kiosk, no PIN.

## Project structure

```
bbvideoplayer/
├── app/                          # Phone edition (kiosk, PIN-protected parent area)
├── app-tv/                       # Android TV edition (Leanback, D-pad, no PIN)
├── core/                         # Shared playback core (library module)
└── README.md
```

```
core/src/main/java/com/dima/bbvideoplayer/
├── data/                         # VideoRepository (DataStore), VideoLibraryService,
│                                 # PlaybackStateRepository, compatibility checker
├── player/                       # VideoPlayerManager (libVLC), SeekAccelerator
├── ui/
│   ├── components/               # BounceButton, SeekButton, VerticalScrollbar
│   ├── screens/
│   │   ├── BaseKidPlayerScreen.kt    # playback orchestration + platform slots
│   │   ├── ParentDashboardScreen.kt  # showPinControls=false hides PIN UI (TV)
│   │   ├── FilePickerScreen.kt
│   │   ├── dashboard/VideoListGrouping.kt
│   │   └── filepicker/
│   └── theme/
└── utils/                        # StoragePermissionHelper, VideoPathUtils, HuaweiStorageHelper

app/src/main/java/com/dima/bbvideoplayer/     (phone only)
├── MainActivity.kt               # Entry point: Compose, kiosk lifecycle, immersive UI
├── BbVideoPlayerApp.kt           # Application (singleton VideoPlayerManager)
├── AppState.kt                   # Shared state (managers, lock-task flag)
├── admin/                        # LockTaskManager, device admin receiver (kiosk)
├── navigation/AppNavHost.kt      # kid_player ↔ parent_dashboard ↔ file_picker
├── ui/components/                # PinDialog, PinValidator
├── ui/screens/KidPlayerScreen.kt # BaseKidPlayerScreen + PIN dialog + secret door
└── ui/screens/kidplayer/         # left-hand PlayerControlsOverlay, Battery, SecretDoorGesture

app-tv/src/main/java/com/dima/bbvideoplayer/tv/   (TV only)
├── TvMainActivity.kt             # Entry point (Leanback launcher, landscape)
├── TvPlayerApp.kt                # Application (singleton VideoPlayerManager)
├── navigation/TvNavHost.kt       # same routes, no kiosk, no PIN
└── ui/                           # TvPlayerScreen + bottom TvPlayerControlsOverlay
```

### Where to look

| Task | File |
|------|------|
| Kiosk | `app/.../admin/LockTaskManager.kt` |
| Kiosk auto-start | `app/.../navigation/AppNavHost.kt` |
| Parent PIN | `app/.../ui/components/PinDialog.kt` |
| Secret door | `app/.../ui/screens/kidplayer/SecretDoorGesture.kt` |
| Video selection | `core/.../ui/screens/FilePickerScreen.kt` |
| Honor/Huawei SD card | `core/.../utils/HuaweiStorageHelper.kt` |
| Video library | `core/.../data/VideoRepository.kt` |
| Playback | `core/.../player/VideoPlayerManager.kt` (libVLC) |
| TV D-pad controls | `app-tv/.../ui/TvPlayerControlsOverlay.kt` |

## Architecture

```
FilePickerScreen / ParentDashboardScreen
  → VideoRepository (DataStore)
  → KidPlayerScreen (phone) / TvPlayerScreen (TV)
      → BaseKidPlayerScreen → VideoPlayerManager (libVLC)
  → PlaybackStateRepository (resume from position)
```

## Build and tests

```bash
./gradlew assembleDebug            # phone (:app), TV (:app-tv) and :core
./gradlew testDebugUnitTest        # unit tests (Robolectric, phone + core)
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

- [x] Kiosk (Lock Task Mode) with Device Owner policies (phone)
- [x] libVLC playback of local videos (AVI/XVID/AC3 included)
- [x] File picker with batch folder selection
- [x] Playback position persistence
- [x] PIN-protected parent area with a configurable PIN (phone)
- [x] Configurable player controls side (left/right) via the parent area (phone)
- [x] Android TV edition: Leanback launcher, D-pad navigation, bottom controls, no PIN
- [x] Unit tests for repositories, path utils, PIN, file system

## Roadmap

- [ ] UI for Device Admin provisioning
