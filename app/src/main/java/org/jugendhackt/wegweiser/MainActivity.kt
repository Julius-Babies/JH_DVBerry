package org.jugendhackt.wegweiser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jugendhackt.wegweiser.ui.theme.WegweiserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WegweiserTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Beispielhaftes ViewModel; falls nicht vorhanden, ggf. anpassen oder entfernen
                    val viewModel by viewModels<MainViewModel>()

                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Greeting(
                            name = "Play/Pause",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Hier wird der Play/Pause-Button aufgerufen:
                        PlayPauseButton()

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String = "Fuck Nazis!", modifier: Modifier = Modifier) {
    Text(
        text = name,
        modifier = modifier
    )
}

@Composable
fun PlayPauseButton() {
    var isPlaying by remember { mutableStateOf(false) }
    // Erhalte die aktuelle Bildschirmbreite
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // Setze die Button-Größe auf 40% der Bildschirmbreite
    val buttonSize = screenWidth * 0.4f
    // Wähle die Icon-Größe als 75% des Button-Durchmessers
    val iconSize = buttonSize * 0.75f

    IconButton(
        onClick = { isPlaying = !isPlaying },
        modifier = Modifier.size(buttonSize)
    ) {
        if (isPlaying) {
            Icon(
                imageVector = Icons.Outlined.Pause,
                contentDescription = "Pause",
                modifier = Modifier.size(iconSize)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WegweiserTheme {
        Greeting("Android")
    }
}
