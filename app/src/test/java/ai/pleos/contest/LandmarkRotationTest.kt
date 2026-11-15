package ai.pleos.contest

import ai.pleos.contest.domain.*
import org.junit.Assert.*
import org.junit.Test

class LandmarkRotationTest {
    @Test fun both_axes_can_pass_vertical_poles_and_complete_repeated_turns() {
        assertEquals(108f, rotateDegrees(60f, 48f), .001f)
        assertEquals(270f, rotateDegrees(0f, -90f), .001f)
        assertEquals(25f, rotateDegrees(25f, 360f * 10), .001f)
        val v = Vertex(.4f, 1.2f, -.7f)
        for (yaw in listOf(0f, 90f, 180f, 270f, 360f)) {
            for (pitch in listOf(0f, 90f, 180f, 270f, 360f)) {
                val r = rotateVertex(v, yaw, pitch)
                assertEquals(v.x*v.x + v.y*v.y + v.z*v.z, r.x*r.x + r.y*r.y + r.z*r.z, .001f)
            }
        }
        val fullTurn = rotateVertex(v, 360f, 360f)
        assertEquals(v.x, fullTurn.x, .001f)
        assertEquals(v.y, fullTurn.y, .001f)
        assertEquals(v.z, fullTurn.z, .001f)
    }
}
