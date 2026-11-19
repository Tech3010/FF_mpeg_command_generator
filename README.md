# FFmpeg Offline Studio — Android APK Project

This project wraps `ffmpeg_offline_studio.html` inside a lightweight native Android WebView app with a real Android Storage Access Framework (`ACTION_OPEN_DOCUMENT` & `ACTION_OPEN_DOCUMENT_TREE`) path resolver and `MediaMetadataRetriever` stream inspector.

## Features Built Into the APK
- **Exact Real Path Capture:** Picking a file in `/storage/emulated/0/Movies/AlightMotion/edit.mp4` automatically fills `/storage/emulated/0/Movies/AlightMotion` and `edit.mp4` into the studio with zero manual path selection.
- **Native Media Inspection:** Reads exact resolution, duration, file size, and bitrate using Android's `MediaMetadataRetriever`.
- **Native Android Clipboard:** Uses `ClipboardManager` directly so **Copy Command** always works cleanly.
- **Zero Background Battery Drain:** Runs as a normal Android app icon with no background server.

## How to Build the `.apk` (No PC Needed — Using GitHub Actions)
1. Create a new repository on [GitHub](https://github.com/new) (e.g. `ffmpeg-studio-apk`).
2. Extract this `.zip` and upload all files (including the `.github/workflows/build-apk.yml` folder) to the repository.
3. Open the **Actions** tab in your GitHub repo, wait ~2 minutes for **Build FFmpeg Studio APK** to turn green, and download **`FFmpeg-Offline-Studio-APK`** (`app-debug.apk`) directly onto your POCO X7 Pro to install.

## Or Build Locally on Lubuntu Linux
```bash
gradle assembleDebug
# Output APK: app/build/outputs/apk/debug/app-debug.apk
```
