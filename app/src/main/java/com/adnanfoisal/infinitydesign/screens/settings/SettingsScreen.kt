package com.adnanfoisal.infinitydesign.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderKind
import com.adnanfoisal.infinitydesign.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val p by viewModel.prefsFlow.collectAsState()
    val ping by viewModel.pingState.collectAsState()

    var backendUrl by remember { mutableStateOf("http://localhost:8080") }
    var geminiApiKey by remember { mutableStateOf("") }
    var litellmUrl by remember { mutableStateOf(p.litellmUrl) }
    var litellmApiKey by remember { mutableStateOf("") }
    var litellmModel by remember { mutableStateOf(p.litellmModel) }
    var geminiModel by remember { mutableStateOf(p.geminiModel) }
    var provider by remember(p.provider) { mutableStateOf(if (p.provider == "litellm") ProviderKind.LITELLM else ProviderKind.GEMINI) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== Provider selection =====
            Text("LLM Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = provider == ProviderKind.GEMINI,
                    onClick = { provider = ProviderKind.GEMINI; viewModel.setProvider("gemini") },
                    label = { Text("Gemini") },
                )
                FilterChip(
                    selected = provider == ProviderKind.LITELLM,
                    onClick = { provider = ProviderKind.LITELLM; viewModel.setProvider("litellm") },
                    label = { Text("LiteLLM") },
                )
            }

            // ===== Backend URL =====
            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("Backend URL") },
                placeholder = { Text("http://localhost:8080") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                "The Android app talks to the backend over HTTP. The backend holds your LLM provider config and proxies requests. API keys are never logged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ===== Gemini fields =====
            if (provider == ProviderKind.GEMINI) {
                HorizontalDivider()
                Text("Gemini", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = { geminiApiKey = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = geminiModel,
                    onValueChange = { viewModel.setGeminiModel(it); geminiModel = it },
                    label = { Text("Gemini model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    "Latest verified models: gemini-3.7-flash (default), gemini-3.5-flash, gemini-3.1-pro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ===== LiteLLM fields =====
            if (provider == ProviderKind.LITELLM) {
                HorizontalDivider()
                Text("LiteLLM (BYOK)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = litellmUrl,
                    onValueChange = { viewModel.setLitellmUrl(it); litellmUrl = it },
                    label = { Text("LiteLLM proxy URL") },
                    placeholder = { Text("https://your-litellm-proxy.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = litellmApiKey,
                    onValueChange = { litellmApiKey = it },
                    label = { Text("LiteLLM API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = litellmModel,
                    onValueChange = { viewModel.setLitellmModel(it); litellmModel = it },
                    label = { Text("LiteLLM model name") },
                    placeholder = { Text("gpt-4o, claude-3-5-sonnet, …") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // ===== Test / Ping button =====
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = ping !is SettingsViewModel.PingState.Testing,
                    onClick = {
                        viewModel.ping(
                            backendUrl = backendUrl,
                            kind = provider,
                            geminiApiKey = geminiApiKey,
                            litellmUrl = litellmUrl,
                            litellmApiKey = litellmApiKey,
                            litellmModel = litellmModel,
                            geminiModel = geminiModel,
                        )
                    },
                ) { Text("Test Connection") }
                if (ping is SettingsViewModel.PingState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    TextButton(onClick = { viewModel.cancelPing() }) { Text("Cancel") }
                }
            }
            when (val s = ping) {
                is SettingsViewModel.PingState.Ok -> {
                    Text("✓ ${s.provider} / ${s.model} — ${s.durationMs}ms", color = MaterialTheme.colorScheme.primary)
                }
                is SettingsViewModel.PingState.Failed -> {
                    Text("✗ ${s.message}", color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }

            // ===== Theme =====
            HorizontalDivider()
            Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = p.theme == "light", onClick = { viewModel.setTheme("light") }, label = { Text("Light") })
                FilterChip(selected = p.theme == "dark", onClick = { viewModel.setTheme("dark") }, label = { Text("Dark") })
                FilterChip(selected = p.theme == "system", onClick = { viewModel.setTheme("system") }, label = { Text("System") })
            }

            HorizontalDivider()
            Text(
                "Infinity Design — Native Android Procedural AI Design Studio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
