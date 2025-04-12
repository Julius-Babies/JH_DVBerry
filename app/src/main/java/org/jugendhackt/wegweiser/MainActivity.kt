package org.jugendhackt.wegweiser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jugendhackt.wegweiser.ui.theme.WegweiserTheme
import org.jugendhackt.wegweiser.tts.TTS

class MainActivity : ComponentActivity() {

    private lateinit var tts: TTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialisiere Text-to-Speech (TTS)
        tts = TTS(this)
        tts.initialize() // Initialisiere das TTS

        enableEdgeToEdge()
        setContent {
            WegweiserTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel by viewModels<MainViewModel>()
                    var counter by remember { mutableStateOf(0) }

                    Column {
                        Text(viewModel.testText.value)

                        val scope = rememberCoroutineScope()

                        Button(onClick = {
                            scope.launch {
                                tts.speak("Hallo Welt")
                            }
                        }) {
                            Text("SAY TEST text")
                        }
                    }
                }
            }
        }
    }
}