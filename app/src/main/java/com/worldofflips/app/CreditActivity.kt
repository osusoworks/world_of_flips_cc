package com.worldofflips.app

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class CreditActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credit)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }
}
