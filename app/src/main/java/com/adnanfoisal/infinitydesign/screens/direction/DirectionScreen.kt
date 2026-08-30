package com.adnanfoisal.infinitydesign.screens.direction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adnanfoisal.infinitydesign.viewmodel.DirectionViewModel

@Composable
fun DirectionScreen(
    blueprintId: String,
    onAccept: (String) -> Unit,
    onRegenerate: () -> Unit,
    onBack: () -> Unit,
    viewModel: DirectionViewModel = hiltViewModel(),
) {
    LaunchedEffect(blueprintId) { viewModel.load(blueprintId) }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Creative Direction", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = onRegenerate) { Icon(Icons.Default.Refresh, contentDescription = "Regenerate") } },
            )
        },
        bottomBar = {
            if (state is DirectionViewModel.UiState.Loaded) {
                Button(
                    onClick = { viewModel.accept { projectId -> onAccept(projectId) } },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Use This Direction") }
            }
        },
    ) { padding ->
        when (val s = state) {
            DirectionViewModel.UiState.Loading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is DirectionViewModel.UiState.NotFound -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Blueprint not found.")
                }
            }
            is DirectionViewModel.UiState.Loaded -> {
                val bp = s.blueprint
                Column(
                    modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(bp.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(bp.purpose, style = MaterialTheme.typography.bodyMedium)
                    Divider()
                    Section("Mood") { Text(bp.mood) }
                    Section("Visual Direction") { Text(bp.visualDirection) }
                    Section("Composition") { Text(bp.composition) }
                    Section("Density") { Text(bp.density.name) }
                    Section("Palette") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Swatch(bp.palette.primary, "primary")
                            Swatch(bp.palette.secondary, "secondary")
                            Swatch(bp.palette.accent, "accent")
                            Swatch(bp.palette.background, "background")
                            Swatch(bp.palette.foreground, "foreground")
                        }
                    }
                    Section("Typography") {
                        Text("Display: ${bp.typography.displayRole} (${bp.typography.displayWeight})")
                        Text("Body: ${bp.typography.bodyRole} (${bp.typography.bodyWeight})")
                        Text("Caption: ${bp.typography.captionRole}")
                    }
                    Section("Visual Language") {
                        Text(bp.visualLanguage.joinToString(" · "))
                    }
                    Section("Texture") {
                        Text(bp.texture.joinToString(", ").ifBlank { "none" })
                    }
                    Section("Decorative") {
                        Text(bp.decorative.joinToString(", ").ifBlank { "none" })
                    }
                    Section("Lighting") { Text(bp.lighting.ifBlank { "ambient" }) }
                    Section("Imagery") { Text(bp.imagery) }
                    Section("Hierarchy") {
                        bp.hierarchy.forEach {
                            Text("• ${it.label} (importance ${it.importance})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Section("Protected Content") {
                        bp.semanticContent.filter { it.protected }.forEach {
                            Text("• ${it.role}: ${it.content}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Swatch(hex: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray))
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
