package com.worldofflips.app

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper

object ContextUtils {
    fun updateContext(context: Context): Context {
        val prefs = context.getSharedPreferences("com.worldofflips.app.prefs", Context.MODE_PRIVATE)

        val fontSelection = prefs.getString("font_selection", "default")
        val themeResId = when (fontSelection) {
            "yomogi" -> R.style.Theme_Orimekun_Yomogi
            "mplus" -> R.style.Theme_Orimekun_Mplus
            else -> R.style.Theme_Orimekun
        }

        val sizeSelection = prefs.getString("size_selection", "medium")
        val fontScale = when (sizeSelection) {
            "small" -> 0.85f
            "large" -> 1.3f
            else -> 1.0f
        }

        val config = Configuration(context.resources.configuration)
        config.fontScale = fontScale

        val updatedContext = context.createConfigurationContext(config)
        return ContextThemeWrapper(updatedContext, themeResId)
    }
}
