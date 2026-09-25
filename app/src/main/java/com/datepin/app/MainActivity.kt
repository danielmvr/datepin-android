package com.datepin.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatePinManager.createNotificationChannel(this)
        DatePinManager.refreshIfEnabled(this)

        setContent {
            DatePinScreen(
                initiallyActive = DatePinManager.isEnabled(this),
                initialStyle = DatePinManager.iconStyle(this),
                notificationsAllowed = DatePinManager.notificationsAllowed(this),
                onEnable = { DatePinManager.setEnabled(this, true) },
                onDisable = { DatePinManager.setEnabled(this, false) },
                onStyleChange = { DatePinManager.setIconStyle(this, it) }
            )
        }
    }
}

@Composable
private fun DatePinScreen(
    initiallyActive: Boolean,
    initialStyle: String,
    notificationsAllowed: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onStyleChange: (String) -> Unit
) {
    val today = LocalDate.now()
    var active by remember { mutableStateOf(initiallyActive) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }

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
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Text(
                    text = "DatePin",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.hero_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 10.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.today_label),
                            style = MaterialTheme.typography.labelLarge
                        )

                        Text(
                            text = today.dayOfMonth.toString(),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 68.sp
                        )

                        Text(
                            text = if (active) {
                                stringResource(R.string.status_active)
                            } else {
                                stringResource(R.string.status_inactive)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = if (active) {
                                stringResource(R.string.status_active_desc)
                            } else {
                                stringResource(R.string.status_inactive_desc)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp)
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
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            Text(
                                if (active) {
                                    stringResource(R.string.deactivate)
                                } else {
                                    stringResource(R.string.activate)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.appearance_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StyleChoice(
                        label = stringResource(R.string.style_bold),
                        previewWeight = FontWeight.Bold,
                        selected = selectedStyle == "bold",
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedStyle = "bold"
                        onStyleChange("bold")
                    }

                    StyleChoice(
                        label = stringResource(R.string.style_clean),
                        previewWeight = FontWeight.Normal,
                        selected = selectedStyle == "clean",
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedStyle = "clean"
                        onStyleChange("clean")
                    }

                    StyleChoice(
                        label = stringResource(R.string.style_compact),
                        previewWeight = FontWeight.Bold,
                        selected = selectedStyle == "compact",
                        modifier = Modifier.weight(1f)
                    ) {
                        selectedStyle = "compact"
                        onStyleChange("compact")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = stringResource(R.string.privacy_line),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun StyleChoice(
    label: String,
    previewWeight: FontWeight,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "25",
                fontWeight = previewWeight,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
