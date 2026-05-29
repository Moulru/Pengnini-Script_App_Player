# Pengnini · Script App Player
<img width="1021" height="456" alt="image" src="https://github.com/user-attachments/assets/7641cd28-c63b-4295-b824-c5f21e26cd66" />

An Android-only video player and library app with funscript playback support.

---

## 📥 Download

### ▶ https://github.com/Moulru/Pengnini-Script_App_Player/releases

**Installation**
1. Download the APK from the link above
2. Android Settings → Security → Allow "Install from unknown sources"
3. Open the downloaded file to install

---

### Video Player
- AndroidX Media3 (ExoPlayer) — `mp4` / `mkv` / `webm`
- Aspect ratio: Fit (default) / Fill / Stretch / 16:9 / 4:3
- Playback speed control (script speed syncs to playback speed)
- Force landscape / portrait orientation toggle
- Pinch-to-zoom video scale, 50% – 400%
- Region-based gestures
  - Left 40% vertical swipe → brightness
  - Right 40% vertical swipe → volume
  - Double-tap left / right → seek N seconds (5 / 10 / 20 / 30 sec, configurable)
- Mute toggle + volume slider
- Auto subtitle matching (`.srt` / `.ass` / `.vtt`) + CC on/off
- Quick previous / next video navigation
- Video loop

### Library
- Folder registration via Storage Access Framework (SAF)
- Grid / list view toggle
- Tags, ratings, favorites
- Search + filters (script presence · folder · tags · personal rating)
- Sort (date added · title · duration · rating · resolution · file size)

### Security / Misc
- Optional **3×3 pattern lock** on app launch
- Background playback (Foreground Service)
- Korean / English

---

## 📋 Requirements

- Android 8.0 (API 26) or higher, tested up to Android 15
- Internet connection
- Storage access permission (SAF)

---

## 🔒 Privacy

- Connection Key is encrypted via `EncryptedSharedPreferences`
- Lock pattern is also encrypted via `EncryptedSharedPreferences`
- Video and script files stay entirely on-device
- When uploading a funscript, it is temporarily hosted on HandyFeeling servers (for device download)
- **Viewing history, statistics, and analytics are never stored or transmitted**

## 🙏 Acknowledgments

This app is built on top of these open-source libraries (all under Apache 2.0):

- AndroidX — Jetpack Compose, Media3 (ExoPlayer), Room, Lifecycle, Navigation, DataStore, Security Crypto
- Square — Retrofit, OkHttp
- Coil-kt — Coil
- JetBrains — Kotlin, Kotlinx Coroutines, Kotlinx Serialization
