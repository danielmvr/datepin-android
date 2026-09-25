package com.datepin.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatePinManager.createNotificationChannel(this)
        DatePinManager.refreshIfEnabled(this)

        setContent {
            DatePinApp(
                initiallyActive = DatePinManager.isEnabled(this),
                notificationsAllowed = DatePinManager.notificationsAllowed(this),
                onEnable = { DatePinManager.setEnabled(this, true) },
                onDisable = { DatePinManager.setEnabled(this, false) },
                lastSystemEvent = DatePinManager.lastSystemEvent(this)
            )
        }
    }
}

@Composable
private fun DatePinApp(
    initiallyActive: Boolean,
    notificationsAllowed: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    lastSystemEvent: Pair<String?, Long>
) {
    val today = LocalDate.now()
    var active by remember { mutableStateOf(initiallyActive) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onEnable()
            active = true
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DatePin",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Hoje é dia ${today.dayOfMonth}",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Text(
                    text = if (active) {
                        "Data ativa na barra de status ✓"
                    } else {
                        "Fixe o dia atual na barra de status."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                Button(
                    onClick = {
                        if (active) {
                            onDisable()
                            active = false
                        } else if (
                            notificationsAllowed ||
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        ) {
                            onEnable()
                            active = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text(if (active) "Desativar DatePin" else "Ativar DatePin")
                }

                val eventName = lastSystemEvent.first
                val eventTime = lastSystemEvent.second

                if (eventName != null && eventTime > 0L) {
                    val formatted = Instant.ofEpochMilli(eventTime)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"))

                    Text(
                        text = "Diagnóstico: $eventName às $formatted",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                } else {
                    Text(
                        text = "Diagnóstico: nenhum evento de sistema recebido",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
        }
    }
}
