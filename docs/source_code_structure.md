# Source code structure

| Source file | Purpose |
|---|---|
| `MainActivity.kt` | Android entry point. |
| `EProtocolApp.kt` | App class. |
| `TestOrchestrator.kt` | BV-NEL workflow controller. |
| `MathUtils.kt` | Averaging, linear regression, R², and outlier utilities. |
| `ProtocolCodec.kt` | Instrument command generation and data parsing. |
| `UsbSerialManager.kt` | USB serial connection and I/O. |
| `DiagnosticLog.kt` | Diagnostic log collection and export support. |
| `Models.kt` | Data classes and UI/measurement state models. |
| `MainViewModel.kt` | UI state, measurement launch, record management, TXT export, diagnostics export. |
| `MainScreen.kt` | Jetpack Compose UI pages. |
| `LiveChart.kt`, `ResultPanel.kt`, `StepProgressBar.kt`, `ValueCard.kt` | Reusable Compose UI components. |
