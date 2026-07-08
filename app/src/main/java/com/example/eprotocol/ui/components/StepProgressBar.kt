package com.example.eprotocol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.eprotocol.data.model.TestStep
import com.example.eprotocol.ui.theme.Primary
import com.example.eprotocol.ui.theme.StepCompleted
import com.example.eprotocol.ui.theme.StepError
import com.example.eprotocol.ui.theme.StepPending

/**
 * Three-step BV-NEL progress display.
 *
 * Display mapping:
 * Step1 = reference Eh / OCP stage
 * Step2 = preliminary Eh stage
 * Step3 = final Eh stage
 */
@Composable
fun StepProgressBar(
    currentStep: TestStep,
    isRunning: Boolean,
    hasError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentGroup = when (currentStep) {
        TestStep.MEASURE_EI -> 0
        TestStep.COARSE_SCAN, TestStep.FIT_E0 -> 1
        TestStep.FINE_SCAN, TestStep.FIT_EE, TestStep.COMPLETED -> 2
    }

    val stepColors = listOf(Primary, Primary, StepCompleted)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepToken(
                label = "Step1",
                iconType = StepIconType.Clock,
                color = when {
                    hasError && currentGroup == 0 -> StepError
                    currentGroup >= 0 -> stepColors[0]
                    else -> StepPending
                }
            )
            StepDivider(color = if (currentGroup >= 1) Primary else StepPending)
            StepToken(
                label = "Step2",
                iconType = StepIconType.Crosshair,
                color = when {
                    hasError && currentGroup == 1 -> StepError
                    currentGroup >= 1 -> stepColors[1]
                    else -> StepPending
                }
            )
            StepDivider(color = if (currentGroup >= 2) StepCompleted else StepPending)
            StepToken(
                label = "Step3",
                iconType = StepIconType.Target,
                color = when {
                    hasError && currentGroup == 2 -> StepError
                    currentGroup >= 2 -> stepColors[2]
                    else -> StepPending
                }
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val y = 8.dp.toPx()
            val xs = listOf(size.width * 0.14f, size.width * 0.50f, size.width * 0.86f)
            val lineWidth = 2.dp.toPx()
            drawLine(
                color = if (currentGroup >= 1) Primary else StepPending,
                start = Offset(0f, y),
                end = Offset(xs[1], y),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = if (currentGroup >= 2) StepCompleted else StepPending,
                start = Offset(xs[1], y),
                end = Offset(size.width, y),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )

            xs.forEachIndexed { index, x ->
                val color = when {
                    hasError && currentGroup == index -> StepError
                    index < 2 && currentGroup >= index -> Primary
                    index == 2 && currentGroup >= index -> StepCompleted
                    else -> StepPending
                }
                drawLine(
                    color = color,
                    start = Offset(x - 14.dp.toPx(), y),
                    end = Offset(x + 14.dp.toPx(), y),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun StepDivider(color: Color) {
    Canvas(
        modifier = Modifier
            .width(24.dp)
            .height(34.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(size.width / 2f, 6.dp.toPx()),
            end = Offset(size.width / 2f, size.height - 6.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun StepToken(
    label: String,
    iconType: StepIconType,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        ElevatedCard(
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = color.copy(alpha = 0.12f)
            )
        ) {
            Canvas(
                modifier = Modifier
                    .size(28.dp)
                    .padding(6.dp)
            ) {
                when (iconType) {
                    StepIconType.Clock -> drawClockIcon(color)
                    StepIconType.Crosshair -> drawCrosshairIcon(color)
                    StepIconType.Target -> drawTargetIcon(color)
                }
            }
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private enum class StepIconType {
    Clock,
    Crosshair,
    Target
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClockIcon(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.42f
    drawCircle(color = color, radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    drawLine(color, center, Offset(center.x, center.y - r * 0.58f), 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, center, Offset(center.x + r * 0.48f, center.y + r * 0.10f), 2.dp.toPx(), cap = StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshairIcon(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.40f
    drawCircle(color = color, radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    drawLine(color, Offset(center.x - r * 1.25f, center.y), Offset(center.x + r * 1.25f, center.y), 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, Offset(center.x, center.y - r * 1.25f), Offset(center.x, center.y + r * 1.25f), 2.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(color = color, radius = 2.dp.toPx(), center = center)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTargetIcon(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.42f
    drawCircle(color = color, radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    drawCircle(color = color, radius = r * 0.55f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    drawCircle(color = color, radius = 2.dp.toPx(), center = center)
    drawLine(color, Offset(center.x - r * 1.15f, center.y), Offset(center.x - r * 0.78f, center.y), 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, Offset(center.x + r * 0.78f, center.y), Offset(center.x + r * 1.15f, center.y), 2.dp.toPx(), cap = StrokeCap.Round)
}
