package com.phonebeacon.companion

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var player: MediaPlayer? = null

    private fun stopAlert() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        runCatching {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.firstOrNull()?.let { manager.setTorchMode(it, false) }
        }
    }

    override fun onDestroy() {
        stopAlert()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = BeaconApi(this)
        val prefs = getSharedPreferences("phonebeacon_private", MODE_PRIVATE)
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var code by remember { mutableStateOf("") }
            var paired by remember { mutableStateOf(prefs.getBoolean("paired", false)) }
            var status by remember { mutableStateOf("Enter the one-time code from Controller.") }
            var whiteScreen by remember { mutableStateOf(false) }
            var soundUri by remember { mutableStateOf(prefs.getString("sound_uri", null)?.let(Uri::parse)) }
            var cameraGranted by remember { mutableStateOf(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
            val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                cameraGranted = it
                status = if (it) "Flashlight permission enabled" else "Flashlight permission was not granted"
            }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    soundUri = uri
                    prefs.edit().putString("sound_uri", uri.toString()).apply()
                    status = "Custom chime saved on this phone"
                }
            }

            LaunchedEffect(paired, soundUri, cameraGranted) {
                while (paired) {
                    val commands = runCatching { withContext(Dispatchers.IO) { api.poll() } }
                    commands.onSuccess { list ->
                        list.forEach { command ->
                            when (command) {
                                "CHIME" -> runCatching {
                                    player?.release()
                                    player = if (soundUri != null) MediaPlayer.create(context, soundUri!!)
                                        else MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                                    player?.start()
                                    status = "Chime playing"
                                }.onFailure { status = "Could not play chime" }
                                "FLASH" -> {
                                    if (!cameraGranted) {
                                        status = "Flashlight permission needed — enable it below"
                                    } else runCatching {
                                        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                                        val camera = manager.cameraIdList.firstOrNull()
                                        if (camera != null) {
                                            repeat(6) {
                                                manager.setTorchMode(camera, true); delay(350)
                                                manager.setTorchMode(camera, false); delay(350)
                                            }
                                        }
                                        status = "Flashlight alert finished"
                                    }.onFailure { status = "Flashlight unavailable" }
                                }
                                "WHITE" -> { whiteScreen = true; status = "White-screen alert active" }
                                "STOP" -> { stopAlert(); whiteScreen = false; status = "Alert stopped remotely" }
                            }
                        }
                    }.onFailure { status = "Waiting for connection…" }
                    delay(2500)
                }
            }

            MaterialTheme {
                Surface(Modifier.fillMaxSize().background(if (whiteScreen) Color.White else MaterialTheme.colorScheme.background)) {
                    if (whiteScreen) {
                        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                            Button(onClick = { whiteScreen = false; stopAlert(); status = "Stopped locally" }) { Text("STOP ALERT") }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("PhoneBeacon", style = MaterialTheme.typography.headlineLarge)
                            Text("Companion", style = MaterialTheme.typography.titleMedium)
                            if (!paired) {
                                Text("Enter the 6-digit code from Controller. It expires after 10 minutes.")
                                OutlinedTextField(
                                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                                    label = { Text("Connection code") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                Button(enabled = code.length == 6, onClick = {
                                    scope.launch {
                                        status = "Connecting…"
                                        val result = runCatching { withContext(Dispatchers.IO) { api.claim(code) } }
                                        if (result.getOrDefault(false)) {
                                            prefs.edit().putBoolean("paired", true).apply()
                                            paired = true
                                            status = "Connected! This phone is paired."
                                        } else {
                                            status = result.exceptionOrNull()?.message ?: "Code is invalid, expired, or already used."
                                        }
                                    }
                                }) { Text("Connect") }
                            } else {
                                Text("Paired. Keep this app open to receive alerts.")
                                Button(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Choose custom chime") }
                                Text(if (soundUri == null) "Using default notification sound" else "Custom chime selected")
                                Button(onClick = {
                                    if (cameraGranted) status = "Flashlight permission is enabled"
                                    else cameraPermission.launch(Manifest.permission.CAMERA)
                                }) { Text(if (cameraGranted) "Flashlight permission enabled" else "Enable flashlight permission") }
                                Button(onClick = { stopAlert(); whiteScreen = false; status = "Stopped locally" }) { Text("Stop alert") }
                            }
                            Text(status, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
