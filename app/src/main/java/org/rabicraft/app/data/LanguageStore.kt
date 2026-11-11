package org.rabicraft.app.data

import android.content.Context

/** Interface language chosen by the user; [SYSTEM] follows the device locale. */
enum class AppLanguage(val storageValue: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    FRENCH("fr")
}

class LanguageStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(): AppLanguage = when (prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.storageValue)) {
        AppLanguage.ENGLISH.storageValue -> AppLanguage.ENGLISH
        AppLanguage.FRENCH.storageValue -> AppLanguage.FRENCH
        else -> AppLanguage.SYSTEM
    }

    fun set(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.storageValue).apply()
    }

    private companion object {
        const val PREFS_NAME = "rabicraft_settings"
        const val KEY_LANGUAGE = "app_language"
    }
}
