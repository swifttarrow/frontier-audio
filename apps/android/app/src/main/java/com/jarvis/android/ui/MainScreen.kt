package com.jarvis.android.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.android.ws.ConnectionState

@Composable
fun MainScreen(viewModel: JarvisViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = "Jarvis",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (uiState.repoDisplayName.isNotBlank()) {
                    Text(
                        text = uiState.repoDisplayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                ConnectionIndicator(uiState.connectionState)
            }

            // State label and transcript/response area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                StateLabel(uiState.state)
                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.lastTranscript.isNotBlank()) {
                    Text(
                        text = uiState.lastTranscript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (uiState.lastAssistantText.isNotBlank()) {
                    Text(
                        text = uiState.lastAssistantText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
                if (uiState.errorMessage.isNotBlank() && uiState.state == UiState.ERROR) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // PTT Button
            PttButton(
                state = uiState.state,
                onPress = { viewModel.onPttPressed() },
                onRelease = { viewModel.onPttReleased() }
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun PttButton(
    state: UiState,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    // Stable pointerInput: using `state` as a key restarts the gesture when state changes (e.g. THINKING → LISTENING),
    // cancelling the in-flight press so interrupt/stop never completes.
    val currentOnPress = rememberUpdatedState(onPress)
    val currentOnRelease = rememberUpdatedState(onRelease)

    val buttonColor = when (state) {
        UiState.IDLE -> MaterialTheme.colorScheme.primary
        UiState.LISTENING -> Color(0xFFE53935) // red while recording
        UiState.THINKING -> MaterialTheme.colorScheme.tertiary
        UiState.SPEAKING -> Color(0xFF43A047) // green while speaking
        UiState.ERROR -> MaterialTheme.colorScheme.error
    }

    val label = when (state) {
        UiState.IDLE -> "Hold to Talk"
        UiState.LISTENING -> "Listening..."
        UiState.THINKING -> "Tap to cancel"
        UiState.SPEAKING -> "Tap to Stop"
        UiState.ERROR -> "Tap to Retry"
    }

    Surface(
        modifier = Modifier
            .size(160.dp)
            .semantics { contentDescription = "Press to talk button. $label" }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        currentOnPress.value()
                        val released = tryAwaitRelease()
                        if (released) {
                            currentOnRelease.value()
                        }
                    }
                )
            },
        shape = CircleShape,
        color = buttonColor,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StateLabel(state: UiState) {
    val text = when (state) {
        UiState.IDLE -> "Ready"
        UiState.LISTENING -> "Listening"
        UiState.THINKING -> "Processing"
        UiState.SPEAKING -> "Speaking"
        UiState.ERROR -> "Error"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun ConnectionIndicator(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
        ConnectionState.CONNECTING -> "Connecting..." to MaterialTheme.colorScheme.tertiary
        ConnectionState.READY -> "Connected" to Color(0xFF43A047)
        ConnectionState.ERROR -> "Connection Error" to MaterialTheme.colorScheme.error
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}
