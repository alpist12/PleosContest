package ai.pleos.contest.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import ai.pleos.contest.domain.landmarkGeometry
import ai.pleos.contest.domain.rotateVertex
import ai.pleos.contest.domain.rotateDegrees
import kotlin.math.abs
import kotlin.math.min

/** Perspective projection with depth-sorted faces. Renders only on user interaction. */
class LandmarkView(context: Context) : View(context) {
    var kind = "windmill"
        set(value) { if (field != value) { field = value; faces = landmarkGeometry(value); invalidate() } }
    var interactionAllowed = true
    var yaw = 25f
    var pitch = -12f
    var zoom = 1f
    var onPoseChanged: ((Float, Float, Float) -> Unit)? = null
    private var faces = landmarkGeometry(kind)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var previousX = 0f
    private var previousY = 0f
    private var previousPointerCount = 0
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(.65f, 1.8f)
            changed(); return true
        }
    })

    init {
        contentDescription = "3D 탐색 모델. 모형 위를 한 손가락으로 끌어 상하좌우 360도 회전, 두 손가락으로 확대와 축소"
        isClickable = true; isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun changed() { onPoseChanged?.invoke(yaw, pitch, zoom); invalidate() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactionAllowed) return false
        scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> if (!scale.isInProgress && event.pointerCount == 1 && previousPointerCount == 1) {
                val sensitivity = .4f / resources.displayMetrics.density
                yaw = rotateDegrees(yaw, (event.x - previousX) * sensitivity)
                pitch = rotateDegrees(pitch, (event.y - previousY) * sensitivity)
                changed()
            }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); performClick() }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        previousX = event.x; previousY = event.y
        previousPointerCount = if (event.actionMasked in setOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) 0 else event.pointerCount
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawColor(0xff122633.toInt())
        val unit = min(width, height) * .24f * zoom
        faces.map { face -> face to face.points.map { rotateVertex(it, yaw, pitch) } }
            .sortedBy { (_, points) -> points.map { it.z }.average() }.forEach { (face, points) ->
                path.reset()
                points.forEachIndexed { index, v ->
                    val perspective = 5f / (5f - v.z)
                    val x = width / 2f + v.x * unit * perspective
                    val y = height * .54f - v.y * unit * perspective
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val a = points[1]; val b = points[0]; val c = points[2]
                val nx = (a.y-b.y)*(c.z-b.z)-(a.z-b.z)*(c.y-b.y)
                val ny = (a.z-b.z)*(c.x-b.x)-(a.x-b.x)*(c.z-b.z)
                val nz = (a.x-b.x)*(c.y-b.y)-(a.y-b.y)*(c.x-b.x)
                val magnitude = kotlin.math.sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.001f)
                val light = .58f + .42f * abs((nx*.3f + ny*.7f + nz*.6f) / magnitude).coerceIn(0f, 1f)
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb((Color.red(face.color)*light).toInt(), (Color.green(face.color)*light).toInt(), (Color.blue(face.color)*light).toInt())
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0x24446677
                canvas.drawPath(path, paint)
            }
        canvas.restore()
    }
}
