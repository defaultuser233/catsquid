package com.maoyou.typingpet

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 設定で選んだ言語(日本語/English/한국어/简体中文)をActivityに適用するためのヘルパー。
 * "system"の場合は端末の言語設定のまま何もしない。
 */
object LocaleHelper {
    fun wrap(context: Context): Context {
        val code = Prefs.getLanguage(context)
        if (code == Prefs.LANG_SYSTEM) {
            Locale.setDefault(Configuration(context.resources.configuration).locales[0])
            return context
        }

        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
