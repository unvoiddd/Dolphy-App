package com.droid.dolphy

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLocale {
    /** Stored when the app should follow the device language. */
    const val AUTO = "system"

    fun wrap(context: Context): Context {
        val prefs = context.getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE)
        val locale = resolve(context, prefs.getString("app_language", AUTO))
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    /**
     * Resolves a stored language preference. [AUTO] (and any unset value) follows the
     * device language; anything that is not Arabic or Russian falls back to English.
     */
    fun resolve(context: Context, language: String?): Locale = when (language) {
        "ar" -> Locale.forLanguageTag("ar")
        "ru" -> Locale.forLanguageTag("ru")
        "en" -> Locale.forLanguageTag("en")
        else -> when (deviceLocale(context).language) {
            "ar" -> Locale.forLanguageTag("ar")
            "ru" -> Locale.forLanguageTag("ru")
            else -> Locale.forLanguageTag("en")
        }
    }

    /**
     * Device language: the system per-app locale when one is set (Android 13+),
     * otherwise the system locale.
     */
    private fun deviceLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (appLocales != null && !appLocales.isEmpty) return appLocales[0]
        }
        val configuration = Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    }
}
