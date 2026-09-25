package com.datepin.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
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
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        setContent {
            DatePinApp(
                onEnable = { enableDatePin() },
                notificationsAllowed = notificationsAllowed()
            )
        }
    }

    private fun notificationsAllowed(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannel(channel)
    }

    private fun enableDatePin() {
        val today = LocalDate.now()
        val manager = getSystemService(NotificationManager::class.java)

        val fullDate = today.format(
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
        ).replaceFirstChar { it.uppercase() }

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(createDayIcon(today.dayOfMonth))
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(fullDate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createDayIcon(day: Int): Icon {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (day < 10) 74f else 62f
        }

        val baseline = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(day.toString(), size / 2f, baseline, paint)

        return Icon.createWithBitmap(bitmap)
    }

    companion object {
        private const val CHANNEL_ID = "datepin_status"
        private const val NOTIFICATION_ID = 2509
    }
}

@Composable
private fun DatePinApp(
    onEnable: () -> Unit,
    notificationsAllowed: Boolean
) {
    val today = LocalDate.now()
    var active by remember { mutableStateOf(false) }

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
                        if (notificationsAllowed || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            onEnable()
                            active = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text(if (active) "DatePin ativo" else "Ativar DatePin")
                }
            }
        }
    }
}
