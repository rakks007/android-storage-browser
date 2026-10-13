# Upload and build with GitHub

## Browser method

1. Create a new empty GitHub repository. Do not add README, .gitignore, or license there.
2. Extract this ZIP on your Mac.
3. Open the new repository on GitHub.
4. Select **Add file > Upload files**.
5. Drag all files and folders from inside `AndroidNativeStorageBrowser` into the upload page, including `.github`.
6. Commit to `main`.
7. Open the repository's **Actions** tab.
8. Select **Build Android APK**, then **Run workflow** if it did not start automatically.
9. Open the completed workflow run.
10. Under **Artifacts**, download `android-storage-browser-debug-apk`.
11. Extract the downloaded artifact ZIP to obtain `app-debug.apk`.

## Git command method

```bash
cd ~/Downloads/AndroidNativeStorageBrowser
git init
git add .
git commit -m "Initial native Android storage browser"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
```

The Actions workflow builds a debug APK. A debug APK is suitable for direct testing but is not a production Play Store release. A release build requires a private signing key stored securely as GitHub Actions secrets.
