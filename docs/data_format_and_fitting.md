# Example data format and fitting

The `examples/` folder contains generated demonstration files that illustrate the expected data structure.

## Raw OCP data

`examples/ocp_raw_data_example.csv`

Columns:

- `sample_index`
- `time_s`
- `voltage_mV`
- `comment`

## Coarse i-t data

`examples/coarse_it_raw_data_example.csv`

This file contains three i-t steps: `Ei - 100 mV`, `Ei`, and `Ei + 100 mV`.

## Fine i-t data

`examples/fine_it_raw_data_example.csv`

This file contains five i-t steps: `E0 - 10 mV`, `E0 - 5 mV`, `E0`, `E0 + 5 mV`, and `E0 + 10 mV`.

## Fitting summary

`examples/step_average_and_fitting_results.csv`

The fitting summary records the averaged current for each i-t step and the linear fitting outputs. The fitting convention is:

```text
X = current
Y = potential
Eh = Y-axis intercept at I = 0
```

## Record export example

`examples/records/V6-BV_NEL_records_20260707_234918.txt`

This TXT file demonstrates the record-export format from the V6 app. It uses tab-delimited columns: No., sample name, time, Eh, and fit R².
