package com.worldofflips.app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class QuizActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ContextUtils.updateContext(newBase))
    }

    private lateinit var questionCountText: TextView
    private lateinit var questionText: TextView
    private lateinit var optionButtons: List<Button>

    private lateinit var selectedQuestions: List<Question>
    private var currentQuestionIndex = 0
    private var correctCount = 0
    private var incorrectCount = 0
    private var defaultButtonTint: android.content.res.ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        questionCountText = findViewById(R.id.questionCountText)
        questionText = findViewById(R.id.questionText)
        optionButtons =
                listOf(
                        findViewById(R.id.option1Button),
                        findViewById(R.id.option2Button),
                        findViewById(R.id.option3Button),
                        findViewById(R.id.option4Button)
                )

        // Save default tint
        defaultButtonTint = optionButtons[0].backgroundTintList

        // ランダムに5問選択
        selectedQuestions = QuizData.allQuestions.shuffled().take(5)

        showQuestion()
    }

    private fun showQuestion() {
        if (currentQuestionIndex >= selectedQuestions.size) {
            finishQuiz()
            return
        }

        val q = selectedQuestions[currentQuestionIndex]
        questionCountText.text = "Question ${currentQuestionIndex + 1}/5"
        questionText.text = q.text

        // 選択肢の配置をシャッフルするためのインデックスリスト
        val indices = (0..3).toList().shuffled()

        indices.forEachIndexed { buttonIndex, optionIndex ->
            optionButtons[buttonIndex].text = q.options[optionIndex]
            optionButtons[buttonIndex].backgroundTintList = defaultButtonTint
            optionButtons[buttonIndex].tag = optionIndex // Save the option index
            optionButtons[buttonIndex].setOnClickListener {
                // ボタンを無効化して連打防止
                optionButtons.forEach { it.isEnabled = false }
                checkAnswer(optionIndex, optionButtons[buttonIndex])
            }
            optionButtons[buttonIndex].isEnabled = true
        }
    }

    private fun checkAnswer(selectedIndex: Int, clickedButton: Button) {
        val q = selectedQuestions[currentQuestionIndex]

        // 全てのボタンに正誤表示を追加
        optionButtons.forEach { button ->
            val idx = button.tag as Int
            if (idx in q.correctIndices) {
                button.text = "${button.text} ○"
            } else {
                button.text = "${button.text} ✖"
            }
        }

        if (selectedIndex in q.correctIndices) {
            correctCount++
            clickedButton.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
            Toast.makeText(this, "正解！", Toast.LENGTH_SHORT).show()
        } else {
            incorrectCount++
            clickedButton.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            Toast.makeText(this, "不正解...", Toast.LENGTH_SHORT).show()
        }

        // 結果を確認する時間を設けるため2秒待機
        questionText.postDelayed(
                {
                    if (incorrectCount >= 4) {
                        finishQuiz()
                    } else {
                        currentQuestionIndex++
                        showQuestion()
                    }
                },
                2000
        )
    }

    private fun finishQuiz() {
        if (correctCount >= 4) { // 5問中4問以上で合格
            setResult(Activity.RESULT_OK)
            Toast.makeText(this, "クイズ突破！ロック解除！", Toast.LENGTH_LONG).show()
        } else {
            setResult(Activity.RESULT_CANCELED)
            Toast.makeText(this, "残念... (${correctCount}/5問正解). 4問以上で合格です。", Toast.LENGTH_LONG)
                    .show()
        }
        finish()
    }
}
