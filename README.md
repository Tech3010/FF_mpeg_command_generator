# FFmpeg Offline Studio — Android APK Project

This project wraps `ffmpeg_offline_studio.html` inside a lightweight native Android WebView app with a real Android Storage Access Framework (`ACTION_OPEN_DOCUMENT` & `ACTION_OPEN_DOCUMENT_TREE`) path resolver and `MediaMetadataRetriever` stream inspector.

## Features Built Into the APK
- **Exact Real Path Capture:** Picking a file in `/storage/emulated/0/Movies/AlightMotion/edit.mp4` automatically fills `/storage/emulated/0/Movies/AlightMotion` and `edit.mp4` into the studio with zero manual path selection.
- **Native Media Inspection:** Reads exact resolution, duration, file size, and bitrate using Android's `MediaMetadataRetriever`.
- **Native Android Clipboard:** Uses `ClipboardManager` directly so **Copy Command** always works cleanly.
- **Zero Background Battery Drain:** Runs as a normal Android app icon with no background server.