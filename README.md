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

## Installation of BV-NEL

1. Open this repository in **Android Studio**.
2. Sync the Gradle project and build the `app` module.
3. Install the software on an Android device that supports USB communication.
4. Connect the portable electrochemical workstation to the Android device using a USB data cable.


## Usage method of BV-NEL

Schematic diagram of the app page

<p align="center">
  <img width="600" height="400" alt="示例截图" src="https://github.com/user-attachments/assets/cd87cbff-7def-457c-a0c3-c4d04323301b" />

Usage process
1. Connect the three electrodes of electrochemistry to the electrochemical workstation.
2. Connect the electrochemical workstation to the mobile phone.
3. Click "**Start New Test**" to begin the measurement.
4. Click "**Save This Result**" to customize the sample name.
5. After the test, click "Records" to display All the results, and then click "**Export All Records**" to output the results.
6. After the tests of 9 samples are completed, click "**Clear**" to clear all current records, and then continue to measure the subsequent samples.


## License and use restrictions

This repository is released for non-commercial academic research, teaching, peer review, and reproducibility purposes. Commercial use is not permitted without prior written permission from the copyright holder(s) or the corresponding author of the associated publication. No patent license is granted. See `LICENSE` for details.

## Notes before public release

Before making this repository public, confirm whether the hardware communication protocol and full source code can be released under the included non-commercial academic research license. Do not upload signing keys, APK/AAB build outputs, or local configuration files.
