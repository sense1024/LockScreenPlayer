package com.lockscreen.player.locale

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.lockscreen.player.LockScreenPlayerApp

object LocalePreference {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun getLanguage(context: Context): AppLanguage {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""
        return AppLanguage.fromStoredTag(stored.ifEmpty { null })
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag ?: "")
            .apply()
        apply(context, language)
        (context.applicationContext as? LockScreenPlayerApp)?.recreateNotificationChannel()
    }

    fun applyStoredLocale(context: Context) {
        apply(context, getLanguage(context))
    }

    private fun apply(context: Context, language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
