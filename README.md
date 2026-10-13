# Android Native Storage Browser

A native Java Android app (version 1.1.0). It browses shared internal storage without requiring the user to select or grant access to a folder. On Android 11 and later, Android settings must grant the app all-files access. The app runs a PIN-protected local HTTP server and shows a persistent foreground-service notification while sharing. The interface follows the device's light or dark appearance, including Redmi/Xiaomi system themes.

## Build in Android Studio

1. Install Android Studio with Android SDK 36 and JDK 17.
2. Open this project folder.
3. Let Gradle sync.
4. Select **Build > Build APK(s)**.
5. Install `app/build/outputs/apk/debug/app-debug.apk` on the Android phone.

## Use

1. Open the app and tap **Start sharing storage**.
2. On Android 11 and later, enable **Allow access to manage all files** in Android settings and return to the app.
3. Keep the phone and receiving device on the same trusted Wi-Fi.
4. Open the displayed URL and enter the displayed PIN.
5. Tap **Stop sharing** when finished.

## Limitations

- Android continues to restrict certain protected system and app-private folders; apps cannot override those OS restrictions.
- Android 10 does not provide this app's broad-storage-access flow; use Android 11 or later for full shared-storage browsing.
- This version is download/read-only in the browser. It does not upload or delete files.
- It uses HTTP, not HTTPS; use only on trusted Wi-Fi.
- Android may impose foreground-service time limits. Restart sharing when required.
- This source archive is not a signed APK. Sign a release build using your own Android signing key.

## Build automatically with GitHub Actions

This repository includes `.github/workflows/build-apk.yml`. Every push to `main` or `master`, pull request, or manual workflow run builds a debug APK and uploads it as a GitHub Actions artifact named `android-storage-browser-debug-apk`.

See `UPLOAD_TO_GITHUB.md` for step-by-step upload and download instructions.
