package com.maoyou.typingpet

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * 設定で選んだ言語(日本語/English/한국어/简体中文)をActivity/Serviceに適用するためのヘルパー。
 * "system"の場合も、既に別言語で包まれた Context から戻せるよう端末の言語で作り直す。
 */
object LocaleHelper {
    fun wrap(context: Context): Context {
        val code = Prefs.getLanguage(context)
        if (code == Prefs.LANG_SYSTEM) {
            val system = Resources.getSystem().configuration.locales
            val locale = if (system.isEmpty) Locale.getDefault() else system[0]
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }

        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
