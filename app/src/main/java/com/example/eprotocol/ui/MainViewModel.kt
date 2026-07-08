package com.example.eprotocol.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.eprotocol.data.model.ConnectionState
import com.example.eprotocol.data.model.OrchestratorState
import com.example.eprotocol.data.model.TestRecord
import com.example.eprotocol.data.model.TestStep
import com.example.eprotocol.data.model.UiState
import com.example.eprotocol.data.model.UserIntent
import com.example.eprotocol.data.usb.DiagnosticLog
import com.example.eprotocol.data.usb.ProtocolCodec
import com.example.eprotocol.data.usb.UsbSerialManager
import com.example.eprotocol.domain.TestOrchestrator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val USB_CONNECT_TIMEOUT_MS = 60_000L
    }

    private val usbManager = UsbSerialManager()
    private val codec = ProtocolCodec()
    private val orchestrator = TestOrchestrator(usbManager, codec)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var testJob: Job? = null
    private var dataForwardJob: Job? = null

    init {
        observeConnectionState()
        observeOrchestratorState()
    }

    fun handleIntent(intent: UserIntent) {
        when (intent) {
            UserIntent.StartTest -> startTest()
            UserIntent.StopTest -> stopTest()
            is UserIntent.SaveCurrentResult -> saveCurrentResult(intent.sampleName)
            is UserIntent.DeleteRecord -> deleteRecord(intent.index)
            UserIntent.ClearRecords -> clearRecords()
            is UserIntent.ExportCsv -> exportRecordsTxt(intent.fileName)
            UserIntent.ExportDiagnostics -> exportDiagnostics()
        }
    }

    private fun startTest() {
        if (_uiState.value.isRunning) return
        DiagnosticLog.clear()
        DiagnosticLog.add(TAG, "start test")

        _uiState.update {
            it.copy(
                isRunning = true,
                ei = null,
                e0 = null,
                ee = null,
                rSquared = null,
                hasError = false,
                progressPercent = 0,
                statusMessage = "Connecting device...",
                currentStep = TestStep.MEASURE_EI,
                liveChartPoints = emptyList(),
                fittingPoints = emptyList(),
                fittingResult = null,
                currentResultSaved = false
            )
        }

        testJob = viewModelScope.launch {
            try {
                if (!usbManager.isConnected) {
                    usbManager.connect(getApplication())
                    val state = withTimeoutOrNull(USB_CONNECT_TIMEOUT_MS) {
                        usbManager.connectionState.first {
                            it is ConnectionState.Connected || it is ConnectionState.Error
                        }
                    }

                    if (state == null || state is ConnectionState.Error) {
                        val msg = if (state is ConnectionState.Error) state.message else "Connection timeout"
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                hasError = true,
                                statusMessage = "Connection failed: $msg"
                            )
                        }
                        return@launch
                    }
                }

                startDataForwarding()
                orchestrator.runTest()
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Test job cancelled")
                _uiState.update { it.copy(isRunning = false, statusMessage = "Test stopped") }
            } catch (e: Exception) {
                Log.e(TAG, "Test execution error", e)
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        hasError = true,
                        statusMessage = "Test error: ${e.message}"
                    )
                }
            }
        }
    }

    private fun stopTest() {
        DiagnosticLog.add(TAG, "stop test requested")
        testJob?.cancel()
        testJob = null
    }

    private fun startDataForwarding() {
        dataForwardJob?.cancel()
        dataForwardJob = viewModelScope.launch {
            usbManager.dataFlow.collect { rawData ->
                codec.feedData(rawData)
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            usbManager.connectionState.collect { connState ->
                _uiState.update { it.copy(connectionState = connState) }
            }
        }
    }

    private fun observeOrchestratorState() {
        viewModelScope.launch {
            orchestrator.state.collect { orchState ->
                when (orchState) {
                    is OrchestratorState.Idle -> Unit

                    is OrchestratorState.Running -> {
                        _uiState.update {
                            it.copy(
                                currentStep = orchState.step,
                                statusMessage = orchState.message
                            )
                        }
                    }

                    is OrchestratorState.LiveDataUpdate -> {
                        _uiState.update {
                            it.copy(
                                currentStep = orchState.step,
                                statusMessage = orchState.message,
                                progressPercent = orchState.progressPercent,
                                liveChartPoints = orchState.chartPoints
                            )
                        }
                    }

                    is OrchestratorState.StepResult -> {
                        _uiState.update {
                            it.copy(
                                currentStep = orchState.step,
                                ei = orchState.ei ?: it.ei,
                                e0 = orchState.e0 ?: it.e0,
                                ee = orchState.ee ?: it.ee
                            )
                        }
                    }

                    is OrchestratorState.Completed -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                currentStep = TestStep.COMPLETED,
                                ee = orchState.ee,
                                rSquared = orchState.rSquared,
                                fittingPoints = orchState.fittingPoints,
                                fittingResult = orchState.fittingResult,
                                progressPercent = 100,
                                statusMessage = "Test completed",
                                currentResultSaved = false
                            )
                        }
                    }

                    is OrchestratorState.Error -> {
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                hasError = true,
                                statusMessage = orchState.message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveCurrentResult(sampleName: String) {
        val state = _uiState.value
        val eh = state.ee
        val r2 = state.rSquared

        if (eh == null || r2 == null || state.isRunning) {
            _uiState.update { it.copy(statusMessage = "No completed result to save") }
            return
        }

        if (state.currentResultSaved) {
            _uiState.update { it.copy(statusMessage = "Current result has already been saved") }
            return
        }

        if (state.testHistory.size >= 9) {
            _uiState.update { it.copy(statusMessage = "Record limit reached: 9 records") }
            return
        }

        val displayName = sampleName.trim().ifBlank { "Sample ${state.testHistory.size + 1}" }
        val record = TestRecord(
            sampleName = displayName,
            timestamp = System.currentTimeMillis(),
            ei = state.ei ?: 0.0,
            e0 = state.e0 ?: 0.0,
            ee = eh,
            rSquared = r2
        )

        _uiState.update {
            it.copy(
                testHistory = it.testHistory + record,
                currentResultSaved = true,
                statusMessage = "Result saved"
            )
        }
    }

    private fun deleteRecord(index: Int) {
        _uiState.update { state ->
            if (index !in state.testHistory.indices) {
                state.copy(statusMessage = "Record not found")
            } else {
                state.copy(
                    testHistory = state.testHistory.filterIndexed { i, _ -> i != index },
                    currentResultSaved = false,
                    statusMessage = "Record deleted"
                )
            }
        }
    }

    private fun clearRecords() {
        _uiState.update {
            it.copy(
                testHistory = emptyList(),
                currentResultSaved = false,
                statusMessage = "Records cleared"
            )
        }
    }

    private fun exportRecordsTxt(fileNameInput: String) {
        val history = _uiState.value.testHistory
        if (history.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No saved records to export") }
            return
        }

        viewModelScope.launch {
            try {
                val context: Context = getApplication()
                val fileName = buildExportFileName(fileNameInput)
                val cacheDir = File(context.cacheDir, "exports")
                cacheDir.mkdirs()
                val file = File(cacheDir, fileName)

                val recordFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                file.bufferedWriter().use { writer ->
                    writer.write("BV-NEL Test Records")
                    writer.newLine()
                    writer.write("Generated: ${recordFormat.format(Date())}")
                    writer.newLine()
                    writer.write("Record count: ${history.size}/9")
                    writer.newLine()
                    writer.newLine()
                    writer.write("No.\tSample name\tTime\tEh (mV)\tFit R2")
                    writer.newLine()

                    history.forEachIndexed { index, record ->
                        writer.write(buildString {
                            append(index + 1)
                            append('\t')
                            append(record.sampleName)
                            append('\t')
                            append(recordFormat.format(Date(record.timestamp)))
                            append('\t')
                            append(String.format(Locale.US, "%.2f", record.ee))
                            append('\t')
                            append(String.format(Locale.US, "%.4f", record.rSquared))
                        })
                        writer.newLine()
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(
                    Intent.createChooser(shareIntent, "Export BV-NEL records").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )

                _uiState.update { it.copy(statusMessage = "Records exported: $fileName") }
            } catch (e: Exception) {
                Log.e(TAG, "TXT export failed", e)
                _uiState.update { it.copy(statusMessage = "Export failed: ${e.message}") }
            }
        }
    }

    private fun buildExportFileName(input: String): String {
        val fallback = "BV_NEL_records_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
        val base = input.trim().ifBlank { fallback }
        val sanitized = base
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifBlank { fallback }
        return if (sanitized.endsWith(".txt", ignoreCase = true)) sanitized else "$sanitized.txt"
    }

    private fun exportDiagnostics() {
        viewModelScope.launch {
            try {
                val context: Context = getApplication()
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "EProtocol_diagnostics_${dateFormat.format(Date())}.txt"

                val cacheDir = File(context.cacheDir, "exports")
                cacheDir.mkdirs()
                val file = File(cacheDir, fileName)

                file.bufferedWriter().use { writer ->
                    writer.write("EProtocol diagnostics")
                    writer.newLine()
                    writer.write("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    writer.newLine()
                    writer.write("Connection: ${_uiState.value.connectionState.javaClass.simpleName}")
                    writer.newLine()
                    writer.write("Status: ${_uiState.value.statusMessage}")
                    writer.newLine()
                    writer.newLine()
                    writer.write(DiagnosticLog.snapshot().ifBlank { "No diagnostic log entries." })
                    writer.newLine()
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(
                    Intent.createChooser(shareIntent, "Export diagnostics").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )

                _uiState.update { it.copy(statusMessage = "Diagnostics exported") }
            } catch (e: Exception) {
                Log.e(TAG, "Diagnostics export failed", e)
                _uiState.update { it.copy(statusMessage = "Diagnostics export failed: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
        dataForwardJob?.cancel()
        usbManager.disconnect()
    }
}
