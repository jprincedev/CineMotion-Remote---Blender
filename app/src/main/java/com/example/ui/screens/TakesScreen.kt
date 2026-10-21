package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TakeEntity
import com.example.network.ConnectionStatus
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TakesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val takes by viewModel.allTakes.collectAsState()
    val isReplaying by viewModel.isReplaying.collectAsState()
    val connectionStatus by viewModel.wsClient.status.collectAsState()

    var takeToRename by remember { mutableStateOf<TakeEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var takeToDelete by remember { mutableStateOf<TakeEntity?>(null) }
    var previewTake by remember { mutableStateOf<TakeEntity?>(null) }

    // Rename Dialog
    if (takeToRename != null) {
        AlertDialog(
            onDismissRequest = { takeToRename = null },
            title = { Text("Rename Take", color = CineTextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Take Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CineAmber,
                        unfocusedBorderColor = CineBorder,
                        focusedTextColor = CineTextPrimary,
                        unfocusedTextColor = CineTextPrimary
                    ),
                    modifier = Modifier.testTag("rename_input_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        takeToRename?.let { viewModel.renameTake(it, renameText) }
                        takeToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineAmber, contentColor = Color.Black),
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { takeToRename = null }) {
                    Text("Cancel", color = CineTextSecondary)
                }
            },
            containerColor = CineSurface
        )
    }

    // Delete Confirmation Dialog
    if (takeToDelete != null) {
        AlertDialog(
            onDismissRequest = { takeToDelete = null },
            title = { Text("Delete ${takeToDelete?.name}?", color = CineRecordRed) },
            text = {
                Text(
                    "Are you sure you want to delete this recorded take? This cannot be undone.",
                    color = CineTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        takeToDelete?.let { viewModel.deleteTake(it) }
                        takeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineRecordRed, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { takeToDelete = null }) {
                    Text("Cancel", color = CineTextSecondary)
                }
            },
            containerColor = CineSurface
        )
    }

    // Preview Dialog
    if (previewTake != null) {
        val samples = remember(previewTake) { previewTake!!.parseSamples() }
        AlertDialog(
            onDismissRequest = { previewTake = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = CineAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(previewTake!!.name, color = CineTextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target: ${previewTake!!.targetName}", color = CineCyan)
                    Text("Lens: ${previewTake!!.lensMm}mm", color = CineTextSecondary)
                    Text("Duration: ${formatDuration(previewTake!!.durationMs)}", color = CineTextSecondary)
                    Text("Total 6DoF Samples: ${samples.size}", color = CineTextSecondary)
                    if (samples.isNotEmpty()) {
                        val firstP = samples.first().pos
                        val lastP = samples.last().pos
                        Text(
                            "Start Pos: [%.2f, %.2f, %.2f]".format(firstP[0], firstP[1], firstP[2]),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = CineTextTertiary
                        )
                        Text(
                            "End Pos: [%.2f, %.2f, %.2f]".format(lastP[0], lastP[1], lastP[2]),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = CineTextTertiary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { previewTake = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CineAmber, contentColor = Color.Black)
                ) {
                    Text("Close")
                }
            },
            containerColor = CineSurface
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
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RECORDED TAKES",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontSize = 18.sp
                        ),
                        color = CineAmber
                    )
                    Text(
                        text = "${takes.size} takes stored locally",
                        style = MaterialTheme.typography.labelSmall,
                        color = CineTextTertiary
                    )
                }

                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    Text(
                        text = "Blender Ready",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CineSuccessGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (takes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = CineBorder,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Camera Takes Yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = CineTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Go to Setup and tap Start Camera Operator\nto record real physical camera motions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CineTextTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(takes, key = { it.id }) { take ->
                        TakeCard(
                            take = take,
                            isReplaying = isReplaying,
                            onPreview = { previewTake = take },
                            onRename = {
                                takeToRename = take
                                renameText = take.name
                            },
                            onDelete = { takeToDelete = take },
                            onReplay = { viewModel.replayTake(take) },
                            onSmooth = { viewModel.smoothTake(take) },
                            onStabilize = { viewModel.stabilizeTake(take) },
                            onBake = { viewModel.bakeTakeToBlender(take) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakeCard(
    take: TakeEntity,
    isReplaying: Boolean,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onReplay: () -> Unit,
    onSmooth: () -> Unit,
    onStabilize: () -> Unit,
    onBake: () -> Unit
) {
    val dateStr = remember(take.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        sdf.format(Date(take.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CineSurfaceVariant)
            .border(1.dp, CineBorder, RoundedCornerShape(10.dp))
            .padding(14.dp)
            .testTag("take_card_${take.id}")
    ) {
        // Take Title & Meta Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = take.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = CineTextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = CineTextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = formatDuration(take.durationMs),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = CineAmber
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Metadata specs: Target, Lens, Samples, Timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target: ${take.targetName}",
                style = MaterialTheme.typography.labelSmall,
                color = CineCyan
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall,
                color = CineTextTertiary
            )
            Text(
                text = "${take.lensMm}mm",
                style = MaterialTheme.typography.labelSmall,
                color = CineTextSecondary
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall,
                color = CineTextTertiary
            )
            Text(
                text = "${take.sampleCount} samples",
                style = MaterialTheme.typography.labelSmall,
                color = CineTextSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = CineTextTertiary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Primary Action: BAKE TO BLENDER
        Button(
            onClick = onBake,
            colors = ButtonDefaults.buttonColors(
                containerColor = CineAmber,
                contentColor = CineBlack
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("bake_to_blender_button_${take.id}")
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "BAKE KEYFRAMES TO BLENDER",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary Actions Flow Row: Preview, Replay, Smooth, Stabilize, Delete
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Preview Action
            OutlinedButton(
                onClick = onPreview,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp), tint = CineTextSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Preview", fontSize = 11.sp, color = CineTextSecondary)
            }

            // Replay Action
            OutlinedButton(
                onClick = onReplay,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = CineCyan)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Replay", fontSize = 11.sp, color = CineCyan)
            }

            // Smooth Action
            OutlinedButton(
                onClick = onSmooth,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp), tint = CineAmber)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Smooth", fontSize = 11.sp, color = CineAmber)
            }

            // Stabilize Action
            OutlinedButton(
                onClick = onStabilize,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Stabilize", fontSize = 11.sp, color = CineTextPrimary)
            }

            // Delete Action
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CineRecordRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val tenths = (ms % 1000) / 100
    return "%02d:%02d.%d".format(minutes, seconds, tenths)
}
