package com.totoro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    listening: Boolean,
    lastCommand: String?,
    statusText: String?,
    lastError: String?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onOpenSettings: () -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Налаштування")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
            ) {
                Spacer(Modifier.height(40.dp))
                Text("🌳", fontSize = 96.sp)
                Text("Тоторо", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = statusText ?: "Готовий. Натисни ▶ щоб почати слухати.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                lastError?.let {
                    Text(
                        text = "⚠ $it",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(24.dp))

                if (!listening) {
                    Button(
                        onClick = onStartListening,
                        modifier = Modifier.size(width = 220.dp, height = 64.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Увімкнути", fontSize = 18.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onStopListening,
                        modifier = Modifier.size(width = 220.dp, height = 64.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Вимкнути", fontSize = 18.sp)
                    }
                }

                lastCommand?.let {
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Остання команда:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
