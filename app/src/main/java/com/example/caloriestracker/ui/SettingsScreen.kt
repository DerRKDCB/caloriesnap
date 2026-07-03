package com.example.caloriestracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.caloriestracker.ai.CalorieEstimator
import com.example.caloriestracker.ai.OllamaTestResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String,
    ollamaAddress: String,
    ollamaModel: String,
    dailyGoal: Int,
    onBack: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onGoalChange: (Int) -> Unit,
    onExportDatabase: suspend () -> String,
    onImportDatabase: suspend (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var localApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var localAddress by remember(ollamaAddress) { mutableStateOf(ollamaAddress) }
    var localModel by remember(ollamaModel) { mutableStateOf(ollamaModel) }
    var localGoal by remember(dailyGoal) { mutableStateOf(dailyGoal.toString()) }
    var showApiKey by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<OllamaTestResult?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var exportPayload by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val exportFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = exportPayload
        if (uri == null) {
            if (payload != null) {
                Toast.makeText(context, "Export canceled", Toast.LENGTH_SHORT).show()
            }
            exportPayload = null
            isExporting = false
            return@rememberLauncherForActivityResult
        }
        if (payload.isNullOrEmpty()) {
            Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
            isExporting = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.toByteArray())
                } ?: error("Unable to open destination")
            }.onSuccess {
                Toast.makeText(context, "Export saved", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, error.localizedMessage ?: "Export failed", Toast.LENGTH_SHORT).show()
            }
            exportPayload = null
            isExporting = false
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            Toast.makeText(context, "Import canceled", Toast.LENGTH_SHORT).show()
            isImporting = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val dataResult = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: error("Unable to read file")
            }
            dataResult.onSuccess { payload ->
                runCatching { onImportDatabase(payload) }
                    .onSuccess {
                        Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure { error ->
                        Toast.makeText(context, error.localizedMessage ?: "Import failed", Toast.LENGTH_SHORT).show()
                    }
            }.onFailure { error ->
                Toast.makeText(context, error.localizedMessage ?: "Import failed", Toast.LENGTH_SHORT).show()
            }
            isImporting = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            OutlinedTextField(
                value = localAddress,
                onValueChange = {
                    localAddress = it
                    onAddressChange(it)
                    testResult = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ollama address") },
                placeholder = { Text("https://ollama.com/api") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri)
            )

            OutlinedTextField(
                value = localModel,
                onValueChange = {
                    localModel = it
                    onModelChange(it)
                    testResult = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                placeholder = { Text("llava") },
                singleLine = true
            )

            OutlinedTextField(
                value = localApiKey,
                onValueChange = {
                    localApiKey = it
                    onApiKeyChange(it)
                    testResult = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ollama API key (optional)") },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showApiKey = !showApiKey }) {
                        Text(if (showApiKey) "Hide" else "Show")
                    }
                }
            )

            OutlinedButton(
                onClick = {
                    isTesting = true
                    testResult = null
                    scope.launch {
                        testResult = CalorieEstimator.testConnection(
                            address = localAddress,
                            model = localModel,
                            apiKey = localApiKey
                        )
                        isTesting = false
                    }
                },
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTesting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Testing connection...")
                    }
                } else {
                    Text("Test connection")
                }
            }

            testResult?.let { result ->
                val (message, color) = when (result) {
                    is OllamaTestResult.Success -> result.message to MaterialTheme.colorScheme.primary
                    is OllamaTestResult.Failure -> result.message to MaterialTheme.colorScheme.error
                }
                Text(text = message, color = color, style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = localGoal,
                onValueChange = {
                    localGoal = it.filter { char -> char.isDigit() }
                    val parsed = localGoal.toIntOrNull()
                    if (parsed != null) onGoalChange(parsed)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Daily calorie goal") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )

            Text(
                text = "Calorie estimates use your Ollama instance. Set the server address and the vision model to use (for example llava), then tap Test connection to verify it works.",
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Data management", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = {
                        if (isExporting) return@OutlinedButton
                        isExporting = true
                        scope.launch {
                            runCatching { onExportDatabase() }
                                .onSuccess { data ->
                                    exportPayload = data
                                    val suggestedName = "calorie-snap-${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}.json"
                                    exportFileLauncher.launch(suggestedName)
                                }
                                .onFailure { error ->
                                    Toast.makeText(context, error.localizedMessage ?: "Export failed", Toast.LENGTH_SHORT).show()
                                    exportPayload = null
                                    isExporting = false
                                }
                        }
                    },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExporting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Exporting…")
                        }
                    } else {
                        Text("Export database")
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (isImporting) return@OutlinedButton
                        isImporting = true
                        importFileLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isImporting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Opening files…")
                        }
                    } else {
                        Text("Import database")
                    }
                }

                Text(
                    text = "Export saves your meals and settings to a JSON file. Import restores from a saved JSON file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }

}
