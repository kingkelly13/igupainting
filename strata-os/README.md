# Strata OS: a custom OS concept for the Xiaomi 15T

Strata is a concept design for a phone OS built around one device, the Xiaomi 15T:
a 6.83-inch 1.5K AMOLED screen at 120 Hz, an in-display fingerprint sensor and a
centred punch-hole camera. That screen is too tall to use comfortably with one hand,
so the design rests on one idea: **things you read stay at the top, and things you
tap live at the bottom.**

Interactive design canvas: https://claude.ai/artifact/Txy7ck4WaQoUSRLy3YHJSD

The source of every screen is in [`design/`](design/). Each `.dc.html` file is
one artboard on the canvas, and `canvas.json` defines the layout.

---

## Principles

1. **Reach zones.** The screen is split into three bands:
   - **Glance** (top third, read-only): time, status and live activities.
   - **Browse** (middle): widgets and the app grid.
   - **Act** (bottom third): search, Controls, the dock and the fingerprint sensor.

   Strata never puts a primary action in the top third.
2. **Colour shows state.** The UI is monochrome graphite. The single accent colour
   marks only something that is on, selected or live. If it's amber, it's active.
3. **Spaces, not pages.** The home screen has three Spaces: Personal, Work and Out.
   Each has its own widgets and apps, and the phone switches between them
   automatically by time and place.
4. **Private by default.** Notification content is hidden on the lock screen.
   Location starts out approximate, the clipboard clears itself, and the assistant
   runs on the device.
5. **AMOLED-aware.** The always-on display uses true black and lights only a few
   pixels.

## Screens

| Artboard | What it shows |
| --- | --- |
| `AOD.dc.html`: Always-on display | True-black clock, notification icons, a fingerprint ring at the sensor. Tap to wake. |
| `Main.dc.html`: Lock screen | Stacked clock, weather and next event, a live parcel-tracking card, notifications with content hidden. The fingerprint target is the unlock control. |
| `Home.dc.html`: Home · Spaces | Personal / Work / Out switcher (it works), a context widget, a 4×3 app grid, a bottom search bar, a Controls button and the dock. |
| `Control.dc.html`: Controls | A bottom sheet in thumb reach: four large toggles, eight quick toggles, and brightness and volume sliders. All of them work. |
| `Privacy.dc.html`: Privacy Center | A 24-hour timeline of camera, microphone and location use, plus working default switches. |
| `Foundations.dc.html` | Colour tokens, type scale, reach-zone diagram, the 30-icon set and core components. |

Prototype flow: AOD → Lock (tap the fingerprint) → Home → Controls or Settings → Privacy.

## Design tokens

| Token | Value | Use |
| --- | --- | --- |
| `ink` | `#0D0E10` | Ground |
| `surface` | `#17191D` | Cards, tiles |
| `raised` | `#212429` | Controls, icon wells |
| `line` | `#2C3036` | Borders, dividers |
| `text` | `#F3F1EC` | Primary text |
| `muted` | `#A9ADB4` | Secondary text (7.7:1 on surface) |
| `accent` | `#FFB547` (alternatives `#6ED3C1`, `#9DB4FF`, `#FF7A59`) | State only |
| `on-accent` | `#1A1306` | Text and icons on accent |
| `black` | `#000000` | Always-on display |

Type: **Space Grotesk** for the clock, numbers and titles (132 / 56 / 46 / 34),
**Inter** for UI text (15 / 14 / 13 / 12 / 11).
Icons: 24 px grid, 1.75 px round stroke. Radii: 18 px for app tiles, 22 to 26 px
for cards, fully round pills. Touch targets are at least 44 px.

---

## Getting this onto your actual phone

The Xiaomi 15T ships with Xiaomi HyperOS, which is built on Android. There are three
realistic routes, from easiest to hardest:

### 1. A launcher app (recommended first step): **built, see [`launcher/`](launcher/)**
Strata's home screen, Spaces, search, dock, Controls and Privacy screen as an Android
launcher (Kotlin + Jetpack Compose) that you set as the default Home app.
[`launcher/README.md`](launcher/README.md) has install steps.
- No bootloader unlock, no warranty risk, and banking and payment apps keep working.
- Covers Home · Spaces, search, widgets and a Strata-styled Controls panel.
- Limits: a normal app can't replace the system lock screen, the always-on display
  or the real quick-settings panel. Since Android 10, apps also can't switch Wi-Fi
  or Bluetooth directly; they open system panels instead.

### 2. Xiaomi Themes
The Themes app can change icons, wallpapers and some lock-screen styles. It's a
quick way to get the graphite-and-amber look, but it can't change layout or
behaviour.

### 3. A full custom ROM
A real OS replacement: an AOSP or LineageOS-based build with a modified SystemUI
(lock screen, always-on display, Controls) and a Strata launcher.
- **Requires an unlocked bootloader.** Under HyperOS, Xiaomi has tightened unlocking
  (waiting periods, account requirements and limits that vary by region). Check the
  current Mi Unlock rules for your model and region first.
- The 15T uses a MediaTek Dimensity chip. MediaTek devices usually get less
  custom-ROM support than Snapdragon ones, so check XDA for an existing device tree
  and kernel source before committing.
- Risks: a voided warranty, failed Play Integrity checks (banking, payments,
  some games) and possible bricking. This is a project of months, not days.

**Suggested path:** start with route 1. Live with the Strata launcher for a while and
improve the design, then decide whether a full ROM is worth it.
