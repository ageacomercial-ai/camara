package com.agea.camerapro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ColorBackground = Color(0xFF0A0A0C)
val ColorSurface = Color(0xFF151518)
val ColorText = Color(0xFFF5F5F7)
val ColorMuted = Color(0xFF9A9AA2)
val ColorRecord = Color(0xFFFF3B30)
val ColorAccent = Color(0xFF2DD4BF)

fun cameraProColorScheme() = darkColorScheme(
    background = ColorBackground,
    surface = ColorSurface,
    primary = ColorAccent,
    onBackground = ColorText,
    onSurface = ColorText,
)

@Composable
fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Câmara Pro", color = ColorText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
        Text(
            "Preciso de acesso à câmara e ao microfone para gravar os teus vídeos.",
            color = ColorMuted,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(10.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = ColorText, contentColor = Color.Black)
        ) {
            Text("Activar câmara", fontWeight = FontWeight.SemiBold)
        }
    }
}
