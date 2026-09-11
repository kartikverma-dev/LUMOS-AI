package com.lumos.ai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.lumos.ai.data.LumosSettings
import com.lumos.ai.data.SettingsRepository
import com.lumos.ai.network.LlamaApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: SettingsRepository, onBack: () -> Unit) {
    val settings by repo.settings.collectAsState(initial = LumosSettings())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Connection", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.serverUrl,
                onValueChange = { v -> scope.launch { repo.setServerUrl(v) } },
                label = { Text("llama-server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = {
                    scope.launch {
                        testResult = "checking…"
                        val ok = withContext(Dispatchers.IO) {
                            LlamaApiClient().isHealthy(settings.serverUrl)
                        }
                        testResult = if (ok) "✅ Server is online"
                        else "❌ Cannot reach server"
                    }
                }) { Text("Test connection") }
                Spacer(Modifier.height(8.dp))
            }
            testResult?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            Text("Model", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.modelName,
                onValueChange = { v -> scope.launch { repo.setModelName(v) } },
                label = { Text("GGUF model name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            Text("Generation", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("Temperature: " + "%.2f".format(settings.temperature))
            Slider(
                value = settings.temperature,
                onValueChange = { v -> scope.launch { repo.setTemperature(v) } },
                valueRange = 0f..1.5f
            )
            OutlinedTextField(
                value = settings.contextSize.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { scope.launch { repo.setContextSize(it) } }
                },
                label = { Text("Context size") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.cpuThreads.toString(),
                onValueChange = { v ->
                    v.toIntOrNull()?.let { scope.launch { repo.setCpuThreads(it) } }
                },
                label = { Text("CPU threads") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            Text("System prompt", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.systemPrompt,
                onValueChange = { v -> scope.launch { repo.setSystemPrompt(v) } },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(Modifier.height(16.dp))

            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )) {
                Column(Modifier.padding(12.dp)) {
                    Text("Start the server (Termux)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "llama-server -m dolphin-3b-iq4_xs.gguf --port 8080 -c 4096 -t 4",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(
                            "llama-server -m dolphin-3b-iq4_xs.gguf --port 8080 -c 4096 -t 4"
                        ))
                    }) { Text("Copy command") }
                }
            }
        }
    }
}
