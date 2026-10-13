# Android Native Storage Browser

A native Java Android app (version 1.1.0). It browses shared internal storage without requiring the user to select or grant access to a folder. On Android 11 and later, Android settings must grant the app all-files access. The app runs a local HTTPS server on an OS-assigned port with a newly generated PIN each time sharing starts and shows a persistent foreground-service notification. The displayed address uses the device's current Wi-Fi IP, which is assigned by the network. Its self-signed TLS certificate's SHA-256 fingerprint is shown in the app for verification. The interface follows the device's light or dark appearance, including Redmi/Xiaomi system themes.

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
4. Open the displayed HTTPS URL, verify the certificate fingerprint against the value in the app, and proceed through the browser's self-signed certificate warning if prompted.
5. Enter the newly generated six-digit server PIN shown in the app.
6. Tap **Stop sharing** when finished.

Set an app-lock PIN the first time sharing starts; the PIN is masked when entered and required when reopening the app. The unlock dialog blurs the app screen behind it on Android 12 and later. The server generates a separate PIN each time sharing starts, and the browser interface requires that current server PIN. TLS encrypts traffic, but the certificate is self-signed; browsers may show a warning until the certificate is trusted. Verify its SHA-256 fingerprint before proceeding. Sharing stops automatically after ten minutes without authenticated browser activity. The browser interface fills the available screen, adapts to phone and desktop widths, and filters the current folder as you type in its search field. You can upload files into the open folder and download files from it; a floating progress panel tracks transfers. Uploading a file with an existing name is rejected rather than overwriting it.

## Limitations

- Android continues to restrict certain protected system and app-private folders; apps cannot override those OS restrictions.
- Android 10 does not provide this app's broad-storage-access flow; use Android 11 or later for full shared-storage browsing.
- The browser can upload and download files, but cannot delete or overwrite existing files.
- TLS uses a self-signed certificate rather than a public certificate authority; verify the displayed SHA-256 fingerprint and use only on trusted Wi-Fi.
- Android may impose foreground-service time limits. Restart sharing when required.
- This source archive is not a signed APK. Sign a release build using your own Android signing key.

## Build automatically with GitHub Actions

This repository includes `.github/workflows/build-apk.yml`. Every push to `main` or `master`, pull request, or manual workflow run builds a debug APK and uploads it as a GitHub Actions artifact named `android-storage-browser-debug-apk`.

See `UPLOAD_TO_GITHUB.md` for step-by-step upload and download instructions.
