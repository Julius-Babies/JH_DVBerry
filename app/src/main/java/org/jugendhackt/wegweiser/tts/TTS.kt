package org.jugendhackt.wegweiser.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TTS(context: Context) {

    private val textToSpeech: TextToSpeech
    private var isSpeaking = false

    init {
        textToSpeech = TextToSpeech(context, OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech.setLanguage(Locale.GERMANY)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Sprache nicht unterstützt oder Daten fehlen.")
                }
            } else {
                throw RuntimeException("Failed to initialize TextToSpeech: $status")
            }
        })
    }

    /**
     * Will not speak if an output is already in progress
     */
    fun speak(text: String, onFinished: (() -> Unit)? = null) {
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
        textToSpeech.stop()
        isSpeaking = false
    }
}