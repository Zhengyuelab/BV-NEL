# BV-NEL_App
Rapidly determine the Eh potential of sediments or soil
# BV-NEL Android App

This repository contains the Android source code and documentation for a BV-NEL electrochemical measurement app. The app controls a compatible electrochemical workstation through USB serial communication, executes the BV-NEL measurement workflow, reads current/potential data, performs linear fitting, and exports saved test records.

## Repository contents

| Path | Description |
|---|---|
| `app/` | Android App source code, including UI, USB communication, protocol encoding/decoding, BV-NEL test control, data processing, record management, and TXT export. |
| `docs/` | Method documentation, parameter table, data format notes, record-management notes, and App-instrument communication protocol. |
| `examples/` | Demonstration files showing the expected data structure: OCP data, coarse i-t data, fine i-t data, fitting summary, strict timing schedule, record export TXT, diagnostic log, and screenshots. |
| `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat` | Android Studio/Gradle project files required for building the app. |
| `GITHUB_UPLOAD_CHECKLIST.md` | Checklist for reviewing files before public release. |
| `PACKAGE_MANIFEST.json` | Summary of the packaged files. |
| `LICENSE` | Custom license allowing non-commercial academic research use only; commercial use requires prior written permission from the copyright holder(s) or corresponding author. |

## Core source files

| File | Main role |
|---|---|
| `app/src/main/java/com/example/eprotocol/domain/TestOrchestrator.kt` | BV-NEL measurement workflow: OCP, coarse i-t, E0 fitting, fine i-t, and final Eh fitting. |
| `app/src/main/java/com/example/eprotocol/domain/MathUtils.kt` | Linear regression, R² calculation, averaging, and outlier handling. |
| `app/src/main/java/com/example/eprotocol/data/usb/ProtocolCodec.kt` | Hex command construction, stream buffering, packet parsing, calibration parsing, and current/potential conversion. |
| `app/src/main/java/com/example/eprotocol/data/usb/UsbSerialManager.kt` | USB serial connection, permission handling, port configuration, and data I/O. |
| `app/src/main/java/com/example/eprotocol/ui/MainViewModel.kt` | App state management, test start/stop, result saving, individual deletion, batch clearing, TXT export, and diagnostic export. |
| `app/src/main/java/com/example/eprotocol/ui/MainScreen.kt` | Main Compose UI, result page, record page, save/delete/clear/export interactions, and charts. |
| `app/src/main/java/com/example/eprotocol/data/model/Models.kt` | Data models for measurement states, raw data points, regression results, and saved records. |

## Current BV-NEL workflow

The current app workflow is documented in `docs/measurement_workflow.md`. In brief, the app performs OCP acquisition, three-point coarse i-t scanning, E0 fitting, five-point fine i-t scanning, final linear fitting, and TXT record export.

The demonstration data in `examples/` are provided only to illustrate file formats, timing, and fitting workflow. Replace them with real exported data when archiving final experimental datasets.

## License and use restrictions

This repository is released as publicly available source code for non-commercial academic research, teaching, peer-review, and reproducibility purposes. Commercial use is not permitted without prior written permission from the copyright holder(s) or the corresponding author of the associated publication. No patent license is granted. See `LICENSE` for details.

## Build environment

- Android Studio project
- Kotlin + Jetpack Compose
- Gradle wrapper included
- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`
- USB serial dependency: `com.github.mik3y:usb-serial-for-android:3.8.1`
- Regression dependency: `org.apache.commons:commons-math3:3.6.1`

## Build

Open the repository folder in Android Studio, sync Gradle, select the `delay5` flavor, and run the app on an Android device connected to the electrochemical workstation.

## Notes before public release

Before making this repository public, confirm whether the hardware communication protocol and full Android source code can be released under the included non-commercial academic research license. Do not upload signing keys, APK/AAB build outputs, or local configuration files.
