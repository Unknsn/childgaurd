package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing

/**
 * PermissionRationaleDialog (v3.6.0 Design Overhaul)
 * Welcoming permission explainer with clear visual icons and plain-English reasons.
 */
@Composable
fun PermissionRationaleDialog(
    title: String,
    rationale: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    iconType: String = "security"
) {
    val icon = when (iconType) {
        "bluetooth" -> Icons.Default.Bluetooth
        "location" -> Icons.Default.LocationOn
        "notification" -> Icons.Default.Notifications
        else -> Icons.Default.Security
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SafeBandColor.PrimaryBrand,
                modifier = Modifier.size(36.dp)
            )
        },
        text = {
            Column {
                Text(
                    text = rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                Text(
                    text = "SafeBand operates 100% locally on-device. No location or biometric information is ever sold or uploaded to third parties.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            SafeBandPrimaryButton(
                text = "Grant Permission",
                onClick = onConfirm,
                modifier = Modifier.testTag("permission_confirm_button")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = SafeBandShapes.ExtraLarge
    )
}
