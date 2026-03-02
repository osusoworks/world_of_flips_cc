package com.worldofflips.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.Drawable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class CrackedScreenDrawable(private val screenWidth: Int, private val screenHeight: Int) : Drawable() {

    // 衝撃点（画面やや上・中央右寄り）
    private val impactX = screenWidth * 0.54f
    private val impactY = screenHeight * 0.40f

    private val rng = Random(7)

    // 事前生成データ
    private val mainCrackPaths = mutableListOf<Path>()
    private val mainCrackWidths = mutableListOf<Float>()
    private val ringPaths = mutableListOf<Path>()
    private val branchLines = mutableListOf<FloatArray>()   // x1, y1, x2, y2
    private val glassLines = mutableListOf<FloatArray>()    // 中心付近の細かい破片

    // ペイント
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val centerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.FILL
    }

    init {
        generateMainCracks()
        generateRings()
        generateBranches()
        generateGlassTexture()
    }

    // 放射状亀裂を生成（全方向に16〜18本）
    private fun generateMainCracks() {
        // 各方向に均等 + ランダムオフセット
        val baseCount = 17
        for (i in 0 until baseCount) {
            val baseAngle = (i.toFloat() / baseCount) * 360f
            val angleOffset = (rng.nextFloat() - 0.5f) * (360f / baseCount * 0.6f)
            val angleDeg = baseAngle + angleOffset
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

            val path = buildJaggedCrack(angleRad)
            mainCrackPaths.add(path)
            // 幅はランダム（2.5〜5.5）
            mainCrackWidths.add(2.5f + rng.nextFloat() * 3f)
        }
    }

    // 衝撃点から外に向かうジグザグ亀裂パス
    private fun buildJaggedCrack(startAngle: Float): Path {
        val path = Path()
        var x = impactX
        var y = impactY
        var angle = startAngle

        path.moveTo(x, y)

        val maxDist = maxOf(screenWidth, screenHeight) * 1.3f
        var dist = 0f
        val stepMin = 40f
        val stepMax = 90f

        while (dist < maxDist) {
            val step = stepMin + rng.nextFloat() * (stepMax - stepMin)
            // 角度にブレを加えてジグザグ感
            angle += (rng.nextFloat() - 0.5f) * 0.18f

            x += cos(angle) * step
            y += sin(angle) * step
            path.lineTo(x, y)
            dist += step

            if (x < -150 || x > screenWidth + 150 || y < -150 || y > screenHeight + 150) break
        }
        return path
    }

    // 同心円状の亀裂リングを3本生成
    private fun generateRings() {
        for (radius in listOf(70f, 175f, 330f)) {
            val path = Path()
            val segmentCount = 18 + rng.nextInt(6)

            for (i in 0 until segmentCount) {
                if (rng.nextFloat() < 0.22f) continue  // 22%確率で欠け（リアル感）

                val startAngleDeg = (i.toFloat() / segmentCount) * 360f
                val endAngleDeg = ((i + 1).toFloat() / segmentCount) * 360f

                val startRad = Math.toRadians(startAngleDeg.toDouble())
                val endRad = Math.toRadians(endAngleDeg.toDouble())
                val midRad = (startRad + endRad) / 2

                // 半径にランダム揺らぎ
                val r1 = radius * (0.85f + rng.nextFloat() * 0.3f)
                val r2 = radius * (0.85f + rng.nextFloat() * 0.3f)
                val rMid = radius * (0.85f + rng.nextFloat() * 0.3f)

                val sx = impactX + cos(startRad).toFloat() * r1
                val sy = impactY + sin(startRad).toFloat() * r1
                val mx = impactX + cos(midRad).toFloat() * rMid
                val my = impactY + sin(midRad).toFloat() * rMid
                val ex = impactX + cos(endRad).toFloat() * r2
                val ey = impactY + sin(endRad).toFloat() * r2

                path.moveTo(sx, sy)
                path.lineTo(mx, my)
                path.lineTo(ex, ey)
            }
            ringPaths.add(path)
        }
    }

    // 各主亀裂に枝亀裂を生成
    private fun generateBranches() {
        for ((pathIdx, crackPath) in mainCrackPaths.withIndex()) {
            // パスから点列を近似取得
            val measure = android.graphics.PathMeasure(crackPath, false)
            val totalLen = measure.length
            if (totalLen < 100f) continue

            val branchPositions = listOf(0.3f, 0.55f, 0.75f)
            for (frac in branchPositions) {
                if (rng.nextFloat() < 0.25f) continue  // たまに省略

                val pos = FloatArray(2)
                val tan = FloatArray(2)
                measure.getPosTan(totalLen * frac, pos, tan)

                val mainAngle = atan2(tan[1].toDouble(), tan[0].toDouble()).toFloat()
                val side = if (rng.nextBoolean()) 1f else -1f
                val branchAngle = mainAngle + side * (0.45f + rng.nextFloat() * 0.3f)
                val branchLen = 50f + rng.nextFloat() * 140f

                branchLines.add(floatArrayOf(
                    pos[0], pos[1],
                    pos[0] + cos(branchAngle) * branchLen,
                    pos[1] + sin(branchAngle) * branchLen
                ))

                // 枝の枝（小枝）
                if (rng.nextFloat() < 0.5f) {
                    val subAngle = branchAngle + (rng.nextFloat() - 0.5f) * 0.5f
                    val subLen = 25f + rng.nextFloat() * 60f
                    val subStartFrac = 0.4f + rng.nextFloat() * 0.4f
                    val subStartX = pos[0] + cos(branchAngle) * branchLen * subStartFrac
                    val subStartY = pos[1] + sin(branchAngle) * branchLen * subStartFrac
                    branchLines.add(floatArrayOf(
                        subStartX, subStartY,
                        subStartX + cos(subAngle) * subLen,
                        subStartY + sin(subAngle) * subLen
                    ))
                }
            }
        }
    }

    // 衝撃中心付近の細かいガラス破片ライン
    private fun generateGlassTexture() {
        for (i in 0..40) {
            val angle = rng.nextFloat() * 360.0
            val angleRad = Math.toRadians(angle)
            val innerDist = 20f + rng.nextFloat() * 35f
            val outerDist = innerDist + 10f + rng.nextFloat() * 30f
            glassLines.add(floatArrayOf(
                impactX + cos(angleRad).toFloat() * innerDist,
                impactY + sin(angleRad).toFloat() * innerDist,
                impactX + cos(angleRad).toFloat() * outerDist,
                impactY + sin(angleRad).toFloat() * outerDist
            ))
        }
    }

    override fun draw(canvas: Canvas) {
        // 1. 全体に薄暗いオーバーレイ
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), overlayPaint)

        // 2. 主亀裂の影（黒縁で深さを表現）
        for ((i, path) in mainCrackPaths.withIndex()) {
            shadowPaint.strokeWidth = mainCrackWidths[i] * 2.8f
            canvas.drawPath(path, shadowPaint)
        }

        // 3. 同心円リング（影）
        shadowPaint.strokeWidth = 5f
        for (path in ringPaths) canvas.drawPath(path, shadowPaint)

        // 4. 主亀裂（白）
        for ((i, path) in mainCrackPaths.withIndex()) {
            crackPaint.strokeWidth = mainCrackWidths[i]
            canvas.drawPath(path, crackPaint)
        }

        // 5. 同心円リング（白）
        for ((i, path) in ringPaths.withIndex()) {
            ringPaint.strokeWidth = 2.2f - i * 0.5f
            canvas.drawPath(path, ringPaint)
        }

        // 6. 枝亀裂
        for (line in branchLines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], branchPaint)
        }

        // 7. 衝撃中心：暗い潰れたエリア
        canvas.drawOval(
            impactX - 48f, impactY - 48f,
            impactX + 48f, impactY + 48f,
            centerPaint
        )

        // 8. 中心のガラス細片
        for (line in glassLines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], glassPaint)
        }

        // 9. 衝撃点の白い光（光の反射）
        canvas.drawOval(
            impactX - 16f, impactY - 16f,
            impactX + 16f, impactY + 16f,
            centerHighlightPaint
        )
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = screenWidth
    override fun getIntrinsicHeight(): Int = screenHeight
}
