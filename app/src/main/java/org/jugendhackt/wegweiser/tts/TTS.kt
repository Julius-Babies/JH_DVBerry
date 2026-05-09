package org.jugendhackt.wegweiser.tts

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.getSystemService
import java.util.Locale
import org.jugendhackt.wegweiser.language.language

class TTS(context: Context) {

    private val context = context.applicationContext
    private val textToSpeech: TextToSpeech
    private val vibrator: Vibrator? = context.getSystemService()
    private var fallbackPlayer: MediaPlayer? = null
    private var isSpeaking = false
    private var isInitialized = false
    private var hasPlayedUnavailableNotice = false
    private val language = language(this@TTS.context)
    private val forceTtsUnavailableForTesting = true

    init {
        textToSpeech = TextToSpeech(this@TTS.context, OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech.setLanguage(
                    if (language.getLanguage() == "de") Locale.GERMAN
                    else if (language.getLanguage() == "en") Locale.ENGLISH
                    else Locale.ENGLISH
                )
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language not supported or data missing.")
                    isInitialized = false
                    announceUnavailableOnStartup()
                } else {
                    isInitialized = true
                }
            } else {
                Log.e("TTS", "Failed to initialize TextToSpeech: $status")
                isInitialized = false
                announceUnavailableOnStartup()
            }
        })
    }

    /**
     * Will not speak if an output is already in progress
     */
    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        if (forceTtsUnavailableForTesting) {
            Log.w("TTS", "TTS unavailable (forced for testing), skipping speak request.")
            notifyTtsUnavailable(playVoice = !hasPlayedUnavailableNotice)
            hasPlayedUnavailableNotice = true
            onFinished?.invoke()
            return
        }

        if (!isInitialized) {
            Log.w("TTS", "TTS not initialized, skipping speak request.")
            notifyTtsUnavailable(playVoice = false)
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
        fallbackPlayer?.release()
        fallbackPlayer = null
        isSpeaking = false
    }

    private fun notifyTtsUnavailable(playVoice: Boolean) {
        if (playVoice) {
            playUnavailableNotice()
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 70, 150), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 150, 70, 150), -1)
            }
        } catch (e: SecurityException) {
            Log.w("TTS", "Vibration unavailable", e)
        }
    }

    private fun announceUnavailableOnStartup() {
        notifyTtsUnavailable(playVoice = true)
    }

    private fun playUnavailableNotice() {
        val resourceName = if (language.getLanguage() == "de") "tts_unavailable_de" else "tts_unavailable_en"
        val rawResId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        if (rawResId == 0) {
            Log.w("TTS", "Fallback voice message missing: res/raw/$resourceName")
            return
        }

        try {
            fallbackPlayer?.release()
            fallbackPlayer = null
            val player = MediaPlayer.create(context, rawResId) ?: return
            fallbackPlayer = player
            player.setOnCompletionListener {
                it.release()
                if (fallbackPlayer === it) fallbackPlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (fallbackPlayer === mp) fallbackPlayer = null
                true
            }
            player.start()
        } catch (e: Exception) {
            Log.w("TTS", "Could not play fallback voice message", e)
            fallbackPlayer?.release()
            fallbackPlayer = null
        }
    }
}
