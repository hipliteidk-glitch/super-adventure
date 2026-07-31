# Floating Launcher APK

A floating overlay app that launches any Android app from a floating bubble.

## How to build the APK via GitHub Actions

1. Push this repository to GitHub:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/YOUR_USERNAME/floating-launcher.git
   git push -u origin main
   ```

2. Go to your repo on GitHub → Actions tab

3. The workflow will run automatically on push. Wait for it to finish.

4. Download the APK from the "Artifacts" section of the workflow run.

## Manual build (if you have Android Studio)

1. Open project in Android Studio
2. Build → Build Bundle(s) / APK(s) → Build APK(s)

## How to use the app

1. Install the APK on your Android device
2. Grant overlay permission when prompted
3. Select which app you want the floating bubble to launch
4. A floating bubble appears on your screen
5. Tap the bubble to launch the selected app
6. Drag the bubble to reposition it
7. Tap the X to close the floating overlay
