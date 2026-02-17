package com.worldofflips.app // パッケージ名を確認

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var triflipViewTop: View? = null
    private var triflipViewBottom: View? = null
    private var controlsView: View? = null

    companion object {
        const val CHANNEL_ID = "overlay_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_OVERLAY = "com.worldofflips.app.ACTION_STOP_OVERLAY"
        const val ACTION_SHOW_CONTROLS = "com.worldofflips.app.ACTION_SHOW_CONTROLS"
        const val ACTION_HIDE_CONTROLS = "com.worldofflips.app.ACTION_HIDE_CONTROLS"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    @RequiresApi(Build.VERSION_CODES.O) // TYPE_APPLICATION_OVERLAY に必要なアノテーション
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                // MainActivityを起動してリセットを通知
                val mainActivityIntent =
                        Intent(this, MainActivity::class.java).apply {
                            this.flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("RESET_APP", true)
                        }
                startActivity(mainActivityIntent)
                return START_NOT_STICKY
            }
            ACTION_SHOW_CONTROLS -> {
                // Only show controls if overlay exists
                if (overlayView != null || triflipViewTop != null) {
                    showControls()
                }
                return START_STICKY
            }
            ACTION_HIDE_CONTROLS -> {
                removeControls()
                return START_STICKY
            }
        }

        val creaseType = intent?.getStringExtra("CREASE_TYPE") ?: "standard"
        showNotification(creaseType)

        // TriFlipモードの場合は特別な処理
        if (creaseType == "triflip") {
            removeAllViews()
            showTriFlipOverlay()
            showControls() // Ensure controls are on top
            return START_STICKY
        }

        // 通常モードの場合、TriFlipのビューが表示されていれば削除
        removeTriFlipViews()

        if (overlayView != null) {
            // 既に表示されている場合は画像のみ更新
            updateCreaseImage(creaseType)
            showControls() // Ensure controls are on top (re-add)
            return START_STICKY
        }

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        updateCreaseImage(creaseType)

        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, // width：画面幅いっぱい
                        WindowManager.LayoutParams.WRAP_CONTENT, // height：ImageView の高さで OK（5dp など）
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.CENTER

        windowManager.addView(overlayView, params)

        showControls() // Add controls last

        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showTriFlipOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val creaseHeight = (resources.displayMetrics.density * 8).toInt()

        // 上側の折り目（画面の1/3位置）
        triflipViewTop = inflater.inflate(R.layout.overlay_layout, null)
        triflipViewTop
                ?.findViewById<android.widget.ImageView>(R.id.creaseLine)
                ?.setImageResource(R.drawable.crease_triflip)

        val paramsTop =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        creaseHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                )
        paramsTop.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        paramsTop.y = screenHeight / 3

        windowManager.addView(triflipViewTop, paramsTop)

        // 下側の折り目（画面の2/3位置）
        triflipViewBottom = inflater.inflate(R.layout.overlay_layout, null)
        triflipViewBottom
                ?.findViewById<android.widget.ImageView>(R.id.creaseLine)
                ?.setImageResource(R.drawable.crease_triflip)

        val paramsBottom =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        creaseHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                )
        paramsBottom.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        paramsBottom.y = screenHeight * 2 / 3

        windowManager.addView(triflipViewBottom, paramsBottom)
    }

    private fun showControls() {
        // Remove existing controls if any to re-add on top
        removeControls()

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        controlsView = inflater.inflate(R.layout.overlay_controls, null)

        // WindowManager Params
        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE, // Allow touch, but don't steal focus
                        PixelFormat.TRANSLUCENT
                )

        // Position: Bottom Left corner, above navigation bar
        val density = resources.displayMetrics.density
        val margin = (8 * density).toInt() // 画面端からの最小マージン

        // ナビゲーションバーの高さを取得
        val navBarHeight = getNavigationBarHeight()

        params.gravity = Gravity.BOTTOM or Gravity.START
        params.x = margin
        params.y = navBarHeight + margin // ナビゲーションバーの上に配置

        // Stop Button Listener
        val stopButton = controlsView?.findViewById<View>(R.id.overlayStopButton)
        stopButton?.setOnClickListener {
            // Same logic as notification "Close"
            val stopIntent =
                    Intent(this, OverlayService::class.java).apply { action = ACTION_STOP_OVERLAY }
            startService(stopIntent)
        }
        stopButton?.visibility = View.VISIBLE

        windowManager.addView(controlsView, params)
    }

    private fun removeControls() {
        controlsView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            controlsView = null
        }
    }

    private fun updateCreaseImage(type: String) {
        val imageView = overlayView?.findViewById<android.widget.ImageView>(R.id.creaseLine)
        
        // Reset both to avoid conflicts
        imageView?.setImageDrawable(null)
        imageView?.background = null

        val params = imageView?.layoutParams
        
        // Calculate screen height for percentage-based height
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels

        if (type == "standard") {
            // Use background for XML drawable to avoid ImageView scaling issues with lines
            imageView?.setBackgroundResource(R.drawable.realistic_crease)
            
            // 7% of screen height
            params?.height = (screenHeight * 0.07).toInt()
        } else if (type == "rgb") {
             // Custom Drawable for RGB
             val drawable = RgbCreaseDrawable(displayMetrics.widthPixels, screenHeight)
             imageView?.setImageDrawable(drawable)
             
             params?.height = WindowManager.LayoutParams.WRAP_CONTENT // Use intrinsic size of drawable (full screen)
        } else {
             val resourceId =
                when (type) {
                    "white" -> R.drawable.crease_white
                    // "rgb" handled above
                    "broken" -> R.drawable.crease_broken
                    "double" -> R.drawable.crease_double
                    "cracked" -> R.drawable.crease_cracked
                    "film_fail" -> R.drawable.crease_film_failure
                    else -> R.drawable.crease_standard
                }
            imageView?.setImageResource(resourceId)

             if (type == "broken" || type == "cracked" || type == "film_fail") {
                params?.height = WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                params?.height = (resources.displayMetrics.density * 8).toInt()
            }
        }
        imageView?.layoutParams = params
    }

    private fun removeTriFlipViews() {
        triflipViewTop?.let {
            windowManager.removeView(it)
            triflipViewTop = null
        }
        triflipViewBottom?.let {
            windowManager.removeView(it)
            triflipViewBottom = null
        }
    }

    private fun removeAllViews() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        removeTriFlipViews()
        removeControls()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllViews()
    }

    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0 // ナビゲーションバーがない場合
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                            CHANNEL_ID,
                            "Overlay Service Channel",
                            NotificationManager.IMPORTANCE_LOW
                    )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun showNotification(creaseType: String) {
        val stopIntent =
                Intent(this, OverlayService::class.java).apply { action = ACTION_STOP_OVERLAY }
        val stopPendingIntent =
                PendingIntent.getService(
                        this,
                        0,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        val notification: Notification =
                NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("折り目表示中")
                        .setContentText("現在 $creaseType を表示しています")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .addAction(
                                android.R.drawable.ic_menu_close_clear_cancel,
                                "閉じる",
                                stopPendingIntent
                        )
                        .setOngoing(true)
                        .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
