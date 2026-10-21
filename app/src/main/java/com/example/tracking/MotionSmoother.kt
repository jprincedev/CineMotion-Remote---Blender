package com.example.tracking

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filter for 6DoF tracking to eliminate hand tremble while retaining snappy response.
 * Uses an adaptive low-pass filter with smoothness parameter [0.0, 1.0].
 */
class MotionSmoother {
    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f

    private var prevQx = 0f
    private var prevQy = 0f
    private var prevQz = 0f
    private var prevQw = 1f

    private var isInitialized = false

    fun reset() {
        isInitialized = false
    }

    fun filter(
        x: Float, y: Float, z: Float,
        qx: Float, qy: Float, qz: Float, qw: Float,
        smoothness: Float
    ): FilteredPose {
        if (!isInitialized) {
            prevX = x
            prevY = y
            prevZ = z
            prevQx = qx
            prevQy = qy
            prevQz = qz
            prevQw = qw
            isInitialized = true
            return FilteredPose(x, y, z, qx, qy, qz, qw)
        }

        // Smoothness in [0.0, 1.0].
        // At 0: alpha = 1.0 (raw instant tracking).
        // At 1: alpha = 0.08 (strong smoothing).
        val clampedSmoothness = smoothness.coerceIn(0f, 1f)
        val posAlpha = (1f - clampedSmoothness * 0.90f).coerceIn(0.08f, 1f)
        val rotAlpha = (1f - clampedSmoothness * 0.85f).coerceIn(0.12f, 1f)

        // Exponential smoothing for position
        val outX = prevX + posAlpha * (x - prevX)
        val outY = prevY + posAlpha * (y - prevY)
        val outZ = prevZ + posAlpha * (z - prevZ)

        // Slerp for rotation quaternion
        val (outQx, outQy, outQz, outQw) = slerp(
            prevQx, prevQy, prevQz, prevQw,
            qx, qy, qz, qw,
            rotAlpha
        )

        prevX = outX
        prevY = outY
        prevZ = outZ
        prevQx = outQx
        prevQy = outQy
        prevQz = outQz
        prevQw = outQw

        return FilteredPose(outX, outY, outZ, outQx, outQy, outQz, outQw)
    }

    private fun slerp(
        q1x: Float, q1y: Float, q1z: Float, q1w: Float,
        q2x: Float, q2y: Float, q2z: Float, q2w: Float,
        t: Float
    ): FloatArray {
        var dot = q1x * q2x + q1y * q2y + q1z * q2z + q1w * q2w

        var q2xAdjusted = q2x
        var q2yAdjusted = q2y
        var q2zAdjusted = q2z
        var q2wAdjusted = q2w

        // If negative dot product, invert one quaternion to take shorter path
        if (dot < 0f) {
            dot = -dot
            q2xAdjusted = -q2x
            q2yAdjusted = -q2y
            q2zAdjusted = -q2z
            q2wAdjusted = -q2w
        }

        if (dot > 0.9995f) {
            // Linear interpolation if quaternions are almost identical
            val rx = q1x + t * (q2xAdjusted - q1x)
            val ry = q1y + t * (q2yAdjusted - q1y)
            val rz = q1z + t * (q2zAdjusted - q1z)
            val rw = q1w + t * (q2wAdjusted - q1w)
            val len = sqrt(rx * rx + ry * ry + rz * rz + rw * rw).coerceAtLeast(1e-6f)
            return floatArrayOf(rx / len, ry / len, rz / len, rw / len)
        }

        val theta0 = acos(dot.coerceIn(-1f, 1f))
        val theta = theta0 * t
        val sinTheta0 = sin(theta0)
        val sinTheta = sin(theta)

        val s0 = cos(theta) - dot * sinTheta / sinTheta0
        val s1 = sinTheta / sinTheta0

        val rx = (s0 * q1x) + (s1 * q2xAdjusted)
        val ry = (s0 * q1y) + (s1 * q2yAdjusted)
        val rz = (s0 * q1z) + (s1 * q2zAdjusted)
        val rw = (s0 * q1w) + (s1 * q2wAdjusted)
        return floatArrayOf(rx, ry, rz, rw)
    }
}

data class FilteredPose(
    val x: Float,
    val y: Float,
    val z: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float
)
