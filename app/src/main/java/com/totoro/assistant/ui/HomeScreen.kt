package com.totoro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings

@Composable
fun HomeScreen(
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    var listening by remember { mutableStateOf(false) }
    var lastCommand by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(40.dp))
            Text("🌳", fontSize = 96.sp)
            Text("Тоторо", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                if (listening) "Слухаю 'Гей, Тоторо'…" else "Натисни ▶ щоб почати",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!listening) {
                    FilledTonalButton(onClick = {
                        onStartListening()
                        listening = true
                    }) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Увімкнути")
                    }
                } else {
                    Button(onClick = {
                        onStopListening()
                        listening = false
                    }) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Вимкнути")
                    }
                }
            }

            lastCommand?.let {
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Остання команда:", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
