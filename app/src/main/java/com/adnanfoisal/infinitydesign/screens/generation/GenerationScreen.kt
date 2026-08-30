package com.adnanfoisal.infinitydesign.screens.generation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adnanfoisal.infinitydesign.viewmodel.GenerationViewModel

@Composable
fun GenerationScreen(
    onBlueprintReady: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: GenerationViewModel = hiltViewModel(),
) {
    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is GenerationViewModel.UiState.Success) {
            val s = state as GenerationViewModel.UiState.Success
            onBlueprintReady(s.blueprint.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Design", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Describe the design you want. The LLM is your art director — it will interpret your prompt into a creative direction.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Prompt") },
                placeholder = { Text("e.g. Create a futuristic robotics competition poster…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
            )
            OutlinedTextField(
                value = style,
                onValueChange = { style = it },
                label = { Text("Style direction (optional)") },
                placeholder = { Text("e.g. dark technical / editorial / minimal") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            when (val s = state) {
                is GenerationViewModel.UiState.Idle -> {
                    Button(
                        onClick = { if (prompt.isNotBlank()) viewModel.generate(prompt, style.ifBlank { null }) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = prompt.isNotBlank(),
                    ) { Text("Generate Direction") }
                }
                is GenerationViewModel.UiState.Loading -> {
                    val stageText = when (s.stage) {
                        GenerationViewModel.Stage.ANALYZING -> "Analyzing prompt…"
                        GenerationViewModel.Stage.BUILDING_DIRECTION -> "Building creative direction…"
                        GenerationViewModel.Stage.DONE -> "Done"
                    }
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stageText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.cancel() }) { Text("Cancel") }
                    }
                }
                is GenerationViewModel.UiState.Success -> {
                    // navigation handled by LaunchedEffect
                }
                is GenerationViewModel.UiState.Error -> {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { if (prompt.isNotBlank()) viewModel.generate(prompt, style.ifBlank { null }) }) {
                            Text("Retry")
                        }
                    }
                }
                GenerationViewModel.UiState.Cancelled -> {
                    Text("Operation cancelled.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { if (prompt.isNotBlank()) viewModel.generate(prompt, style.ifBlank { null }) }) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}
