# svl — Samsung 2×2 Home Widget 💗

A small native Android app. The home-screen widget shows a square photo. Tapping it plays a short **video with sound without opening an Activity**, then restores the still image. The video image is implemented with fast `RemoteViews` bitmap updates (~12fps) synchronized to Android `MediaPlayer` audio. Native `VideoView` is not supported inside Android `RemoteViews`, so playback smoothness depends on launcher/device/OS throttling.

## Install

1. Open **Actions → Build svl Android APK** on GitHub. After a successful build, download the `svl-debug-apk` artifact and extract the APK inside it. Or open this project with Android Studio (Java 17, Android SDK 35) and build an APK.
2. Install the APK on a Samsung phone. Open `svl` and choose your private video and photo using the pink buttons.
3. Tap **홈 화면에 2×2 위젯 추가**, or long press a blank home screen → Widgets → svl → add.
4. Tapping the widget plays the locally stored clip and sound, then restores your photo.

The app requires **no INTERNET permission**. Selected media stays in app-private storage.

## Personal bundled edition

The separate ZIP shared inside the ChatGPT conversation includes `app/src/main/assets/default_photo.jpg` and `app/src/main/assets/naye_clip.mp4`. That personalized edition uses the uploaded media as its initial default **without publishing the user's personal media in this public GitHub repository**. This public repo intentionally excludes these two personal files; its build prompts you to select them once.

## Technical notes

- Android standard launcher widgets cannot embed a real video surface; frames are updated at about 12fps (best effort). Some One UI versions may drop frames.
- The audio plays via `MediaPlayer` in a `mediaPlayback` foreground service launched by tapping the widget. A playback notification may show.
- The first playback may pause briefly while decoding frames.
- No overlay permission, no accessibility permission, and no internet connection are needed.

## v1.1: Photo orientation fix

Samsung Gallery photos may store rotation as EXIF metadata. The app now
applies all eight EXIF orientations, normalizes new imports as upright,
center-cropped JPEGs, and also repairs legacy saved photos on display.
The new **사진 방향 90° 돌리기 ↻** button lets the user correct any unusual
image directly; it updates the preview and all widget instances and is
saved across widget updates/reboots. Photo imports reset manual rotation.
Update the signed APK without uninstalling to preserve existing settings.
