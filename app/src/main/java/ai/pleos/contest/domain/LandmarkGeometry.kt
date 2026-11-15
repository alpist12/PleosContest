package ai.pleos.contest.domain

import kotlin.math.cos
import kotlin.math.sin

data class Vertex(val x: Float, val y: Float, val z: Float)
data class ModelFace(val points: List<Vertex>, val color: Int)

fun rotateDegrees(angle: Float, delta: Float): Float = ((angle + delta) % 360f + 360f) % 360f

/** Original low-poly teaching models, independent of external assets or map services. */
fun landmarkGeometry(kind: String): List<ModelFace> {
    require(kind in setOf("windmill", "tower"))
    val faces = mutableListOf<ModelFace>()
    fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: Int, angle: Float = 0f) {
        val a = Math.toRadians(angle.toDouble())
        val vertices = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).flatMap { (sx, sy) ->
            listOf(-1, 1).map { sz ->
                val px = sx * w / 2; val py = sy * h / 2
                Vertex(x + (px * cos(a) - py * sin(a)).toFloat(), y + (px * sin(a) + py * cos(a)).toFloat(), z + sz * d / 2)
            }
        }
        listOf(listOf(0, 2, 4, 6), listOf(1, 7, 5, 3), listOf(0, 1, 3, 2),
            listOf(2, 3, 5, 4), listOf(4, 5, 7, 6), listOf(6, 7, 1, 0)).forEach { indices ->
            faces += ModelFace(indices.map { vertices[it] }, color)
        }
    }
    box(0f, -1.25f, 0f, 2.8f, .2f, 2.1f, 0xff447865.toInt())
    if (kind == "windmill") {
        box(0f, -.35f, 0f, .2f, 1.8f, .2f, 0xffd8e6ee.toInt())
        box(0f, .65f, .04f, .44f, .27f, .6f, 0xff8ec9e6.toInt())
        for (angle in listOf(0f, 120f, 240f)) {
            val a = Math.toRadians(angle.toDouble())
            box((-.53f * sin(a)).toFloat(), .65f + (.53f * cos(a)).toFloat(), .43f,
                .15f, 1.15f, .08f, 0xfff3f6ed.toInt(), angle)
        }
        box(0f, .65f, .5f, .24f, .24f, .16f, 0xff2f6f94.toInt())
    } else {
        box(0f, -.3f, 0f, .35f, 1.9f, .35f, 0xffd8e6ee.toInt())
        box(0f, .55f, 0f, 1.05f, .4f, 1.05f, 0xff8ec9e6.toInt())
        box(0f, .83f, 0f, 1.18f, .16f, 1.18f, 0xff2f6f94.toInt())
        box(0f, 1.15f, 0f, .08f, .6f, .08f, 0xfff1b76a.toInt())
    }
    return faces
}

fun rotateVertex(v: Vertex, yaw: Float, pitch: Float): Vertex {
    val y = Math.toRadians(yaw.toDouble()); val p = Math.toRadians(pitch.toDouble())
    val x = v.x * cos(y) + v.z * sin(y)
    val z = -v.x * sin(y) + v.z * cos(y)
    return Vertex(x.toFloat(), (v.y * cos(p) - z * sin(p)).toFloat(), (v.y * sin(p) + z * cos(p)).toFloat())
}
