package com.example.model

import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.sqrt

data class CameraPose(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val qx: Float = 0f,
    val qy: Float = 0f,
    val qz: Float = 0f,
    val qw: Float = 1f,
    val timestamp: Long = System.currentTimeMillis(),
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val anchorZ: Float = -1.5f,
    val isAnchored: Boolean = false,
    val anchorScreenX: Float? = null,
    val anchorScreenY: Float? = null,
    val isAnchorBehind: Boolean = false
) {
    // Vector from camera to anchor
    val toAnchorX: Float get() = anchorX - x
    val toAnchorY: Float get() = anchorY - y
    val toAnchorZ: Float get() = anchorZ - z

    // Distance to anchor (in meters)
    val radiusToAnchor: Float
        get() = sqrt(toAnchorX * toAnchorX + toAnchorY * toAnchorY + toAnchorZ * toAnchorZ)

    // Distance from reference center or target (in meters)
    val radius: Float
        get() = if (isAnchored) radiusToAnchor else sqrt(x * x + y * y + z * z)

    // Roll angle in degrees (phone tilting left/right along view axis)
    val rollDegrees: Float
        get() {
            val sinrCosp = 2f * (qw * qz + qx * qy)
            val cosrCosp = 1f - 2f * (qy * qy + qz * qz)
            return (atan2(sinrCosp, cosrCosp) * 180f / Math.PI.toFloat())
        }

    // Pitch angle in degrees (looking up/down)
    val pitchDegrees: Float
        get() {
            val sinp = 2f * (qw * qx - qy * qz)
            val clamped = sinp.coerceIn(-1f, 1f)
            return (asin(clamped) * 180f / Math.PI.toFloat())
        }

    // Yaw angle in degrees (heading / azimuth orbit)
    val yawDegrees: Float
        get() {
            val sinyCosp = 2f * (qw * qy + qz * qx)
            val cosyCosp = 1f - 2f * (qx * qx + qy * qy)
            return (atan2(sinyCosp, cosyCosp) * 180f / Math.PI.toFloat())
        }

    // Orbit angle in degrees: azimuthal angle in ground plane around anchor/center
    val orbitDegrees: Float
        get() {
            val dx = if (isAnchored) (x - anchorX) else x
            val dz = if (isAnchored) (z - anchorZ) else (if (z == 0f) 0.0001f else z)
            return (atan2(dx, if (dz == 0f) 0.0001f else dz) * 180f / Math.PI.toFloat())
        }

    // Elevation angle in degrees relative to anchor/center
    val elevationDegrees: Float
        get() {
            val dx = if (isAnchored) (x - anchorX) else x
            val dy = if (isAnchored) (y - anchorY) else y
            val dz = if (isAnchored) (z - anchorZ) else z
            val groundDist = sqrt(dx * dx + dz * dz)
            return (atan2(dy, if (groundDist == 0f) 0.0001f else groundDist) * 180f / Math.PI.toFloat())
        }

    companion object {
        val ZERO = CameraPose()
    }
}
