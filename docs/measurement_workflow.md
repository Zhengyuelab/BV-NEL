# BV-NEL measurement workflow

This document describes the current BV-NEL workflow implemented in `TestOrchestrator.kt`.

## Step 1: OCP / Ei acquisition

- OCP acquisition duration: 40 s.
- Sampling interval: 0.1 s.
- Expected demonstration points: 400.
- The Ei value is calculated from the final 3 s averaging window.
- The app uses the Win-aligned channel mapping implemented in the source code.

## Step 2: Coarse i-t scan and E0 fitting

- Current range: `0x06` by default.
- Coarse potentials: `Ei - 100 mV`, `Ei`, and `Ei + 100 mV`.
- Each i-t measurement runs for 8 s.
- i-t sampling interval: 0.1 s.
- Each i-t measurement therefore contains 80 expected data points.
- The last 1 s of each i-t step is used for current averaging.
- The fitting uses current as X and potential as Y.
- The fitted zero-current intercept is used as E0.

## Step 3: Fine i-t scan and Eh fitting

- Fine potentials: `E0 - 10 mV`, `E0 - 5 mV`, `E0`, `E0 + 5 mV`, and `E0 + 10 mV`.
- Each fine i-t measurement runs for 8 s at 0.1 s sampling interval.
- The last 1 s of each i-t step is used for current averaging.
- The final linear fit uses current as X and potential as Y.
- The zero-current intercept is reported as Eh.

## Timing controls

- Inter-i-t delay between consecutive i-t measurements: 5 s.
- Internal post-stop delay after stopping a measurement: 1.5 s.
- These timing values are represented in `examples/timing_schedule_example.csv`.

## Record management in V6

The record page supports saving up to 9 results in one batch. Individual records can be deleted, and the whole batch can be cleared after export. TXT export supports user-defined file names.
