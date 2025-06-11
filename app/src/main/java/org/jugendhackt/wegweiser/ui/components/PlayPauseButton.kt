package org.jugendhackt.wegweiser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jugendhackt.wegweiser.language.language

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    canPlay: Boolean,
    language: language,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .clickable(onClick = onClick, enabled = canPlay),
        contentAlignment = Alignment.Center
    ) {
        when {
            isPlaying && canPlay -> {
                Icon(
                    imageVector = Icons.Outlined.Stop,
                    contentDescription = language.getString("contentDescription.stop"),
                    modifier = Modifier
                        .size(256.dp)
                        .padding(16.dp)
                )
            }
            !isPlaying && canPlay -> {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = language.getString("contentDescription.play"),
                    modifier = Modifier
                        .size(256.dp)
                        .padding(16.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = language.getString("contentDescription.loading"),
                    modifier = Modifier
                        .size(256.dp)
                        .padding(16.dp)
                )
            }
        }
    }
} 