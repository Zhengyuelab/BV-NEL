package com.example.eprotocol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eprotocol.data.model.TestRecord
import com.example.eprotocol.data.model.UiState
import com.example.eprotocol.data.model.UserIntent
import com.example.eprotocol.ui.components.ScatterFitChart
import com.example.eprotocol.ui.components.StepProgressBar
import com.example.eprotocol.ui.theme.DeepBlue
import com.example.eprotocol.ui.theme.Primary
import com.example.eprotocol.ui.theme.StepCompleted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var isButtonEnabled by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(AppPage.Test) }

    var showSampleDialog by remember { mutableStateOf(false) }
    var sampleName by remember { mutableStateOf("") }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf(defaultExportName()) }

    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            text = if (currentPage == AppPage.Test) "BV-NEL" else "All Records",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = DeepBlue,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                },
                navigationIcon = {
                    if (currentPage == AppPage.Records) {
                        IconButton(onClick = { currentPage = AppPage.Test }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentPage == AppPage.Test) {
                        IconButton(onClick = { viewModel.handleIntent(UserIntent.ExportDiagnostics) }) {
                            Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                        }
                    } else if (uiState.testHistory.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) {
                            Text("Clear")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(58.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentPage == AppPage.Test,
                    onClick = { currentPage = AppPage.Test },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Test") },
                    label = { Text("Test") }
                )
                NavigationBarItem(
                    selected = currentPage == AppPage.Records,
                    onClick = { currentPage = AppPage.Records },
                    icon = { Icon(Icons.Default.List, contentDescription = "Records") },
                    label = { Text("Records") }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (currentPage) {
            AppPage.Test -> TestPage(
                padding = innerPadding,
                uiState = uiState,
                onStartStop = {
                    if (uiState.isRunning) {
                        viewModel.handleIntent(UserIntent.StopTest)
                    } else if (isButtonEnabled) {
                        isButtonEnabled = false
                        scope.launch {
                            delay(1000)
                            isButtonEnabled = true
                        }
                        viewModel.handleIntent(UserIntent.StartTest)
                    }
                },
                startStopEnabled = uiState.isRunning || isButtonEnabled,
                onSave = {
                    sampleName = "Sample ${uiState.testHistory.size + 1}"
                    showSampleDialog = true
                },
                onViewRecords = { currentPage = AppPage.Records }
            )

            AppPage.Records -> RecordsPage(
                padding = innerPadding,
                records = uiState.testHistory,
                onDelete = { index -> viewModel.handleIntent(UserIntent.DeleteRecord(index)) },
                onExport = {
                    exportFileName = defaultExportName()
                    showExportDialog = true
                },
                onClear = { showClearDialog = true }
            )
        }
    }

    if (showSampleDialog) {
        AlertDialog(
            onDismissRequest = { showSampleDialog = false },
            title = { Text("Sample Name") },
            text = {
                OutlinedTextField(
                    value = sampleName,
                    onValueChange = { sampleName = it },
                    label = { Text("Enter sample name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(UserIntent.SaveCurrentResult(sampleName))
                        showSampleDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSampleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("TXT File Name") },
            text = {
                Column {
                    Text(
                        text = "Enter a file name for this batch of records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportFileName,
                        onValueChange = { exportFileName = it },
                        label = { Text("File name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(UserIntent.ExportCsv(exportFileName))
                        showExportDialog = false
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Records") },
            text = { Text("This will remove all saved records from the current batch. Export first if you need to keep them.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(UserIntent.ClearRecords)
                        showClearDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private enum class AppPage {
    Test,
    Records
}

@Composable
private fun TestPage(
    padding: PaddingValues,
    uiState: UiState,
    onStartStop: () -> Unit,
    startStopEnabled: Boolean,
    onSave: () -> Unit,
    onViewRecords: () -> Unit
) {
    val lastRecord = uiState.testHistory.lastOrNull()
    val saveEnabled = uiState.ee != null &&
            uiState.rSquared != null &&
            !uiState.isRunning &&
            !uiState.currentResultSaved &&
            uiState.testHistory.size < 9

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        StepProgressBar(
            currentStep = uiState.currentStep,
            isRunning = uiState.isRunning,
            hasError = uiState.hasError,
            modifier = Modifier.padding(top = 0.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        CurrentResultCard(
            eh = uiState.ee,
            rSquared = uiState.rSquared,
            isRunning = uiState.isRunning
        )

        if (!uiState.isRunning && uiState.fittingPoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(7.dp))
            FinalFitCard(
                points = uiState.fittingPoints,
                regression = uiState.fittingResult
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Button(
            onClick = onStartStop,
            enabled = startStopEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = if (uiState.isRunning) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                ButtonDefaults.buttonColors(containerColor = Primary)
            }
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.isRunning) "Stop Test" else "Start New Test",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        OutlinedButton(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    uiState.currentResultSaved -> "Result Saved"
                    uiState.testHistory.size >= 9 -> "Record Limit Reached"
                    else -> "Save This Result"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        NavCard(
            text = "View All Records (${uiState.testHistory.size}/9)",
            onClick = onViewRecords
        )

        if (lastRecord != null) {
            Spacer(modifier = Modifier.height(5.dp))
            RecordCard(
                index = uiState.testHistory.size,
                record = lastRecord,
                compact = true,
                onDelete = null
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RecordsPage(
    padding: PaddingValues,
    records: List<TestRecord>,
    onDelete: (Int) -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Saved ${records.size}/9 records. Delete individual records or clear the batch after export.",
            style = MaterialTheme.typography.bodyMedium,
            color = Primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        for (i in 0 until 9) {
            val record = records.getOrNull(i)
            if (record == null) {
                EmptyRecordCard(index = i + 1)
            } else {
                RecordCard(
                    index = i + 1,
                    record = record,
                    compact = false,
                    onDelete = { onDelete(i) }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onExport,
            enabled = records.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Export All Records (TXT)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = onClear,
            enabled = records.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                text = "Clear All Records",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun CurrentResultCard(
    eh: Double?,
    rSquared: Double?,
    isRunning: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (eh == null && isRunning) "Testing..." else formatEh(eh),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (eh != null) StepCompleted else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (eh != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "mV",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StepCompleted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            HorizontalDividerLine()

            Text(
                text = "Fit: R²",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = rSquared?.let { String.format(Locale.US, "%.4f", it) } ?: "--",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HorizontalDividerLine() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .padding(horizontal = 32.dp)
    ) {
        drawLine(
            color = androidx.compose.ui.graphics.Color(0xFFE0E6EF),
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun FinalFitCard(
    points: List<Pair<Double, Double>>,
    regression: com.example.eprotocol.data.model.RegressionResult?
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Final Linear Fit",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = DeepBlue
            )
            ScatterFitChart(
                points = points,
                regression = regression,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun NavCard(
    text: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineSmall,
                color = Primary
            )
        }
    }
}

@Composable
private fun RecordCard(
    index: Int,
    record: TestRecord,
    compact: Boolean,
    onDelete: (() -> Unit)?
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!compact) {
                IndexBubble(index = index, active = true)
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.sampleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatTime(record.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Eh:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatEh(record.ee)} mV",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = StepCompleted
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "R²:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.4f", record.rSquared),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            if (onDelete != null) {
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            } else {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyRecordCard(index: Int) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IndexBubble(index = index, active = false)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Empty",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IndexBubble(index: Int, active: Boolean) {
    val brush = if (active) {
        Brush.verticalGradient(listOf(Primary, androidx.compose.ui.graphics.Color(0xFF004D8A)))
    } else {
        Brush.verticalGradient(
            listOf(
                androidx.compose.ui.graphics.Color(0xFFE0E5EA),
                androidx.compose.ui.graphics.Color(0xFFB8C0C8)
            )
        )
    }

    Canvas(modifier = Modifier.width(30.dp).height(30.dp)) {
        drawCircle(brush = brush, radius = size.minDimension / 2f)
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 13.dp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawText(
                index.toString(),
                size.width / 2f,
                size.height / 2f - (paint.descent() + paint.ascent()) / 2f,
                paint
            )
        }
    }
}

private fun formatEh(value: Double?): String {
    return value?.let { String.format(Locale.US, "%+.2f", it) } ?: "--"
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun defaultExportName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return "BV_NEL_records_$stamp"
}
