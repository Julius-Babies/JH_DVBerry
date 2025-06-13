package org.jugendhackt.wegweiser.language

import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.util.Log

class language(val context: Context) {
    private val TAG = "Language"
    private val en = en()
    private val de = de()

    private fun getDeviceLanguage(): String {
        val lang = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }
        Log.d(TAG, "Device language detected: $lang")
        return lang
    }

    fun getLanguage(): String {
        return getDeviceLanguage()
    }

    fun getString(key: String): String {
        val lang = getDeviceLanguage()
        Log.d(TAG, "Getting string for key: $key in language: $lang")
        
        return when (lang) {
            "de" -> de.getString(key)
            "en" -> en.getString(key)
            else -> {
                Log.d(TAG, "Language $lang not supported, falling back to English")
                en.getString(key)
            }
        }
    }
}