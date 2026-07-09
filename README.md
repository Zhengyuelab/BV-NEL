# BV-NEL

**BV-NEL** stands for the **Butler-Volmer guided near-equilibrium linearization method**. It is a rapid electrochemical method for measuring the redox potential (Eh) of sediments. The method uses a portable electrochemical workstation and a three-electrode probe to actively sample the near-equilibrium current-potential response, then estimates Eh from the zero-current intercept of the final local linear fit.

This repository provides the source code, method documentation, communication protocol, and example files needed to understand, build, and reproduce the BV-NEL workflow.

## What BV-NEL does

BV-NEL standardizes the measurement process by combining:

- controlled potential-step measurement;
- current acquisition from the electrochemical workstation;
- automatic extraction of steady-state current values;
- coarse and refined linear fitting;
- quality control using the fitting coefficient (R²);
- saving, deleting, clearing, and exporting measurement records.

The software is implemented as an Android Studio project and is used to control the portable electrochemical workstation through USB serial communication. The customized portable electrochemical workstation used in this implementation was provided by **Wuxi Signal Technology Co., Ltd.**

## Three-step measurement workflow

<img width="1888" height="870" alt="流程图" src="https://github.com/user-attachments/assets/89aef09d-2142-4597-aeb7-7b03e267a67b" />


BV-NEL follows a three-step workflow:

### Step 1. Initial potential measurement

The initial open-circuit potential is recorded for **40 s**. The mean potential within the final **1 s** is used as the initial potential, **E<sub><i>initial</i></sub>**.

### Step 2. Preliminary potential-step measurement

A coarse local response is measured around **E<sub><i>initial</i></sub> ± 100 mV** using **3 potential steps**. Each i-t measurement lasts **8 s**, with data recorded every **0.1 s**. The average current from the final **1 s** of each i-t step is used for linear fitting to identify a preliminary zero-current potential, **E<sub><i>prelim</i></sub>**.

### Step 3. Refined near-equilibrium measurement

A refined near-equilibrium response is measured around **E<sub><i>prelim</i></sub> ± 10 mV** using **5 potential steps**. Each i-t measurement lasts **8 s**, with data recorded every **0.1 s**. The average current from the final **1 s** of each i-t step is used for the final current-potential linear fitting. The final Eh is calculated from the zero-current intercept of this local linear fit. The fit is accepted when the R² value meets the preset quality-control criterion.

## How to use BV-NEL

1. Open this repository in **Android Studio**.
2. Sync the Gradle project and build the `app` module.
3. Install the software on an Android device that supports USB communication.
4. Connect the portable electrochemical workstation to the Android device using a USB data cable.
5. Insert the working, counter, and reference electrodes into the sediment sample.
6. Start a new measurement. BV-NEL will automatically perform Step 1, Step 2, and Step 3.
7. Review the final Eh value, R² value, and final linear fit.
8. Save the result if the measurement is valid.
9. Use the records page to view saved records, delete individual records, clear a batch after export, or export all records as a TXT file.

## Example files

The `examples/` folder contains files exported or captured directly from the mobile-side workflow:

| File/folder | Description |
|---|---|
| `examples/records/` | Example TXT file exported from the record-management page. |
| `examples/screenshots/` | Example screenshots showing the result page and record page. |

These files are provided only to show the expected record-export format and user-interface output. Intermediate OCP values, potential-step currents, and fitting tables are not included as example files in this repository.

## Repository contents

| Path | Description |
|---|---|
| `app/` | Main Android Studio source code, including the user interface, USB communication, protocol encoding/decoding, BV-NEL workflow control, data processing, record management, and TXT export. |
| `docs/` | Method documentation, parameter table, data-format notes, record-management notes, communication protocol, and workflow figure. |
| `examples/` | Mobile-side TXT record export example and screenshots. |
| `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat` | Android Studio/Gradle project files required for building the software. |
| `README.md` | Overview and usage instructions. |
| `GITHUB_UPLOAD_CHECKLIST.md` | Checklist for reviewing files before public release. |
| `PACKAGE_MANIFEST.json` | Summary of the packaged files. |
| `CITATION.cff` | Citation metadata for the repository. |
| `LICENSE` | Custom license allowing non-commercial academic research use only. Commercial use requires prior written permission. |

## Core source files

| File | Main role |
|---|---|
| `app/src/main/java/com/example/eprotocol/domain/TestOrchestrator.kt` | Controls the BV-NEL measurement sequence: initial potential measurement, coarse i-t measurement, preliminary fitting, refined i-t measurement, and final Eh fitting. |
| `app/src/main/java/com/example/eprotocol/domain/MathUtils.kt` | Provides averaging, linear regression, R² calculation, and outlier handling. |
| `app/src/main/java/com/example/eprotocol/data/usb/ProtocolCodec.kt` | Builds hex commands, buffers incoming streams, parses data packets, reads calibration data, and converts raw current/potential values. |
| `app/src/main/java/com/example/eprotocol/data/usb/UsbSerialManager.kt` | Manages USB permission, serial-port configuration, connection state, and data input/output. |
| `app/src/main/java/com/example/eprotocol/ui/MainViewModel.kt` | Manages measurement state, test start/stop, result saving, record deletion, batch clearing, TXT export, and diagnostic export. |
| `app/src/main/java/com/example/eprotocol/ui/MainScreen.kt` | Provides the main Compose interface, result page, record page, save/delete/clear/export controls, and plots. |
| `app/src/main/java/com/example/eprotocol/data/model/Models.kt` | Defines data models for measurement states, raw points, regression results, and saved records. |

## Communication protocol

The software-instrument communication protocol is documented in:

- `docs/hardware_app_communication_protocol.md`
- `docs/protocol_original_v1.md`

These files describe the customized portable electrochemical workstation information, USB serial configuration, command frame format, current-range setting command, OCP read command, i-t command, start/stop sampling commands, returned data structure, and raw-value conversion rules.

## Build environment

- Android Studio project
- Kotlin + Jetpack Compose
- Gradle wrapper included
- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`
- USB serial dependency: `com.github.mik3y:usb-serial-for-android:3.8.1`
- Regression dependency: `org.apache.commons:commons-math3:3.6.1`

## Build

Open the repository folder in Android Studio, sync Gradle, select the required build variant, and run the `app` module on an Android device connected to the electrochemical workstation.

## License and use restrictions

This repository is released for non-commercial academic research, teaching, peer review, and reproducibility purposes. Commercial use is not permitted without prior written permission from the copyright holder(s) or the corresponding author of the associated publication. No patent license is granted. See `LICENSE` for details.

## Notes before public release

Before making this repository public, confirm whether the hardware communication protocol and full source code can be released under the included non-commercial academic research license. Do not upload signing keys, APK/AAB build outputs, or local configuration files.
