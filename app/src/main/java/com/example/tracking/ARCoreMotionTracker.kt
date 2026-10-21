package com.example.tracking

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.model.CameraPose
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ARCoreStatus {
    CHECKING,
    SUPPORTED_READY,
    NEED_INSTALL,
    NOT_SUPPORTED
}

class ARCoreMotionTracker(private val context: Context) : SensorEventListener {
    private val tag = "ARCoreTracker"

    private val _arStatus = MutableStateFlow(ARCoreStatus.CHECKING)
    val arStatus: StateFlow<ARCoreStatus> = _arStatus.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentPose = MutableStateFlow(CameraPose.ZERO)
    val currentPose: StateFlow<CameraPose> = _currentPose.asStateFlow()

    private val smoother = MotionSmoother()

    private var session: Session? = null
    private var isSessionActive = false

    // Physical reference center
    private var centerRawX = 0f
    private var centerRawY = 0f
    private var centerRawZ = 0f
    private var centerRawQx = 0f
    private var centerRawQy = 0f
    private var centerRawQz = 0f
    private var centerRawQw = 1f
    private var hasCenter = false

    // 3D Anchor ("Henkò") tracking coordinates in world space
    private var anchorWorldX = 0f
    private var anchorWorldY = 0f
    private var anchorWorldZ = -1.5f
    private var isAnchored = true
    private var anchorDistance = 1.5f

    // Fallback sensor tracking (for emulators or non-AR devices)
    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var fallbackX = 0f
    private var fallbackY = 0f
    private var fallbackZ = 0f
    private var fallbackQx = 0f
    private var fallbackQy = 0f
    private var fallbackQz = 0f
    private var fallbackQw = 1f
    private var usingFallback = false

    // Radius Lock initial distance
    private var lockedRadius: Float? = null

    init {
        checkARCoreSupport()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }

    fun checkARCoreSupport() {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            if (availability.isTransient) {
                _arStatus.value = ARCoreStatus.CHECKING
            } else if (availability.isSupported) {
                _arStatus.value = ARCoreStatus.SUPPORTED_READY
            } else {
                _arStatus.value = ARCoreStatus.NOT_SUPPORTED
            }
        } catch (e: Exception) {
            Log.e(tag, "ARCore check failed: ${e.message}")
            _arStatus.value = ARCoreStatus.NOT_SUPPORTED
        }
    }

    fun requestInstallIfNeeded(activity: Activity): Boolean {
        return try {
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, true)
            if (installStatus == ArCoreApk.InstallStatus.INSTALLED) {
                _arStatus.value = ARCoreStatus.SUPPORTED_READY
                true
            } else {
                _arStatus.value = ARCoreStatus.NEED_INSTALL
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "ARCore install request error: ${e.message}")
            false
        }
    }

    fun startTracking(activity: Activity) {
        if (isSessionActive) return

        if (_arStatus.value == ARCoreStatus.SUPPORTED_READY) {
            try {
                if (session == null) {
                    session = Session(activity).apply {
                        val config = Config(this).apply {
                            focusMode = Config.FocusMode.AUTO
                            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            lightEstimationMode = Config.LightEstimationMode.DISABLED
                            planeFindingMode = Config.PlaneFindingMode.DISABLED
                        }
                        configure(config)
                    }
                }
                session?.resume()
                isSessionActive = true
                usingFallback = false
                _isTracking.value = true
                Log.d(tag, "ARCore 6DoF session resumed")
                return
            } catch (e: CameraNotAvailableException) {
                Log.w(tag, "Camera not available: ${e.message}")
            } catch (e: Exception) {
                Log.w(tag, "Could not initialize ARCore Session: ${e.message}. Using fallback sensor tracking.")
            }
        }

        startFallbackTracking()
    }

    private fun startFallbackTracking() {
        usingFallback = true
        _isTracking.value = true
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun pauseTracking() {
        if (isSessionActive) {
            try {
                session?.pause()
            } catch (_: Exception) {}
            isSessionActive = false
        }
        if (usingFallback) {
            sensorManager?.unregisterListener(this)
        }
        _isTracking.value = false
    }

    fun destroy() {
        pauseTracking()
        try {
            session?.close()
        } catch (_: Exception) {}
        session = null
    }

    /**
     * Set anchor directly in front of camera at the specified distance (meters).
     */
    fun setAnchorInFront(distanceMeters: Float = 1.5f) {
        anchorDistance = distanceMeters.coerceAtLeast(0.3f)
        val pose = _currentPose.value

        // Forward direction from camera orientation quaternion
        val qx = pose.qx
        val qy = pose.qy
        val qz = pose.qz
        val qw = pose.qw

        // Forward vector in local space is (0, 0, -1), rotated by camera quaternion
        val fx = -2f * (qx * qz + qy * qw)
        val fy = -2f * (qy * qz - qx * qw)
        val fz = -(1f - 2f * (qx * qx + qy * qy))
        val len = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-5f)

        anchorWorldX = pose.x + (fx / len) * anchorDistance
        anchorWorldY = pose.y + (fy / len) * anchorDistance
        anchorWorldZ = pose.z + (fz / len) * anchorDistance
        isAnchored = true
        Log.d(tag, "Anchor set at [$anchorWorldX, $anchorWorldY, $anchorWorldZ]")
    }

    /**
     * Pin anchor onto an object tapped on the screen (normalized coords [0..1]).
     */
    fun setAnchorAtScreenTap(normX: Float, normY: Float, distanceMeters: Float = 1.5f) {
        anchorDistance = distanceMeters.coerceAtLeast(0.3f)
        val pose = _currentPose.value

        // Angle offset based on screen position
        val xOffset = (normX - 0.5f) * 1.1f
        val yOffset = -(normY - 0.5f) * 0.9f

        val localDirX = xOffset
        val localDirY = yOffset
        val localDirZ = -1.0f
        val localLen = sqrt(localDirX * localDirX + localDirY * localDirY + localDirZ * localDirZ)

        val dirNormX = localDirX / localLen
        val dirNormY = localDirY / localLen
        val dirNormZ = localDirZ / localLen

        // Rotate ray by camera quaternion
        val worldDir = rotateVectorByQuat(dirNormX, dirNormY, dirNormZ, pose.qx, pose.qy, pose.qz, pose.qw)

        anchorWorldX = pose.x + worldDir[0] * anchorDistance
        anchorWorldY = pose.y + worldDir[1] * anchorDistance
        anchorWorldZ = pose.z + worldDir[2] * anchorDistance
        isAnchored = true
        Log.d(tag, "Anchor pinned at tap [$anchorWorldX, $anchorWorldY, $anchorWorldZ]")
    }

    /**
     * Adjust anchor distance closer or farther (e.g. +0.2m or -0.2m).
     */
    fun adjustAnchorDistance(deltaMeters: Float) {
        val pose = _currentPose.value
        val toAnchorX = anchorWorldX - pose.x
        val toAnchorY = anchorWorldY - pose.y
        val toAnchorZ = anchorWorldZ - pose.z
        val currentDist = sqrt(toAnchorX * toAnchorX + toAnchorY * toAnchorY + toAnchorZ * toAnchorZ).coerceAtLeast(0.2f)
        val newDist = (currentDist + deltaMeters).coerceIn(0.3f, 15f)
        val factor = newDist / currentDist

        anchorWorldX = pose.x + toAnchorX * factor
        anchorWorldY = pose.y + toAnchorY * factor
        anchorWorldZ = pose.z + toAnchorZ * factor
        anchorDistance = newDist
    }

    fun resetAnchor() {
        setAnchorInFront(1.5f)
    }

    fun recenter() {
        hasCenter = false
        smoother.reset()
        lockedRadius = null
        resetAnchor()
        Log.d(tag, "Tracking center and anchor reset")
    }

    /**
     * Called on each frame loop to read the newest 6DoF pose.
     */
    fun updateFrame(
        motionScale: Float,
        smoothness: Float,
        targetLock: Boolean,
        horizonLock: Boolean,
        radiusLock: Boolean,
        stabilize: Boolean
    ): CameraPose {
        var rawX = 0f
        var rawY = 0f
        var rawZ = 0f
        var rawQx = 0f
        var rawQy = 0f
        var rawQz = 0f
        var rawQw = 1f
        var hasValidPose = false

        if (isSessionActive && session != null) {
            try {
                val frame: Frame = session!!.update()
                val camera = frame.camera
                if (camera.trackingState == TrackingState.TRACKING) {
                    val pose: Pose = camera.pose
                    rawX = pose.tx()
                    rawY = pose.ty()
                    rawZ = pose.tz()
                    rawQx = pose.qx()
                    rawQy = pose.qy()
                    rawQz = pose.qz()
                    rawQw = pose.qw()
                    hasValidPose = true
                }
            } catch (e: Exception) {
                Log.w(tag, "ARCore update error: ${e.message}")
            }
        }

        if (!hasValidPose && usingFallback) {
            rawX = fallbackX
            rawY = fallbackY
            rawZ = fallbackZ
            rawQx = fallbackQx
            rawQy = fallbackQy
            rawQz = fallbackQz
            rawQw = fallbackQw
            hasValidPose = true
        }

        if (!hasValidPose) {
            return _currentPose.value
        }

        // If reference center has not been captured yet, capture this first pose
        if (!hasCenter) {
            setCenterPose(rawX, rawY, rawZ, rawQx, rawQy, rawQz, rawQw)
            setAnchorInFront(1.5f)
        }

        // 1. Subtract reference center position & apply motion scale
        var relX = (rawX - centerRawX) * motionScale
        var relY = (rawY - centerRawY) * motionScale
        var relZ = (rawZ - centerRawZ) * motionScale

        // 2. Relative rotation: Q_rel = Q_center^(-1) * Q_raw
        var (relQx, relQy, relQz, relQw) = quatMultiply(
            -centerRawQx, -centerRawQy, -centerRawQz, centerRawQw,
            rawQx, rawQy, rawQz, rawQw
        )

        // 3. Radius Lock: clamp/lock the distance to anchor or physical center
        if (radiusLock) {
            val dist = if (isAnchored) {
                val dx = relX - anchorWorldX
                val dy = relY - anchorWorldY
                val dz = relZ - anchorWorldZ
                sqrt(dx * dx + dy * dy + dz * dz)
            } else {
                sqrt(relX * relX + relY * relY + relZ * relZ)
            }
            val targetDist = lockedRadius ?: dist.coerceAtLeast(0.5f).also { lockedRadius = it }
            if (dist > 0.001f) {
                val factor = targetDist / dist
                if (isAnchored) {
                    relX = anchorWorldX + (relX - anchorWorldX) * factor
                    relY = anchorWorldY + (relY - anchorWorldY) * factor
                    relZ = anchorWorldZ + (relZ - anchorWorldZ) * factor
                } else {
                    relX *= factor
                    relY *= factor
                    relZ *= factor
                }
            }
        } else {
            lockedRadius = null
        }

        // 4. Target Lock: orientation automatically locks onto the active Anchor
        if (targetLock) {
            val lookDirX = anchorWorldX - relX
            val lookDirY = anchorWorldY - relY
            val lookDirZ = anchorWorldZ - relZ
            val dirLen = sqrt(lookDirX * lookDirX + lookDirY * lookDirY + lookDirZ * lookDirZ)
            if (dirLen > 0.01f) {
                val yaw = atan2(lookDirX, -lookDirZ)
                val pitch = atan2(lookDirY, sqrt(lookDirX * lookDirX + lookDirZ * lookDirZ))
                val targetQ = eulerToQuaternion(pitch = pitch, yaw = yaw, roll = 0f)
                relQx = targetQ[0]
                relQy = targetQ[1]
                relQz = targetQ[2]
                relQw = targetQ[3]
            }
        }

        // 5. Horizon Lock: zero out roll angle to keep horizon perfectly level
        if (horizonLock && !targetLock) {
            val sinp = (2f * (relQw * relQx - relQy * relQz)).coerceIn(-1f, 1f)
            val pitch = kotlin.math.asin(sinp)
            val sinyCosp = 2f * (relQw * relQy + relQz * relQx)
            val cosyCosp = 1f - 2f * (relQx * relQx + relQy * relQy)
            val yaw = atan2(sinyCosp, cosyCosp)

            val levelQ = eulerToQuaternion(pitch = pitch, yaw = yaw, roll = 0f)
            relQx = levelQ[0]
            relQy = levelQ[1]
            relQz = levelQ[2]
            relQw = levelQ[3]
        }

        // 6. Smoothing / Stabilization filter
        val effectiveSmoothness = if (stabilize) (smoothness + 0.35f).coerceAtMost(0.98f) else smoothness
        val filtered = smoother.filter(
            relX, relY, relZ,
            relQx, relQy, relQz, relQw,
            effectiveSmoothness
        )

        // 7. Calculate 2D Screen projection of the Anchor point for HUD tracking
        val toAnchorX = anchorWorldX - filtered.x
        val toAnchorY = anchorWorldY - filtered.y
        val toAnchorZ = anchorWorldZ - filtered.z

        // Transform anchor offset vector into camera local space (inverse camera rotation)
        val localAnchor = rotateVectorByQuat(toAnchorX, toAnchorY, toAnchorZ, -filtered.qx, -filtered.qy, -filtered.qz, filtered.qw)
        val localZ = localAnchor[2]
        val isBehind = localZ >= -0.05f

        val screenX: Float?
        val screenY: Float?
        if (!isBehind) {
            val focal = 0.95f
            val projX = 0.5f + (localAnchor[0] / -localZ) * focal
            val projY = 0.5f - (localAnchor[1] / -localZ) * focal
            screenX = projX.coerceIn(0.05f, 0.95f)
            screenY = projY.coerceIn(0.08f, 0.92f)
        } else {
            screenX = null
            screenY = null
        }

        val finalPose = CameraPose(
            x = filtered.x,
            y = filtered.y,
            z = filtered.z,
            qx = filtered.qx,
            qy = filtered.qy,
            qz = filtered.qz,
            qw = filtered.qw,
            timestamp = System.currentTimeMillis(),
            anchorX = anchorWorldX,
            anchorY = anchorWorldY,
            anchorZ = anchorWorldZ,
            isAnchored = isAnchored,
            anchorScreenX = screenX,
            anchorScreenY = screenY,
            isAnchorBehind = isBehind
        )

        _currentPose.value = finalPose
        return finalPose
    }

    private fun setCenterPose(
        x: Float, y: Float, z: Float,
        qx: Float, qy: Float, qz: Float, qw: Float
    ) {
        centerRawX = x
        centerRawY = y
        centerRawZ = z
        centerRawQx = qx
        centerRawQy = qy
        centerRawQz = qz
        centerRawQw = qw
        hasCenter = true
        smoother.reset()
    }

    private fun quatMultiply(
        q1x: Float, q1y: Float, q1z: Float, q1w: Float,
        q2x: Float, q2y: Float, q2z: Float, q2w: Float
    ): FloatArray {
        val w = q1w * q2w - q1x * q2x - q1y * q2y - q1z * q2z
        val x = q1w * q2x + q1x * q2w + q1y * q2z - q1z * q2y
        val y = q1w * q2y - q1x * q2z + q1y * q2w + q1z * q2x
        val z = q1w * q2z + q1x * q2y - q1y * q2x + q1z * q2w
        val norm = sqrt(x * x + y * y + z * z + w * w).coerceAtLeast(1e-6f)
        return floatArrayOf(x / norm, y / norm, z / norm, w / norm)
    }

    private fun rotateVectorByQuat(
        vx: Float, vy: Float, vz: Float,
        qx: Float, qy: Float, qz: Float, qw: Float
    ): FloatArray {
        // q * v * q^-1
        val ix = qw * vx + qy * vz - qz * vy
        val iy = qw * vy + qz * vx - qx * vz
        val iz = qw * vz + qx * vy - qy * vx
        val iw = -qx * vx - qy * vy - qz * vz

        val rx = ix * qw + iw * -qx + iy * -qz - iz * -qy
        val ry = iy * qw + iw * -qy + iz * -qx - ix * -qz
        val rz = iz * qw + iw * -qz + ix * -qy - iy * -qx
        return floatArrayOf(rx, ry, rz)
    }

    private fun eulerToQuaternion(pitch: Float, yaw: Float, roll: Float): FloatArray {
        val cy = cos(yaw * 0.5f)
        val sy = sin(yaw * 0.5f)
        val cp = cos(pitch * 0.5f)
        val sp = sin(pitch * 0.5f)
        val cr = cos(roll * 0.5f)
        val sr = sin(roll * 0.5f)

        val w = cr * cp * cy + sr * sp * sy
        val x = sr * cp * cy - cr * sp * sy
        val y = cr * sp * cy + sr * cp * sy
        val z = cr * cp * sy - sr * sp * cy
        val len = sqrt(x * x + y * y + z * z + w * w).coerceAtLeast(1e-6f)
        return floatArrayOf(x / len, y / len, z / len, w / len)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val q = FloatArray(4)
                SensorManager.getQuaternionFromVector(q, event.values)
                fallbackQw = q[0]
                fallbackQx = q[1]
                fallbackQy = q[2]
                fallbackQz = q[3]
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                if (abs(ax) > 0.2f) fallbackX += ax * 0.015f
                if (abs(ay) > 0.2f) fallbackY += ay * 0.015f
                if (abs(az) > 0.2f) fallbackZ += az * 0.015f
                fallbackX *= 0.96f
                fallbackY *= 0.96f
                fallbackZ *= 0.96f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
