package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionStatus
import com.example.ui.theme.CineAmber
import com.example.ui.theme.CineBorder
import com.example.ui.theme.CineCyan
import com.example.ui.theme.CineRecordRed
import com.example.ui.theme.CineSuccessGreen
import com.example.ui.theme.CineSurface
import com.example.ui.theme.CineSurfaceVariant
import com.example.ui.theme.CineTextPrimary
import com.example.ui.theme.CineTextSecondary
import com.example.ui.theme.CineTextTertiary
import com.example.ui.theme.CineWarningYellow

@Composable
fun CineConnectionBadge(
    status: ConnectionStatus,
    latencyMs: Long,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (status) {
        ConnectionStatus.CONNECTED -> CineSuccessGreen to "CONNECTED"
        ConnectionStatus.CONNECTING -> CineWarningYellow to "CONNECTING"
        ConnectionStatus.ERROR -> CineRecordRed to "ERROR"
        ConnectionStatus.DISCONNECTED -> CineTextTertiary to "OFFLINE"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CineSurfaceVariant)
            .border(1.dp, CineBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("connection_status_badge")
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 11.sp
            ),
            color = dotColor
        )
        if (status == ConnectionStatus.CONNECTED) {
            Text(
                text = "${latencyMs}ms",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = CineCyan
            )
        }
    }
}

@Composable
fun CineToggleChip(
    label: String,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "toggle_chip"
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) CineAmber.copy(alpha = 0.2f) else CineSurfaceVariant,
        label = "chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) CineAmber else CineBorder,
        label = "chip_border"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) CineAmber else CineTextSecondary,
        label = "chip_text"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onToggle(!isActive) }
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag(testTag)
    ) {
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = CineAmber,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.5.sp
            ),
            color = textColor
        )
    }
}

@Composable
fun CineLensSelector(
    selectedLens: Int,
    options: List<Int> = listOf(24, 35, 50, 85),
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CineSurfaceVariant)
            .border(1.dp, CineBorder, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { mm ->
            val isSelected = mm == selectedLens
            val bg = if (isSelected) CineAmber else Color.Transparent
            val textCol = if (isSelected) Color.Black else CineTextSecondary
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(bg)
                    .clickable { onSelect(mm) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("lens_option_${mm}mm"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${mm}mm",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = textCol
                )
            }
        }
    }
}

@Composable
fun CineTelemetryPill(
    label: String,
    value: String,
    unit: String = "",
    highlightColor: Color = CineAmber,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CineSurface.copy(alpha = 0.85f))
            .border(1.dp, CineBorder.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = CineTextTertiary
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = highlightColor
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = CineTextTertiary
                )
            }
        }
    }
}

@Composable
fun QrConnectDialog(
    onDismiss: () -> Unit,
    onQrResolved: (String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = CineAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Blender QR / Fast Connect", color = CineTextPrimary)
            }
        },
        text = {
            Column {
                Text(
                    "Scan or paste the WebSocket connection string provided by the Blender CineMotion addon dialog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CineTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text("e.g. 192.168.1.100:8765 or ws://...", color = CineTextTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CineAmber,
                        unfocusedBorderColor = CineBorder,
                        focusedTextColor = CineTextPrimary,
                        unfocusedTextColor = CineTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawText.isNotBlank()) {
                        onQrResolved(rawText.trim())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CineAmber, contentColor = Color.Black),
                modifier = Modifier.testTag("qr_confirm_button")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CineTextSecondary)
            }
        },
        containerColor = CineSurface,
        shape = RoundedCornerShape(12.dp)
    )
}
