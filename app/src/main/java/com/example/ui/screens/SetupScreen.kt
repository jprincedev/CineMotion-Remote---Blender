package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
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
import com.example.network.ConnectionStatus
import com.example.tracking.ARCoreStatus
import com.example.ui.components.CineConnectionBadge
import com.example.ui.components.CineLensSelector
import com.example.ui.components.CineToggleChip
import com.example.ui.components.QrConnectDialog
import com.example.ui.theme.CineAmber
import com.example.ui.theme.CineAmberBright
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
import com.example.ui.theme.CineWarningYellow
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val connectionStatus by viewModel.wsClient.status.collectAsState()
    val statusMessage by viewModel.wsClient.statusMessage.collectAsState()
    val latencyMs by viewModel.wsClient.latencyMs.collectAsState()
    val targetName by viewModel.selectedTargetName.collectAsState()
    val arCoreStatus by viewModel.tracker.arStatus.collectAsState()

    val targetLock by viewModel.targetLock.collectAsState()
    val horizonLock by viewModel.horizonLock.collectAsState()
    val stabilize by viewModel.stabilize.collectAsState()
    val autoFocus by viewModel.autoFocus.collectAsState()
    val radiusLock by viewModel.radiusLock.collectAsState()
    val selectedLens by viewModel.lensMm.collectAsState()
    val smoothness by viewModel.smoothness.collectAsState()
    val motionScale by viewModel.motionScale.collectAsState()

    var serverIpInput by remember { mutableStateOf(viewModel.serverIp.value) }
    var serverPortInput by remember { mutableStateOf(viewModel.serverPort.value) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var editTargetInput by remember { mutableStateOf(targetName) }

    val scrollState = rememberScrollState()

    if (showTargetDialog) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text("Choose Target Object", color = CineTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Set target object name in Blender to anchor and track.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CineTextSecondary
                    )
                    OutlinedTextField(
                        value = editTargetInput,
                        onValueChange = { editTargetInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CineAmber,
                            unfocusedBorderColor = CineBorder,
                            focusedTextColor = CineTextPrimary,
                            unfocusedTextColor = CineTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("setup_target_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Character_01", "Hero_Cam", "Prop_Main").forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CineSurfaceVariant)
                                    .border(1.dp, CineBorder, RoundedCornerShape(6.dp))
                                    .clickable { editTargetInput = preset }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(preset, fontSize = 10.sp, color = CineAmber)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setTargetName(editTargetInput)
                        showTargetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineAmber, contentColor = Color.Black)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetDialog = false }) {
                    Text("Cancel", color = CineTextSecondary)
                }
            },
            containerColor = CineSurface
        )
    }

    if (showQrDialog) {
        QrConnectDialog(
            onDismiss = { showQrDialog = false },
            onQrResolved = { raw ->
                viewModel.setServerFromQr(raw)
                serverIpInput = viewModel.serverIp.value
                serverPortInput = viewModel.serverPort.value
            }
        )
    }

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

            // Header title & connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CINEMOTION",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontSize = 18.sp
                        ),
                        color = CineAmber
                    )
                    Text(
                        text = "6DoF Physical Blender Controller",
                        style = MaterialTheme.typography.labelSmall,
                        color = CineTextTertiary
                    )
                }

                CineConnectionBadge(status = connectionStatus, latencyMs = latencyMs)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ARCore Status banner (if unsupported or checking)
            if (arCoreStatus == ARCoreStatus.NOT_SUPPORTED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CineRecordRed.copy(alpha = 0.15f))
                        .border(1.dp, CineRecordRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = CineRecordRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ARCore not supported on device",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CineRecordRed
                        )
                        Text(
                            text = "Using hardware orientation fallback sensors for motion tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CineTextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // SECTION: Blender Connection
            Text(
                text = "BLENDER CONNECTION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = CineTextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = serverIpInput,
                    onValueChange = {
                        serverIpInput = it
                        viewModel.serverIp.value = it
                    },
                    label = { Text("IP Address", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CineAmber,
                        unfocusedBorderColor = CineBorder,
                        focusedTextColor = CineTextPrimary,
                        unfocusedTextColor = CineTextPrimary,
                        focusedLabelColor = CineAmber,
                        unfocusedLabelColor = CineTextTertiary
                    ),
                    modifier = Modifier
                        .weight(2.2f)
                        .testTag("server_ip_input")
                )

                OutlinedTextField(
                    value = serverPortInput,
                    onValueChange = {
                        serverPortInput = it
                        viewModel.serverPort.value = it
                    },
                    label = { Text("Port", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CineAmber,
                        unfocusedBorderColor = CineBorder,
                        focusedTextColor = CineTextPrimary,
                        unfocusedTextColor = CineTextPrimary,
                        focusedLabelColor = CineAmber,
                        unfocusedLabelColor = CineTextTertiary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("server_port_input")
                )

                IconButton(
                    onClick = { showQrDialog = true },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CineSurfaceVariant)
                        .border(1.dp, CineBorder, RoundedCornerShape(8.dp))
                        .testTag("qr_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR Code",
                        tint = CineAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connect / Disconnect Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (connectionStatus != ConnectionStatus.CONNECTED) {
                    Button(
                        onClick = { viewModel.connectBlender() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CineAmber,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("connect_blender_button")
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect to Blender", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.disconnectBlender() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CineRecordRed
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(CineRecordRed.copy(alpha = 0.5f))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("disconnect_blender_button")
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect (${serverIpInput}:${serverPortInput})", fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Connection Error / Status Feedback
            if (connectionStatus == ConnectionStatus.ERROR) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CineRecordRed.copy(alpha = 0.15f))
                        .border(1.dp, CineRecordRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CineRecordRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Connection Failed",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CineRecordRed
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = CineTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Troubleshooting Checklist:\n" +
                                "1. Verify the script or Addon in Blender is running (listening on port 8765).\n" +
                                "2. Ensure phone and laptop are on the exact same Wi-Fi.\n" +
                                "3. In Windows Defender / Mac Firewall, allow incoming connections to Blender on port 8765.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = CineTextSecondary
                    )
                }
            } else if (connectionStatus == ConnectionStatus.CONNECTING) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Attempting to reach ws://${serverIpInput.trim()}:${serverPortInput.trim()}...",
                    style = MaterialTheme.typography.bodySmall.copy(color = CineWarningYellow, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION: Target Object
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CineSurfaceVariant)
                    .border(1.dp, CineBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        editTargetInput = targetName
                        showTargetDialog = true
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("setup_target_card"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ACTIVE TARGET OBJECT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = CineTextTertiary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Edit Target", tint = CineAmber, modifier = Modifier.size(12.dp))
                    }
                    Text(
                        text = targetName.ifEmpty { "None (Physical Origin)" },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = CineCyan
                    )
                }

                Text(
                    text = if (connectionStatus == ConnectionStatus.CONNECTED) "Synced" else "Offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connectionStatus == ConnectionStatus.CONNECTED) CineSuccessGreen else CineTextTertiary
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // SECTION: Motion & Camera Controls (Compact Chips - NO stacked cards)
            Text(
                text = "CAMERA RIG CONTROLS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = CineTextTertiary
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CineToggleChip(
                    label = "Target Lock",
                    isActive = targetLock,
                    onToggle = { viewModel.targetLock.value = it },
                    testTag = "target_lock_chip"
                )
                CineToggleChip(
                    label = "Horizon Lock",
                    isActive = horizonLock,
                    onToggle = { viewModel.horizonLock.value = it },
                    testTag = "horizon_lock_chip"
                )
                CineToggleChip(
                    label = "Stabilize",
                    isActive = stabilize,
                    onToggle = { viewModel.stabilize.value = it },
                    testTag = "stabilize_chip"
                )
                CineToggleChip(
                    label = "Auto Focus",
                    isActive = autoFocus,
                    onToggle = { viewModel.autoFocus.value = it },
                    testTag = "auto_focus_chip"
                )
                CineToggleChip(
                    label = "Radius Lock",
                    isActive = radiusLock,
                    onToggle = { viewModel.radiusLock.value = it },
                    testTag = "radius_lock_chip"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION: Lens Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LENS FOCAL LENGTH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = CineTextTertiary
                )
                Text(
                    text = "${selectedLens}mm",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = CineAmber
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            CineLensSelector(
                selectedLens = selectedLens,
                options = listOf(24, 35, 50, 85),
                onSelect = { viewModel.lensMm.value = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION: Smoothness Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MOTION SMOOTHNESS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = CineTextTertiary
                )
                Text(
                    text = "%.0f%%".format(smoothness * 100f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CineAmber
                )
            }
            Slider(
                value = smoothness,
                onValueChange = { viewModel.smoothness.value = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = CineAmber,
                    activeTrackColor = CineAmber,
                    inactiveTrackColor = CineBorder
                ),
                modifier = Modifier.testTag("smoothness_slider")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // SECTION: Motion Scale Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PHYSICAL MOTION SCALE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = CineTextTertiary
                )
                Text(
                    text = "%.1fx".format(motionScale),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CineCyan
                )
            }
            Slider(
                value = motionScale,
                onValueChange = { viewModel.motionScale.value = it },
                valueRange = 0.2f..3.0f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = CineCyan,
                    activeTrackColor = CineCyan,
                    inactiveTrackColor = CineBorder
                ),
                modifier = Modifier.testTag("motion_scale_slider")
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Large START CAMERA Button at the bottom
            Button(
                onClick = {
                    if (activity != null) {
                        viewModel.startCamera(activity)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CineAmber,
                    contentColor = CineBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("start_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "START CAMERA OPERATOR",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
