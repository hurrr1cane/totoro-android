package com.totoro.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.totoro.assistant.diagnostics.LogStore
import com.totoro.assistant.prefs.TotoroPrefs
import com.totoro.assistant.service.TotoroListenerService

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen() {
        val prefs = TotoroPrefs(applicationContext)
        var trigger by remember { mutableStateOf(prefs.trigger) }
        var haUrl by remember { mutableStateOf(prefs.haUrl) }
        var haToken by remember { mutableStateOf(prefs.haToken) }
        var hermes by remember { mutableStateOf(prefs.hermesUrl) }
        var tgToken by remember { mutableStateOf(prefs.telegramToken) }
        var tgChat by remember { mutableStateOf(prefs.telegramChat) }
        var obsidianUrl by remember { mutableStateOf(prefs.obsidianUrl) }
        var language by remember { mutableStateOf(prefs.language) }
        var status by remember { mutableStateOf("") }
        var logs by remember { mutableStateOf(LogStore.readAll()) }

        MaterialTheme(colorScheme = darkColorScheme()) {
            Scaffold(topBar = {
                TopAppBar(title = { Text("Налаштування Тоторо") })
            }) { p ->
                Column(
                    modifier = Modifier
                        .padding(p)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Wake-word зараз: «$trigger»", fontWeight = FontWeight.Bold)
                    Text("Служба активна: ${TotoroListenerService.isRunning}")
                    Text("Остання помилка: ${TotoroListenerService.lastError ?: "—"}")

                    HorizontalDivider()

                    OutlinedTextField(value = trigger, onValueChange = { trigger = it }, label = { Text("Wake-word") }, singleLine = true)
                    OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text("Мова (uk-UA, en-US, …)") }, singleLine = true)
                    OutlinedTextField(value = hermes, onValueChange = { hermes = it }, label = { Text("Hermes endpoint") }, singleLine = true)
                    OutlinedTextField(value = haUrl, onValueChange = { haUrl = it }, label = { Text("Home Assistant URL") }, singleLine = true)
                    OutlinedTextField(value = haToken, onValueChange = { haToken = it }, label = { Text("HA long-lived token") }, singleLine = true)
                    OutlinedTextField(value = tgToken, onValueChange = { tgToken = it }, label = { Text("Telegram bot token") }, singleLine = true)
                    OutlinedTextField(value = tgChat, onValueChange = { tgChat = it }, label = { Text("Telegram chat id") }, singleLine = true)
                    OutlinedTextField(value = obsidianUrl, onValueChange = { obsidianUrl = it }, label = { Text("Obsidian webhook URL") }, singleLine = true)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = {
                            prefs.trigger = trigger
                            prefs.language = language
                            prefs.hermesUrl = hermes
                            prefs.haUrl = haUrl
                            prefs.haToken = haToken
                            prefs.telegramToken = tgToken
                            prefs.telegramChat = tgChat
                            prefs.obsidianUrl = obsidianUrl
                            status = "Збережено."
                        }) { Text("Зберегти") }
                        OutlinedButton(onClick = { finish() }) { Text("Закрити") }
                    }

                    Text(status, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Лог (останні ${logs.length} символів):", fontWeight = FontWeight.Bold)
                    Card {
                        Text(
                            logs.takeLast(3000),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { logs = LogStore.readAll() }) { Text("Оновити лог") }
                        OutlinedButton(onClick = { LogStore.share(this@SettingsActivity) }) { Text("Поділитись") }
                        OutlinedButton(onClick = { LogStore.clear(); logs = "" }) { Text("Очистити") }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("Файл логів: ${LogStore.path() ?: "—"}", fontSize = 11.sp)
                    Text("Якщо додаток падає — надішли цей лог розробнику.", fontSize = 11.sp)
                }
            }
        }
    }
}
