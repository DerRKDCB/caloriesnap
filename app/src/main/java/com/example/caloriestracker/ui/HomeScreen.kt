package com.example.caloriestracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.caloriestracker.CalorieTrackerUiState
import com.example.caloriestracker.ai.CalorieEstimator
import com.example.caloriestracker.data.Meal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    state: CalorieTrackerUiState,
    onOpenCamera: () -> Unit,
    onManualEntry: (Int, String) -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteMeal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val headerFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val today = LocalDate.now()
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(today) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val headerDate = headerFormatter.format(selectedDate)
    val canGoForward = selectedDate.isBefore(today)
    val mealsForSelectedDate = state.mealHistory[selectedDate]
        ?: if (selectedDate == today) state.meals else emptyList()
    val selectedDayTotal = mealsForSelectedDate.sumOf { it.calories }
    val progress = if (state.dailyGoal == 0) 0f else (selectedDayTotal / state.dailyGoal.toFloat()).coerceIn(0f, 1f)
    val isTodaySelected = selectedDate == today
    val caloriesLeft = (state.dailyGoal - selectedDayTotal).coerceAtLeast(0)
    var manualDialogVisible by rememberSaveable { mutableStateOf(false) }
    var manualCalories by rememberSaveable { mutableStateOf("") }
    var manualNote by rememberSaveable { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var manualIsWorkout by rememberSaveable { mutableStateOf(false) }
    var llmDialogVisible by rememberSaveable { mutableStateOf(false) }
    var llmDescription by rememberSaveable { mutableStateOf("") }
    var llmError by remember { mutableStateOf<String?>(null) }
    var llmIsLoading by remember { mutableStateOf(false) }
    var workoutDialogVisible by rememberSaveable { mutableStateOf(false) }
    var workoutDescription by rememberSaveable { mutableStateOf("") }
    var workoutError by remember { mutableStateOf<String?>(null) }
    var workoutIsLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochMillis(),
            initialDisplayedMonthMillis = selectedDate.toEpochMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            selectedDate = if (pickedDate.isAfter(today)) today else pickedDate
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(text = "Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                    )
                )
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                    Icon(imageVector = Icons.Rounded.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                val dateButtonModifier = if (isTodaySelected) {
                    Modifier
                        .weight(1f)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(26.dp)
                        )
                } else {
                    Modifier.weight(1f)
                }
                FilledTonalButton(
                    onClick = { showDatePicker = true },
                    modifier = dateButtonModifier,
                    shape = RoundedCornerShape(26.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    colors = if (isTodaySelected) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Rounded.CalendarMonth, contentDescription = null)
                            HorizontalSpacer(12)
                            Text(
                                text = headerDate,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (isTodaySelected) {
                            VerticalSpacer(4)
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { selectedDate = selectedDate.plusDays(1) },
                    enabled = canGoForward
                ) {
                    Icon(imageVector = Icons.Rounded.KeyboardArrowRight, contentDescription = "Next day")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }

            VerticalSpacer(24)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${selectedDayTotal} / ${state.dailyGoal}kcal",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "left: ${caloriesLeft}kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
            }

            VerticalSpacer(32)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onOpenCamera, modifier = Modifier.weight(1f)) {
                    Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null)
                    HorizontalSpacer(8)
                    Text(text = "Snap meal")
                }
                Button(onClick = { llmDialogVisible = true }, modifier = Modifier.weight(1f)) {
                    Icon(imageVector = Icons.Rounded.Psychology, contentDescription = null)
                    HorizontalSpacer(8)
                    Text(text = "Text")
                }
            }

            VerticalSpacer(12)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { manualDialogVisible = true }, modifier = Modifier.weight(1f)) {
                    Icon(imageVector = Icons.Rounded.EditNote, contentDescription = null)
                    HorizontalSpacer(8)
                    Text(text = "Manual")
                }
                Button(onClick = { workoutDialogVisible = true }, modifier = Modifier.weight(1f)) {
                    Icon(imageVector = Icons.Rounded.FitnessCenter, contentDescription = null)
                    HorizontalSpacer(8)
                    Text(text = "Workout")
                }
            }

            VerticalSpacer(24)

            Text(
                text = if (selectedDate == today) "Today's meals" else "$headerDate meals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            VerticalSpacer(12)

            if (mealsForSelectedDate.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedDate == today) {
                            "No meals logged yet. Snap a meal to get started."
                        } else {
                            "No meals logged for $headerDate."
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = mealsForSelectedDate, key = { it.id }) { meal ->
                        MealRow(meal = meal, onDelete = { onDeleteMeal(meal.id) })
                    }
                }
            }
        }
    }

    if (manualDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                manualDialogVisible = false
                manualError = null
            },
            title = { Text(text = "Log meal manually") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = manualCalories,
                        onValueChange = {
                            manualCalories = it.filter { char -> char.isDigit() }
                            manualError = null
                        },
                        label = { Text("Calories") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualNote,
                        onValueChange = {
                            manualNote = it
                            manualError = null
                        },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Workout (subtract calories)")
                        Switch(checked = manualIsWorkout, onCheckedChange = { manualIsWorkout = it })
                    }
                    manualError?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val caloriesValue = manualCalories.toIntOrNull()
                    if (caloriesValue == null || caloriesValue <= 0) {
                        manualError = "Enter calories"
                        return@TextButton
                    }
                    val finalCalories = if (manualIsWorkout) -caloriesValue else caloriesValue
                    if (finalCalories == 0) {
                        manualError = "Calories cannot be zero"
                        return@TextButton
                    }
                    val note = manualNote.ifBlank { if (manualIsWorkout) "Workout" else "" }
                    onManualEntry(finalCalories, note)
                    manualCalories = ""
                    manualNote = ""
                    manualIsWorkout = false
                    manualError = null
                    manualDialogVisible = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    manualDialogVisible = false
                    manualError = null
                    manualIsWorkout = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (llmDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!llmIsLoading) {
                    llmDialogVisible = false
                    llmDescription = ""
                    llmError = null
                }
            },
            title = { Text(text = "AI calorie estimate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = llmDescription,
                        onValueChange = {
                            llmDescription = it
                            llmError = null
                        },
                        label = { Text("Describe your meal") },
                        placeholder = { Text("e.g. Grilled salmon with quinoa and roasted veggies") },
                        minLines = 3,
                        enabled = !llmIsLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (llmIsLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(text = "Requesting estimate…")
                        }
                    }
                    llmError?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = llmDescription.isNotBlank() && !llmIsLoading,
                    onClick = {
                        val cleanedDescription = llmDescription.trim()
                        llmIsLoading = true
                        llmError = null
                        scope.launch {
                            runCatching {
                                CalorieEstimator.estimateFromDescription(
                                    description = cleanedDescription,
                                    apiKey = state.apiKey,
                                    address = state.ollamaAddress,
                                    model = state.ollamaModel
                                )
                            }.onSuccess { estimate ->
                                onManualEntry(estimate.calories, cleanedDescription.ifBlank { estimate.note })
                                llmDescription = ""
                                llmDialogVisible = false
                            }.onFailure { error ->
                                llmError = error.localizedMessage ?: "Could not estimate calories"
                            }
                            llmIsLoading = false
                        }
                    }
                ) {
                    if (llmIsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Estimate")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !llmIsLoading,
                    onClick = {
                        llmDialogVisible = false
                        llmDescription = ""
                        llmError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (workoutDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!workoutIsLoading) {
                    workoutDialogVisible = false
                    workoutDescription = ""
                    workoutError = null
                }
            },
            title = { Text(text = "AI workout estimate") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = workoutDescription,
                        onValueChange = {
                            workoutDescription = it
                            workoutError = null
                        },
                        label = { Text("Describe your workout") },
                        placeholder = { Text("e.g. 30 min HIIT ride") },
                        minLines = 3,
                        enabled = !workoutIsLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (workoutIsLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(text = "Estimating calories burned…")
                        }
                    }
                    workoutError?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = workoutDescription.isNotBlank() && !workoutIsLoading,
                    onClick = {
                        val cleanedDescription = workoutDescription.trim()
                        workoutIsLoading = true
                        workoutError = null
                        scope.launch {
                            runCatching {
                                CalorieEstimator.estimateWorkoutFromDescription(
                                    description = cleanedDescription,
                                    apiKey = state.apiKey,
                                    address = state.ollamaAddress,
                                    model = state.ollamaModel
                                )
                            }.onSuccess { estimate ->
                                val caloriesBurned = estimate.calories.coerceAtLeast(1)
                                val noteBase = cleanedDescription.ifBlank { estimate.note }
                                val finalNote = if (noteBase.isBlank()) "Workout" else "Workout: ${noteBase.take(80)}"
                                onManualEntry(-caloriesBurned, finalNote)
                                workoutDescription = ""
                                workoutDialogVisible = false
                            }.onFailure { error ->
                                workoutError = error.localizedMessage ?: "Could not estimate workout calories"
                            }
                            workoutIsLoading = false
                        }
                    }
                ) {
                    if (workoutIsLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Estimate")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !workoutIsLoading,
                    onClick = {
                        workoutDialogVisible = false
                        workoutDescription = ""
                        workoutError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
private fun MealRow(meal: Meal, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.note.ifBlank { "Logged meal" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatMealTime(meal.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = "${meal.calories} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete meal",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatMealTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
    }.getOrDefault("")
}

@Composable
private fun VerticalSpacer(space: Int) {
    Spacer(modifier = Modifier.height(space.dp))
}

@Composable
private fun HorizontalSpacer(space: Int) {
    Spacer(modifier = Modifier.width(space.dp))
}

private val LocalDateSaver: Saver<LocalDate, Long> = Saver(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) }
)

private fun LocalDate.toEpochMillis(): Long =
    this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
