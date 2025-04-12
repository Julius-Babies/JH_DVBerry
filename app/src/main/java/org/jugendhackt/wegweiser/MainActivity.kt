package org.jugendhackt.wegweiser

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jugendhackt.wegweiser.dvb.Dvb
import org.jugendhackt.wegweiser.ui.theme.WegweiserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WegweiserTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel by viewModels<MainViewModel>()
                    var counter = remember { mutableStateOf(0) }
                    Column {
                        Text(viewModel.testText.value)
                        nmbrView(
                            name = "Android (Counter: ${counter.value})",
                            modifier = Modifier.padding(innerPadding)
                        )
                        Button(onClick = {
                            counter.value = counter.value + 1
                        }) {
                            Text("Click me")
                        }
                        Button(onClick = {
                            counter.value = 0
                        }) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun nmbrView(name: String = "Rechnen...", modifier: Modifier = Modifier) {
    Text(
        text = name,  // Or text :)
        modifier = modifier
    )

}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WegweiserTheme {
        Scaffold {
            Text("Hello Data")
        }
    }
}