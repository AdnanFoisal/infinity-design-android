@file:OptIn(ExperimentalMaterial3Api::class)

package com.adnanfoisal.infinitydesign.screens.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.history.DesignHistory
import com.adnanfoisal.infinitydesign.export.png.AndroidCanvasSurface
import com.adnanfoisal.infinitydesign.export.png.SkiaRendererAndroid
import com.adnanfoisal.infinitydesign.graphics.procedural.ProceduralRegistry
import com.adnanfoisal.infinitydesign.graphics.renderer.RenderQuality
import com.adnanfoisal.infinitydesign.viewmodel.EditorViewModel
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(projectId) { viewModel.load(projectId) }
    val state by viewModel.state.collectAsState()

    // Procedural registry — DI'd in real app, here we use EntryPointAccessors for the canvas composable.
    val context = LocalContext.current
    val registry = remember { ProceduralRegistry() }
    val renderer = remember { SkiaRendererAndroid(registry) }

    var selectedId by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) { Icon(Icons.Default.Undo, contentDescription = "Undo") }
                    IconButton(onClick = { viewModel.redo() }) { Icon(Icons.Default.Redo, contentDescription = "Redo") }
                    IconButton(onClick = { viewModel.save {} }) { Icon(Icons.Default.Save, contentDescription = "Save") }
                },
            )
        },
        bottomBar = {
            val st = state
            if (st is EditorViewModel.UiState.Ready) {
                EditorToolbar(
                    selectionId = selectedId,
                    onDelete = {
                        val id = selectedId ?: return@EditorToolbar
                        val cmd = com.adnanfoisal.infinitydesign.design.commands.DesignCommand.DeleteElement(
                            "c-${System.nanoTime()}", id)
                        viewModel.pushCommand(cmd)
                        selectedId = null
                    },
                    onExport = {
                        // Handled by parent screen via bottom sheet.
                    },
                )
            }
        },
    ) { padding ->
        when (val s = state) {
            EditorViewModel.UiState.Loading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            EditorViewModel.UiState.NotFound -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Project not found.")
                }
            }
            is EditorViewModel.UiState.Error -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${s.message}")
                }
            }
            is EditorViewModel.UiState.Ready -> {
                DesignCanvas(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    history = s.history,
                    renderer = renderer,
                    registry = registry,
                    selectedId = selectedId,
                    onSelect = { id -> selectedId = id },
                    scale = scale,
                    offset = offset,
                    onTransform = { zoomChange, panChange ->
                        scale = (scale * zoomChange).coerceIn(0.25f, 4f)
                        offset += panChange
                    },
                )
            }
        }
    }
}

@Composable
private fun DesignCanvas(
    modifier: Modifier,
    history: DesignHistory,
    renderer: SkiaRendererAndroid,
    registry: ProceduralRegistry,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    scale: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit,
) {
    val doc by history.state.collectAsState()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Recompute bitmap when doc changes.
    LaunchedEffect(doc, scale, offset) {
        val w = (doc.canvas.width * scale).toInt().coerceIn(1, 4000)
        val h = (doc.canvas.height * scale).toInt().coerceIn(1, 4000)
        if (w * h * 4 > 256L * 1024 * 1024) return@LaunchedEffect
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        canvas.scale(scale, scale)
        val surface = AndroidCanvasSurface(canvas, registry)
        renderer.render(doc, surface, RenderQuality.EDIT)
        bitmap = bmp
    }

    Box(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color(0xFF1A1A2E))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransform(zoom, pan)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val docW = doc.canvas.width
                    val docH = doc.canvas.height
                    val worldX = (tap.x - offset.x) / scale
                    val worldY = (tap.y - offset.y) / scale
                    // Hit test elements top-down.
                    val hit = doc.elements.reversed().firstOrNull { el ->
                        val b = el.bounds
                        worldX in b.x..(b.x + b.width) && worldY in b.y..(b.y + b.height)
                    }
                    onSelect(hit?.id)
                }
            },
    ) {
        bitmap?.let { bmp ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(bmp, offset.x, offset.y, null)
                    // Selection highlight.
                    val sel = selectedId
                    if (sel != null) {
                        val el = doc.elements.find { it.id == sel }
                        if (el != null) {
                            val b = el.bounds
                            val paint = android.graphics.Paint().apply {
                                color = Color.BLUE; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f
                            }
                            canvas.nativeCanvas.drawRect(
                                b.x * scale + offset.x, b.y * scale + offset.y,
                                (b.x + b.width) * scale + offset.x,
                                (b.y + b.height) * scale + offset.y, paint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    selectionId: String?,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    BottomAppBar {
        IconButton(onClick = onDelete, enabled = selectionId != null) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
        IconButton(onClick = onExport) {
            Icon(Icons.Default.Share, contentDescription = "Export")
        }
    }
}
