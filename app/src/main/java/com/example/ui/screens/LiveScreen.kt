package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionStatus
import com.example.ui.components.CameraLiveView
import com.example.ui.components.CineTelemetryPill
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
fun LiveScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.stopCamera()
    }

    val livePose by viewModel.livePose.collectAsState()
    val isTracking by viewModel.tracker.isTracking.collectAsState()
    val connectionStatus by viewModel.wsClient.status.collectAsState()
    val latencyMs by viewModel.wsClient.latencyMs.collectAsState()
    val targetName by viewModel.selectedTargetName.collectAsState()
    val lensMm by viewModel.lensMm.collectAsState()
    val motionScale by viewModel.motionScale.collectAsState()

    val targetLock by viewModel.targetLock.collectAsState()
    val horizonLock by viewModel.horizonLock.collectAsState()
    val stabilize by viewModel.stabilize.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDurationMs by viewModel.recordingDurationMs.collectAsState()

    var isHudVisible by remember { mutableStateOf(true) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var editTargetInput by remember { mutableStateOf(targetName) }

    // Blinking record indicator transition
    val infiniteTransition = rememberInfiniteTransition(label = "rec_blink")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    // Pulsing anchor ring transition
    val anchorPulse by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anchor_pulse"
    )

    // Target Selection / Rename Dialog
    if (showTargetDialog) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text("Choose Target Object to Follow", color = CineTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Set the target object name to match your Blender scene (e.g. Character_01, Hero, Prop_01).",
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
                        modifier = Modifier.fillMaxWidth().testTag("edit_target_input")
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                    Text("Apply Target")
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("live_screen_container")
    ) {
        // 1. REAL CAMERA LIVE FEED (Operator Viewfinder)
        CameraLiveView(
            onTap = {
                isHudVisible = !isHudVisible
            },
            onDoubleTap = { normX, normY ->
                viewModel.pinAnchorAtTap(normX, normY)
            },
            onLongPress = { normX, normY ->
                viewModel.pinAnchorAtTap(normX, normY)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. HUD Reticles, Horizon Line & 3D Anchor ("Henkò") Tracking Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Cinematic safe action area border
            val insetX = size.width * 0.05f
            val insetY = size.height * 0.08f
            drawRect(
                color = CineBorder.copy(alpha = 0.5f),
                topLeft = Offset(insetX, insetY),
                size = Size(size.width - 2 * insetX, size.height - 2 * insetY),
                style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))
            )

            // Center Crosshair Reticle
            val reticleSize = 28.dp.toPx()
            drawLine(
                color = CineAmber.copy(alpha = 0.7f),
                start = Offset(cx - reticleSize, cy),
                end = Offset(cx - 6.dp.toPx(), cy),
                strokeWidth = 1.5f
            )
            drawLine(
                color = CineAmber.copy(alpha = 0.7f),
                start = Offset(cx + 6.dp.toPx(), cy),
                end = Offset(cx + reticleSize, cy),
                strokeWidth = 1.5f
            )
            drawLine(
                color = CineAmber.copy(alpha = 0.7f),
                start = Offset(cx, cy - reticleSize),
                end = Offset(cx, cy - 6.dp.toPx()),
                strokeWidth = 1.5f
            )
            drawLine(
                color = CineAmber.copy(alpha = 0.7f),
                start = Offset(cx, cy + 6.dp.toPx()),
                end = Offset(cx, cy + reticleSize),
                strokeWidth = 1.5f
            )

            // Center Ring
            drawCircle(
                color = CineAmber.copy(alpha = 0.5f),
                radius = 4.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.2f)
            )

            // Dynamic Level Horizon Line
            val rollRad = Math.toRadians(livePose.rollDegrees.toDouble())
            val hLen = 70.dp.toPx()
            val dx = (kotlin.math.cos(rollRad) * hLen).toFloat()
            val dy = (kotlin.math.sin(rollRad) * hLen).toFloat()
            drawLine(
                color = if (horizonLock) CineCyan.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.35f),
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = if (horizonLock) 2.5f else 1f
            )

            // 3D ANCHOR / TARGET ("HENKÒ") TRACKING RETICLE
            if (livePose.anchorScreenX != null && livePose.anchorScreenY != null) {
                val ax = size.width * livePose.anchorScreenX!!
                val ay = size.height * livePose.anchorScreenY!!
                val r = anchorPulse.dp.toPx()

                // Glowing outer tracking circle
                drawCircle(
                    color = CineCyan.copy(alpha = 0.85f),
                    radius = r,
                    center = Offset(ax, ay),
                    style = Stroke(width = 2f)
                )
                // Center point
                drawCircle(
                    color = CineAmber,
                    radius = 3.dp.toPx(),
                    center = Offset(ax, ay)
                )

                // Corner brackets around target object
                val bracketLen = 10.dp.toPx()
                val bOffset = r + 4.dp.toPx()

                // Top-left bracket
                drawLine(CineCyan, Offset(ax - bOffset, ay - bOffset), Offset(ax - bOffset + bracketLen, ay - bOffset), strokeWidth = 2f)
                drawLine(CineCyan, Offset(ax - bOffset, ay - bOffset), Offset(ax - bOffset, ay - bOffset + bracketLen), strokeWidth = 2f)

                // Top-right bracket
                drawLine(CineCyan, Offset(ax + bOffset, ay - bOffset), Offset(ax + bOffset - bracketLen, ay - bOffset), strokeWidth = 2f)
                drawLine(CineCyan, Offset(ax + bOffset, ay - bOffset), Offset(ax + bOffset, ay - bOffset + bracketLen), strokeWidth = 2f)

                // Bottom-left bracket
                drawLine(CineCyan, Offset(ax - bOffset, ay + bOffset), Offset(ax - bOffset + bracketLen, ay + bOffset), strokeWidth = 2f)
                drawLine(CineCyan, Offset(ax - bOffset, ay + bOffset), Offset(ax - bOffset, ay + bOffset - bracketLen), strokeWidth = 2f)

                // Bottom-right bracket
                drawLine(CineCyan, Offset(ax + bOffset, ay + bOffset), Offset(ax + bOffset - bracketLen, ay + bOffset), strokeWidth = 2f)
                drawLine(CineCyan, Offset(ax + bOffset, ay + bOffset), Offset(ax + bOffset, ay + bOffset - bracketLen), strokeWidth = 2f)

                // Connecting guideline to optical center
                drawLine(
                    color = CineCyan.copy(alpha = 0.35f),
                    start = Offset(cx, cy),
                    end = Offset(ax, ay),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
        }

        // 3. Anchor HUD Label over object (if in front of camera)
        if (livePose.anchorScreenX != null && livePose.anchorScreenY != null) {
            val ax = livePose.anchorScreenX!!
            val ay = livePose.anchorScreenY!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = (ax * 700).coerceIn(10f, 650f).dp,
                        top = (ay * 320).coerceIn(40f, 300f).dp
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CineBlack.copy(alpha = 0.75f))
                        .border(1.dp, CineCyan, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ANCHOR: $targetName",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = CineCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "%.2fm".format(livePose.radius),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = CineAmber
                    )
                }
            }
        }

        // 4. Main Overlay Layout (Toggled with Screen Tap)
        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP STATUS ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CineSurface.copy(alpha = 0.88f))
                    .border(1.dp, CineBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Exit button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.stopCamera() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit to Setup",
                        tint = CineAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "EXIT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = CineAmber
                    )
                }

                // Blender Connection indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = if (connectionStatus == ConnectionStatus.CONNECTED) CineSuccessGreen else CineRecordRed
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (connectionStatus == ConnectionStatus.CONNECTED) "BLENDER CONNECTED" else "DISCONNECTED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = statusColor
                    )
                }

                // 6DoF Active
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (isTracking) CineCyan else CineTextTertiary))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isTracking) "6DoF LIVE" else "PAUSED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = if (isTracking) CineCyan else CineTextTertiary
                    )
                }

                // Target Object selector with Edit button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CineSurfaceVariant)
                        .clickable {
                            editTargetInput = targetName
                            showTargetDialog = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TARGET: $targetName",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = CineCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Edit Target", tint = CineAmber, modifier = Modifier.size(12.dp))
                }

                // Latency
                Text(
                    text = "${latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = CineCyan
                )

                // Lens
                Text(
                    text = "${lensMm}mm",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = CineAmber
                )
            }

            // MIDDLE SECTION: Telemetry readouts and Anchor distance adjustment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Orbit, Elevation, Roll
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CineTelemetryPill(
                        label = "Orbit",
                        value = "%.1f°".format(livePose.orbitDegrees),
                        highlightColor = CineAmber
                    )
                    CineTelemetryPill(
                        label = "Elevation",
                        value = "%.1f°".format(livePose.elevationDegrees),
                        highlightColor = CineCyan
                    )
                    CineTelemetryPill(
                        label = "Roll",
                        value = "%.1f°".format(livePose.rollDegrees),
                        highlightColor = if (horizonLock) CineSuccessGreen else CineAmber
                    )
                }

                // Center Recording Overlay / Anchor prompt
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(visible = isRecording) {
                        val sec = (recordingDurationMs / 1000) % 60
                        val min = (recordingDurationMs / 60000)
                        val millis = (recordingDurationMs % 1000) / 100
                        val timeStr = "%02d:%02d.%d".format(min, sec, millis)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CineRecordRed.copy(alpha = 0.25f))
                                .border(1.dp, CineRecordRed, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(CineRecordRed.copy(alpha = recAlpha)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REC $timeStr",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black
                                ),
                                color = CineRecordRed
                            )
                        }
                    }

                    if (livePose.isAnchorBehind) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CineAmber.copy(alpha = 0.2f))
                                .border(1.dp, CineAmber, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "◂ ANCHOR BEHIND PHONE ▸",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = CineAmber
                            )
                        }
                    }

                    // Quick prompt hint
                    Text(
                        text = "Tap on screen to pin Anchor to any object",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                // Right Column: Radius, Motion Scale, and Anchor Distance Nudge
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    CineTelemetryPill(
                        label = "Distance",
                        value = "%.2fm".format(livePose.radius),
                        highlightColor = CineCyan
                    )

                    // Anchor Distance Nudge Buttons (+0.2m / -0.2m)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CineSurface.copy(alpha = 0.85f))
                            .border(1.dp, CineBorder, RoundedCornerShape(6.dp))
                            .padding(2.dp)
                    ) {
                        Text(
                            text = "ANCHOR",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = CineTextTertiary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        IconButton(
                            onClick = { viewModel.adjustAnchorDistance(-0.2f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Closer", tint = CineCyan, modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = { viewModel.adjustAnchorDistance(0.2f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Farther", tint = CineCyan, modifier = Modifier.size(14.dp))
                        }
                    }

                    CineTelemetryPill(
                        label = "Scale",
                        value = "%.1fx".format(motionScale),
                        highlightColor = CineAmber
                    )
                }
            }

            // BOTTOM CONTROL ROW: Touch Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CineSurface.copy(alpha = 0.92f))
                    .border(1.dp, CineBorder, RoundedCornerShape(12.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Recenter Button
                Button(
                    onClick = { viewModel.recenter() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CineSurfaceVariant,
                        contentColor = CineTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("recenter_button")
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = CineCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RECENTER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                }

                // 2. Pin Anchor In Front
                Button(
                    onClick = { viewModel.pinAnchorInFront(1.5f) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CineSurfaceVariant,
                        contentColor = CineTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("pin_anchor_button")
                ) {
                    Icon(Icons.Default.PinDrop, contentDescription = null, tint = CineAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PIN ANCHOR", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                }

                // 3. Target Lock Toggle
                Button(
                    onClick = { viewModel.targetLock.value = !targetLock },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (targetLock) CineAmber.copy(alpha = 0.25f) else CineSurfaceVariant,
                        contentColor = if (targetLock) CineAmber else CineTextSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = if (targetLock) ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(CineAmber)
                    ) else null,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("live_target_lock_button")
                ) {
                    Icon(if (targetLock) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (targetLock) "LOCK ON" else "TRACK LOCK", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                }

                // 4. Horizon Lock Toggle
                Button(
                    onClick = { viewModel.horizonLock.value = !horizonLock },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (horizonLock) CineCyan.copy(alpha = 0.25f) else CineSurfaceVariant,
                        contentColor = if (horizonLock) CineCyan else CineTextSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = if (horizonLock) ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(CineCyan)
                    ) else null,
                    modifier = Modifier.weight(0.9f).height(48.dp).testTag("live_horizon_lock_button")
                ) {
                    Text(if (horizonLock) "HORIZON ON" else "HORIZON", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                }

                // 5. Stabilize Toggle
                Button(
                    onClick = { viewModel.stabilize.value = !stabilize },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (stabilize) CineAmber.copy(alpha = 0.25f) else CineSurfaceVariant,
                        contentColor = if (stabilize) CineAmber else CineTextSecondary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = if (stabilize) ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(CineAmber)
                    ) else null,
                    modifier = Modifier.weight(0.9f).height(48.dp).testTag("live_stabilize_button")
                ) {
                    Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(if (stabilize) "STABLE ON" else "STABLE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp))
                }

                // 6. RECORD / STOP Button
                Button(
                    onClick = { viewModel.toggleRecord() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) CineRecordRed else CineAmber,
                        contentColor = if (isRecording) Color.White else Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.3f).height(48.dp).testTag("live_record_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRecording) "STOP TAKE" else "RECORD",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, fontSize = 12.sp)
                    )
                }
            }
        }
    }

    // 5. Minimal Clean Feed Recording Badge (when controls are hidden)
    if (!isHudVisible && isRecording) {
        val sec = (recordingDurationMs / 1000) % 60
        val min = (recordingDurationMs / 60000)
        val millis = (recordingDurationMs % 1000) / 100
        val timeStr = "%02d:%02d.%d".format(min, sec, millis)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CineBlack.copy(alpha = 0.75f))
                    .border(1.dp, CineRecordRed, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CineRecordRed.copy(alpha = recAlpha))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "REC $timeStr",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CineRecordRed
                )
            }
        }
    }

    // 6. Clean Feed Tap Hint
    if (!isHudVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "Clean Feed • Tap screen to show controls",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
}
