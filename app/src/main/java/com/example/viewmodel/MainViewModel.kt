package com.example.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.model.CameraPose
import com.example.model.PoseSample
import com.example.model.TakeEntity
import com.example.network.BlenderWebSocketClient
import com.example.network.ConnectionStatus
import com.example.tracking.ARCoreMotionTracker
import com.example.tracking.ARCoreStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab {
    SETUP,
    LIVE,
    TAKES,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val takeDao = db.takeDao()

    val wsClient = BlenderWebSocketClient(viewModelScope)
    val tracker = ARCoreMotionTracker(application)

    // Current navigation tab
    private val _currentTab = MutableStateFlow(AppTab.SETUP)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Connection configuration
    val serverIp = MutableStateFlow("192.168.1.100")
    val serverPort = MutableStateFlow("8765")

    // Camera and Tracking parameters
    val targetLock = MutableStateFlow(false)
    val horizonLock = MutableStateFlow(false)
    val stabilize = MutableStateFlow(false)
    val autoFocus = MutableStateFlow(true)
    val radiusLock = MutableStateFlow(false)
    val lensMm = MutableStateFlow(35)
    val smoothness = MutableStateFlow(0.40f)
    val motionScale = MutableStateFlow(1.0f)
    val frameRate = MutableStateFlow(60) // 30 or 60 fps
    val trackingSensitivity = MutableStateFlow(1.0f)
    val selectedTargetName = MutableStateFlow("Character_01")

    // Live Readouts
    private val _livePose = MutableStateFlow(CameraPose.ZERO)
    val livePose: StateFlow<CameraPose> = _livePose.asStateFlow()

    // Recording State
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private var recordStartTime = 0L
    private val recordedSamples = mutableListOf<PoseSample>()
    private var takeCounter = 1

    // Replay State
    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()
    private var replayJob: Job? = null

    // Event notifications
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    // Takes from Room Database
    val allTakes: StateFlow<List<TakeEntity>> = takeDao.getAllTakes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Motion loop job
    private var streamJob: Job? = null

    init {
        // Count existing takes to set take counter
        viewModelScope.launch {
            takeDao.getAllTakes().collect { list ->
                if (list.isNotEmpty()) {
                    takeCounter = list.size + 1
                }
            }
        }
        viewModelScope.launch {
            wsClient.targetName.collect { blenderTarget ->
                if (blenderTarget.isNotBlank() && blenderTarget != selectedTargetName.value) {
                    selectedTargetName.value = blenderTarget
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        if (_currentTab.value == AppTab.LIVE && tab != AppTab.LIVE) {
            stopCamera()
        }
        _currentTab.value = tab
    }

    fun connectBlender() {
        val portInt = serverPort.value.toIntOrNull() ?: 8765
        wsClient.connect(serverIp.value, portInt)
    }

    fun disconnectBlender() {
        wsClient.disconnect()
    }

    fun setServerFromQr(rawString: String) {
        // Supports "192.168.1.100:8765", "ws://192.168.1.100:8765", or JSON {"ip":"...","port":...}
        val clean = rawString.trim().removePrefix("ws://").removePrefix("http://")
        if (clean.contains(":")) {
            val parts = clean.split(":")
            val ip = parts[0].trim()
            val port = parts[1].filter { it.isDigit() }
            serverIp.value = ip
            if (port.isNotEmpty()) serverPort.value = port
            emitMessage("Configured from QR: $ip:${serverPort.value}")
            connectBlender()
        } else {
            serverIp.value = clean
            emitMessage("IP set: $clean")
        }
    }

    fun startCamera(activity: Activity) {
        tracker.startTracking(activity)
        _currentTab.value = AppTab.LIVE
        startMotionLoop()
    }

    fun stopCamera() {
        if (_isRecording.value) {
            stopRecording()
        }
        stopMotionLoop()
        tracker.pauseTracking()
        _currentTab.value = AppTab.SETUP
    }

    private fun startMotionLoop() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val delayTime = (1000L / frameRate.value.coerceIn(24, 120))
                val pose = tracker.updateFrame(
                    motionScale = motionScale.value * trackingSensitivity.value,
                    smoothness = smoothness.value,
                    targetLock = targetLock.value,
                    horizonLock = horizonLock.value,
                    radiusLock = radiusLock.value,
                    stabilize = stabilize.value
                )
                _livePose.value = pose

                // Stream pose to Blender with target and anchor tracking
                wsClient.sendPose(
                    pose = pose,
                    lensMm = lensMm.value,
                    targetLock = targetLock.value,
                    horizonLock = horizonLock.value,
                    targetName = selectedTargetName.value
                )

                // If recording, sample this pose
                if (_isRecording.value) {
                    val sample = PoseSample(
                        t = System.currentTimeMillis() - recordStartTime,
                        pos = listOf(pose.x, pose.y, pose.z),
                        quat = listOf(pose.qx, pose.qy, pose.qz, pose.qw)
                    )
                    synchronized(recordedSamples) {
                        recordedSamples.add(sample)
                    }
                    _recordingDurationMs.value = System.currentTimeMillis() - recordStartTime
                }

                delay(delayTime)
            }
        }
    }

    private fun stopMotionLoop() {
        streamJob?.cancel()
        streamJob = null
    }

    fun recenter() {
        tracker.recenter()
        emitMessage("Physical reference center and anchor reset")
    }

    fun setTargetName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            selectedTargetName.value = trimmed
            emitMessage("Active Target: $trimmed")
        }
    }

    fun pinAnchorAtTap(normX: Float, normY: Float) {
        tracker.setAnchorAtScreenTap(normX, normY)
        emitMessage("Anchor pinned to target object at tapped position")
    }

    fun pinAnchorInFront(distanceMeters: Float = 1.5f) {
        tracker.setAnchorInFront(distanceMeters)
        emitMessage("Anchor set %.1fm in front of camera".format(distanceMeters))
    }

    fun adjustAnchorDistance(deltaMeters: Float) {
        tracker.adjustAnchorDistance(deltaMeters)
        val r = tracker.currentPose.value.radius
        emitMessage("Anchor distance: %.2fm".format(r))
    }

    fun resetCalibration() {
        tracker.recenter()
        smoothness.value = 0.40f
        motionScale.value = 1.0f
        trackingSensitivity.value = 1.0f
        emitMessage("Calibration and parameters reset to default")
    }

    fun toggleRecord() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        recordedSamples.clear()
        recordStartTime = System.currentTimeMillis()
        _recordingDurationMs.value = 0L
        _isRecording.value = true

        val takeName = "Take %02d".format(takeCounter)
        wsClient.sendRecordStart(takeName)
        emitMessage("Recording started: $takeName")
    }

    private fun stopRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false
        val duration = System.currentTimeMillis() - recordStartTime
        val takeName = "Take %02d".format(takeCounter)
        takeCounter++

        wsClient.sendRecordStop(takeName, duration)

        val samplesCopy: List<PoseSample>
        synchronized(recordedSamples) {
            samplesCopy = recordedSamples.toList()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val jsonString = TakeEntity.encodeSamples(samplesCopy)
            val entity = TakeEntity(
                name = takeName,
                durationMs = duration,
                targetName = selectedTargetName.value,
                lensMm = lensMm.value,
                timestamp = System.currentTimeMillis(),
                sampleCount = samplesCopy.size,
                samplesJson = jsonString
            )
            takeDao.insertTake(entity)
            emitMessage("Saved $takeName (${samplesCopy.size} samples)")
        }
    }

    // Takes management
    fun deleteTake(take: TakeEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            takeDao.deleteTake(take)
            emitMessage("Deleted ${take.name}")
        }
    }

    fun renameTake(take: TakeEntity, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            takeDao.updateTake(take.copy(name = newName.trim()))
            emitMessage("Renamed to $newName")
        }
    }

    fun bakeTakeToBlender(take: TakeEntity) {
        if (wsClient.status.value != ConnectionStatus.CONNECTED) {
            emitMessage("Cannot bake: Blender disconnected")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val success = wsClient.sendBakeTake(take)
            if (success) {
                emitMessage("Baked ${take.name} keyframes to Blender!")
            } else {
                emitMessage("Failed to send bake message")
            }
        }
    }

    fun replayTake(take: TakeEntity) {
        if (wsClient.status.value != ConnectionStatus.CONNECTED) {
            emitMessage("Connect to Blender before replaying")
            return
        }
        if (_isReplaying.value) {
            replayJob?.cancel()
            _isReplaying.value = false
            emitMessage("Replay stopped")
            return
        }

        val samples = take.parseSamples()
        if (samples.isEmpty()) {
            emitMessage("Take has no recorded samples")
            return
        }

        _isReplaying.value = true
        emitMessage("Replaying ${take.name} to Blender...")

        replayJob = viewModelScope.launch(Dispatchers.Default) {
            var lastT = 0L
            for (sample in samples) {
                if (!isActive) break
                val diff = (sample.t - lastT).coerceIn(5L, 100L)
                delay(diff)
                lastT = sample.t

                val replayPose = CameraPose(
                    x = sample.pos.getOrElse(0) { 0f },
                    y = sample.pos.getOrElse(1) { 0f },
                    z = sample.pos.getOrElse(2) { 0f },
                    qx = sample.quat.getOrElse(0) { 0f },
                    qy = sample.quat.getOrElse(1) { 0f },
                    qz = sample.quat.getOrElse(2) { 0f },
                    qw = sample.quat.getOrElse(3) { 1f },
                    timestamp = System.currentTimeMillis()
                )
                wsClient.sendPose(
                    pose = replayPose,
                    lensMm = take.lensMm,
                    targetLock = false,
                    horizonLock = false
                )
            }
            _isReplaying.value = false
            emitMessage("Finished replay of ${take.name}")
        }
    }

    fun smoothTake(take: TakeEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val samples = take.parseSamples()
            if (samples.size < 3) return@launch

            val smoothed = mutableListOf<PoseSample>()
            for (i in samples.indices) {
                if (i == 0 || i == samples.size - 1) {
                    smoothed.add(samples[i])
                } else {
                    val pPrev = samples[i - 1].pos
                    val pCurr = samples[i].pos
                    val pNext = samples[i + 1].pos

                    val avgX = (pPrev[0] + pCurr[0] + pNext[0]) / 3f
                    val avgY = (pPrev[1] + pCurr[1] + pNext[1]) / 3f
                    val avgZ = (pPrev[2] + pCurr[2] + pNext[2]) / 3f

                    smoothed.add(
                        samples[i].copy(
                            pos = listOf(avgX, avgY, avgZ)
                        )
                    )
                }
            }

            val updatedJson = TakeEntity.encodeSamples(smoothed)
            takeDao.updateTake(take.copy(samplesJson = updatedJson))
            emitMessage("Applied smoothing filter to ${take.name}")
        }
    }

    fun stabilizeTake(take: TakeEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val samples = take.parseSamples()
            if (samples.isEmpty()) return@launch

            // Level roll across all samples for stabilized horizon
            val stabilized = samples.map { sample ->
                val qx = sample.quat.getOrElse(0) { 0f }
                val qy = sample.quat.getOrElse(1) { 0f }
                val qz = sample.quat.getOrElse(2) { 0f }
                val qw = sample.quat.getOrElse(3) { 1f }

                val sinp = (2f * (qw * qx - qy * qz)).coerceIn(-1f, 1f)
                val pitch = kotlin.math.asin(sinp)
                val sinyCosp = 2f * (qw * qy + qz * qx)
                val cosyCosp = 1f - 2f * (qx * qx + qy * qy)
                val yaw = kotlin.math.atan2(sinyCosp, cosyCosp)

                // Rebuild quaternion with roll = 0
                val cy = kotlin.math.cos(yaw * 0.5f)
                val sy = kotlin.math.sin(yaw * 0.5f)
                val cp = kotlin.math.cos(pitch * 0.5f)
                val sp = kotlin.math.sin(pitch * 0.5f)

                val outW = cp * cy
                val outX = sp * cy
                val outY = cp * sy
                val outZ = -sp * sy

                sample.copy(quat = listOf(outX, outY, outZ, outW))
            }

            val updatedJson = TakeEntity.encodeSamples(stabilized)
            takeDao.updateTake(take.copy(samplesJson = updatedJson))
            emitMessage("Stabilized horizon on ${take.name}")
        }
    }

    private fun emitMessage(msg: String) {
        viewModelScope.launch {
            _uiEvents.emit(msg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMotionLoop()
        wsClient.disconnect()
        tracker.destroy()
    }
}
