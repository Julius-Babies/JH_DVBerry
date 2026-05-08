package org.jugendhackt.wegweiser.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import org.jugendhackt.wegweiser.language.language

class TTS(context: Context) {

    private val textToSpeech: TextToSpeech
    private var isSpeaking = false
    private var isInitialized = false
    private val language = language(context)

    init {
        textToSpeech = TextToSpeech(context, OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech.setLanguage(
                    if (language.getLanguage() == "de") Locale.GERMAN
                    else if (language.getLanguage() == "en") Locale.ENGLISH
                    else Locale.ENGLISH
                )
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported or data missing.")
                    isInitialized = false
                } else {
                    isInitialized = true
                }
            } else {
                Log.e("TTS", "Failed to initialize TextToSpeech: $status")
                isInitialized = false
            }
        })
    }

    /**
     * Will not speak if an output is already in progress
     */
    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w("TTS", "TTS not initialized, skipping speak request.")
            onFinished?.invoke()
            return
        }

        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                onFinished?.invoke()
            }

            override fun onError(utteranceId: String?) {}
        })

        if (isSpeaking) {
            Log.w("TTS", "Another speaking is in progress. This speak-request will be ignored.")
            return
        }

        isSpeaking = true
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "key")
    }

    fun stop() {
        if (!isInitialized) {
            return
        }
        textToSpeech.stop()
        isSpeaking = false
    }
}
