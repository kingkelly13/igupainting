# Strata Remote

A universal remote for the Xiaomi 15T's IR blaster, styled like the rest of Strata. It
controls TVs, streaming boxes, LED lights, soundbars, projectors and anything else with an
infrared remote.

## What it does

- **Finds your code.** Pick what you're controlling and its brand. Strata Remote sends the
  power code, and you say whether the device responded. If you don't know the brand, it tries
  every built-in code set in turn.
- **Built-in codes** for Samsung, LG, Sony, Roku TVs (TCL, Hisense, Sharp, Insignia and others)
  and Roku players, Vizio, Philips (RC6 and the older RC5), Panasonic and Toshiba TVs; the
  common 24-key and 44-key LED-strip remotes; and Epson projectors (power only).
- **Imports any remote.** Open a Flipper Zero `.ir` file and every button comes with it. The
  community [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) has files for thousands of
  devices: air conditioners, fans, soundbars, set-top boxes, projectors and more. Download the
  file for your model on the phone, then choose **Add a remote › (kind) › Import a Flipper .ir file**.
- **Layouts** that match the device: power, input and mute, media keys, a D-pad with volume and
  channel rockers, back, home, menu and exit. A **Numbers** tab has the digits and the coloured
  keys, and LED lights get their colour grid and effects. **All buttons** lists everything,
  including imported buttons with no fixed place (Netflix, subtitles and so on).
- **Holds like a real remote.** Keep a finger on volume, channel, the arrows, rewind or fast
  forward and the code repeats until you let go.
- **Volume buttons.** While a remote is open, your phone's volume buttons send its volume codes.
  You can turn this off in Settings.
- **Edit any button.** Tap **Edit**, then a button, to type a code as a protocol and
  address/command (as a Flipper Zero shows them), paste a Pronto hex code, or enter raw
  timings. **Test** sends it before you save. In **All buttons**, **Add** creates a named button.
- **Export.** Any remote can be saved as a `.ir` file, for a backup or for a Flipper.

Protocols: NEC, NEC extended, NEC 42-bit, Samsung, Sony SIRC (12, 15 and 20-bit), Philips RC5,
RC5X and RC6, Panasonic/Kaseikyo, RCA and Pioneer, plus raw timings and Pronto hex (learned
`0000` codes, and `5000`/`6000` RC5/RC6 codes).

### What it can't do

- **Learn from your old remote.** The 15T's IR blaster only sends; it has no receiver. Use a
  built-in code set or a `.ir` file instead.
- **Air conditioners from the library.** An AC remote sends the whole state (mode, temperature,
  fan) every time, so the codes differ for each setting. Imported `.ir` files for your AC model
  work button by button.
- **Devices that don't use infrared,** such as most Fire TV, Chromecast and Apple TV remotes
  (Bluetooth or Wi-Fi), or Roku sticks paired with a voice remote.

## Install it on your Xiaomi 15T

1. On the phone, open the repository's **Releases** page and download `strata-remote.apk` from
   **Strata Remote (latest build)**.
2. Open the downloaded file. If HyperOS asks, allow your browser or file manager to install
   unknown apps, then tap **Install**.
3. Open **Strata Remote** and tap **Add a remote**. The IR blaster is on the top edge of the
   phone, so point that edge at the device.

The app needs no permissions you have to grant. IR transmit is allowed at install.

## Building

GitHub Actions builds the app on every push that touches `strata-os/remote/`
(see `.github/workflows/strata-remote.yml`). It runs the unit and screen tests, uploads the APK as
a workflow artifact and replaces the `strata-remote-latest` pre-release.

To build it yourself you need JDK 17 and the Android SDK (platform 35):

```sh
cd strata-os/remote
./gradlew testDebugUnitTest assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

The unit tests in `app/src/test` check each encoder against the codes as code lists print them
(LG power is `0x20DF10EF`, Samsung power `0xE0E040BF`, and so on), and that every built-in code
fits in Android's two-second transmit limit. `ScreenshotTest` walks through setup and every
remote layout under Robolectric and saves each screen to `app/build/screenshots/`, so layout
changes can be checked without a phone.

Every build is signed with `app/strata-remote.keystore`, so a new build installs over the old
one. That key is committed on purpose and only fits a personal, sideloaded app. Don't use it to
sign anything you publish.

### Where things are

| Path | What it holds |
| --- | --- |
| `ir/IrEncoder.kt` | Protocol encoders: address and command to on/off timings |
| `ir/IrFormats.kt` | Pronto hex and Flipper `.ir` reading and writing |
| `ir/IrBlaster.kt` | Sending through `ConsumerIrManager`: frame gaps, held-key repeats, RC5/RC6 toggle |
| `data/CodeLibrary.kt` | The built-in code sets |
| `data/Keys.kt` | Standard buttons and the names imported files use for them |
| `ui/` | Jetpack Compose screens |

Built with Kotlin and Jetpack Compose; minimum Android 10, targets Android 15.
Fonts: Space Grotesk and Inter, both under the SIL Open Font License (see `third_party/fonts/`).
