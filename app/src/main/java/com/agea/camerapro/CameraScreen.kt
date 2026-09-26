package com.agea.camerapro

import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterPreset.ALL.first()) }
    var resolution by remember { mutableStateOf(ResolutionOption.FHD_1080) }
    var stabOn by remember { mutableStateOf(true) }
    var flickerOn by remember { mutableStateOf(true) }
    var torchOn by remember { mutableStateOf(false) }
    var showResMenu by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }

    LaunchedEffect(recording) {
        seconds = 0
        while (recording) {
            delay(1000)
            seconds++
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(ColorBackground)) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).also { pv ->
                    previewViewRef = pv
                    controller.start(lifecycleOwner, pv) { msg ->
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        // --- Top bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 20.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill(text = "${resolution.label} ▾") { showResMenu = !showResMenu }

            if (recording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(ColorRecord)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "%02d:%02d".format(seconds / 60, seconds % 60),
                        color = ColorText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            RoundIconButton(icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.Bolt) {
                if (controller.hasTorch()) {
                    torchOn = controller.toggleTorch()
                } else {
                    Toast.makeText(context, "Flash indisponível neste aparelho", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (showResMenu) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 68.dp, start = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ColorSurface.copy(alpha = 0.95f))
                    .width(150.dp)
            ) {
                ResolutionOption.values().forEach { opt ->
                    Text(
                        text = when (opt) {
                            ResolutionOption.UHD_4K -> "4K Ultra HD"
                            ResolutionOption.FHD_1080 -> "1080p Full HD"
                            ResolutionOption.HD_720 -> "720p HD"
                        },
                        color = if (opt == resolution) ColorAccent else ColorText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                resolution = opt
                                showResMenu = false
                                previewViewRef?.let { pv ->
                                    controller.setResolution(opt, lifecycleOwner, pv) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // --- Bottom bar ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(FilterPreset.ALL) { preset ->
                    FilterChip(
                        preset = preset,
                        selected = preset.id == selectedFilter.id,
                        onClick = {
                            selectedFilter = preset
                            controller.setFilter(preset)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToggleIconButton(icon = Icons.Filled.GraphicEq, on = stabOn) {
                        stabOn = controller.toggleStabilization()
                        Toast.makeText(
                            context,
                            if (stabOn) "Estabilização activa" else "Estabilização desligada",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    ToggleIconButton(icon = Icons.Filled.GraphicEq, on = flickerOn) {
                        flickerOn = controller.toggleAntiFlicker()
                        Toast.makeText(
                            context,
                            if (flickerOn) "Anti-cintilação activa" else "Anti-cintilação desligada",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                ShutterButton(recording = recording) {
                    if (!recording) {
                        controller.startRecording(
                            onSaved = {
                                Toast.makeText(context, "Vídeo guardado em Filmes/CameraPro", Toast.LENGTH_LONG).show()
                            },
                            onFailed = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                        )
                        recording = true
                    } else {
                        controller.stopRecording()
                        recording = false
                    }
                }

                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    RoundIconButton(icon = Icons.Filled.Cameraswitch) {
                        previewViewRef?.let { pv ->
                            controller.switchCamera(lifecycleOwner, pv) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = ColorText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(ColorSurface.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(ColorSurface.copy(alpha = 0.65f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = ColorText, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToggleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (on) ColorAccent else ColorSurface.copy(alpha = 0.7f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (on) Color(0xFF00201C) else ColorText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FilterChip(preset: FilterPreset, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ColorSurface)
                .border(2.dp, if (selected) ColorAccent else Color.Transparent, RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            preset.label,
            color = if (selected) ColorText else ColorMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ShutterButton(recording: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, ColorText, CircleShape)
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .let { if (recording) it.fillMaxSize(0.44f) else it.fillMaxSize() }
                .clip(if (recording) RoundedCornerShape(8.dp) else CircleShape)
                .background(ColorRecord)
        )
    }
}
