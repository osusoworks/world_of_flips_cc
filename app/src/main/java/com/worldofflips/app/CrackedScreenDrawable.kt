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

class CrackedScreenDrawable(private val screenWidth: Int, private val screenHeight: Int) : Drawable() {

    private val impactX = screenWidth * 0.54f
    private val impactY = screenHeight * 0.40f
    private val rng = Random(7)

    // --- データ構造 ---
    private data class CrackRay(val points: List<PointF>, val width: Float)
    private val crackRays = mutableListOf<CrackRay>()
    private val ringPaths = mutableListOf<Path>()
    private val branchLines = mutableListOf<FloatArray>()
    private val shardFills = mutableListOf<Pair<Path, Int>>()   // <パス, アルファ>
    private val glassTexture = mutableListOf<FloatArray>()

    // --- ペイント ---
    // 全体に薄暗いオーバーレイ
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 0, 0)
        style = Paint.Style.FILL
    }
    // シャード（三角破片）塗りつぶし
    private val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // 亀裂の暗い影
    private val crackShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // 亀裂の白いハイライト
    private val crackHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // 同心円リング
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    // 衝撃点塗りつぶし
    private val impactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // 中央の細いガラス質感
    private val glassDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }

    init {
        generateCrackRays()
        generateShardFills()
        generateRings()
        generateBranches()
        generateGlassTexture()
    }

    // 放射状亀裂を16本生成（ジグザグ）
    private fun generateCrackRays() {
        val count = 16
        for (i in 0 until count) {
            val baseAngleDeg = (i.toFloat() / count) * 360f
            val offsetDeg = (rng.nextFloat() - 0.5f) * (360f / count * 0.5f)
            val angleRad = Math.toRadians((baseAngleDeg + offsetDeg).toDouble()).toFloat()
            val pts = buildJaggedRay(angleRad)
            crackRays.add(CrackRay(pts, 1.8f + rng.nextFloat() * 3.5f))
        }
        // 角度順にソート（シャード生成のため）
        crackRays.sortBy { ray ->
            atan2(
                (ray.points.last().y - impactY).toDouble(),
                (ray.points.last().x - impactX).toDouble()
            ).toFloat()
        }
    }

    private fun buildJaggedRay(startAngle: Float): List<PointF> {
        val pts = mutableListOf<PointF>()
        var x = impactX; var y = impactY; var angle = startAngle
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

    // 隣接する亀裂レイの間に「黒い三角シャード」を生成
    private fun generateShardFills() {
        for (i in crackRays.indices) {
            val rayA = crackRays[i]
            val rayB = crackRays[(i + 1) % crackRays.size]
            val segments = minOf(rayA.points.size, rayB.points.size) - 1

            for (seg in 0 until minOf(segments, 7)) {
                val progress = seg.toFloat() / 7f

                // 衝撃点に近いほど不透明（真っ黒）、外側は薄く
                val alpha = ((1f - progress) * 170f + 15f).toInt()
                    .let { (it * (0.55f + rng.nextFloat() * 0.45f)).toInt() }
                    .coerceIn(10, 190)

                val path = Path()
                path.moveTo(rayA.points[seg].x, rayA.points[seg].y)
                path.lineTo(rayA.points[seg + 1].x, rayA.points[seg + 1].y)
                val bNext = rayB.points.getOrElse(seg + 1) { rayB.points.last() }
                val bCur  = rayB.points[seg]
                path.lineTo(bNext.x, bNext.y)
                path.lineTo(bCur.x, bCur.y)
                path.close()

                shardFills.add(Pair(path, alpha))
            }
        }
    }

    // 不規則な同心リング（欠けあり）を4本生成
    private fun generateRings() {
        for (radius in listOf(55f, 140f, 270f, 430f)) {
            val path = Path()
            val segCount = 20 + rng.nextInt(8)
            for (i in 0 until segCount) {
                if (rng.nextFloat() < 0.22f) continue
                val a1 = (i.toFloat() / segCount) * 360f
                val a2 = ((i + 1).toFloat() / segCount) * 360f
                val mid = (a1 + a2) / 2f
                val r1   = radius * (0.82f + rng.nextFloat() * 0.36f)
                val r2   = radius * (0.82f + rng.nextFloat() * 0.36f)
                val rMid = radius * (0.78f + rng.nextFloat() * 0.44f)
                fun toX(a: Double, r: Float) = impactX + cos(Math.toRadians(a)).toFloat() * r
                fun toY(a: Double, r: Float) = impactY + sin(Math.toRadians(a)).toFloat() * r
                path.moveTo(toX(a1.toDouble(), r1), toY(a1.toDouble(), r1))
                path.lineTo(toX(mid.toDouble(), rMid), toY(mid.toDouble(), rMid))
                path.lineTo(toX(a2.toDouble(), r2), toY(a2.toDouble(), r2))
            }
            ringPaths.add(path)
        }
    }

    // 各亀裂レイから枝亀裂を生成
    private fun generateBranches() {
        for (ray in crackRays) {
            val p = ray.toPath()
            val measure = PathMeasure(p, false)
            val totalLen = measure.length
            if (totalLen < 80f) continue
            for (frac in listOf(0.28f, 0.48f, 0.67f, 0.82f)) {
                if (rng.nextFloat() < 0.35f) continue
                val pos = FloatArray(2); val tan = FloatArray(2)
                measure.getPosTan(totalLen * frac, pos, tan)
                val mainAngle = atan2(tan[1].toDouble(), tan[0].toDouble()).toFloat()
                val side = if (rng.nextBoolean()) 1f else -1f
                val branchAngle = mainAngle + side * (0.42f + rng.nextFloat() * 0.32f)
                val len = 35f + rng.nextFloat() * 115f
                branchLines.add(floatArrayOf(
                    pos[0], pos[1],
                    pos[0] + cos(branchAngle) * len,
                    pos[1] + sin(branchAngle) * len
                ))
            }
        }
    }

    // 衝撃中心付近の細かいガラス質感ライン
    private fun generateGlassTexture() {
        for (i in 0..55) {
            val angle = rng.nextFloat() * 360.0
            val r1 = 12f + rng.nextFloat() * 40f
            val r2 = r1 + 6f + rng.nextFloat() * 22f
            val rad = Math.toRadians(angle)
            glassTexture.add(floatArrayOf(
                impactX + cos(rad).toFloat() * r1, impactY + sin(rad).toFloat() * r1,
                impactX + cos(rad).toFloat() * r2, impactY + sin(rad).toFloat() * r2
            ))
        }
    }

    private fun CrackRay.toPath(): Path {
        val p = Path()
        if (points.isEmpty()) return p
        p.moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { p.lineTo(it.x, it.y) }
        return p
    }

    override fun draw(canvas: Canvas) {
        // 1. 全体に薄暗いオーバーレイ
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), overlayPaint)

        // 2. 三角シャード塗りつぶし（最重要：リアルな割れガラス感）
        for ((path, alpha) in shardFills) {
            shardPaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawPath(path, shardPaint)
        }

        // 3. 衝撃点の段階的な暗転（中心に近いほど真っ黒）
        for ((r, a) in listOf(90f to 120, 65f to 165, 42f to 200, 22f to 230)) {
            impactPaint.color = Color.argb(a, 0, 0, 0)
            canvas.drawOval(impactX - r, impactY - r, impactX + r, impactY + r, impactPaint)
        }

        // 4. 亀裂レイ：太い暗い影
        for (ray in crackRays) {
            crackShadowPaint.strokeWidth = ray.width * 2.8f
            canvas.drawPath(ray.toPath(), crackShadowPaint)
        }
        // 5. 亀裂レイ：細い白いハイライト（ガラス端の反射）
        for (ray in crackRays) {
            crackHighlightPaint.strokeWidth = ray.width * 0.6f
            canvas.drawPath(ray.toPath(), crackHighlightPaint)
        }

        // 6. 同心リング
        for ((i, path) in ringPaths.withIndex()) {
            ringPaint.strokeWidth = (2.8f - i * 0.5f).coerceAtLeast(0.7f)
            canvas.drawPath(path, ringPaint)
        }

        // 7. 枝亀裂（影＋ハイライト）
        val branchShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2.8f
            strokeCap = Paint.Cap.ROUND
        }
        val branchHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
            strokeCap = Paint.Cap.ROUND
        }
        for (line in branchLines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], branchShadow)
            canvas.drawLine(line[0], line[1], line[2], line[3], branchHighlight)
        }

        // 8. 中央付近の細かいガラスの質感
        for (line in glassTexture) {
            canvas.drawLine(line[0], line[1], line[2], line[3], glassDetailPaint)
        }

        // 9. 衝撃点の光る白い核
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawOval(impactX - 14f, impactY - 14f, impactX + 14f, impactY + 14f, corePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = screenWidth
    override fun getIntrinsicHeight(): Int = screenHeight
}
