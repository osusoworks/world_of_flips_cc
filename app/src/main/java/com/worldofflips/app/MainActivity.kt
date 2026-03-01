package com.worldofflips.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ContextUtils.updateContext(newBase))
    }

    private lateinit var creaseRadioGroup: RadioGroup
    private var selectedCreaseType = "standard"
    private var isBgmOn = true

    // オーバーレイ権限を要求するためのランチャー（折り目選択時）
    private val overlayPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Settings.canDrawOverlays(this)) {
                    startOverlayService()
                } else {
                    Toast.makeText(this, "オーバーレイ権限が許可されませんでした", Toast.LENGTH_SHORT).show()
                }
            }

    // 起動時の権限案内ダイアログ用ランチャー（ダイアログを閉じるだけ）
    private val guidePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Settings.canDrawOverlays(this)) {
                    dismissOverlayPermissionGuide()
                    Toast.makeText(this, "権限が許可されました！", Toast.LENGTH_SHORT).show()
                }
            }

    private var isWhiteUnlocked = false
    private var isRgbUnlocked = false
    private var isBrokenUnlocked = false
    private var isDoubleUnlocked = false // New
    private var brokenUnlockTapCount = 0
    private var pendingUnlockId = -1

    private val quizLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    unlockCrease(pendingUnlockId)
                } else {
                    Toast.makeText(this, "クイズに失敗しました。再挑戦してください。", Toast.LENGTH_SHORT).show()
                    creaseRadioGroup.clearCheck()
                }
            }

    private val unlockChallengeLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    unlockCrease(pendingUnlockId)
                } else {
                    // Canceled or failed
                    creaseRadioGroup.clearCheck()
                }
            }

    private var shouldResetOnResume = false

    private val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.worldofflips.app.ACTION_CREASE_STOPPED") {
                        shouldResetOnResume = true
                        // MainActivityを前面に持ってくる
                        val bringToFrontIntent =
                                Intent(this@MainActivity, MainActivity::class.java).apply {
                                    flags =
                                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                        startActivity(bringToFrontIntent)
                    }
                }
            }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // 新しいIntentを保存
    }

    override fun onResume() {
        super.onResume()
        // BGM状態を最新に同期
        val prefs = getSharedPreferences("com.worldofflips.app.prefs", MODE_PRIVATE)
        isBgmOn = prefs.getBoolean("bgm_on", true)

        // Show overlay controls when app comes to foreground
        startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_SHOW_CONTROLS
                }
        )

        // 設定画面から戻ってきた時、権限が取得済みならダイアログを閉じる
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            dismissOverlayPermissionGuide()
        }

        val currentIntent = intent
        if (currentIntent?.getBooleanExtra("RESET_APP", false) == true) {
            // フラグをクリアして再度トリガーされないようにする
            currentIntent.removeExtra("RESET_APP")
            resetApp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // システムスプラッシュを1.5秒間表示
        val splashScreen = installSplashScreen()
        var keepSplashScreen = true
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        Handler(Looper.getMainLooper()).postDelayed({ keepSplashScreen = false }, 1500)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // BGM状態を読み込み
        val prefs = getSharedPreferences("com.worldofflips.app.prefs", MODE_PRIVATE)
        isBgmOn = prefs.getBoolean("bgm_on", true)

        // レシーバー登録
        val filter = IntentFilter("com.worldofflips.app.ACTION_CREASE_STOPPED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // スプラッシュ画面をスキップして直接タイトル画面へ
        // （Androidシステムスプラッシュ後に表示）
        findViewById<FrameLayout>(R.id.splashOverlay).visibility = View.GONE
        setupTitleScreen()

        // オーバーレイ権限の案内を表示（権限未取得の場合のみ）
        showOverlayPermissionGuideIfNeeded()

        val backgroundImageView: ImageView = findViewById(R.id.backgroundImageView)
        val characterImageView: ImageView = findViewById(R.id.characterImageView)
        creaseRadioGroup = findViewById(R.id.creaseRadioGroup)

        // 初期設定：ロック状態の更新
        updateUiState()

        // タイトル画面が初期状態で表示されるため、メニューUIは非表示にする
        findViewById<View>(R.id.bottomControlsLayout).visibility = View.GONE
        findViewById<View>(R.id.menuScrollView).visibility = View.GONE

        // 液晶漏れ（Locked）のタップ処理をOnTouchListenerで行う（ラグ解消のため）
        findViewById<RadioButton>(R.id.radioBroken).setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (!isBrokenUnlocked) {
                    brokenUnlockTapCount++

                    val suspiciousOverlay = findViewById<ImageView>(R.id.suspiciousOverlay)
                    val handler = Handler(Looper.getMainLooper())

                    when (brokenUnlockTapCount) {
                        2 -> {
                            // 画像なし、Snackbarのみ
                            Snackbar.make(view, "・・・。", Snackbar.LENGTH_SHORT).show()
                        }
                        5 -> {
                            // 画像なし、Snackbarのみ
                            Snackbar.make(view, "・・・・・。", Snackbar.LENGTH_SHORT).show()
                        }
                        8 -> {
                            // 画像1、「・・・・・・。」をSnackbarで表示
                            suspiciousOverlay.setImageResource(R.drawable.suspicious_1)
                            suspiciousOverlay.visibility = View.VISIBLE
                            Snackbar.make(view, "・・・・・・。", Snackbar.LENGTH_SHORT).show()
                            handler.postDelayed({ suspiciousOverlay.visibility = View.GONE }, 200)
                        }
                        16 -> {
                            // 画像2、「・・・・！？」をSnackbarで表示
                            suspiciousOverlay.setImageResource(R.drawable.suspicious_2)
                            suspiciousOverlay.visibility = View.VISIBLE
                            Snackbar.make(view, "・・・・！？", Snackbar.LENGTH_SHORT).show()
                            handler.postDelayed({ suspiciousOverlay.visibility = View.GONE }, 1000)
                        }
                        1, in 3..4, in 6..7, in 9..15, in 17..20 -> {
                            // Silent phases
                        }
                        else -> {
                            // Developer mode style countdown (starts from tap 21)
                            val devModeSteps = brokenUnlockTapCount - 20
                            val remaining = 7 - devModeSteps

                            if (remaining > 0) {
                                if (remaining <= 4) {
                                    Snackbar.make(
                                                    view,
                                                    "あと${remaining}ステップで解除...",
                                                    Snackbar.LENGTH_SHORT
                                            )
                                            .show()
                                }
                            } else {
                                unlockCrease(R.id.radioBroken)
                                brokenUnlockTapCount = 0
                            }
                        }
                    }
                    return@setOnTouchListener true // イベント消費（選択させない）
                }
            }
            return@setOnTouchListener false // 通常動作
        }

        creaseRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == -1) return@setOnCheckedChangeListener

            // ロック確認
            if (checkedId == R.id.radioWhite && !isWhiteUnlocked) {
                pendingUnlockId = checkedId
                // Start Quiz
                val intent = Intent(this, QuizActivity::class.java)
                quizLauncher.launch(intent)
                return@setOnCheckedChangeListener
            }
            if (checkedId == R.id.radioRGB && !isRgbUnlocked) {
                pendingUnlockId = checkedId
                // Start Quiz
                val intent = Intent(this, QuizActivity::class.java)
                quizLauncher.launch(intent)
                return@setOnCheckedChangeListener
            }
            if (checkedId == R.id.radioBroken && !isBrokenUnlocked) {
                // OnTouchListenerで処理するため、ここには到達しないはずだが念のため
                creaseRadioGroup.clearCheck()
                return@setOnCheckedChangeListener
            }
            if (checkedId == R.id.radioDouble && !isDoubleUnlocked) {
                pendingUnlockId = checkedId
                // Start Challenge
                val intent = Intent(this, UnlockChallengeActivity::class.java)
                unlockChallengeLauncher.launch(intent)
                return@setOnCheckedChangeListener
            }
            selectedCreaseType =
                    when (checkedId) {
                        R.id.radioStandard -> "standard"
                        R.id.radioWhite -> "white"
                        R.id.radioRGB -> "rgb"
                        R.id.radioBroken -> "broken"
                        R.id.radioDouble -> "double"
                        R.id.radioCracked -> "cracked"
                        R.id.radioTriFlip -> "triflip"
                        R.id.radioFilmFail -> "film_fail"
                        else -> "standard"
                    }

            backgroundImageView.setImageResource(R.drawable.dark_background)

            // アンロック数に応じて差分キャラ画像を上から順に解放
            val unlockCount = listOf(isWhiteUnlocked, isRgbUnlocked, isBrokenUnlocked, isDoubleUnlocked).count { it }
            val availableDarkChars = mutableListOf<Int>()
            if (unlockCount >= 1) availableDarkChars.add(R.drawable.main_charactor_dark2)
            if (unlockCount >= 2) availableDarkChars.add(R.drawable.main_charactor_dark3)
            if (unlockCount >= 3) availableDarkChars.add(R.drawable.main_charactor_dark4)
            if (unlockCount >= 4) availableDarkChars.add(R.drawable.main_charactor_dark5)

            if (checkedId == R.id.radioRGB && (1..5).random() == 1) {
                // RGBのイースターエッグ（1/5確率）
                characterImageView.setImageResource(R.drawable.paripi)
            } else if (availableDarkChars.isNotEmpty() && (1..10).random() == 1) {
                // 差分キャラをランダム表示（1/10確率・アンロック数分だけ候補が増える）
                characterImageView.setImageResource(availableDarkChars.random())
            } else {
                characterImageView.setImageResource(R.drawable.main_character_dark)
            }

            checkPermissionAndStart()
        }

        findViewById<Button>(R.id.btnBackToTitle).setOnClickListener { returnToTitle() }
    }

    private fun returnToTitle() {
        val titleScreenOverlay: View = findViewById(R.id.titleScreenOverlay)
        val menuScrollView: View = findViewById(R.id.menuScrollView)
        val bottomControlsLayout: View = findViewById(R.id.bottomControlsLayout)
        val characterImageView: ImageView = findViewById(R.id.characterImageView)
        val backgroundImageView: ImageView = findViewById(R.id.backgroundImageView)

        // 折り目表示を停止
        stopService(Intent(this, OverlayService::class.java))
        creaseRadioGroup.clearCheck()

        // 通常BGMに戻す（もし折り目BGMが流れていたら）
        val musicIntent =
                Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_PLAY_NORMAL
                    putExtra(MusicService.EXTRA_SEEK_TO, 300)
                }
        startService(musicIntent)

        // 背景とキャラクターを初期状態に戻す
        backgroundImageView.setImageResource(R.drawable.main_background_v2)
        characterImageView.setImageResource(R.drawable.main_character)

        // メニューUIを隠す
        menuScrollView
                .animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction { menuScrollView.visibility = View.GONE }
                .start()

        bottomControlsLayout
                .animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction { bottomControlsLayout.visibility = View.GONE }
                .start()

        characterImageView
                .animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction { characterImageView.visibility = View.GONE }
                .start()

        // タイトル画面を表示
        titleScreenOverlay.alpha = 0f
        titleScreenOverlay.visibility = View.VISIBLE
        titleScreenOverlay.animate().alpha(1f).setDuration(600).start()
    }

    private fun checkPermissionAndStart() {
        // Android M (API 23) 以上で権限を確認
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // 権限がない場合、設定画面に遷移して許可を求める
                val intent =
                        Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                        )
                overlayPermissionLauncher.launch(intent)
            } else {
                startOverlayService()
            }
        } else {
            // 古いバージョンでは権限不要
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("CREASE_TYPE", selectedCreaseType)
        startService(intent)

        // 折り目表示BGMに切り替え
        val musicIntent =
                Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_PLAY_CREASE
                    putExtra(MusicService.EXTRA_SEEK_TO, 20900)
                }
        startService(musicIntent)

        // Activityのボタンを表示 (OverlayServiceのボタンは非表示になったため... wait, OverlayService buttons show/hide
        // based on Activity visibility now)
        findViewById<View>(R.id.bottomControlsLayout).visibility = View.VISIBLE
    }

    private fun resetApp() {
        stopService(Intent(this, OverlayService::class.java))
        val backgroundImageView: ImageView = findViewById(R.id.backgroundImageView)
        val characterImageView: ImageView = findViewById(R.id.characterImageView)

        backgroundImageView.setImageResource(R.drawable.main_background_v2)
        characterImageView.setImageResource(R.drawable.main_character)

        // 通常BGMに切り替え
        val musicIntent =
                Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_PLAY_NORMAL
                    putExtra(MusicService.EXTRA_SEEK_TO, 300) // 0.3秒から再生
                }
        startService(musicIntent)

        // 次回同じ折り目を選べるようにチェックを外す
        creaseRadioGroup.clearCheck()

        // Hideしたボタンを再表示、ただしStopボタンは初期状態(非表示)に戻す
        findViewById<View>(R.id.bottomControlsLayout).visibility = View.VISIBLE
    }

    override fun onRestart() {
        super.onRestart()
    }

    override fun onStop() {
        super.onStop()
        // Hide overlay controls when app goes to background
        startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_HIDE_CONTROLS
                }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        // MusicServiceは停止させないように変更（BGM停止問題を回避）
        // アプリプロセスが終了すればシステムが自動的にサービスをクリーンアップします
    }

    private fun updateUiState() {
        val prefs = getSharedPreferences("com.worldofflips.app.prefs", MODE_PRIVATE)
        isWhiteUnlocked = prefs.getBoolean("white_unlocked", false)
        isRgbUnlocked = prefs.getBoolean("rgb_unlocked", false)
        isBrokenUnlocked = prefs.getBoolean("broken_unlocked", false)
        isDoubleUnlocked = prefs.getBoolean("double_unlocked", false) // Load

        for (i in 0 until creaseRadioGroup.childCount) {
            val view = creaseRadioGroup.getChildAt(i)
            if (view is RadioButton) {
                when (view.id) {
                    R.id.radioStandard -> {
                        // 1番目: アンロック状態 (XMLのテキストを使用、有効)
                        view.isEnabled = true
                    }
                    R.id.radioWhite -> {
                        // 2番目: クイズでアンロック
                        if (isWhiteUnlocked) {
                            view.text = "白いスレ傷"
                        } else {
                            view.text = "? ? ?"
                        }
                        view.isEnabled = true
                    }
                    R.id.radioRGB -> {
                        // 3番目: クイズでアンロック
                        if (isRgbUnlocked) {
                            view.text = " R G B "
                        } else {
                            view.text = "? ? ?"
                        }
                        view.isEnabled = true
                    }
                    R.id.radioBroken -> {
                        // 4番目: 連打でアンロック
                        if (isBrokenUnlocked) {
                            view.text = "液晶漏れ"
                        } else {
                            view.text = "? ? ?"
                        }
                        view.isEnabled = true
                    }
                    R.id.radioDouble -> {
                        // 5番目: 放置でアンロック
                        if (isDoubleUnlocked) {
                            view.text = "二重映り"
                        } else {
                            view.text = "? ? ?"
                        }
                        view.isEnabled = true
                    }
                    else -> {
                        // それ以下: 全部ロック
                        view.text = "? ? ?"
                        view.isEnabled = false
                    }
                }
            }
        }
    }

    private fun unlockCrease(id: Int) {
        // Show unlock effect first, then proceed with unlock
        showUnlockEffect { proceedWithUnlock(id) }
    }

    private fun showUnlockEffect(onComplete: () -> Unit) {
        val startVisualEffect = {
            val unlockOverlay: FrameLayout = findViewById(R.id.unlockOverlayRoot)
            val flashOverlay: View = findViewById(R.id.flashOverlay)
            val dimBackground: View = findViewById(R.id.dimBackground)
            val unlockContent: LinearLayout = findViewById(R.id.unlockContentContainer)

            // Load animations
            val flashIn = AnimationUtils.loadAnimation(this, R.anim.flash_in)
            val flashOut = AnimationUtils.loadAnimation(this, R.anim.flash_out)
            val popInBounce = AnimationUtils.loadAnimation(this, R.anim.pop_in_bounce)
            val sparklePulse = AnimationUtils.loadAnimation(this, R.anim.sparkle_pulse)
            val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)

            // Show overlay
            unlockOverlay.visibility = View.VISIBLE

            // Step 1: Flash in
            flashOverlay.alpha = 1f
            flashOverlay.startAnimation(flashIn)

            Handler(Looper.getMainLooper())
                    .postDelayed(
                            {
                                // Step 2: Flash out + show dim background
                                flashOverlay.startAnimation(flashOut)
                                dimBackground.alpha = 1f

                                Handler(Looper.getMainLooper())
                                        .postDelayed(
                                                {
                                                    flashOverlay.alpha = 0f

                                                    // Step 3: Pop in the unlock content
                                                    unlockContent.alpha = 1f
                                                    unlockContent.startAnimation(popInBounce)

                                                    Handler(Looper.getMainLooper())
                                                            .postDelayed(
                                                                    {
                                                                        // Step 4: Sparkle pulse
                                                                        // effect
                                                                        unlockContent
                                                                                .startAnimation(
                                                                                        sparklePulse
                                                                                )

                                                                        Handler(
                                                                                        Looper.getMainLooper()
                                                                                )
                                                                                .postDelayed(
                                                                                        {
                                                                                            // Step
                                                                                            // 5:
                                                                                            // Fade
                                                                                            // out
                                                                                            // everything
                                                                                            unlockContent
                                                                                                    .startAnimation(
                                                                                                            fadeOut
                                                                                                    )
                                                                                            dimBackground
                                                                                                    .animate()
                                                                                                    .alpha(
                                                                                                            0f
                                                                                                    )
                                                                                                    .setDuration(
                                                                                                            400
                                                                                                    )
                                                                                                    .start()

                                                                                            Handler(
                                                                                                            Looper.getMainLooper()
                                                                                                    )
                                                                                                    .postDelayed(
                                                                                                            {
                                                                                                                // Hide overlay and reset
                                                                                                                unlockOverlay
                                                                                                                        .visibility =
                                                                                                                        View.GONE
                                                                                                                unlockContent
                                                                                                                        .alpha =
                                                                                                                        0f
                                                                                                                dimBackground
                                                                                                                        .alpha =
                                                                                                                        0f
                                                                                                                flashOverlay
                                                                                                                        .alpha =
                                                                                                                        0f

                                                                                                                // Callback
                                                                                                                onComplete()
                                                                                                            },
                                                                                                            400
                                                                                                    )
                                                                                        },
                                                                                        1800
                                                                                ) // Pulse duration
                                                                    },
                                                                    400
                                                            ) // Pop in duration
                                                },
                                                300
                                        ) // Flash out duration
                            },
                            100
                    ) // Flash in duration
        }

        // Play unlock sound
        try {
            // Stop BGM first
            stopService(Intent(this, MusicService::class.java))

            val mediaPlayer = MediaPlayer.create(this, R.raw.unlockalarm)
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener { it.release() }
                mediaPlayer.start()
                // Start visual effect 2 seconds after MP3 starts playing
                Handler(Looper.getMainLooper()).postDelayed({ startVisualEffect() }, 2000)
            } else {
                startVisualEffect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            startVisualEffect()
        }
    }

    private fun proceedWithUnlock(id: Int) {
        val prefs = getSharedPreferences("com.worldofflips.app.prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        when (id) {
            R.id.radioWhite -> {
                isWhiteUnlocked = true
                editor.putBoolean("white_unlocked", true)
                findViewById<RadioButton>(R.id.radioWhite).text = "白いスレ傷"
                creaseRadioGroup.clearCheck()
                creaseRadioGroup.check(R.id.radioWhite)
            }
            R.id.radioRGB -> {
                isRgbUnlocked = true
                editor.putBoolean("rgb_unlocked", true)
                findViewById<RadioButton>(R.id.radioRGB).text = " R G B "
                creaseRadioGroup.clearCheck()
                creaseRadioGroup.check(R.id.radioRGB)
            }
            R.id.radioBroken -> {
                isBrokenUnlocked = true
                editor.putBoolean("broken_unlocked", true)
                findViewById<RadioButton>(R.id.radioBroken).text = "液晶漏れ"
                creaseRadioGroup.clearCheck()
                creaseRadioGroup.check(R.id.radioBroken)
            }
            R.id.radioDouble -> {
                isDoubleUnlocked = true
                editor.putBoolean("double_unlocked", true)
                findViewById<RadioButton>(R.id.radioDouble).text = "二重映り"
                creaseRadioGroup.clearCheck()
                creaseRadioGroup.check(R.id.radioDouble)
            }
        }
        editor.apply()
    }

    private fun startSplashScreen() {
        val splashOverlay: FrameLayout = findViewById(R.id.splashOverlay)
        val splashLogo: ImageView = findViewById(R.id.splashLogo)

        // フェードイン (ロゴ)
        splashLogo
                .animate()
                .alpha(1f)
                .setDuration(1500)
                .withEndAction {
                    // しばらく待機
                    Handler(Looper.getMainLooper())
                            .postDelayed(
                                    {
                                        // フェードアウト (オーバーレイ全体)
                                        splashOverlay
                                                .animate()
                                                .alpha(0f)
                                                .setDuration(1200)
                                                .withEndAction {
                                                    splashOverlay.visibility = View.GONE
                                                    setupTitleScreen() // タイトル画面のセットアップ（BGM開始など）
                                                }
                                                .start()
                                    },
                                    1500
                            )
                }
                .start()
    }

    private fun setupTitleScreen() {
        val titleScreenOverlay: View = findViewById(R.id.titleScreenOverlay)
        val btnStart: PopButton = findViewById(R.id.btnStart)
        val btnHowToPlay: PopButton = findViewById(R.id.btnHowToPlay)
        val btnSettings: PopButton = findViewById(R.id.btnSettings)
        val btnCredit: PopButton = findViewById(R.id.btnCredit)

        val menuScrollView: View = findViewById(R.id.menuScrollView)
        val bottomControlsLayout: View = findViewById(R.id.bottomControlsLayout)

        // ランダムなタイトル画像を表示
        val titleImages =
                listOf(
                        R.drawable.title_image_1,
                        R.drawable.title_image_2,
                        R.drawable.title_image_3,
                        R.drawable.title_image_4,
                        R.drawable.title_image_5,
                        R.drawable.title_image_6,
                        R.drawable.title_image_7,
                        R.drawable.title_image_8,
                        R.drawable.title_image_9,
                        R.drawable.title_image_10,
                        R.drawable.title_image_11,
                        R.drawable.title_image_12
                )
        val randomImage = titleImages.random()
        findViewById<ImageView>(R.id.randomTitleImage).setImageResource(randomImage)

        // BGM開始（タイトル画面で再生）
        startBgm()

        val btnBgmToggle: ImageButton = findViewById(R.id.btnBgmToggle)

        // アイコンの初期化
        btnBgmToggle.setImageResource(if (isBgmOn) R.drawable.ic_bgm_on else R.drawable.ic_bgm_off)

        btnBgmToggle.setOnClickListener {
            playSe()
            isBgmOn = !isBgmOn
            getSharedPreferences("com.worldofflips.app.prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("bgm_on", isBgmOn)
                    .apply()

            btnBgmToggle.setImageResource(
                    if (isBgmOn) R.drawable.ic_bgm_on else R.drawable.ic_bgm_off
            )

            val musicIntent =
                    Intent(this, MusicService::class.java).apply {
                        if (isBgmOn) {
                            // Mute解除
                            action = MusicService.ACTION_SET_MUTE
                            putExtra(MusicService.EXTRA_IS_MUTED, false)
                            // 再生のきっかけが再開の可能性があるため明示的に通常BGMを鳴らす指示を出すのもアリですが、
                            // ACTION_SET_MUTEのサービス側での処理により、MUTE解除なら自動で再開される前提です
                        } else {
                            // Mute設定
                            action = MusicService.ACTION_SET_MUTE
                            putExtra(MusicService.EXTRA_IS_MUTED, true)
                        }
                    }
            startService(musicIntent)
        }

        // STARTボタン：メニュー画面へ
        btnStart.setOnClickListener {
            playSe()
            titleScreenOverlay
                    .animate()
                    .alpha(0f)
                    .setDuration(600)
                    .withEndAction {
                        titleScreenOverlay.visibility = View.GONE

                        // キャラクターを表示
                        val characterImageView: ImageView = findViewById(R.id.characterImageView)
                        characterImageView.alpha = 0f
                        characterImageView.visibility = View.VISIBLE
                        characterImageView.animate().alpha(1f).setDuration(400).start()

                        // メニューUIを表示
                        menuScrollView.alpha = 0f
                        menuScrollView.visibility = View.VISIBLE
                        menuScrollView.animate().alpha(1f).setDuration(400).start()

                        bottomControlsLayout.alpha = 0f
                        bottomControlsLayout.visibility = View.VISIBLE
                        bottomControlsLayout.animate().alpha(1f).setDuration(400).start()
                    }
                    .start()
        }

        btnHowToPlay.setOnClickListener {
            playSe()
            val intent = Intent(this, HowToPlayActivity::class.java)
            startActivity(intent)
        }
        btnSettings.setOnClickListener {
            playSe()
            val settingsOverlay: View = findViewById(R.id.settingsOverlay)
            settingsOverlay.alpha = 0f
            settingsOverlay.visibility = View.VISIBLE
            settingsOverlay.animate().alpha(1f).setDuration(300).start()
        }

        val settingsOverlay: View = findViewById(R.id.settingsOverlay)

        val btnSettingsReset: PopButton = findViewById(R.id.btnSettingsReset)
        val btnSettingsBack: PopButton = findViewById(R.id.btnSettingsBack)

        btnSettingsReset.setOnClickListener {
            playSe()
            AlertDialog.Builder(this)
                    .setTitle("確認")
                    .setMessage("すべてのアンロック状態をリセットしますか？")
                    .setPositiveButton("リセット") { _, _ -> performReset() }
                    .setNegativeButton("キャンセル", null)
                    .show()
        }

        btnSettingsBack.setOnClickListener {
            playSe()
            val settingsOverlay: View = findViewById(R.id.settingsOverlay)
            settingsOverlay
                    .animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { settingsOverlay.visibility = View.GONE }
                    .start()
        }
        btnCredit.setOnClickListener {
            playSe()
            val intent = Intent(this, CreditActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startBgm() {
        // BGM状態を読み込み
        val prefs = getSharedPreferences("com.worldofflips.app.prefs", MODE_PRIVATE)
        isBgmOn = prefs.getBoolean("bgm_on", true)

        if (isBgmOn) {
            val musicIntent =
                    Intent(this, MusicService::class.java).apply {
                        action = MusicService.ACTION_PLAY_NORMAL
                        putExtra(MusicService.EXTRA_SEEK_TO, 300)
                    }
            startService(musicIntent)
        } else {
            val musicIntent =
                    Intent(this, MusicService::class.java).apply {
                        action = MusicService.ACTION_SET_MUTE
                        putExtra(MusicService.EXTRA_IS_MUTED, true)
                    }
            startService(musicIntent)
        }
    }

    private fun playSe() {
        val musicIntent =
                Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_PLAY_SE
                }
        startService(musicIntent)
    }

    private fun performReset() {
        val prefs = getSharedPreferences("com.worldofflips.app.prefs", MODE_PRIVATE)
        prefs.edit()
                .putBoolean("white_unlocked", false)
                .putBoolean("rgb_unlocked", false)
                .putBoolean("broken_unlocked", false)
                .putBoolean("double_unlocked", false)
                .apply()

        isWhiteUnlocked = false
        isRgbUnlocked = false
        isBrokenUnlocked = false
        isDoubleUnlocked = false
        brokenUnlockTapCount = 0
        updateUiState()

        Toast.makeText(this, "ロック状態をリセットしました", Toast.LENGTH_SHORT).show()
    }

    private fun showOverlayPermissionGuideIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val guideOverlay: View = findViewById(R.id.overlayPermissionGuide)
            guideOverlay.alpha = 0f
            guideOverlay.visibility = View.VISIBLE
            guideOverlay.animate().alpha(1f).setDuration(400).start()

            // 「設定を開く」ボタン
            findViewById<PopButton>(R.id.btnOpenOverlaySettings).setOnClickListener {
                playSe()
                val intent =
                        Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                        )
                guidePermissionLauncher.launch(intent)
            }

            // 「あとで」ボタン
            findViewById<PopButton>(R.id.btnOverlayLater).setOnClickListener {
                playSe()
                dismissOverlayPermissionGuide()
            }
        }
    }

    private fun dismissOverlayPermissionGuide() {
        val guideOverlay: View = findViewById(R.id.overlayPermissionGuide)
        if (guideOverlay.visibility == View.VISIBLE) {
            guideOverlay
                    .animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { guideOverlay.visibility = View.GONE }
                    .start()
        }
    }
}
