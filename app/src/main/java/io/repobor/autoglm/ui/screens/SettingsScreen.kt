package io.repobor.autoglm.ui.screens

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.repobor.autoglm.accessibility.AccessibilityServiceHelper
import io.repobor.autoglm.accessibility.AutoGLMAccessibilityService
import io.repobor.autoglm.accessibility.ShizukuHelper
import io.repobor.autoglm.config.I18n
import io.repobor.autoglm.data.ExecutionMode
import io.repobor.autoglm.data.SettingsRepository
import io.repobor.autoglm.model.ModelClient
import io.repobor.autoglm.model.ModelConfig

/**
 * Settings screen for API configuration.
 */
@Composable
fun SettingsScreen(settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Collect settings
    val apiEndpoint by settingsRepository.apiEndpoint.collectAsState(initial = SettingsRepository.DEFAULT_API_ENDPOINT)
    val apiKey by settingsRepository.apiKey.collectAsState(initial = SettingsRepository.DEFAULT_API_KEY)
    val modelName by settingsRepository.modelName.collectAsState(initial = SettingsRepository.DEFAULT_MODEL_NAME)
    val maxSteps by settingsRepository.maxSteps.collectAsState(initial = SettingsRepository.DEFAULT_MAX_STEPS)
    val language by settingsRepository.language.collectAsState(initial = SettingsRepository.DEFAULT_LANGUAGE)
    val executionMode by settingsRepository.executionMode.collectAsState(initial = SettingsRepository.DEFAULT_EXECUTION_MODE)

    // Local state for editing
    var editApiEndpoint by remember { mutableStateOf(apiEndpoint) }
    var editApiKey by remember { mutableStateOf(apiKey) }
    var editModelName by remember { mutableStateOf(modelName) }
    var editMaxSteps by remember { mutableStateOf(maxSteps.toString()) }
    var editLanguage by remember { mutableStateOf(language) }
    var editExecutionMode by remember { mutableStateOf(executionMode) }

    // Update local state when settings change
    LaunchedEffect(apiEndpoint) { editApiEndpoint = apiEndpoint }
    LaunchedEffect(apiKey) { editApiKey = apiKey }
    LaunchedEffect(modelName) { editModelName = modelName }
    LaunchedEffect(maxSteps) { editMaxSteps = maxSteps.toString() }
    LaunchedEffect(language) { editLanguage = language }
    LaunchedEffect(executionMode) { editExecutionMode = executionMode }

    // UI state
    var showMessage by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var serviceEnabled by remember { mutableStateOf(AutoGLMAccessibilityService.isRunning()) }

    // Get string resources based on language
    val s = if (language == "zh") I18n.Chinese else I18n.English

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = s.SETTINGS_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // API Endpoint
        OutlinedTextField(
            value = editApiEndpoint,
            onValueChange = { editApiEndpoint = it },
            label = { Text(s.SETTINGS_ENDPOINT) },
            placeholder = { Text(s.SETTINGS_ENDPOINT_HINT) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // API Key
        OutlinedTextField(
            value = editApiKey,
            onValueChange = { editApiKey = it },
            label = { Text(s.SETTINGS_API_KEY) },
            placeholder = { Text(s.SETTINGS_API_KEY_HINT) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        // Model Name
        OutlinedTextField(
            value = editModelName,
            onValueChange = { editModelName = it },
            label = { Text(s.SETTINGS_MODEL_NAME) },
            placeholder = { Text(s.SETTINGS_MODEL_NAME_HINT) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Max Steps
        OutlinedTextField(
            value = editMaxSteps,
            onValueChange = { editMaxSteps = it.filter { c -> c.isDigit() } },
            label = { Text(s.SETTINGS_MAX_STEPS) },
            placeholder = { Text(s.SETTINGS_MAX_STEPS_HINT) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Language Selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = s.SETTINGS_LANGUAGE,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Chinese option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = editLanguage == "zh",
                        onClick = { editLanguage = "zh" }
                    )
                    Text(s.SETTINGS_LANGUAGE_CHINESE)
                }

                // English option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = editLanguage == "en",
                        onClick = { editLanguage = "en" }
                    )
                    Text(s.SETTINGS_LANGUAGE_ENGLISH)
                }
            }
        }

        // Execution Mode Selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (language == "zh") "执行模式" else "Execution Mode",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Accessibility mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = editExecutionMode == ExecutionMode.ACCESSIBILITY,
                        onClick = { editExecutionMode = ExecutionMode.ACCESSIBILITY }
                    )
                    Text(
                        text = if (language == "zh") "无障碍服务" else "Accessibility",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Shizuku mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = editExecutionMode == ExecutionMode.SHIZUKU,
                        onClick = { editExecutionMode = ExecutionMode.SHIZUKU }
                    )
                    Text(
                        text = "Shizuku",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Mode description
            Text(
                text = when (editExecutionMode) {
                    ExecutionMode.ACCESSIBILITY -> if (language == "zh") {
                        "默认模式，使用Android无障碍服务实现自动化"
                    } else {
                        "Default mode, uses Android Accessibility Service"
                    }
                    ExecutionMode.SHIZUKU -> if (language == "zh") {
                        "高级模式，通过Shizuku执行命令（需要先启动Shizuku）"
                    } else {
                        "Advanced mode, executes commands via Shizuku (requires Shizuku running)"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )

            // Shizuku status indicator
            if (editExecutionMode == ExecutionMode.SHIZUKU) {
                val shizukuRunning = remember { mutableStateOf(false) }
                val shizukuPermission = remember { mutableStateOf(false) }

                // Refresh status - check Shizuku state safely
                LaunchedEffect(Unit) {
                    try {
                        shizukuRunning.value = ShizukuHelper.isShizukuRunning()
                        shizukuPermission.value = ShizukuHelper.checkShizukuPermission(context)
                    } catch (e: Exception) {
                        Log.e("SettingsScreen", "Error checking Shizuku status", e)
                        shizukuRunning.value = false
                        shizukuPermission.value = false
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (shizukuRunning.value && shizukuPermission.value) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (language == "zh") "Shizuku 状态：" else "Shizuku Status:",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                shizukuRunning.value = ShizukuHelper.isShizukuRunning()
                                shizukuPermission.value = ShizukuHelper.checkShizukuPermission(context)
                            }) {
                                Text(
                                    text = if (language == "zh") "刷新" else "Refresh",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Text(
                            text = if (language == "zh") {
                                "运行状态: ${if (shizukuRunning.value) "✓ 运行中" else "✗ 未运行"}"
                            } else {
                                "Running: ${if (shizukuRunning.value) "✓ Yes" else "✗ No"}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = if (language == "zh") {
                                "权限授予: ${if (shizukuPermission.value) "✓ 已授予" else "✗ 未授予"}"
                            } else {
                                "Permission: ${if (shizukuPermission.value) "✓ Granted" else "✗ Not granted"}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (!shizukuRunning.value || !shizukuPermission.value) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = if (language == "zh") {
                                    "请先安装并启动 Shizuku 应用，然后授予权限"
                                } else {
                                    "Please install and start Shizuku app, then grant permission"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Test Connection Button
        Button(
            onClick = {
                isTestingConnection = true
                coroutineScope.launch {
                    try {
                        val config = ModelConfig(
                            baseUrl = editApiEndpoint,
                            apiKey = editApiKey,
                            modelName = editModelName
                        )
                        val client = ModelClient(config)
                        val success = client.testConnection()

                        showMessage = if (success) {
                            s.SETTINGS_CONNECTION_SUCCESS
                        } else {
                            s.SETTINGS_CONNECTION_FAILED
                        }
                    } catch (e: Exception) {
                        showMessage = "${s.SETTINGS_CONNECTION_FAILED}: ${e.message}"
                    } finally {
                        isTestingConnection = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTestingConnection
        ) {
            if (isTestingConnection) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(s.SETTINGS_TEST_CONNECTION)
        }

        // Enable Accessibility Service Button
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(s.SETTINGS_ENABLE_SERVICE)
        }

        // Service status and auto-enable info
        if (serviceEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "✓ 无障碍服务已启用 / Accessibility Service Enabled",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            // Show auto-enable instructions when service is not enabled
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (language == "zh") "⚠️ 无障碍服务未启用" else "⚠️ Accessibility Service Not Enabled",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = AccessibilityServiceHelper.getEnableInstructions(context, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    // Copy ADB command button
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                val clip = android.content.ClipData.newPlainText(
                                    "ADB Command",
                                    AccessibilityServiceHelper.getAdbPermissionCommand(context)
                                )
                                clipboard.setPrimaryClip(clip)
                                showMessage = if (language == "zh") "已复制ADB命令" else "ADB command copied"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (language == "zh") "复制ADB命令" else "Copy ADB Command"
                            )
                        }
                    }
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        settingsRepository.saveAllSettings(
                            apiEndpoint = editApiEndpoint,
                            apiKey = editApiKey,
                            modelName = editModelName,
                            maxSteps = editMaxSteps.toIntOrNull() ?: SettingsRepository.DEFAULT_MAX_STEPS,
                            language = editLanguage
                        )
                        settingsRepository.saveExecutionMode(editExecutionMode)
                        showMessage = s.SETTINGS_SAVED
                    } catch (e: Exception) {
                        showMessage = "Error: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(s.SETTINGS_SAVE)
        }

        // Message display
        showMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.contains("success", ignoreCase = true) ||
                        message.contains("成功") || message.contains("已保存")
                    ) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Auto-dismiss message after 3 seconds
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                showMessage = null
            }
        }

        // Check service status periodically
        LaunchedEffect(Unit) {
            while (true) {
                serviceEnabled = AutoGLMAccessibilityService.isRunning()
                kotlinx.coroutines.delay(1000)
            }
        }
    }
}
