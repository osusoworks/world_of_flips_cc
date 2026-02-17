package com.worldofflips.app

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class UnlockChallengeActivity : AppCompatActivity() {

    private lateinit var timerText: TextView
    private var countDownTimer: CountDownTimer? = null
    private var remainingTimeMillis: Long = 3600000 // 1 hour in ms
    private val TOTAL_TIME = 3600000L
    private var wakeLock: PowerManager.WakeLock? = null
    private var isUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unlock_challenge)

        timerText = findViewById(R.id.timerText)
        
        // Prevent screen sleep
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Setup WakeLock as requested
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Orimekun:UnlockChallenge")
    }

    override fun onResume() {
        super.onResume()
        // Start/Resume timer
        startTimer()
        
        // Acquire wake lock backup
        wakeLock?.acquire(TOTAL_TIME + 60000) 
    }

    override fun onPause() {
        super.onPause()
        // Pause timer
        countDownTimer?.cancel()
        
        // If screen is off, reset timer (Challenge failed)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
             remainingTimeMillis = TOTAL_TIME
             updateTimerUI(TOTAL_TIME)
        }

        // Release wake lock
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun startTimer() {
        countDownTimer?.cancel()
        
        if (remainingTimeMillis <= 0) {
            return
        }

        countDownTimer = object : CountDownTimer(remainingTimeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMillis = millisUntilFinished
                updateTimerUI(millisUntilFinished)
            }

            override fun onFinish() {
                remainingTimeMillis = 0
                updateTimerUI(0)
                performUnlock()
            }
        }.start()
    }

    private fun updateTimerUI(millis: Long) {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        timerText.text = String.format(Locale.getDefault(), " %02d:%02d", minutes, seconds)
    }

    private fun performUnlock() {
        if (isUnlocked) return
        isUnlocked = true

        // Play sound
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.unlockalarm)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Show Snackbar
        Snackbar.make(findViewById(android.R.id.content), "アンロック完了！", Snackbar.LENGTH_LONG).show()

        // Set result URL/Data or SharedPrefs
        // We handle unlock logic in MainActivity based on Result or directly here?
        // Let's set the flag directly here to be safe and return OK
        
        val prefs = getSharedPreferences("com.worldofflips.app.prefs", MODE_PRIVATE)
        // Unlock "Double" crease (radioDouble)
        prefs.edit().putBoolean("double_unlocked", true).apply()

        // Set result OK
        setResult(RESULT_OK)

        // Wait a bit then finish or let user finish?
        // Prompt says "Unlock complete Snackbar display". 
        // Maybe finish after a delay
        timerText.postDelayed({
            finish()
        }, 3000)
    }
}
