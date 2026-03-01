package com.worldofflips.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat

/** PopButton - カートゥーン風3Dボタン */
class PopButton
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        View(context, attrs, defStyleAttr) {

    var text: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var buttonColor: Int = Color.parseColor("#F06292")
        set(value) {
            field = value
            buttonPaint.color = value
            invalidate()
        }

    var shadowColor: Int = Color.parseColor("#C2185B")
        set(value) {
            field = value
            shadowPaint.color = value
            invalidate()
        }

    var onPressedListener: (() -> Unit)? = null
    var textScale: Float = 1.0f

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
            }
    private val textShadowPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#40000000")
                textAlign = Paint.Align.CENTER
            }

    private val buttonRect = RectF()
    private val shadowRect = RectF()
    private var isPressed = false
    private val shadowOffset = 6f * resources.displayMetrics.density
    private val cornerRadius = 30f * resources.displayMetrics.density

    init {
        // デフォルトフォントを設定
        try {
            val tf = ResourcesCompat.getFont(context, R.font.yusei_magic)
            textPaint.typeface = tf
            textShadowPaint.typeface = tf
        } catch (e: Exception) {}

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.PopButton)
            text = typedArray.getString(R.styleable.PopButton_popButtonText) ?: ""
            buttonColor = typedArray.getColor(R.styleable.PopButton_popButtonColor, buttonColor)
            shadowColor = typedArray.getColor(R.styleable.PopButton_popShadowColor, shadowColor)
            textScale = typedArray.getFloat(R.styleable.PopButton_popTextScale, 1.0f)
            typedArray.recycle()
        }
        buttonPaint.color = buttonColor
        shadowPaint.color = shadowColor
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (260 * resources.displayMetrics.density).toInt()
        val desiredHeight = (70 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 左右にマージンを持たせて回転による欠けを防止
        val padding = 10f * resources.displayMetrics.density
        val w = width.toFloat() - padding * 2
        val h = height.toFloat()
        val buttonHeight = h - shadowOffset

        canvas.save()
        canvas.translate(padding, 0f)

        if (isPressed) {
            buttonRect.set(0f, shadowOffset, w, h)
            shadowRect.set(0f, shadowOffset, w, h)
        } else {
            shadowRect.set(0f, shadowOffset, w, h)
            buttonRect.set(0f, 0f, w, buttonHeight)
        }

        if (!isPressed) {
            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
        }
        canvas.drawRoundRect(buttonRect, cornerRadius, cornerRadius, buttonPaint)

        // テキストサイズを計算
        val textSize = buttonHeight * 0.45f * textScale
        textPaint.textSize = textSize
        textShadowPaint.textSize = textSize

        val textX = w / 2
        val centerY = if (isPressed) shadowOffset + buttonHeight / 2 else buttonHeight / 2
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2

        val ts = 2f * resources.displayMetrics.density
        canvas.drawText(text, textX + ts, textY + ts, textShadowPaint)
        canvas.drawText(text, textX, textY, textPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isPressed) {
                    isPressed = false
                    invalidate()
                    if (event.x >= 0 && event.x <= width && event.y >= 0 && event.y <= height) {
                        performClick()
                        onPressedListener?.invoke()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
