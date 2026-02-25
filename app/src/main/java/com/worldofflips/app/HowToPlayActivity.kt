package com.worldofflips.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class HowToPlayActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_how_to_play)

        findViewById<PopButton>(R.id.btnBack).setOnClickListener { finish() }
    }
}
