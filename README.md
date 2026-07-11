# BodyTempGage

Android app for the **Xiaomi Miaomiaoce MMC-T201-2** (and MMC-T201-1) wearable body
thermometer. It listens to the thermometer's Bluetooth LE advertisements — no pairing or
connection needed — and shows:

- **Gauge (skin sensor) temperature**, **body temperature**, or **both** — switchable on the main screen
- **Battery level** of the gauge
- **Background monitoring** with a configurable **fever alert** notification
- UI in **English** and **Russian**, °C or °F

## How it works

The thermometer continuously broadcasts Xiaomi **MiBeacon** service data (UUID `0xFE95`).
Each frame carries object `0x2000` with two raw thermistor readings (skin side and
environment side) plus a battery byte. Body temperature is not transmitted by the device;
it is estimated from both sensors (dual heat flux) with the empirical formula derived by
the community in [ble_monitor issue #264](https://github.com/custom-components/ble_monitor/issues/264) —
the same formula Home Assistant's ble_monitor uses:

```
body = 3.71934e-11 * exp(0.69314 * temp1) - 1.02801e-8 * exp(0.53871 * temp2) + 36.413
```

The gauge never transmits a computed body temperature: the official app calculates it
on the phone from this same sensor pair. Besides advertisements, the app can optionally
**connect** (a button on the main screen) to the Health Thermometer service (`0x1809`).
The MMC-T201-2's Intermediate Temperature characteristic (`0x2A1E`) does not follow the
Bluetooth spec — instead of an IEEE-11073 float it streams
`status (1) | int16 temp1 | int16 temp2 | uint8 battery` every ~2 seconds (captured from
real hardware, e.g. `00 840d 5c0d 61` = 34.60 °C / 34.20 °C / 97 %). While connected the
readings update every ~2 s instead of the advertisement cadence. Only one central can
connect at a time — the official app and this one can't be connected simultaneously.

The parsers and decoders live in the pure-Kotlin `:core` module and are covered by unit
tests with real captures (advertisement from issue #264, GATT notification from an
MMC-T201-2).

## Project structure

| Module            | Contents |
|-------------------|----------|
| `:core`           | Pure JVM Kotlin: MiBeacon parser, T201 decoder, threshold state machine, settings-sync snapshot/merge policy, models. No Android dependencies. |
| `:common-android` | Shared Android library used by both apps: BLE scan (`BleEngine`), GATT client, in-memory reading hub, DataStore settings, and phone↔watch settings sync over the Wearable Data Layer. |
| `:app`            | Phone app: Jetpack Compose UI, BLE scanning, foreground monitoring service, fever alerts. |
| `:wear`           | **Wear OS companion**: Compose-for-Wear UI, its own BLE advertisement scan and monitoring service, local alerts, and settings sync with the phone. |

### Wear OS companion

The watch app reads the thermometer **directly** over BLE advertisements (it does not proxy
through the phone), shows the same temperatures, and raises the same fever / low-temperature
alerts. Alarm thresholds, the selected gauge, display mode, °F and alerts-on/off are **synced
both ways** with the phone over the Wearable Data Layer — change them on either device and the
other follows (last-write-wins). The Wear app installs and runs standalone (declared
`com.google.android.wearable.standalone`); it shares the phone's `applicationId`
(`com.bodytempgage`) and signing key, which the Data Layer requires for pairing. Build it with
`./gradlew :wear:assembleDebug` (APK at `wear/build/outputs/apk/debug/`).

It also provides a **"Temperature" tile** (long-press the watch face → add tile) showing the
current body temperature — coloured by the warning/alert bands — or the gauge temperature while
the sensor is off the body, plus battery. Tapping the tile opens the app.

## Building

Requirements: JDK 17+, Android SDK (compileSdk 35). Open in Android Studio, or:

```
./gradlew :app:assembleDebug     # APK at app/build/outputs/apk/debug/
./gradlew :core:test             # protocol unit tests, no Android SDK needed
```

In environments without the Android SDK / access to `dl.google.com`, set `SKIP_ANDROID=1`
to work with `:core` alone (`SKIP_ANDROID=1 ./gradlew :core:test`).

## Usage

1. Grant the Bluetooth permission on first launch.
2. Pick your thermometer from the list (it must be broadcasting — insert the battery).
3. Main screen: switch between *Gauge / Body / Both*, watch battery and signal.
4. Toggle *Background monitoring* to keep measuring with the app closed; configure the
   fever alert threshold in Settings.

## Roadmap

- Wear OS watch-face **complication** for the body temperature.

## Disclaimer

This is not a medical device application. The body temperature estimate comes from a
community-derived formula and may differ from the official app. Do not use it for
medical decisions.
