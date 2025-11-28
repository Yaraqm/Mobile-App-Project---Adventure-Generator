package com.example.mobileproject.fragments

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.mobileproject.R

class LuckyWheelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 45f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 2f, 2f, Color.argb(128, 0, 0, 0))
    }

    private val GLOW_STROKE_WIDTH = 30f // Define the width of the glow outline

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = GLOW_STROKE_WIDTH // Set the width of the stroke
        color = Color.parseColor("#FFFFC5") // Your existing glow color
        maskFilter = BlurMaskFilter(GLOW_STROKE_WIDTH / 2f, BlurMaskFilter.Blur.NORMAL)
    }


    private val baseColors = listOf(
        R.color.md_green_500,
        R.color.md_blue_500,
        R.color.md_red_500,
        R.color.md_orange_500,
        R.color.md_purple_500,
        R.color.md_teal_500,
        R.color.md_brown_500,
        R.color.md_pink_500,
        R.color.md_blue_grey_500,
    )

    private var segments = listOf<String>()
    private var segmentColors = listOf<Int>() 
    private var wheelBounds = RectF()
    private var center = PointF(0f, 0f)
    private var radius = 0f

    private var currentRotation = 0f
    private var winningSegmentIndex = -1

    fun setData(newSegments: List<String>) {
        this.segments = newSegments

        // This ensures the wheel can handle any number of categories.
        val newColors = mutableListOf<Int>()
        for (i in newSegments.indices) {
            // Cycle through the base colors if there are more segments than available colors.
            val colorRes = baseColors[i % baseColors.size]
            newColors.add(ContextCompat.getColor(context, colorRes))
        }
        this.segmentColors = newColors

        // Log for debugging to confirm the wheel receives the correct data.
        Log.d("LuckyWheelView", "Received ${newSegments.size} segments: $newSegments")
        Log.d("LuckyWheelView", "Generated ${segmentColors.size} colors.")

        winningSegmentIndex = -1 // Reset winner when data changes
        invalidate() // Tell the view to redraw itself with the new data
    }

    fun getSegmentCount(): Int {
        return segments.size
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val viewSize = w.coerceAtMost(h)
        // Adjust radius to account for the glow stroke width, so the glow is fully visible
        radius = (viewSize / 2f) * 0.9f
        center = PointF(w / 2f, h / 2f)
        wheelBounds = RectF(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty() || segmentColors.isEmpty()) return

        canvas.save()
        canvas.rotate(currentRotation, center.x, center.y)

        val angle = 360f / segments.size

        for (i in segments.indices) {
            segmentPaint.color = segmentColors[i]
            val startAngle = i * angle
            canvas.drawArc(wheelBounds, startAngle, angle, true, segmentPaint)

            val path = Path()
            path.addArc(wheelBounds, startAngle, angle)
            val text = segments[i]
            val textVerticalOffset = radius * 0.25f
            canvas.drawTextOnPath(text, path, 0f, textVerticalOffset, textPaint)
        }

        if (winningSegmentIndex != -1) {
            val segmentIndex = winningSegmentIndex
            val startAngle = segmentIndex * angle

            val halfStroke = GLOW_STROKE_WIDTH / 2f
            val glowBounds = RectF(
                wheelBounds.left + halfStroke,
                wheelBounds.top + halfStroke,
                wheelBounds.right - halfStroke,
                wheelBounds.bottom - halfStroke
            )

            canvas.drawArc(glowBounds, startAngle, angle, true, glowPaint)
        }

        canvas.restore()
    }


    fun rotateWheelTo(targetIndex: Int, onRotationEnd: () -> Unit) {
        if (segments.isEmpty() || targetIndex < 0 || targetIndex >= segments.size) {
            onRotationEnd()
            return
        }

        val segmentDegrees = 360f / segments.size
        val targetSegmentCenterAngle = 270f - (targetIndex * segmentDegrees) - (segmentDegrees / 2)
        val extraSpins = 360f * 5
        val randomOffset = (Math.random() * (segmentDegrees * 0.8) - (segmentDegrees * 0.4)).toFloat()
        val finalTargetRotation = extraSpins + targetSegmentCenterAngle + randomOffset

        val animator = ValueAnimator.ofFloat(currentRotation, finalTargetRotation).apply {
            duration = 4000
            interpolator = DecelerateInterpolator()

            addUpdateListener { animation ->
                currentRotation = animation.animatedValue as Float
                invalidate()
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animator: Animator) {
                    winningSegmentIndex = -1
                }

                override fun onAnimationEnd(animator: Animator) {
                    currentRotation %= 360
                    winningSegmentIndex = targetIndex
                    invalidate()
                    onRotationEnd()
                }

                override fun onAnimationCancel(animator: Animator) {}
                override fun onAnimationRepeat(animator: Animator) {}
            })
        }
        animator.start()
    }
}



