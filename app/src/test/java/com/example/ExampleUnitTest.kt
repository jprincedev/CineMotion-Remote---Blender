package com.example

import com.example.model.CameraPose
import com.example.model.PoseSample
import com.example.model.TakeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun cameraPose_computesCorrectRadiusAndAngles() {
        val pose = CameraPose(
            x = 3f, y = 4f, z = 0f,
            qx = 0f, qy = 0f, qz = 0f, qw = 1f,
            isAnchored = false
        )
        assertEquals(5f, pose.radius, 0.001f)
        assertEquals(0f, pose.rollDegrees, 0.01f)
    }

    @Test
    fun cameraPose_computesAnchorDistanceAndOrbit() {
        // Camera at origin (0, 0, 0), anchor at (0, 0, -2)
        val pose = CameraPose(
            x = 0f, y = 0f, z = 0f,
            qx = 0f, qy = 0f, qz = 0f, qw = 1f,
            anchorX = 0f, anchorY = 0f, anchorZ = -2f,
            isAnchored = true
        )
        assertEquals(2f, pose.radius, 0.001f)
        assertEquals(2f, pose.radiusToAnchor, 0.001f)
    }

    @Test
    fun takeEntity_sampleEncodingAndDecoding() {
        val samples = listOf(
            PoseSample(t = 0L, pos = listOf(0.1f, 0.2f, 0.3f), quat = listOf(0f, 0f, 0f, 1f)),
            PoseSample(t = 16L, pos = listOf(0.15f, 0.25f, 0.35f), quat = listOf(0f, 0.1f, 0f, 0.99f))
        )
        val json = TakeEntity.encodeSamples(samples)
        val entity = TakeEntity(
            name = "Take 01",
            durationMs = 1000L,
            targetName = "Character_01",
            lensMm = 35,
            timestamp = 123456789L,
            sampleCount = samples.size,
            samplesJson = json
        )

        val parsed = entity.parseSamples()
        assertEquals(2, parsed.size)
        assertEquals(0.1f, parsed[0].pos[0], 0.001f)
        assertEquals(0.99f, parsed[1].quat[3], 0.01f)
    }
}
