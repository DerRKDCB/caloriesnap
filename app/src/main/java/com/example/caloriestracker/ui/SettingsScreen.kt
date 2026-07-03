package com.example.caloriestracker.ui

import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import com.example.caloriestracker.ai.CalorieEstimator
import com.example.caloriestracker.ai.OllamaTestResult
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
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<OllamaTestResult?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importPayload by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

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
                        isExporting = true
                        scope.launch {
                            runCatching { onExportDatabase() }
                                .onSuccess { data ->
                                    clipboardManager.setText(AnnotatedString(data))
                                    Toast.makeText(context, "Exported to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { error ->
                                    Toast.makeText(context, error.localizedMessage ?: "Export failed", Toast.LENGTH_SHORT).show()
                                }
                            isExporting = false
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
                        importPayload = ""
                        importError = null
                        showImportDialog = true
                    },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import database")
                }

                Text(
                    text = "Export copies your meals and settings to the clipboard. Import restores from exported text.",
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

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) {
                    showImportDialog = false
                    importPayload = ""
                    importError = null
                }
            },
            title = { Text("Import database") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = importPayload,
                        onValueChange = {
                            importPayload = it
                            importError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste exported JSON") },
                        minLines = 4
                    )
                    importError?.let { error ->
                        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importPayload.isNotBlank() && !isImporting,
                    onClick = {
                        isImporting = true
                        scope.launch {
                            runCatching { onImportDatabase(importPayload) }
                                .onSuccess {
                                    Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                                    showImportDialog = false
                                    importPayload = ""
                                    importError = null
                                }
                                .onFailure { error ->
                                    importError = error.localizedMessage ?: "Import failed"
                                }
                            isImporting = false
                        }
                    }
                ) {
                    if (isImporting) {
                        Text("Importing…")
                    } else {
                        Text("Import")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isImporting) {
                            showImportDialog = false
                            importPayload = ""
                            importError = null
                        }
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
