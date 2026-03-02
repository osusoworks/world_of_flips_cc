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
 * 割れ画面エフェクト描画クラス（5パターン）
 *
 * pattern 0: 中央スタンダード  — 画面中央から全方向に均等なクモの巣
 * pattern 1: 右上コーナー落下 — 右上角から下・左方向に広がる亀裂
 * pattern 2: 下端中央落下    — 下端から上方向に密集した細かい亀裂
 * pattern 3: 2点同時衝撃    — 画面に2箇所のクモの巣
 * pattern 4: 左側面衝撃     — 左端からほぼ直線的に横断する大きな亀裂
 */
class CrackedScreenDrawable(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val pattern: Int = 0
) : Drawable() {

    companion object {
        const val PATTERN_COUNT = 5
    }

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
    /**
     * @param ix, iy       衝撃点（ピクセル座標）
     * @param rayCount     放射亀裂の本数
     * @param seed         乱数シード
     * @param minAngleDeg  亀裂を生成する角度範囲の開始（null = 全周360°）
     * @param maxAngleDeg  亀裂を生成する角度範囲の終了
     * @param jitter       亀裂のジグザグ量（大きいほど曲がりくねる、デフォルト0.22）
     * @param minStep      亀裂1ステップの最短距離
     * @param maxStep      亀裂1ステップの最長距離
     * @param widthScale   亀裂の太さ係数
     */
    private inner class CrackCluster(
        val ix: Float,
        val iy: Float,
        val rayCount: Int,
        seed: Int,
        private val minAngleDeg: Float? = null,
        private val maxAngleDeg: Float? = null,
        private val jitter: Float = 0.22f,
        private val minStep: Float = 30f,
        private val maxStep: Float = 65f,
        private val widthScale: Float = 1.0f
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
            val angleSpan  = if (minAngleDeg != null && maxAngleDeg != null) maxAngleDeg - minAngleDeg else 360f
            val angleStart = minAngleDeg ?: 0f

            for (i in 0 until rayCount) {
                val baseAngleDeg = angleStart + (i.toFloat() / rayCount) * angleSpan
                val offsetDeg    = (rng.nextFloat() - 0.5f) * (angleSpan / rayCount * 0.5f)
                val angleRad     = Math.toRadians((baseAngleDeg + offsetDeg).toDouble()).toFloat()
                val baseWidth    = (1.8f + rng.nextFloat() * 3.5f) * widthScale
                crackRays.add(CrackRay(buildJaggedRay(angleRad), baseWidth))
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
            val maxDist = maxOf(screenWidth, screenHeight) * 1.4f
            while (dist < maxDist) {
                val step = minStep + rng.nextFloat() * (maxStep - minStep)
                angle += (rng.nextFloat() - 0.5f) * jitter
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

    // ── パターン定義 ─────────────────────────────────────────────────────────
    private val clusters: List<CrackCluster> = when (pattern % PATTERN_COUNT) {

        // ── pattern 0: 中央スタンダード ──────────────────────────────────────
        // 画面中央やや右上から全方向に16本の亀裂。典型的なクモの巣割れ。
        0 -> listOf(CrackCluster(
            ix = screenWidth * 0.54f,
            iy = screenHeight * 0.40f,
            rayCount = 16,
            seed = 7
        ))

        // ── pattern 1: 右上コーナー落下 ──────────────────────────────────────
        // 右上コーナーへの落下。亀裂は下〜左方向（100°〜290°）にのみ伸び、
        // 大きくジグザグして「角が砕けた」感を表現。
        1 -> listOf(CrackCluster(
            ix = screenWidth * 0.88f,
            iy = screenHeight * 0.09f,
            rayCount = 12,
            seed = 42,
            minAngleDeg = 100f,
            maxAngleDeg = 290f,
            jitter = 0.38f,
            minStep = 25f,
            maxStep = 60f
        ))

        // ── pattern 2: 下端中央落下 ───────────────────────────────────────────
        // 下端中央への落下。亀裂は上方向（195°〜345°）にのみ伸び、
        // 短いステップで細かく密集した亀裂が上へ扇状に広がる。
        2 -> listOf(CrackCluster(
            ix = screenWidth * 0.50f,
            iy = screenHeight * 0.92f,
            rayCount = 18,
            seed = 13,
            minAngleDeg = 195f,
            maxAngleDeg = 345f,
            jitter = 0.26f,
            minStep = 18f,
            maxStep = 42f
        ))

        // ── pattern 3: 2点同時衝撃 ────────────────────────────────────────────
        // 2箇所に独立したクモの巣。落下後に跳ねて2か所が割れたイメージ。
        3 -> listOf(
            CrackCluster(
                ix = screenWidth * 0.28f,
                iy = screenHeight * 0.28f,
                rayCount = 10,
                seed = 99
            ),
            CrackCluster(
                ix = screenWidth * 0.74f,
                iy = screenHeight * 0.68f,
                rayCount = 10,
                seed = 77
            )
        )

        // ── pattern 4: 左側面衝撃 ────────────────────────────────────────────
        // 左端への強い衝撃。ジグザグが少なくほぼ直線的な太い亀裂が
        // 右方向（-55°〜55°）に画面を横断する。
        else -> listOf(CrackCluster(
            ix = screenWidth * 0.04f,
            iy = screenHeight * 0.48f,
            rayCount = 9,
            seed = 55,
            minAngleDeg = -55f,
            maxAngleDeg = 55f,
            jitter = 0.09f,
            minStep = 50f,
            maxStep = 110f,
            widthScale = 2.2f
        ))
    }

    // ── 描画 ─────────────────────────────────────────────────────────────────
    override fun draw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), overlayPaint)
        for (c in clusters) drawCluster(canvas, c)
    }

    private fun drawCluster(canvas: Canvas, c: CrackCluster) {
        for ((path, alpha) in c.shardFills) {
            shardPaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawPath(path, shardPaint)
        }
        for ((r, a) in listOf(90f to 120, 65f to 165, 42f to 200, 22f to 230)) {
            impactPaint.color = Color.argb(a, 0, 0, 0)
            canvas.drawOval(c.ix - r, c.iy - r, c.ix + r, c.iy + r, impactPaint)
        }
        for (ray in c.crackRays) {
            crackShadowPaint.strokeWidth = ray.width * 2.8f
            canvas.drawPath(ray.toPath(), crackShadowPaint)
        }
        for (ray in c.crackRays) {
            crackHighlightPaint.strokeWidth = ray.width * 0.6f
            canvas.drawPath(ray.toPath(), crackHighlightPaint)
        }
        for ((i, path) in c.ringPaths.withIndex()) {
            ringPaint.strokeWidth = (2.8f - i * 0.5f).coerceAtLeast(0.7f)
            canvas.drawPath(path, ringPaint)
        }
        for (line in c.branchLines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], branchShadowPaint)
            canvas.drawLine(line[0], line[1], line[2], line[3], branchHighlightPaint)
        }
        for (line in c.glassTexture) {
            canvas.drawLine(line[0], line[1], line[2], line[3], glassDetailPaint)
        }
        canvas.drawOval(c.ix - 14f, c.iy - 14f, c.ix + 14f, c.iy + 14f, corePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int  = screenWidth
    override fun getIntrinsicHeight(): Int = screenHeight
}
