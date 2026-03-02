package com.worldofflips.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.Drawable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 割れ画面エフェクト描画クラス
 * pattern 0: 中央やや右上（スタンダードクモの巣）
 * pattern 1: 右上コーナー落下
 * pattern 2: 下端中央落下
 * pattern 3: 2点同時衝撃
 * pattern 4: 左上寄り・少ないレイ
 */
class CrackedScreenDrawable(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val pattern: Int = 0
) : Drawable() {

    companion object {
        const val PATTERN_COUNT = 5
    }

    // CrackRay にメソッドを持たせることでスコープ問題を回避
    private data class CrackRay(val points: List<PointF>, val width: Float) {
        fun toPath(): Path {
            val p = Path()
            if (points.isEmpty()) return p
            p.moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { p.lineTo(it.x, it.y) }
            return p
        }
    }

    // ── ペイント ──────────────────────────────────────────────────────────────
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val crackShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val crackHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val impactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glassDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }
    private val branchShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2.8f
        strokeCap = Paint.Cap.ROUND
    }
    private val branchHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // ── 1衝撃点のデータを保持するクラス ──────────────────────────────────────
    private inner class CrackCluster(
        val ix: Float,
        val iy: Float,
        val rayCount: Int,
        seed: Int
    ) {
        private val rng = Random(seed)
        val crackRays    = mutableListOf<CrackRay>()
        val ringPaths    = mutableListOf<Path>()
        val branchLines  = mutableListOf<FloatArray>()
        val shardFills   = mutableListOf<Pair<Path, Int>>()
        val glassTexture = mutableListOf<FloatArray>()

        init {
            generateCrackRays()
            generateShardFills()
            generateRings()
            generateBranches()
            generateGlassTexture()
        }

        private fun generateCrackRays() {
            for (i in 0 until rayCount) {
                val baseAngleDeg = (i.toFloat() / rayCount) * 360f
                val offsetDeg    = (rng.nextFloat() - 0.5f) * (360f / rayCount * 0.5f)
                val angleRad     = Math.toRadians((baseAngleDeg + offsetDeg).toDouble()).toFloat()
                crackRays.add(CrackRay(buildJaggedRay(angleRad), 1.8f + rng.nextFloat() * 3.5f))
            }
            crackRays.sortBy { ray ->
                atan2(
                    (ray.points.last().y - iy).toDouble(),
                    (ray.points.last().x - ix).toDouble()
                ).toFloat()
            }
        }

        private fun buildJaggedRay(startAngle: Float): List<PointF> {
            val pts = mutableListOf<PointF>()
            var x = ix; var y = iy; var angle = startAngle
            pts.add(PointF(x, y))
            var dist = 0f
            val maxDist = maxOf(screenWidth, screenHeight) * 1.3f
            while (dist < maxDist) {
                val step = 30f + rng.nextFloat() * 65f
                angle += (rng.nextFloat() - 0.5f) * 0.22f
                x += cos(angle) * step
                y += sin(angle) * step
                pts.add(PointF(x, y))
                dist += step
                if (x < -150 || x > screenWidth + 150 || y < -150 || y > screenHeight + 150) break
            }
            return pts
        }

        private fun generateShardFills() {
            for (i in crackRays.indices) {
                val rayA = crackRays[i]
                val rayB = crackRays[(i + 1) % crackRays.size]
                val segments = minOf(rayA.points.size, rayB.points.size) - 1
                for (seg in 0 until minOf(segments, 7)) {
                    val progress = seg.toFloat() / 7f
                    val alpha = ((1f - progress) * 170f + 15f).toInt()
                        .let { (it * (0.55f + rng.nextFloat() * 0.45f)).toInt() }
                        .coerceIn(10, 190)
                    val bNext = rayB.points.getOrElse(seg + 1) { rayB.points.last() }
                    val path = Path().apply {
                        moveTo(rayA.points[seg].x, rayA.points[seg].y)
                        lineTo(rayA.points[seg + 1].x, rayA.points[seg + 1].y)
                        lineTo(bNext.x, bNext.y)
                        lineTo(rayB.points[seg].x, rayB.points[seg].y)
                        close()
                    }
                    shardFills.add(Pair(path, alpha))
                }
            }
        }

        private fun generateRings() {
            for (radius in listOf(55f, 140f, 270f, 430f)) {
                val path = Path()
                val segCount = 20 + rng.nextInt(8)
                for (i in 0 until segCount) {
                    if (rng.nextFloat() < 0.22f) continue
                    val a1   = (i.toFloat() / segCount) * 360f
                    val a2   = ((i + 1).toFloat() / segCount) * 360f
                    val mid  = (a1 + a2) / 2f
                    val r1   = radius * (0.82f + rng.nextFloat() * 0.36f)
                    val r2   = radius * (0.82f + rng.nextFloat() * 0.36f)
                    val rMid = radius * (0.78f + rng.nextFloat() * 0.44f)
                    fun toX(a: Double, r: Float) = ix + cos(Math.toRadians(a)).toFloat() * r
                    fun toY(a: Double, r: Float) = iy + sin(Math.toRadians(a)).toFloat() * r
                    path.moveTo(toX(a1.toDouble(), r1), toY(a1.toDouble(), r1))
                    path.lineTo(toX(mid.toDouble(), rMid), toY(mid.toDouble(), rMid))
                    path.lineTo(toX(a2.toDouble(), r2), toY(a2.toDouble(), r2))
                }
                ringPaths.add(path)
            }
        }

        private fun generateBranches() {
            for (ray in crackRays) {
                val measure = PathMeasure(ray.toPath(), false)
                val totalLen = measure.length
                if (totalLen < 80f) continue
                for (frac in listOf(0.28f, 0.48f, 0.67f, 0.82f)) {
                    if (rng.nextFloat() < 0.35f) continue
                    val pos = FloatArray(2); val tan = FloatArray(2)
                    measure.getPosTan(totalLen * frac, pos, tan)
                    val mainAngle   = atan2(tan[1].toDouble(), tan[0].toDouble()).toFloat()
                    val side        = if (rng.nextBoolean()) 1f else -1f
                    val branchAngle = mainAngle + side * (0.42f + rng.nextFloat() * 0.32f)
                    val len         = 35f + rng.nextFloat() * 115f
                    branchLines.add(floatArrayOf(
                        pos[0], pos[1],
                        pos[0] + cos(branchAngle) * len,
                        pos[1] + sin(branchAngle) * len
                    ))
                }
            }
        }

        private fun generateGlassTexture() {
            for (i in 0..55) {
                val angle = rng.nextFloat() * 360.0
                val r1    = 12f + rng.nextFloat() * 40f
                val r2    = r1 + 6f + rng.nextFloat() * 22f
                val rad   = Math.toRadians(angle)
                glassTexture.add(floatArrayOf(
                    ix + cos(rad).toFloat() * r1, iy + sin(rad).toFloat() * r1,
                    ix + cos(rad).toFloat() * r2, iy + sin(rad).toFloat() * r2
                ))
            }
        }
    }

    // ── パターン定義（衝撃位置・レイ数・シード） ─────────────────────────────
    private val clusters: List<CrackCluster> = when (pattern % PATTERN_COUNT) {
        // pattern 0: 中央やや右上 — スタンダードなクモの巣
        0 -> listOf(CrackCluster(screenWidth * 0.54f, screenHeight * 0.40f, 16, 7))
        // pattern 1: 右上コーナー落下 — 左下に広がる亀裂
        1 -> listOf(CrackCluster(screenWidth * 0.85f, screenHeight * 0.12f, 14, 42))
        // pattern 2: 下端中央落下 — 上に広がる亀裂
        2 -> listOf(CrackCluster(screenWidth * 0.50f, screenHeight * 0.88f, 14, 13))
        // pattern 3: 2点同時衝撃 — 左上と右下に2つのクモの巣
        3 -> listOf(
            CrackCluster(screenWidth * 0.30f, screenHeight * 0.32f, 10, 99),
            CrackCluster(screenWidth * 0.68f, screenHeight * 0.62f, 10, 77)
        )
        // pattern 4: 左上寄り — 少ないレイで大きなシャード
        else -> listOf(CrackCluster(screenWidth * 0.18f, screenHeight * 0.22f, 12, 55))
    }

    // ── 描画 ─────────────────────────────────────────────────────────────────
    override fun draw(canvas: Canvas) {
        // 1. 全体に薄暗いオーバーレイ
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), overlayPaint)
        // 各衝撃点クラスターを描画
        for (c in clusters) drawCluster(canvas, c)
    }

    private fun drawCluster(canvas: Canvas, c: CrackCluster) {
        // 2. 三角シャード塗りつぶし
        for ((path, alpha) in c.shardFills) {
            shardPaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawPath(path, shardPaint)
        }
        // 3. 衝撃点の段階的な暗転
        for ((r, a) in listOf(90f to 120, 65f to 165, 42f to 200, 22f to 230)) {
            impactPaint.color = Color.argb(a, 0, 0, 0)
            canvas.drawOval(c.ix - r, c.iy - r, c.ix + r, c.iy + r, impactPaint)
        }
        // 4. 亀裂レイ：太い暗い影
        for (ray in c.crackRays) {
            crackShadowPaint.strokeWidth = ray.width * 2.8f
            canvas.drawPath(ray.toPath(), crackShadowPaint)
        }
        // 5. 亀裂レイ：細い白いハイライト
        for (ray in c.crackRays) {
            crackHighlightPaint.strokeWidth = ray.width * 0.6f
            canvas.drawPath(ray.toPath(), crackHighlightPaint)
        }
        // 6. 同心リング
        for ((i, path) in c.ringPaths.withIndex()) {
            ringPaint.strokeWidth = (2.8f - i * 0.5f).coerceAtLeast(0.7f)
            canvas.drawPath(path, ringPaint)
        }
        // 7. 枝亀裂（影＋ハイライト）
        for (line in c.branchLines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], branchShadowPaint)
            canvas.drawLine(line[0], line[1], line[2], line[3], branchHighlightPaint)
        }
        // 8. 中央付近の細かいガラス質感
        for (line in c.glassTexture) {
            canvas.drawLine(line[0], line[1], line[2], line[3], glassDetailPaint)
        }
        // 9. 衝撃点の光る白い核
        canvas.drawOval(c.ix - 14f, c.iy - 14f, c.ix + 14f, c.iy + 14f, corePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int  = screenWidth
    override fun getIntrinsicHeight(): Int = screenHeight
}
