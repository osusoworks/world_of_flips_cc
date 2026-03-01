package com.worldofflips.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.util.Log

class MusicService : Service() {

    private var normalPlayer: MediaPlayer? = null
    private var creasePlayer: MediaPlayer? = null

    private lateinit var audioManager: AudioManager
    private var soundPool: android.media.SoundPool? = null
    private var seSoundId: Int = 0
    private var isMuted = false
    private var currentMode = MODE_NONE // 現在のモードを追跡

    // State flags for Async Preparation
    private var isNormalPrepared = false
    private var isCreasePrepared = false
    private var pendingPlayNormal = false
    private var pendingSeekToNormal = 0
    private var pendingPlayCrease = false
    private var pendingSeekToCrease = 0

    // BGM sequence control
    private var currentBgmResId = R.raw.normal_bgm
    private var isBgmLooping = true

    companion object {
        private const val TAG = "MusicService"
        const val ACTION_PLAY_NORMAL = "com.worldofflips.app.ACTION_PLAY_NORMAL"
        const val ACTION_PLAY_CREASE = "com.worldofflips.app.ACTION_PLAY_CREASE"
        const val ACTION_STOP = "com.worldofflips.app.ACTION_STOP"
        const val ACTION_TOGGLE_MUTE = "com.worldofflips.app.ACTION_TOGGLE_MUTE"
        const val ACTION_SET_MUTE = "com.worldofflips.app.ACTION_SET_MUTE"
        const val EXTRA_SEEK_TO = "com.worldofflips.app.EXTRA_SEEK_TO"
        const val EXTRA_IS_MUTED = "com.worldofflips.app.EXTRA_IS_MUTED"
        const val ACTION_PLAY_SE = "com.worldofflips.app.ACTION_PLAY_SE"

        private const val MODE_NONE = 0
        private const val MODE_NORMAL = 1
        private const val MODE_CREASE = 2
        private const val SE_VOLUME = 0.5f // SEの音量を50%に設定
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // SoundPoolの準備 (SE用)
        val audioAttributes =
                android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()

        soundPool =
                android.media.SoundPool.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setMaxStreams(1)
                        .build()

        // SEを読み込み（メニュー選択音：kapa）
        seSoundId = soundPool?.load(this, R.raw.kapa, 1) ?: 0

        // 通常時のMediaPlayerを準備
        // 初期状態は normal_bgm (ループなし) で設定
        currentBgmResId = R.raw.normal_bgm
        isBgmLooping = false
        recreateNormalPlayer()

        // 折り目表示時のMediaPlayerを準備 (Async)
        recreateCreasePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val seekTo = intent?.getIntExtra(EXTRA_SEEK_TO, 0) ?: 0

        when (intent?.action) {
            ACTION_PLAY_NORMAL -> {
                Log.d(TAG, "ACTION_PLAY_NORMAL received, seekTo: $seekTo")
                currentMode = MODE_NORMAL

                // creasePlayerを停止
                if (creasePlayer?.isPlaying == true) {
                    creasePlayer?.pause()
                }

                if (!isMuted) {
                    try {
                        // Reset to normal_bgm sequence
                        // 現在ループモード(isBgmLooping=true)の場合や、曲が違う場合は再作成してシーケンスをリセット
                        val startResId = R.raw.normal_bgm
                        val needsRecreate =
                                (normalPlayer == null ||
                                        currentBgmResId != startResId ||
                                        isBgmLooping)

                        if (needsRecreate) {
                            Log.d(TAG, "Initializing normal_bgm sequence (Force Reset)")
                            currentBgmResId = startResId
                            isBgmLooping = false
                            recreateNormalPlayer()
                        }

                        // 再生要求
                        pendingSeekToNormal = seekTo
                        if (!isNormalPrepared) {
                            pendingPlayNormal = true
                            Log.d(TAG, "normalPlayer not ready, play pending")
                        } else {
                            normalPlayer?.let { player ->
                                if (player.isPlaying) {
                                    player.pause()
                                }
                                player.seekTo(seekTo)
                                player.start()
                                Log.d(TAG, "normalPlayer started from position: $seekTo")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting normalPlayer", e)
                        // エラーが発生した場合、MediaPlayerを再作成して再試行
                        try {
                            recreateNormalPlayer()
                            pendingPlayNormal = true
                            pendingSeekToNormal = seekTo
                        } catch (e2: Exception) {
                            Log.e(TAG, "Failed to recover normalPlayer", e2)
                        }
                    }
                }
            }
            ACTION_PLAY_CREASE -> {
                Log.d(TAG, "ACTION_PLAY_CREASE received, seekTo: $seekTo")
                currentMode = MODE_CREASE

                // normalPlayerを停止
                if (normalPlayer?.isPlaying == true) {
                    normalPlayer?.pause()
                }

                playCreaseBgm(seekTo)
            }
            ACTION_TOGGLE_MUTE -> {
                handleMute(!isMuted)
            }
            ACTION_SET_MUTE -> {
                val shouldMute = intent?.getBooleanExtra(EXTRA_IS_MUTED, false) ?: false
                handleMute(shouldMute)
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                // プレーヤーを解放して、次回の再生時に再作成するようにする
                normalPlayer?.release()
                normalPlayer = null
                creasePlayer?.release()
                creasePlayer = null

                Log.d(TAG, "Players released, service remains active, currentMode: $currentMode")
            }
            ACTION_PLAY_SE -> {
                if (!isMuted && seSoundId != 0) {
                    soundPool?.play(seSoundId, SE_VOLUME, SE_VOLUME, 0, 0, 1.0f)
                }
            }
        }
        return START_STICKY
    }

    private fun handleMute(shouldMute: Boolean) {
        isMuted = shouldMute
        Log.d(TAG, "Mute set: $isMuted")
        if (isMuted) {
            // ミュート時はnormalPlayerのみ一時停止
            normalPlayer?.pause()
        } else {
            // ミュート解除時は、現在のモードに応じて再生
            try {
                if (currentMode == MODE_NORMAL) {
                    if (isNormalPrepared && normalPlayer?.isPlaying == false) {
                        normalPlayer?.start()
                        Log.d(TAG, "Resumed normalPlayer after unmute")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming player", e)
            }
        }
    }

    private fun recreateNormalPlayer() {
        try {
            normalPlayer?.release()
            normalPlayer = MediaPlayer()
            isNormalPrepared = false

            val afd = resources.openRawResourceFd(currentBgmResId) ?: return

            normalPlayer?.apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                isLooping = isBgmLooping
                setVolume(0.5f, 0.5f)

                // normal_bgmが終わったらランダムループへ移行
                if (!isBgmLooping && currentBgmResId == R.raw.normal_bgm) {
                    setOnCompletionListener { playRandomMenuBgm() }
                }

                setOnPreparedListener {
                    isNormalPrepared = true
                    Log.d(TAG, "normalPlayer prepared")
                    if (pendingPlayNormal && !isMuted) {
                        pendingPlayNormal = false
                        it.seekTo(pendingSeekToNormal)
                        it.start()
                        Log.d(TAG, "normalPlayer ended pending play")
                    }
                }
                prepareAsync()
            }
            Log.d(
                    TAG,
                    "normalPlayer recreating enable async: $currentBgmResId, looping: $isBgmLooping"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error recreating normalPlayer", e)
        }
    }

    private fun playRandomMenuBgm() {
        if (currentMode != MODE_NORMAL || isMuted) return

        try {
            // ランダムにmenubgm1かmenubgm2を選択
            val useFirst = (0..1).random() == 0
            val menu1Id = resources.getIdentifier("menubgm1", "raw", packageName)
            val menu2Id = resources.getIdentifier("menubgm2", "raw", packageName)

            // リソースが見つからない場合のフォールバック（normal_bgmループ）
            if (menu1Id == 0 && menu2Id == 0) {
                Log.e(TAG, "Menu BGMs not found, falling back to normal_bgm loop")
                currentBgmResId = R.raw.normal_bgm
                isBgmLooping = true
            } else {
                // 片方しかなければある方を使う、両方あればランダム
                if (menu1Id != 0 && menu2Id != 0) {
                    currentBgmResId = if (useFirst) menu1Id else menu2Id
                } else if (menu1Id != 0) {
                    currentBgmResId = menu1Id
                } else {
                    currentBgmResId = menu2Id
                }
                isBgmLooping = true
            }

            recreateNormalPlayer()
            pendingPlayNormal = true
            pendingSeekToNormal = 0
            Log.d(TAG, "Transitioned to random menu BGM: $currentBgmResId")
        } catch (e: Exception) {
            Log.e(TAG, "Error in playRandomMenuBgm", e)
        }
    }

    private fun recreateCreasePlayer() {
        try {
            creasePlayer?.release()
            creasePlayer = MediaPlayer()
            isCreasePrepared = false

            val afd = resources.openRawResourceFd(R.raw.crease_bgm) ?: return

            creasePlayer?.apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(0.5f, 0.5f)
                setOnPreparedListener {
                    isCreasePrepared = true
                    Log.d(TAG, "creasePlayer prepared")
                    if (pendingPlayCrease) {
                        pendingPlayCrease = false
                        it.seekTo(pendingSeekToCrease)
                        it.start()
                    }
                }
                prepareAsync()
            }
            Log.d(TAG, "creasePlayer recreating async")
        } catch (e: Exception) {
            Log.e(TAG, "Error recreating creasePlayer", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        normalPlayer?.release()
        creasePlayer?.release()

        normalPlayer = null
        creasePlayer = null
        soundPool?.release()
        soundPool = null
    }

    private fun playCreaseBgm(seekTo: Int) {
        // モードが変わっている場合は再生しない
        if (currentMode != MODE_CREASE) return
        try {
            // creasePlayerがnullまたは無効な状態の場合は再作成
            if (creasePlayer == null) {
                Log.w(TAG, "creasePlayer is null, recreating...")
                recreateCreasePlayer()
            }

            pendingSeekToCrease = if (seekTo == 0) 65000 else seekTo

            if (isCreasePrepared) {
                creasePlayer?.let { player ->
                    if (player.isPlaying) player.pause()
                    player.seekTo(pendingSeekToCrease)
                    player.start()
                    Log.d(TAG, "creasePlayer started from: $pendingSeekToCrease")
                }
            } else {
                pendingPlayCrease = true
                Log.d(TAG, "creasePlayer not ready, pending play")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting creasePlayer", e)
            try {
                recreateCreasePlayer()
                pendingPlayCrease = true
                pendingSeekToCrease = if (seekTo == 0) 65000 else seekTo
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to recover creasePlayer", e2)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
