# BV-NEL parameter table

| Parameter | Current app value | Source / note |
|---|---:|---|
| OCP duration | 40 s | `OCP_DURATION_SEC` |
| OCP sampling interval | 0.1 s | `OCP_INTERVAL_SEC` |
| OCP averaging window | final 3 s | `OCP_AVERAGE_WINDOW_MS` |
| i-t duration | 8 s | `CV_DURATION_SEC` used for constant-potential i-t step |
| i-t sampling interval | 0.1 s | `CV_INTERVAL_SEC` |
| i-t averaging window | final 1 s | `CV_AVERAGE_WINDOW_MS` |
| Coarse scan potentials | Ei − 100 mV, Ei, Ei + 100 mV | `COARSE_OFFSETS_MV` |
| Fine scan range | E0 ± 10 mV | `FINE_RANGE_MV` |
| Fine scan step | 5 mV | `FINE_STEP_MV` |
| Fine scan points | 5 | E0 −10, −5, 0, +5, +10 mV |
| Default current range | `0x06` | 1 µA range; sample resistance 1,000,000 |
| Inter-i-t delay | 5 s | `IT_INTERVAL_BETWEEN_MEASUREMENTS_MS` in the `delay5` flavor |
| Post-stop delay | 1.5 s | `POST_EXPERIMENT_DELAY_MS` |
| Fit coordinate system | X = current, Y = potential | `MathUtils.kt` |
| Reported Eh | zero-current intercept | final fine-scan linear fit |
| Saved-record limit | 9 records | `MainViewModel.kt` / UI record page |
| Record export format | TXT | tab-delimited columns |
