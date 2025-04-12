package org.jugendhackt.wegweiser.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.CountDownLatch

class TTS(private val context: android.content.Context) {

    private var textToSpeech: TextToSpeech? = null
    private var latch: CountDownLatch? = null

    // Flag, das angibt, ob gerade gesprochen wird
    private var isSpeaking = false

    // Initialisiert das TTS und setzt die Sprache auf Deutsch
    fun initialize() {
        textToSpeech = TextToSpeech(context, OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech?.setLanguage(Locale.GERMANY)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Sprache nicht unterstützt oder Daten fehlen.")
                } else {
                    Log.d("TTS", "TTS erfolgreich initialisiert")
                    // Setzt den Listener, nachdem TTS initialisiert wurde
                    setUtteranceListener()
                }
            } else {
                Log.e("TTS", "TextToSpeech Initialisierung fehlgeschlagen.")
            }
        })
    }

    // Spricht den übergebenen Text und blockiert, bis es fertig ist
    suspend fun speak(text: String) {
        if (isSpeaking) {
            Log.w("TTS", "Es läuft bereits eine Sprachausgabe. Bitte warte, bis sie abgeschlossen ist.")
            return
        }

        if (textToSpeech == null) {
            Log.e("TTS", "TextToSpeech ist nicht initialisiert.")
            return
        }

        // Setze isSpeaking auf true, um zu verhindern, dass gleichzeitig eine weitere Sprachausgabe gestartet wird
        isSpeaking = true

        // Erstelle ein CountDownLatch, das auf das Ende des Sprechvorgangs wartet
        latch = CountDownLatch(1)

        // Sprich den Text im Hintergrund (nicht auf dem Haupt-Thread)
        withContext(Dispatchers.IO) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "key")

            // Blockiere den Hintergrund-Thread bis das Sprechen abgeschlossen ist
            try {
                latch?.await()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            Log.d("TTS", "Sprechen abgeschlossen")
            isSpeaking = false
        }
    }

    // Gibt den TTS-Dienst frei
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    // Setzt den Listener für das Abschließen des Sprechens
    private fun setUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                latch?.countDown()
                Log.d("TTS", "UtteranceProgressListener: onDone")
            }

            override fun onError(utteranceId: String?) {}
        })
    }
}
