package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertFlag
import com.example.model.RiskLevel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiskStatusBanner(
    riskLevel: RiskLevel,
    activeFlags: Set<AlertFlag>,
    isAdvertising: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedContainerColor by animateColorAsState(
        targetValue = riskLevel.containerColor,
        animationSpec = tween(500),
        label = "ContainerColor"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = riskLevel.primaryColor.copy(alpha = 0.5f),
        animationSpec = tween(500),
        label = "BorderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("risk_status_banner"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = animatedContainerColor),
        border = BorderStroke(1.5.dp, animatedBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Status Beacon Icon Circle
                Surface(
                    shape = CircleShape,
                    color = riskLevel.primaryColor,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (riskLevel) {
                            RiskLevel.NORMAL -> Icons.Default.CheckCircle
                            RiskLevel.LOW -> Icons.Default.Info
                            RiskLevel.MEDIUM -> Icons.Default.WarningAmber
                            RiskLevel.HIGH -> Icons.Default.Warning
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = riskLevel.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = riskLevel.onContainerColor
                        )

                        if (isAdvertising) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444)
                            ) {
                                Text(
                                    text = "BLE BROADCASTING",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = riskLevel.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = riskLevel.onContainerColor.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Active Flags Chips
            if (activeFlags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "ACTIVE ANOMALY FLAGS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = riskLevel.onContainerColor.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeFlags.forEach { flag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = riskLevel.primaryColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, riskLevel.primaryColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "⚠️ ${flag.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = riskLevel.onContainerColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
