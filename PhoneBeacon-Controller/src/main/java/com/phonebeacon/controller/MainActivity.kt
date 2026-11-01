package com.phonebeacon.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = BeaconApi(this)
        setContent {
            var code by remember { mutableStateOf("Not generated") }
            var status by remember { mutableStateOf("Not connected") }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PhoneBeacon", style = MaterialTheme.typography.headlineLarge)
                        Text("Controller", style = MaterialTheme.typography.titleMedium)
                        Text("Generate a one-time code and enter it on your brother's Companion app. It expires after 10 minutes.")
                        Text(code, style = MaterialTheme.typography.displaySmall)
                        Button(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { api.createCode() } }
                                    .onSuccess { code = it; status = "Code ready — expires in 10 minutes" }
                                    .onFailure { status = it.message ?: "Could not create code" }
                                busy = false
                            }
                        }) { Text("Generate connection code") }
                        HorizontalDivider()
                        Text("Find-phone controls")
                        listOf("CHIME" to "Play chime", "FLASH" to "Pulse flashlight", "WHITE" to "White screen", "STOP" to "Stop alert").forEach { (command, label) ->
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy && code != "Not generated",
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        status = runCatching { withContext(Dispatchers.IO) { api.send(command) } }
                                            .fold({ if (it) "$label command sent" else "Phone is not paired yet" }, { it.message ?: "Command failed" })
                                        busy = false
                                    }
                                }
                            ) { Text(label) }
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
