# Strata launcher

The Strata OS design as an Android home-screen app for the Xiaomi 15T. It replaces
HyperOS's home screen without unlocking the bootloader, and you can switch back any time.

## What it does

- **Spaces.** Personal, Work and Out each have their own set of up to 16 apps.
  Strata switches to Work on weekdays during your work hours and to Personal the rest
  of the time. Tap a Space to pin it until the next switch, or turn automatic switching
  off in settings.
- **Context widget.** The time and date, your next calendar event (once you allow
  calendar access) and your battery level.
- **Thumb-reach bottom.** Search, the Controls button and a dock with your default
  phone, messages, camera and browser apps.
- **Controls.** Tiles for Wi-Fi, mobile data, Bluetooth and Focus (Do Not Disturb);
  quick toggles for torch, vibrate, rotation lock, auto-brightness, location, airplane
  mode, battery saver and cast; brightness and media-volume sliders.
- **All apps and search.** Swipe up, or tap the search bar. Results also include
  settings pages and a web search. Hold any app to add it to a Space, see app info or
  uninstall it.
- **Privacy.** Lists which of your apps can use the camera, microphone and location,
  with a link to each app's permissions.
- **Look.** A graphite theme, the strata wallpaper, and a choice of four accent colours.
  Monochrome icons use each app's themed icon where it has one.

Gestures on the home screen: swipe up for All apps, swipe down for notifications.

### What a launcher can't do
Android only lets the system change some settings. For Wi-Fi, mobile data, Bluetooth,
location, airplane mode, battery saver and cast, Strata shows their state and opens the
system panel for them. Torch, vibrate and volume work directly. Brightness, rotation
lock and Focus work once you grant the permission Strata asks for. The lock screen,
always-on display and system notification shade stay as HyperOS draws them.

## Install it on your Xiaomi 15T

1. On the phone, open the repository's **Releases** page and download
   `strata-launcher.apk` from **Strata launcher (latest build)**.
2. Open the downloaded file. If HyperOS asks, allow your browser or file manager to
   install unknown apps, then tap **Install**.
3. Press the Home button. When HyperOS asks which app to use, pick **Strata** and
   choose **Always**. You can also go to **Settings › Apps › Default apps › Home app**.
   Strata shows a "Make Strata your home screen" card until it is the default.
4. Optional, in Strata's **Privacy and settings** screen (Controls › Privacy and
   settings, or the settings button in All apps):
   - **Calendar**, to show your next event.
   - **Modify system settings**, for the brightness slider, auto-brightness and
     rotation lock.
   - **Do Not Disturb access**, for the Focus tile.

To go back to the HyperOS home screen, set **System launcher** as the Home app in the
same Default apps setting, or uninstall Strata.

Some HyperOS versions turn off full-screen gestures while a third-party home app is
the default. If that happens, HyperOS offers to switch you to navigation buttons.

## Building

GitHub Actions builds the app on every push that touches `strata-os/launcher/`
(see `.github/workflows/strata-launcher.yml`), uploads the APK as a workflow artifact
and replaces the `strata-launcher-latest` pre-release.

To build it yourself you need JDK 17 and the Android SDK (platform 35):

```sh
cd strata-os/launcher
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Every build is signed with `app/strata.keystore`, so a new build installs over the
old one. That key is committed to the repo on purpose and only fits a personal,
sideloaded app. Don't use it to sign anything you publish.

Built with Kotlin and Jetpack Compose; minimum Android 10, targets Android 15.
Fonts: Space Grotesk and Inter, both under the SIL Open Font License
(see `third_party/fonts/`).
