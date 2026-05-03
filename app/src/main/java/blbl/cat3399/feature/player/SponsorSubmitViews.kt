package blbl.cat3399.feature.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import blbl.cat3399.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class SponsorSubmitThumbnail(
    val timeMs: Long,
    val frame: SpriteFrame,
)

class SponsorSubmitThumbnailStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xCC000000.toInt()
        }
    private val dimPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x73000000
        }
    private val selectedHaloPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x3300A1D6
        }
    private val selectedStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(4f)
            color = playerFocusStrokeColor()
        }
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val clipPath = Path()

    private var thumbnails: List<SponsorSubmitThumbnail> = emptyList()
    private var selectedIndex: Int = -1
    private var aspectWidth: Int = 16
    private var aspectHeight: Int = 9

    fun setContentAspectRatio(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (aspectWidth == w && aspectHeight == h) return
        aspectWidth = w
        aspectHeight = h
        invalidate()
    }

    internal fun setThumbnails(items: List<SponsorSubmitThumbnail>, selectedIndex: Int) {
        thumbnails = items
        this.selectedIndex = selectedIndex.takeIf { it in items.indices } ?: -1
        invalidate()
    }

    internal fun clearThumbnails() {
        thumbnails = emptyList()
        selectedIndex = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = thumbnails.size
        if (count <= 0) {
            drawEmpty(canvas)
            return
        }

        val gap = dp(10f)
        val horizontalPadding = dp(6f)
        val verticalPadding = dp(5f)
        val selectedScale = 1.07f
        val contentHeight = (height - verticalPadding * 2f).coerceAtLeast(1f)
        val aspect = aspectWidth.toFloat() / aspectHeight.coerceAtLeast(1).toFloat()
        val itemHeight = contentHeight / selectedScale
        val itemWidth = itemHeight * aspect.coerceAtLeast(0.0001f)
        val top = verticalPadding + ((contentHeight - itemHeight) / 2f).coerceAtLeast(0f)
        val rowWidth = itemWidth * count + gap * (count - 1)
        val selected = selectedIndex.takeIf { it in thumbnails.indices } ?: -1
        val centeredRowLeft =
            if (selected >= 0) {
                width / 2f - (selected * (itemWidth + gap) + itemWidth / 2f)
            } else {
                (width - rowWidth) / 2f
            }
        val rowLeft =
            if (rowWidth <= width - horizontalPadding * 2) {
                (width - rowWidth) / 2f
            } else {
                centeredRowLeft.coerceIn(width - horizontalPadding - rowWidth, horizontalPadding)
            }

        for (i in thumbnails.indices) {
            if (i == selectedIndex) continue
            val left = rowLeft + i * (itemWidth + gap)
            dstRect.set(left, top, left + itemWidth, top + itemHeight)
            drawThumb(canvas, thumbnails[i], dstRect, selected = false)
        }

        if (selected in thumbnails.indices) {
            val baseLeft = rowLeft + selected * (itemWidth + gap)
            val centerX = baseLeft + itemWidth / 2f
            val selectedWidth = (itemWidth * selectedScale).coerceAtMost(width.toFloat())
            val selectedHeight = (itemHeight * selectedScale).coerceAtMost(contentHeight)
            val left = (centerX - selectedWidth / 2f).coerceIn(0f, (width - selectedWidth).coerceAtLeast(0f))
            val selectedTop = verticalPadding + ((contentHeight - selectedHeight) / 2f).coerceAtLeast(0f)
            dstRect.set(left, selectedTop, left + selectedWidth, selectedTop + selectedHeight)
            drawThumb(canvas, thumbnails[selected], dstRect, selected = true)
        }
    }

    private fun drawEmpty(canvas: Canvas) {
        val radius = dp(10f)
        dstRect.set(dp(6f), dp(6f), width - dp(6f), height - dp(6f))
        canvas.drawRoundRect(dstRect, radius, radius, placeholderPaint)
    }

    private fun drawThumb(
        canvas: Canvas,
        item: SponsorSubmitThumbnail,
        rect: RectF,
        selected: Boolean,
    ) {
        val radius = dp(if (selected) 12f else 9f)
        if (selected) {
            val halo = RectF(rect).apply { inset(-dp(5f), -dp(5f)) }
            canvas.drawRoundRect(halo, radius + dp(5f), radius + dp(5f), selectedHaloPaint)
        }
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        val frame = item.frame
        updateDrawSourceRect(frame.srcRect)
        canvas.drawBitmap(frame.spriteSheet, srcRect, rect, bitmapPaint)
        if (!selected) canvas.drawRect(rect, dimPaint)
        canvas.restoreToCount(checkpoint)
        if (selected) {
            val inset = selectedStrokePaint.strokeWidth / 2f
            val strokeRect = RectF(rect).apply { inset(inset, inset) }
            canvas.drawRoundRect(strokeRect, radius, radius, selectedStrokePaint)
        }
    }

    private fun updateDrawSourceRect(cellRect: Rect) {
        srcRect.set(cellRect)
        val sourceWidth = cellRect.width().coerceAtLeast(1)
        val sourceHeight = cellRect.height().coerceAtLeast(1)
        val targetAspect = aspectWidth.toFloat() / aspectHeight.coerceAtLeast(1).toFloat()
        val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
        if (abs(sourceAspect - targetAspect) < 0.001f) return

        if (sourceAspect > targetAspect) {
            val croppedWidth = (sourceHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
            val inset = ((sourceWidth - croppedWidth) / 2).coerceAtLeast(0)
            srcRect.left = cellRect.left + inset
            srcRect.right = srcRect.left + croppedWidth
            return
        }

        val croppedHeight = (sourceWidth / targetAspect.coerceAtLeast(0.0001f)).roundToInt().coerceIn(1, sourceHeight)
        val inset = ((sourceHeight - croppedHeight) / 2).coerceAtLeast(0)
        srcRect.top = cellRect.top + inset
        srcRect.bottom = srcRect.top + croppedHeight
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun playerFocusStrokeColor(): Int = ContextCompat.getColor(context, R.color.blbl_blue)
}

class SponsorSubmitTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x66000000
        }
    private val selectedRangePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.blbl_blue)
            alpha = 150
        }
    private val cursorPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val startPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.blbl_blue)
        }
    private val endPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.blbl_red)
        }
    private val markerStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
            color = 0xFFFFFFFF.toInt()
            strokeJoin = Paint.Join.ROUND
        }
    private val selectedStrokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.4f)
            color = playerFocusStrokeColor()
            strokeJoin = Paint.Join.ROUND
        }
    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
            textSize = dp(13f)
        }
    private val tmpRect = RectF()
    private val markerPath = Path()

    private var durationMs: Long = 0L
    private var cursorMs: Long = 0L
    private var markers: List<SponsorSubmitMarker> = emptyList()
    private var selectedMarkerId: Long? = null
    private var movingMarkerId: Long? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
    }

    internal fun setState(
        durationMs: Long,
        cursorMs: Long,
        markers: List<SponsorSubmitMarker>,
        selectedMarkerId: Long?,
        movingMarkerId: Long?,
    ) {
        this.durationMs = durationMs.coerceAtLeast(0L)
        this.cursorMs = cursorMs.coerceAtLeast(0L)
        this.markers = markers
        this.selectedMarkerId = selectedMarkerId
        this.movingMarkerId = movingMarkerId
        invalidate()
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft + dp(18f)
        val right = width - paddingRight - dp(18f)
        if (right <= left) return

        val centerY = height * 0.52f
        val trackHeight = dp(8f)
        tmpRect.set(left, centerY - trackHeight / 2f, right, centerY + trackHeight / 2f)
        canvas.drawRoundRect(tmpRect, trackHeight / 2f, trackHeight / 2f, trackPaint)

        drawCompleteRanges(canvas, left, right, centerY, trackHeight)
        drawCursor(canvas, left, right, centerY)
        drawMarkers(canvas, left, right, centerY)
    }

    private fun drawCompleteRanges(canvas: Canvas, left: Float, right: Float, centerY: Float, trackHeight: Float) {
        val byPair = markers.groupBy { it.pairId }
        for ((_, pairMarkers) in byPair) {
            val start = pairMarkers.firstOrNull { it.kind == SponsorSubmitMarkerKind.START } ?: continue
            val end = pairMarkers.firstOrNull { it.kind == SponsorSubmitMarkerKind.END } ?: continue
            if (end.timeMs <= start.timeMs) continue
            val x1 = xForTime(start.timeMs, left, right)
            val x2 = xForTime(end.timeMs, left, right)
            tmpRect.set(x1, centerY - trackHeight / 2f, x2, centerY + trackHeight / 2f)
            canvas.drawRoundRect(tmpRect, trackHeight / 2f, trackHeight / 2f, selectedRangePaint)
        }
    }

    private fun drawCursor(canvas: Canvas, left: Float, right: Float, centerY: Float) {
        val x = xForTime(cursorMs, left, right)
        val active = isFocused
        val h = dp(if (active) 36f else 29f)
        val halfWidth = dp(if (active) 2.1f else 1.45f)
        cursorPaint.color = if (active) playerFocusStrokeColor() else 0x99FFFFFF.toInt()
        tmpRect.set(x - halfWidth, centerY - h / 2f, x + halfWidth, centerY + h / 2f)
        canvas.drawRoundRect(tmpRect, halfWidth, halfWidth, cursorPaint)
    }

    private fun drawMarkers(canvas: Canvas, left: Float, right: Float, centerY: Float) {
        for (marker in markers) {
            val highlighted = marker.id == selectedMarkerId || marker.id == movingMarkerId
            val x = xForTime(marker.timeMs, left, right)
            val markerHeight = dp(13f)
            val halfWidth = markerHeight / sqrt(3f)
            val top = centerY - dp(21f)
            buildRoundedTrianglePath(
                path = markerPath,
                tipX = x,
                tipY = top,
                halfWidth = halfWidth,
                height = markerHeight,
                cornerRadius = markerHeight * 0.28f,
            )
            val paint = if (marker.kind == SponsorSubmitMarkerKind.START) startPaint else endPaint
            canvas.drawPath(markerPath, paint)
            canvas.drawPath(markerPath, markerStrokePaint)
            if (highlighted) canvas.drawPath(markerPath, selectedStrokePaint)
            val label = if (marker.kind == SponsorSubmitMarkerKind.START) "始" else "终"
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, x - textWidth / 2f, top + markerHeight + dp(16f), textPaint)
        }
    }

    private fun buildRoundedTrianglePath(
        path: Path,
        tipX: Float,
        tipY: Float,
        halfWidth: Float,
        height: Float,
        cornerRadius: Float,
    ) {
        val tip = PointF(tipX, tipY)
        val left = PointF(tipX - halfWidth, tipY + height)
        val right = PointF(tipX + halfWidth, tipY + height)
        val points = arrayOf(tip, right, left)

        path.reset()
        for (i in points.indices) {
            val current = points[i]
            val prev = points[(i - 1 + points.size) % points.size]
            val next = points[(i + 1) % points.size]
            val start = pointToward(current, prev, cornerRadius)
            val end = pointToward(current, next, cornerRadius)
            if (i == 0) {
                path.moveTo(start.x, start.y)
            } else {
                path.lineTo(start.x, start.y)
            }
            path.quadTo(current.x, current.y, end.x, end.y)
        }
        path.close()
    }

    private fun pointToward(from: PointF, to: PointF, distance: Float): PointF {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
        val ratio = (distance / length).coerceIn(0f, 0.45f)
        return PointF(from.x + dx * ratio, from.y + dy * ratio)
    }

    private fun xForTime(timeMs: Long, left: Float, right: Float): Float {
        val duration = durationMs.coerceAtLeast(1L)
        val fraction = (timeMs.toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
        return left + ((right - left) * fraction).toFloat()
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun playerFocusStrokeColor(): Int = ContextCompat.getColor(context, R.color.blbl_blue)
}
