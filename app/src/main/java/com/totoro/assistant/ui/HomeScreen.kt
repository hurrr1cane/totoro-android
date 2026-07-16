package com.totoro.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    listening: Boolean,
    lastCommand: String?,
    statusText: String?,
    lastError: String?,
    liveEvents: List<String>,
    speechAvailable: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onOpenSettings: () -> Unit,
    onTestCommand: (String) -> Unit,
    onTestVoiceListen: () -> Unit,
    onRefresh: () -> Unit
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
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Налаштування")
            }
            TextButton(
                onClick = onRefresh,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) { Text("Оновити") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Text("🌳", fontSize = 72.sp)
                Text("Тоторо", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = statusText ?: "Готовий. Натисни ▶ щоб почати слухати.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                lastError?.let {
                    Text(
                        text = "⚠ $it",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (!listening) {
                        Button(
                            onClick = onStartListening,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Увімкнути")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStopListening,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Вимкнути")
                        }
                    }
                    OutlinedButton(
                        onClick = onTestVoiceListen,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Тест мікрофона") }
                }

                lastCommand?.let {
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Остання команда:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(it, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (speechAvailable) Color(0xFF4CAF50) else Color(0xFFF44336),
                                        shape = RoundedCornerShape(5.dp)
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (speechAvailable) "SR: готовий" else "SR: недоступний",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Події (оновлюй 🔄, якщо порожньо):", fontSize = 11.sp)
                        if (liveEvents.isEmpty()) {
                            Text("(поки порожньо — зачекай або натисни 'Тест мікрофона')",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            liveEvents.takeLast(40).forEach { ev ->
                                Text(
                                    "• $ev",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                ManualCommandInput(onSubmit = onTestCommand)
            }
        }
    }
}

@Composable
private fun ManualCommandInput(onSubmit: (String) -> Unit) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Введи команду… напр. 'увімкни youtube'", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
        )
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onSubmit(text.trim())
                    text = ""
                }
            }
        ) { Text("▶") }
    }
}
