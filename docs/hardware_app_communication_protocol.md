# App-instrument communication protocol

This document summarizes the communication protocol used by the Android app. The detailed original protocol is preserved in `docs/protocol_original_v1.md`.

## USB serial connection

- Driver: CDC ACM serial driver.
- Baud rate: 115200.
- Data bits: 8.
- Stop bits: 1.
- Parity: none.
- DTR and RTS are set to `true` when supported.

## Packet conventions

- Packet header: `55 AA 00 00 00 00`.
- Standard trailer: `0D 0A`.
- Calibration response may be parsed as a fixed 91-byte response when the expected trailer is not present.
- Data packets are parsed as groups of 4 bytes.
- Each group contains two raw 16-bit values using the Win-aligned interpretation:
  - first raw value = current raw value;
  - second raw value = voltage raw value.

## Core commands used by the app

### Find device / read calibration

```hex
55 AA 00 00 00 00 00 01 01 00 00 00 0D 0A
```

The response contains voltage coefficient, current coefficients, voltage zero point, and current zero points.

### Set current range

```hex
55 AA 00 00 00 00 00 02 02 00 [range_code] 0D 0A
```

Default range in the current BV-NEL workflow: `0x06`.

| Range code | Nominal range | Sample resistance / scaling factor |
|---|---:|---:|
| `0x02` | 10 mA | 100 |
| `0x03` | 1 mA | 1,000 |
| `0x04` | 100 µA | 10,000 |
| `0x05` | 10 µA | 100,000 |
| `0x06` | 1 µA | 1,000,000 |
| `0x07` | 100 nA | 10,000,000 |
| `0x08` | 10 nA | 100,000,000 |

### Start sampling

```hex
55 AA 00 00 00 00 00 03 08 01 00 0D 0A
```

### Stop sampling

```hex
55 AA 00 00 00 00 00 03 08 02 00 0D 0A
```

### Read OCP command

```hex
55 AA 00 00 00 00 00 03 08 04 00 0D 0A
```

In the current app workflow, OCP is read for 40 s with 0.1 s polling interval, and the final 3 s are averaged to obtain Ei.

### OCP experiment command

```hex
55 AA 00 00 00 00 00 0A 03 00 [duration_float] [interval_float] 0D 0A
```

For the current BV-NEL workflow:

- duration = 40 s;
- interval = 0.1 s.

### i-t curve command

```hex
55 AA 00 00 00 00 00 13 03 04 [potential_float] [runtime_float] [interval_float] [quiet_time_float] 0D 0A
```

For the current BV-NEL workflow:

- runtime = 8 s;
- interval = 0.1 s;
- quiet time = 0 s;
- inter-i-t delay between consecutive i-t measurements = 5 s;
- internal post-stop delay = 1.5 s.

## Conversion used in the app

The app parses current and potential from raw packets using the calibration values and range scaling implemented in `ProtocolCodec.kt`. The fitting code uses:

```text
X = current
Y = potential
Eh = Y-axis intercept when current = 0
```
