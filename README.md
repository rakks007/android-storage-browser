# Android Native Storage Browser

A native Java Android app. It uses Android's Storage Access Framework, so the user explicitly selects the folder to share. The app runs a PIN-protected local HTTP server and shows a persistent foreground-service notification while sharing.

## Build in Android Studio

1. Install Android Studio with Android SDK 36 and JDK 17.
2. Open this project folder.
3. Let Gradle sync.
4. Select **Build > Build APK(s)**.
5. Install `app/build/outputs/apk/debug/app-debug.apk` on the Android phone.

## Use

1. Open the app and tap **Select shared folder**.
2. Select Internal storage, Download, DCIM, a supported SD-card folder, or another folder offered by Android's system picker.
3. Tap **Start sharing**.
4. Keep the phone and receiving device on the same trusted Wi-Fi.
5. Open the displayed URL and enter the displayed PIN.
6. Tap **Stop sharing** when finished.

## Limitations

- Android app-private folders remain unavailable without root.
- This version is download/read-only in the browser. It does not upload or delete files.
- It uses HTTP, not HTTPS; use only on trusted Wi-Fi.
- Android may impose foreground-service time limits. Restart sharing when required.
- This source archive is not a signed APK. Sign a release build using your own Android signing key.

## Build automatically with GitHub Actions

This repository includes `.github/workflows/build-apk.yml`. Every push to `main` or `master`, pull request, or manual workflow run builds a debug APK and uploads it as a GitHub Actions artifact named `android-storage-browser-debug-apk`.

See `UPLOAD_TO_GITHUB.md` for step-by-step upload and download instructions.
