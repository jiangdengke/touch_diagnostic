package com.company.touchdiagnostic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class TouchTestView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class TouchStats(
        val activePointers: Int,
        val totalEvents: Long,
        val finishedStrokes: Long,
        val visitedCells: Int,
        val totalCells: Int,
        val lastX: Float,
        val lastY: Float,
        val lastAction: String,
        val maxJumpDp: Float,
        val gridEnabled: Boolean
    ) {
        val coveragePercent: Int
            get() = if (totalCells == 0) 0 else ((visitedCells * 100f) / totalCells).roundToInt()
    }

    private data class Stroke(
        val color: Int,
        val path: Path = Path(),
        var lastX: Float = 0f,
        var lastY: Float = 0f
    )

    companion object {
        private const val MAX_STORED_STROKES = 96
        private val BACKGROUND_COLOR = Color.parseColor("#F4F7FB")
        private val GRID_COLOR = Color.parseColor("#B7C2D0")
        private val VISITED_COLOR = Color.parseColor("#D9E8FF")
        private val BORDER_COLOR = Color.parseColor("#2B4C7E")
        private val POINTER_COLORS = intArrayOf(
            Color.parseColor("#E63946"),
            Color.parseColor("#FF8C42"),
            Color.parseColor("#1D3557"),
            Color.parseColor("#2A9D8F"),
            Color.parseColor("#8338EC"),
            Color.parseColor("#FF006E")
        )
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GRID_COLOR
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f).toFloat()
    }
    private val visitedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = VISITED_COLOR
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BORDER_COLOR
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f).toFloat()
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dpToPx(5f).toFloat()
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val helperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BORDER_COLOR
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f).toFloat()
        alpha = 90
    }

    private val activeStrokes = mutableMapOf<Int, Stroke>()
    private val finishedStrokes = ArrayList<Stroke>()

    private var visitedCells = BooleanArray(0)
    private var columns = 0
    private var rows = 0
    private var cellSizePx = 0
    private var visitedCount = 0
    private var totalEvents = 0L
    private var strokeCount = 0L
    private var lastX = -1f
    private var lastY = -1f
    private var maxJumpPx = 0f
    private var lastAction = "IDLE"

    var gridEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
            notifyStatsChanged()
        }

    var statsListener: ((TouchStats) -> Unit)? = null
        set(value) {
            field = value
            post(::notifyStatsChanged)
        }

    init {
        isFocusable = true
        isClickable = true
    }

    fun clearCanvas() {
        activeStrokes.clear()
        finishedStrokes.clear()
        visitedCount = 0
        visitedCells.fill(false)
        strokeCount = 0L
        totalEvents = 0L
        maxJumpPx = 0f
        lastX = -1f
        lastY = -1f
        lastAction = "CLEARED"
        invalidate()
        notifyStatsChanged()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) {
            return
        }
        cellSizePx = max(dpToPx(48f), 1)
        columns = ceil(w.toDouble() / cellSizePx.toDouble()).toInt()
        rows = ceil(h.toDouble() / cellSizePx.toDouble()).toInt()
        visitedCells = BooleanArray(columns * rows)
        clearCanvas()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(BACKGROUND_COLOR)
        drawCoverage(canvas)
        drawGrid(canvas)
        drawFinishedStrokes(canvas)
        drawActiveStrokes(canvas)
        drawFrame(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        totalEvents++

        val actionMasked = event.actionMasked
        val actionIndex = event.actionIndex
        lastAction = actionToLabel(actionMasked)
        lastX = event.getX(actionIndex)
        lastY = event.getY(actionIndex)

        when (actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> startStroke(
                event.getPointerId(actionIndex),
                event.getX(actionIndex),
                event.getY(actionIndex)
            )

            MotionEvent.ACTION_MOVE -> {
                for (pointerIndex in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(pointerIndex)
                    val stroke = activeStrokes[pointerId]
                        ?: startStroke(pointerId, event.getX(pointerIndex), event.getY(pointerIndex))
                    appendHistoricalPoints(stroke, event, pointerIndex)
                    appendPoint(stroke, event.getX(pointerIndex), event.getY(pointerIndex))
                }
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP -> finishStroke(
                event.getPointerId(actionIndex),
                event.getX(actionIndex),
                event.getY(actionIndex)
            )

            MotionEvent.ACTION_CANCEL -> {
                activeStrokes.clear()
                lastAction = "CANCEL"
            }
        }

        invalidate()
        notifyStatsChanged()
        return true
    }

    private fun drawCoverage(canvas: Canvas) {
        if (!gridEnabled || visitedCells.isEmpty()) {
            return
        }
        for (index in visitedCells.indices) {
            if (!visitedCells[index]) {
                continue
            }
            val column = index % columns
            val row = index / columns
            val left = column * cellSizePx.toFloat()
            val top = row * cellSizePx.toFloat()
            canvas.drawRect(left, top, left + cellSizePx, top + cellSizePx, visitedPaint)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        if (!gridEnabled || columns == 0 || rows == 0) {
            return
        }
        for (column in 0..columns) {
            val x = min(column * cellSizePx.toFloat(), width.toFloat())
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }
        for (row in 0..rows) {
            val y = min(row * cellSizePx.toFloat(), height.toFloat())
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
    }

    private fun drawFinishedStrokes(canvas: Canvas) {
        for (stroke in finishedStrokes) {
            pathPaint.color = stroke.color
            pathPaint.alpha = 170
            canvas.drawPath(stroke.path, pathPaint)
        }
    }

    private fun drawActiveStrokes(canvas: Canvas) {
        for (stroke in activeStrokes.values) {
            pathPaint.color = stroke.color
            pathPaint.alpha = 255
            canvas.drawPath(stroke.path, pathPaint)

            pointPaint.color = stroke.color
            canvas.drawCircle(stroke.lastX, stroke.lastY, dpToPx(10f).toFloat(), pointPaint)
            canvas.drawCircle(stroke.lastX, stroke.lastY, dpToPx(18f).toFloat(), helperPaint)
        }
    }

    private fun drawFrame(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
    }

    private fun startStroke(pointerId: Int, x: Float, y: Float): Stroke {
        val stroke = Stroke(colorForPointer(pointerId))
        stroke.path.moveTo(x, y)
        stroke.lastX = x
        stroke.lastY = y
        activeStrokes[pointerId] = stroke
        strokeCount++
        markVisited(x, y)
        return stroke
    }

    private fun finishStroke(pointerId: Int, x: Float, y: Float) {
        val stroke = activeStrokes[pointerId] ?: startStroke(pointerId, x, y)
        appendPoint(stroke, x, y)
        activeStrokes.remove(pointerId)
        finishedStrokes.add(stroke)
        if (finishedStrokes.size > MAX_STORED_STROKES) {
            finishedStrokes.removeAt(0)
        }
    }

    private fun appendHistoricalPoints(stroke: Stroke, event: MotionEvent, pointerIndex: Int) {
        for (historyIndex in 0 until event.historySize) {
            appendPoint(
                stroke,
                event.getHistoricalX(pointerIndex, historyIndex),
                event.getHistoricalY(pointerIndex, historyIndex)
            )
        }
    }

    private fun appendPoint(stroke: Stroke, x: Float, y: Float) {
        val dx = x - stroke.lastX
        val dy = y - stroke.lastY
        val jumpPx = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (jumpPx > maxJumpPx) {
            maxJumpPx = jumpPx
        }
        stroke.path.lineTo(x, y)
        stroke.lastX = x
        stroke.lastY = y
        markVisited(x, y)
    }

    private fun markVisited(x: Float, y: Float) {
        if (columns == 0 || rows == 0) {
            return
        }
        val column = clamp((x / cellSizePx).toInt(), 0, columns - 1)
        val row = clamp((y / cellSizePx).toInt(), 0, rows - 1)
        val index = row * columns + column
        if (!visitedCells[index]) {
            visitedCells[index] = true
            visitedCount++
        }
    }

    private fun notifyStatsChanged() {
        statsListener?.invoke(
            TouchStats(
                activeStrokes.size,
                totalEvents,
                strokeCount,
                visitedCount,
                visitedCells.size,
                lastX,
                lastY,
                lastAction,
                pxToDp(maxJumpPx),
                gridEnabled
            )
        )
    }

    private fun colorForPointer(pointerId: Int): Int {
        return POINTER_COLORS[abs(pointerId) % POINTER_COLORS.size]
    }

    private fun actionToLabel(actionMasked: Int): String {
        return when (actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
            MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> "ACTION_$actionMasked"
        }
    }

    private fun clamp(value: Int, minValue: Int, maxValue: Int): Int {
        return max(minValue, min(value, maxValue))
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    private fun pxToDp(px: Float): Float {
        return px / resources.displayMetrics.density
    }
}
