package com.droid.dolphy.widget

import android.content.Context
import android.content.Intent
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Authenticates broadcasts sent from app widgets.
 *
 * Widget receivers must stay exported so the launcher can deliver
 * APPWIDGET_UPDATE. Every state-changing action therefore carries an
 * unguessable per-install token inside the (immutable) PendingIntent. A
 * broadcast without a matching token is ignored.
 */
object WidgetSecurity {

    const val EXTRA_TOKEN = "com.droid.dolphy.extra.WIDGET_TOKEN"

    private const val PREFS = "widget_security"
    private const val KEY_TOKEN = "install_token"
    private const val TOKEN_BYTES = 32

    private val random = SecureRandom()

    @Synchronized
    fun token(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.let { return it }

        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val token = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        check(prefs.edit().putString(KEY_TOKEN, token).commit()) {
            "Unable to persist widget install token"
        }
        return token
    }

    fun isValid(context: Context, intent: Intent): Boolean {
        val provided = intent.getStringExtra(EXTRA_TOKEN) ?: return false
        val expected = token(context)
        return MessageDigest.isEqual(
            provided.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
