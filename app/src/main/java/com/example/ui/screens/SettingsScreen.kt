package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracking.ARCoreStatus
import com.example.ui.components.CineToggleChip
import com.example.ui.theme.CineAmber
import com.example.ui.theme.CineBlack
import com.example.ui.theme.CineBorder
import com.example.ui.theme.CineCyan
import com.example.ui.theme.CineRecordRed
import com.example.ui.theme.CineSuccessGreen
import com.example.ui.theme.CineSurface
import com.example.ui.theme.CineSurfaceVariant
import com.example.ui.theme.CineTextPrimary
import com.example.ui.theme.CineTextSecondary
import com.example.ui.theme.CineTextTertiary
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val arCoreStatus by viewModel.tracker.arStatus.collectAsState()
    val trackingSensitivity by viewModel.trackingSensitivity.collectAsState()
    val smoothness by viewModel.smoothness.collectAsState()
    val motionScale by viewModel.motionScale.collectAsState()
    val frameRate by viewModel.frameRate.collectAsState()

    var ipInput by remember { mutableStateOf(viewModel.serverIp.value) }
    var portInput by remember { mutableStateOf(viewModel.serverPort.value) }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CineBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SETTINGS & CALIBRATION",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontSize = 18.sp
                ),
                color = CineAmber
            )
            Text(
                text = "6DoF engine configuration & Blender bridge",
                style = MaterialTheme.typography.labelSmall,
                color = CineTextTertiary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION: ARCore Status
            Text(
                text = "ARCORE ENGINE STATUS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = CineTextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CineSurfaceVariant)
                    .border(1.dp, CineBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color, text) = when (arCoreStatus) {
                        ARCoreStatus.SUPPORTED_READY -> Triple(Icons.Default.CheckCircle, CineSuccessGreen, "Supported & Ready")
                        ARCoreStatus.NEED_INSTALL -> Triple(Icons.Default.Download, CineAmber, "Requires ARCore Install")
                        ARCoreStatus.NOT_SUPPORTED -> Triple(Icons.Default.Warning, CineRecordRed, "Not Supported (Fallback active)")
                        ARCoreStatus.CHECKING -> Triple(Icons.Default.Refresh, CineCyan, "Checking ARCore...")
                    }
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = color)
                        Text(
                            text = if (arCoreStatus == ARCoreStatus.SUPPORTED_READY) "6DoF VIO motion tracking active" else "Google Play Services for AR",
                            style = MaterialTheme.typography.labelSmall,
                            color = CineTextTertiary
                        )
                    }
                }

                if (arCoreStatus == ARCoreStatus.NEED_INSTALL && activity != null) {
                    Button(
                        onClick = { viewModel.tracker.requestInstallIfNeeded(activity) },
                        colors = ButtonDefaults.buttonColors(containerColor = CineAmber, contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Install", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.tracker.checkARCoreSupport() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check", tint = CineTextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION: Motion Parameters
            Text(
                text = "MOTION TRACKING SENSITIVITY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = CineTextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sensitivity Factor", style = MaterialTheme.typography.bodySmall, color = CineTextSecondary)
                Text("%.2fx".format(trackingSensitivity), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = CineAmber)
            }
            Slider(
                value = trackingSensitivity,
                onValueChange = { viewModel.trackingSensitivity.value = it },
                valueRange = 0.5f..2.5f,
                colors = SliderDefaults.colors(thumbColor = CineAmber, activeTrackColor = CineAmber, inactiveTrackColor = CineBorder),
                modifier = Modifier.testTag("tracking_sensitivity_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Motion Smoothness", style = MaterialTheme.typography.bodySmall, color = CineTextSecondary)
                Text("%.0f%%".format(smoothness * 100f), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = CineAmber)
            }
            Slider(
                value = smoothness,
                onValueChange = { viewModel.smoothness.value = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = CineAmber, activeTrackColor = CineAmber, inactiveTrackColor = CineBorder),
                modifier = Modifier.testTag("settings_smoothness_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Physical Motion Scale", style = MaterialTheme.typography.bodySmall, color = CineTextSecondary)
                Text("%.1fx".format(motionScale), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = CineCyan)
            }
            Slider(
                value = motionScale,
                onValueChange = { viewModel.motionScale.value = it },
                valueRange = 0.2f..3.0f,
                steps = 13,
                colors = SliderDefaults.colors(thumbColor = CineCyan, activeTrackColor = CineCyan, inactiveTrackColor = CineBorder),
                modifier = Modifier.testTag("settings_motion_scale_slider")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION: Frame Rate Selector (30 / 60 FPS)
            Text(
                text = "STREAMING FRAME RATE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = CineTextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(30, 60).forEach { fps ->
                    CineToggleChip(
                        label = "$fps FPS",
                        isActive = frameRate == fps,
                        onToggle = { viewModel.frameRate.value = fps },
                        modifier = Modifier.weight(1f),
                        testTag = "fps_chip_$fps"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION: Connection Defaults
            Text(
                text = "DEFAULT WEBSOCKET ADDRESS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = CineTextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = {
                        ipInput = it
                        viewModel.serverIp.value = it
                    },
                    label = { Text("IP Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CineAmber,
                        unfocusedBorderColor = CineBorder,
                        focusedTextColor = CineTextPrimary,
                        unfocusedTextColor = CineTextPrimary
                    ),
                    modifier = Modifier.weight(2f)
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = {
                        portInput = it
                        viewModel.serverPort.value = it
                    },
                    label = { Text("Port") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CineAmber,
                        unfocusedBorderColor = CineBorder,
                        focusedTextColor = CineTextPrimary,
                        unfocusedTextColor = CineTextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Reset Calibration Button
            OutlinedButton(
                onClick = { viewModel.resetCalibration() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CineAmber),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(CineBorder)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("reset_calibration_button")
            ) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Calibration to Defaults")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: Blender Addon Spec Guide
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CineSurfaceVariant)
                    .border(1.dp, CineBorder, RoundedCornerShape(8.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CineCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Blender Addon Integration", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = CineCyan)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Runs a WebSocket server on ws://0.0.0.0:8765. The app sends:\n" +
                            "• type: 'pose', pos: [x, y, z], quat: [qx, qy, qz, qw], lens: mm\n" +
                            "• type: 'bake', take: { name, samples: [...] }\n" +
                            "The Blender camera's location and rotation_mode='QUATERNION' are updated per frame.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = CineTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
