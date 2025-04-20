package org.jugendhackt.wegweiser.language

import android.content.Context

class language(val context: Context) {

    private val en = en()
    private val de = de()

    private fun getDeviceLanguage(): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].language
        } else {
            context.resources.configuration.locale.language
        }
    }

    fun getLanguage(): String {
        return getDeviceLanguage()
    }

    fun getString(key: String): String {
        var lang = getDeviceLanguage()

        if (lang == "de") {
            return de.getString(key)
        }
        else if (lang == "en") {
            return en.getString(key)
        }
        else {
            return en.getString(key)
        }
    }
}