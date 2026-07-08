package com.example.eprotocol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.eprotocol.data.model.RegressionResult
import com.example.eprotocol.ui.theme.Primary
import com.example.eprotocol.ui.theme.StepCompleted
import com.example.eprotocol.ui.theme.StepPending
import com.example.eprotocol.ui.theme.WarningOrange
import java.util.Locale
import kotlin.math.abs

/**
 * Real-time line chart for internal live data.
 */
@Composable
fun RealtimeLineChart(
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    lineColor: Color = Primary,
    yAxisLabel: String = "mV"
) {
    if (points.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val padding = 48.dp.toPx()
        val rightPadding = 16.dp.toPx()
        val topPadding = 16.dp.toPx()
        val bottomPadding = 32.dp.toPx()

        val chartWidth = size.width - padding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding

        if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

        val xMin = points.minOf { it.first }
        val xMax = points.maxOf { it.first }
        val yMin = points.minOf { it.second }
        val yMax = points.maxOf { it.second }

        val xRange = if (xMax - xMin < 0.001f) 1f else xMax - xMin
        val yRange = if (yMax - yMin < 0.001f) 1f else yMax - yMin
        val yPadding = yRange * 0.1f

        fun mapX(x: Float) = padding + (x - xMin) / xRange * chartWidth
        fun mapY(y: Float) = topPadding + chartHeight - (y - yMin + yPadding) / (yRange + 2 * yPadding) * chartHeight

        val axisColor = StepPending
        drawLine(axisColor, Offset(padding, topPadding), Offset(padding, topPadding + chartHeight), 1.dp.toPx())
        drawLine(axisColor, Offset(padding, topPadding + chartHeight), Offset(padding + chartWidth, topPadding + chartHeight), 1.dp.toPx())

        drawAxisLabels(
            yMin = yMin - yPadding, yMax = yMax + yPadding,
            chartHeight = chartHeight, leftPadding = padding, topPadding = topPadding,
            xMin = xMin, xMax = xMax,
            chartWidth = chartWidth, bottomY = topPadding + chartHeight
        )

        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(mapX(points[0].first), mapY(points[0].second))
                for (i in 1 until points.size) {
                    lineTo(mapX(points[i].first), mapY(points[i].second))
                }
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        } else if (points.size == 1) {
            drawCircle(lineColor, 3.dp.toPx(), Offset(mapX(points[0].first), mapY(points[0].second)))
        }
    }
}

/**
 * Final linear fit chart.
 *
 * Input points are still the original computation points:
 * x = current in A, y = potential in V.
 * The chart displays x as µA and y as mV.
 * R² and Eh are intentionally not drawn here because they are already shown
 * in the result card above the chart.
 */
@Composable
fun ScatterFitChart(
    points: List<Pair<Double, Double>>,
    regression: RegressionResult?,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
    ) {
        val padding = 46.dp.toPx()
        val rightPadding = 14.dp.toPx()
        val topPadding = 8.dp.toPx()
        val bottomPadding = 34.dp.toPx()

        val chartWidth = size.width - padding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding

        if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

        val displayPoints = points.map { (currentA, potentialV) ->
            currentA * 1_000_000.0 to potentialV * 1_000.0
        }

        val xMinRaw = displayPoints.minOf { it.first }.toFloat()
        val xMaxRaw = displayPoints.maxOf { it.first }.toFloat()
        val yMinRaw = displayPoints.minOf { it.second }.toFloat()
        val yMaxRaw = displayPoints.maxOf { it.second }.toFloat()

        val xRange = if (abs(xMaxRaw - xMinRaw) < 1e-12f) 1f else xMaxRaw - xMinRaw
        val yRange = if (abs(yMaxRaw - yMinRaw) < 1e-12f) 1f else yMaxRaw - yMinRaw
        val xPad = xRange * 0.10f
        val yPad = yRange * 0.12f

        val xMin = xMinRaw - xPad
        val xMax = xMaxRaw + xPad
        val yMin = yMinRaw - yPad
        val yMax = yMaxRaw + yPad

        fun mapX(x: Float) = padding + (x - xMin) / (xMax - xMin) * chartWidth
        fun mapY(y: Float) = topPadding + chartHeight - (y - yMin) / (yMax - yMin) * chartHeight

        val axisColor = StepPending
        drawLine(axisColor, Offset(padding, topPadding), Offset(padding, topPadding + chartHeight), 1.dp.toPx())
        drawLine(axisColor, Offset(padding, topPadding + chartHeight), Offset(padding + chartWidth, topPadding + chartHeight), 1.dp.toPx())

        drawAxisLabels(
            yMin = yMin, yMax = yMax,
            chartHeight = chartHeight, leftPadding = padding, topPadding = topPadding,
            xMin = xMin, xMax = xMax,
            chartWidth = chartWidth, bottomY = topPadding + chartHeight
        )

        drawAxisTitles(
            xTitle = "Current (µA)",
            yTitle = "Potential (mV)",
            leftPadding = padding,
            chartWidth = chartWidth,
            topPadding = topPadding,
            chartHeight = chartHeight,
            bottomY = topPadding + chartHeight
        )

        if (regression != null) {
            val lineXMinUa = xMin
            val lineXMaxUa = xMax
            val lineYMinMv = (regression.slope * (lineXMinUa / 1_000_000.0) + regression.intercept).toFloat() * 1000f
            val lineYMaxMv = (regression.slope * (lineXMaxUa / 1_000_000.0) + regression.intercept).toFloat() * 1000f

            val fitLineColor = if (regression.rSquared >= 0.9) StepCompleted else WarningOrange
            drawLine(
                fitLineColor,
                Offset(mapX(lineXMinUa), mapY(lineYMinMv)),
                Offset(mapX(lineXMaxUa), mapY(lineYMaxMv)),
                2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        for ((x, y) in displayPoints) {
            drawCircle(Primary, 4.dp.toPx(), Offset(mapX(x.toFloat()), mapY(y.toFloat())))
        }
    }
}

private fun DrawScope.drawAxisLabels(
    yMin: Float, yMax: Float,
    chartHeight: Float, leftPadding: Float, topPadding: Float,
    xMin: Float, xMax: Float,
    chartWidth: Float, bottomY: Float
) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9.dp.toPx()
        isAntiAlias = true
    }

    paint.textAlign = android.graphics.Paint.Align.RIGHT
    drawContext.canvas.nativeCanvas.drawText(
        formatValue(yMax),
        leftPadding - 4.dp.toPx(),
        topPadding + paint.textSize / 2,
        paint
    )
    drawContext.canvas.nativeCanvas.drawText(
        formatValue(yMin),
        leftPadding - 4.dp.toPx(),
        bottomY,
        paint
    )

    paint.textAlign = android.graphics.Paint.Align.LEFT
    drawContext.canvas.nativeCanvas.drawText(
        formatValue(xMin),
        leftPadding,
        bottomY + 12.dp.toPx(),
        paint
    )
    paint.textAlign = android.graphics.Paint.Align.RIGHT
    drawContext.canvas.nativeCanvas.drawText(
        formatValue(xMax),
        leftPadding + chartWidth,
        bottomY + 12.dp.toPx(),
        paint
    )
}

private fun DrawScope.drawAxisTitles(
    xTitle: String,
    yTitle: String,
    leftPadding: Float,
    chartWidth: Float,
    topPadding: Float,
    chartHeight: Float,
    bottomY: Float
) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0D47A1")
        textSize = 10.dp.toPx()
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    drawContext.canvas.nativeCanvas.drawText(
        xTitle,
        leftPadding + chartWidth / 2f,
        bottomY + 28.dp.toPx(),
        paint
    )

    drawContext.canvas.nativeCanvas.save()
    drawContext.canvas.nativeCanvas.rotate(-90f, 11.dp.toPx(), topPadding + chartHeight / 2f)
    drawContext.canvas.nativeCanvas.drawText(
        yTitle,
        14.dp.toPx(),
        topPadding + chartHeight / 2f,
        paint
    )
    drawContext.canvas.nativeCanvas.restore()
}

private fun formatValue(v: Float): String {
    return when {
        abs(v) < 0.001f && v != 0f -> String.format(Locale.US, "%.2e", v)
        abs(v) >= 1000f -> String.format(Locale.US, "%.0f", v)
        abs(v) >= 10f -> String.format(Locale.US, "%.0f", v)
        abs(v) >= 1f -> String.format(Locale.US, "%.1f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }
}
