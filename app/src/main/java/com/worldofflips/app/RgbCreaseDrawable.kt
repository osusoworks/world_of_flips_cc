package com.worldofflips.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.random.Random

class RgbCreaseDrawable(private val screenWidth: Int, private val screenHeight: Int) : Drawable() {

    private val lines = mutableListOf<GlitchLine>()

    // Line definition
    private data class GlitchLine(
        val x: Float,
        val width: Float,
        val color: Int,
        val baseAlpha: Int
    )

    init {
        generateLines()
    }

    private fun generateLines() {
        lines.clear()
        val lineCount = Random.nextInt(10, 16) // 10-15 lines

        for (i in 0 until lineCount) {
            // 4. Configuration: Slightly concentrated in center (near fold)
            val x = if (Random.nextBoolean()) {
                 Random.nextFloat() * screenWidth
            } else {
                // Center +/- 15% of width
                val center = screenWidth / 2f
                val range = screenWidth * 0.15f
                center + (Random.nextFloat() - 0.5f) * 2 * range
            }

            // 3. Thickness
            val randThickness = Random.nextFloat()
            val width = when {
                randThickness < 0.70 -> Random.nextDouble(1.0, 3.0).toFloat() // 1-2px (approx)
                randThickness < 0.95 -> Random.nextDouble(3.0, 6.0).toFloat() // 3-5px
                else -> Random.nextDouble(6.0, 9.0).toFloat() // 6-8px
            }

            // 2. Color
            val color = pickRandomColor()

            // 5. Opacity
            val alpha = Random.nextInt((255 * 0.60).toInt(), (255 * 0.85).toInt())

            lines.add(GlitchLine(x, width, color, alpha))
        }

        // Add a few clustered lines (copy a line and shift slightly)
        val extraLines = mutableListOf<GlitchLine>()
        val clusterSourceCount = 2
        for (i in 0 until clusterSourceCount) {
            if (lines.isNotEmpty()) {
                val source = lines.random()
                val offset = Random.nextDouble(2.0, 6.0).toFloat() * (if (Random.nextBoolean()) 1 else -1)
                extraLines.add(source.copy(x = source.x + offset))
            }
        }
        lines.addAll(extraLines)
    }

    private fun pickRandomColor(): Int {
        val colors = listOf(
            Color.RED, Color.GREEN, Color.BLUE, // RGB
            Color.parseColor("#8B00FF"), // Purple
            Color.parseColor("#FF69B4"), // Pink
            Color.CYAN,
            Color.YELLOW,
            Color.WHITE,
            Color.BLACK
        )
        // White/Black are "minority", so let's reduce their chance
        val r = Random.nextFloat()
        if (r < 0.1) { // 10% chance for white/black
            return if (Random.nextBoolean()) Color.WHITE else Color.BLACK
        }
        return colors[Random.nextInt(0, colors.size - 2)] 
        
        // Simplified fallback from previous logic kept for consistency if needed, 
        // but the above logic is effectively picking from the list except last 2.
        // Let's just use the clearer logic below to match previous behavior exactly.
        /*
        val mainColors = listOf(
            Color.RED, Color.GREEN, Color.BLUE,
            Color.parseColor("#8B00FF"),
            Color.parseColor("#FF69B4"),
            Color.CYAN,
            Color.YELLOW
        )
        return if (Random.nextFloat() < 0.15) {
             if (Random.nextBoolean()) Color.WHITE else Color.BLACK
        } else {
            mainColors.random()
        }
        */
    }

    override fun draw(canvas: Canvas) {
        val paint = Paint()
        paint.isAntiAlias = true
        
        // Draw each line
        for (line in lines) {
            paint.color = line.color
            paint.strokeWidth = line.width
            paint.alpha = line.baseAlpha
            
            // Draw vertical line top to bottom
            canvas.drawLine(line.x, 0f, line.x, screenHeight.toFloat(), paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        // Not used
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Not implemented
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return screenWidth
    }

    override fun getIntrinsicHeight(): Int {
        return screenHeight
    }
}
