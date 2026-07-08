package com.example.eprotocol.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.eprotocol.ui.theme.StepCompleted
import com.example.eprotocol.ui.theme.WarningOrange

/**
 * 氧化还原电位 Eh 结果展示面板
 *
 * 测试完成后会放大显示 Eh 数值，并在下方显示 R^2 拟合质量。
 * 当 R^2 < 0.9 时显示橙色警告文本。
 */
@Composable
fun ResultPanel(
    ee: Double?,
    statusMessage: String,
    isRunning: Boolean,
    rSquared: Double? = null,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Eh (mV)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            AnimatedContent(
                targetState = ee,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.8f))
                        .togetherWith(fadeOut())
                },
                label = "ee_value_animation",
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            ) { targetEe ->
                if (targetEe != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.2f", targetEe),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = StepCompleted,
                            textAlign = TextAlign.Center
                        )

                        if (rSquared != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val qualityColor = if (rSquared >= 0.9) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                WarningOrange
                            }
                            val qualityText = buildString {
                                append("R² = ")
                                append(String.format("%.4f", rSquared))
                                if (rSquared < 0.9) append("  (拟合质量不佳)")
                            }
                            Text(
                                text = qualityText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = qualityColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            if (ee != null) {
                Text(
                    text = "mV",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
