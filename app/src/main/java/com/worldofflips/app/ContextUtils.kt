package com.worldofflips.app

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper

object ContextUtils {
    fun updateContext(context: Context): Context {
        return ContextThemeWrapper(context, R.style.Theme_Orimekun)
    }
}
