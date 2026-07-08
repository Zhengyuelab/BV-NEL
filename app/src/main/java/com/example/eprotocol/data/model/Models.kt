package com.example.eprotocol.data.model

/**
 * Internal measurement steps.
 * The UI maps these six internal steps to three display steps:
 * Step1, Step2, and Step3.
 */
enum class TestStep(val index: Int, val label: String) {
    MEASURE_EI(0, "Step1"),
    COARSE_SCAN(1, "Step2"),
    FIT_E0(2, "Step2"),
    FINE_SCAN(3, "Step3"),
    FIT_EE(4, "Step3"),
    COMPLETED(5, "Step3");
}

/**
 * USB connection state.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Single sampling data point.
 *
 * Win-aligned protocol interpretation:
 * first raw value = currentRaw, second raw value = voltageRaw.
 */
data class DataPoint(
    val voltage: Double,
    val current: Double,
    val firstRaw: Int = 0,
    val secondRaw: Int = 0,
    val secondAsVoltage: Double = 0.0
)

/**
 * Instrument calibration data parsed from device response.
 */
data class CalibrationData(
    val voltageCoeff: Float = 1.0f,
    val currentCoeffs: FloatArray = FloatArray(9) { 1.0f },
    val voltageZero: Float = 0.0f,
    val currentZeros: FloatArray = FloatArray(9) { 0.0f }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalibrationData) return false
        return voltageCoeff == other.voltageCoeff
                && currentCoeffs.contentEquals(other.currentCoeffs)
                && voltageZero == other.voltageZero
                && currentZeros.contentEquals(other.currentZeros)
    }

    override fun hashCode(): Int {
        var result = voltageCoeff.hashCode()
        result = 31 * result + currentCoeffs.contentHashCode()
        result = 31 * result + voltageZero.hashCode()
        result = 31 * result + currentZeros.contentHashCode()
        return result
    }
}

/**
 * Linear regression result.
 */
data class RegressionResult(
    val slope: Double,
    val intercept: Double,
    val rSquared: Double
)

/**
 * Saved BV-NEL test record.
 */
data class TestRecord(
    val sampleName: String,
    val timestamp: Long,
    val ei: Double,
    val e0: Double,
    val ee: Double,
    val rSquared: Double
)

/**
 * Orchestrator state.
 */
sealed class OrchestratorState {
    data object Idle : OrchestratorState()

    data class Running(
        val step: TestStep,
        val message: String
    ) : OrchestratorState()

    data class LiveDataUpdate(
        val step: TestStep,
        val message: String,
        val progressPercent: Int,
        val chartPoints: List<Pair<Float, Float>>
    ) : OrchestratorState()

    data class StepResult(
        val step: TestStep,
        val ei: Double? = null,
        val e0: Double? = null,
        val ee: Double? = null
    ) : OrchestratorState()

    data class Completed(
        val ee: Double,
        val rSquared: Double,
        val fittingPoints: List<Pair<Double, Double>>,
        val fittingResult: RegressionResult
    ) : OrchestratorState()

    data class Error(val message: String) : OrchestratorState()
}

/**
 * Main UI state.
 */
data class UiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val currentStep: TestStep = TestStep.MEASURE_EI,
    val ei: Double? = null,
    val e0: Double? = null,
    val ee: Double? = null,
    val statusMessage: String = "Ready",
    val isRunning: Boolean = false,
    val rSquared: Double? = null,
    val hasError: Boolean = false,
    val progressPercent: Int = 0,
    val liveChartPoints: List<Pair<Float, Float>> = emptyList(),
    val fittingPoints: List<Pair<Double, Double>> = emptyList(),
    val fittingResult: RegressionResult? = null,
    val testHistory: List<TestRecord> = emptyList(),
    val currentResultSaved: Boolean = false
)

/**
 * User intent.
 */
sealed class UserIntent {
    data object StartTest : UserIntent()
    data object StopTest : UserIntent()
    data class SaveCurrentResult(val sampleName: String) : UserIntent()
    data class DeleteRecord(val index: Int) : UserIntent()
    data object ClearRecords : UserIntent()
    data class ExportCsv(val fileName: String) : UserIntent()
    data object ExportDiagnostics : UserIntent()
}
