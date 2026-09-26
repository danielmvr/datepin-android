package com.datepin.app

import android.Manifest
import android.app.DatePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatePinManager.createNotificationChannel(this)
        DatePinManager.refreshIfEnabled(this)

        setContent {
            DatePinScreen(
                initiallyActive = DatePinManager.isEnabled(this),
                initialStyle = DatePinManager.iconStyle(this),
                initialEvents = EventManager.getEvents(this),
                initialPinnedEventId = EventManager.pinnedEventId(this),
                premiumUnlocked = PremiumManager.isPremium(this),
                notificationsAllowed = DatePinManager.notificationsAllowed(this),
                onEnable = { DatePinManager.setEnabled(this, true) },
                onDisable = { DatePinManager.setEnabled(this, false) },
                onStyleChange = { DatePinManager.setIconStyle(this, it) },
                onSaveEvent = { EventManager.saveEvent(this, it) },
                onDeleteEvent = { EventManager.deleteEvent(this, it) },
                onPinToday = { EventManager.setPinnedToday(this) },
                onPinEvent = { EventManager.setPinnedEvent(this, it) }
            )
        }
    }
}

@Composable
private fun DatePinScreen(
    initiallyActive: Boolean,
    initialStyle: String,
    initialEvents: List<DatePinEvent>,
    initialPinnedEventId: String?,
    premiumUnlocked: Boolean,
    notificationsAllowed: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onStyleChange: (String) -> Unit,
    onSaveEvent: (DatePinEvent) -> Boolean,
    onDeleteEvent: (String) -> Unit,
    onPinToday: () -> Unit,
    onPinEvent: (String) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()

    var active by remember { mutableStateOf(initiallyActive) }
    var selectedStyle by remember { mutableStateOf(initialStyle) }
    var events by remember { mutableStateOf(initialEvents) }
    var pinnedEventId by remember { mutableStateOf(initialPinnedEventId) }
    var showEventForm by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<DatePinEvent?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onEnable()
            active = true
        }
    }

    val pinnedEvent = events.firstOrNull { it.id == pinnedEventId }

    val pinnedTitle = pinnedEvent?.name ?: stringResource(R.string.today_label)
    val pinnedValue = pinnedEvent?.let { EventManager.eventValue(it).toString() }
        ?: today.dayOfMonth.toString()
    val pinnedDescription = pinnedEvent?.let {
        EventManager.eventDescription(context, it)
    } ?: today.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(Locale.getDefault())
    ).replaceFirstChar { it.uppercase() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Text(
                    text = "DatePin",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

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

                Spacer(modifier = Modifier.height(24.dp))

                StatusCard(
                    title = pinnedTitle,
                    value = pinnedValue,
                    description = pinnedDescription,
                    active = active,
                    onEnable = {
                        if (
                            notificationsAllowed ||
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        ) {
                            onEnable()
                            active = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onDisable = {
                        onDisable()
                        active = false
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.dates_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.dates_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                TodayItem(
                    isPinned = pinnedEventId == null,
                    onPin = {
                        onPinToday()
                        pinnedEventId = null
                    }
                )

                events.forEach { event ->
                    Spacer(modifier = Modifier.height(10.dp))

                    EventItem(
                        event = event,
                        isPinned = pinnedEventId == event.id,
                        onPin = {
                            onPinEvent(event.id)
                            pinnedEventId = event.id
                        },
                        onEdit = {
                            editingEvent = event
                            showEventForm = true
                        },
                        onDelete = {
                            onDeleteEvent(event.id)
                            events = events.filterNot { it.id == event.id }

                            if (pinnedEventId == event.id) {
                                pinnedEventId = null
                            }

                            if (editingEvent?.id == event.id) {
                                editingEvent = null
                                showEventForm = false
                            }
                        }
                    )
                }

                val canCreateAnother = premiumUnlocked || events.isEmpty()

                if (!showEventForm) {
                    OutlinedButton(
                        onClick = {
                            if (canCreateAnother) {
                                editingEvent = null
                                showEventForm = true
                            }
                        },
                        enabled = canCreateAnother,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            if (canCreateAnother) {
                                stringResource(R.string.new_date)
                            } else {
                                stringResource(R.string.new_date_premium)
                            }
                        )
                    }
                }

                if (showEventForm) {
                    Spacer(modifier = Modifier.height(14.dp))

                    EventEditor(
                        existing = editingEvent,
                        onCancel = {
                            editingEvent = null
                            showEventForm = false
                        },
                        onSave = { event ->
                            val saved = onSaveEvent(event)

                            if (saved) {
                                val currentIndex = events.indexOfFirst { it.id == event.id }

                                events = if (currentIndex >= 0) {
                                    events.toMutableList().also {
                                        it[currentIndex] = event
                                    }
                                } else {
                                    events + event
                                }

                                pinnedEventId = event.id
                                editingEvent = null
                                showEventForm = false
                            }

                            saved
                        }
                    )
                }

                Text(
                    text = if (premiumUnlocked) {
                        stringResource(R.string.premium_events_active)
                    } else {
                        stringResource(R.string.free_event_limit)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )

                Spacer(modifier = Modifier.height(30.dp))

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

                Spacer(modifier = Modifier.height(30.dp))

                FreeSection()

                Spacer(modifier = Modifier.height(26.dp))

                PremiumSection(unlocked = premiumUnlocked)

                Text(
                    text = stringResource(R.string.privacy_line),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 30.dp, bottom = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    description: String,
    active: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit
) {
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
                text = stringResource(R.string.pinned_now),
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = value,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 68.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (active) {
                    stringResource(R.string.status_active)
                } else {
                    stringResource(R.string.status_inactive)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp)
            )

            Button(
                onClick = if (active) onDisable else onEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
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
}

@Composable
private fun TodayItem(
    isPinned: Boolean,
    onPin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.today_item_desc),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(
                onClick = onPin,
                enabled = !isPinned
            ) {
                Text(
                    if (isPinned) {
                        stringResource(R.string.pinned)
                    } else {
                        stringResource(R.string.pin)
                    }
                )
            }
        }
    }
}

@Composable
private fun EventItem(
    event: DatePinEvent,
    isPinned: Boolean,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = EventManager.eventDescription(context, event),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                TextButton(
                    onClick = onPin,
                    enabled = !isPinned
                ) {
                    Text(
                        if (isPinned) {
                            stringResource(R.string.pinned)
                        } else {
                            stringResource(R.string.pin)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun EventEditor(
    existing: DatePinEvent?,
    onCancel: () -> Unit,
    onSave: (DatePinEvent) -> Boolean
) {
    val context = LocalContext.current
    val today = LocalDate.now()

    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var selectedDate by remember(existing?.id) { mutableStateOf(existing?.date) }
    var mode by remember(existing?.id) {
        mutableStateOf(existing?.mode ?: EventManager.MODE_COUNTDOWN)
    }
    var validationError by remember(existing?.id) { mutableStateOf<Int?>(null) }

    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(40)
                    validationError = null
                },
                label = { Text(stringResource(R.string.event_name_label)) },
                placeholder = { Text(stringResource(R.string.event_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = {
                    val start = selectedDate ?: today

                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                            validationError = null
                        },
                        start.year,
                        start.monthValue - 1,
                        start.dayOfMonth
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    selectedDate?.format(dateFormatter)
                        ?: stringResource(R.string.event_date_label)
                )
            }

            Text(
                text = stringResource(R.string.event_mode_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeChoice(
                    label = stringResource(R.string.mode_countdown),
                    selected = mode == EventManager.MODE_COUNTDOWN,
                    modifier = Modifier.weight(1f)
                ) {
                    mode = EventManager.MODE_COUNTDOWN
                    validationError = null
                }

                ModeChoice(
                    label = stringResource(R.string.mode_since),
                    selected = mode == EventManager.MODE_SINCE,
                    modifier = Modifier.weight(1f)
                ) {
                    mode = EventManager.MODE_SINCE
                    validationError = null
                }
            }

            validationError?.let { errorRes ->
                Text(
                    text = stringResource(errorRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Button(
                onClick = {
                    val date = selectedDate

                    validationError = when {
                        name.isBlank() -> R.string.name_required
                        date == null -> R.string.date_required
                        mode == EventManager.MODE_COUNTDOWN && date.isBefore(today) ->
                            R.string.countdown_date_invalid
                        mode == EventManager.MODE_SINCE && date.isAfter(today) ->
                            R.string.since_date_invalid
                        else -> null
                    }

                    if (validationError == null && date != null) {
                        val event = if (existing == null) {
                            DatePinEvent(
                                name = name.trim(),
                                date = date,
                                mode = mode
                            )
                        } else {
                            existing.copy(
                                name = name.trim(),
                                date = date,
                                mode = mode
                            )
                        }

                        if (!onSave(event)) {
                            validationError = R.string.free_event_limit
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.save_event))
            }

            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun ModeChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FreeSection() {
    Text(
        text = stringResource(R.string.free_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp)
    ) {
        FeatureLine("✓", stringResource(R.string.free_item_day))
        FeatureLine("✓", stringResource(R.string.free_item_event))
        FeatureLine("✓", stringResource(R.string.free_item_boot))
        FeatureLine("✓", stringResource(R.string.free_item_midnight))
        FeatureLine("✓", stringResource(R.string.free_item_styles))
    }
}

@Composable
private fun PremiumSection(unlocked: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.premium_badge),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = stringResource(R.string.premium_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = stringResource(R.string.premium_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                PremiumFeature(stringResource(R.string.premium_feature_events), unlocked)
                PremiumFeature(stringResource(R.string.premium_feature_recurring), unlocked)
                PremiumFeature(stringResource(R.string.premium_feature_rotation), unlocked)
                PremiumFeature(stringResource(R.string.premium_feature_styles), unlocked)
                PremiumFeature(stringResource(R.string.premium_feature_widgets), unlocked)
                PremiumFeature(stringResource(R.string.premium_feature_formats), unlocked)
            }

            Text(
                text = stringResource(R.string.premium_launch_price),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp)
            )

            Text(
                text = stringResource(R.string.premium_normal_price),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp)
            )

            Text(
                text = stringResource(R.string.premium_not_for_sale),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun PremiumFeature(label: String, unlocked: Boolean) {
    FeatureLine(
        icon = if (unlocked) "✓" else "🔒",
        text = label
    )
}

@Composable
private fun FeatureLine(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
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
                text = "26",
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
