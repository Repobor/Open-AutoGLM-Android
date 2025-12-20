package io.repobor.autoglm.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.repobor.autoglm.accessibility.AutoGLMAccessibilityService
import io.repobor.autoglm.config.I18n
import io.repobor.autoglm.data.SettingsRepository
import io.repobor.autoglm.ui.viewmodel.TaskExecutionViewModel
import io.repobor.autoglm.ui.viewmodel.TaskExecutionViewModelFactory

/**
 * Task execution screen with real-time logging.
 * Now uses TaskExecutionViewModel for state management.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable

fun TaskScreen(
    settingsRepository: SettingsRepository,
    viewModel: TaskExecutionViewModel = viewModel(
        factory = TaskExecutionViewModelFactory(LocalContext.current, settingsRepository)
    ),
    initialTask: String = ""
) {
    val context = LocalContext.current

    // Collect settings (only need language for localization)
    val language by settingsRepository.language.collectAsState(initial = SettingsRepository.DEFAULT_LANGUAGE)

    // Task input state (local) - initialized with initialTask if provided
    var taskDescription by remember { mutableStateOf(initialTask) }

    // Collect state from ViewModel
    val isRunning by viewModel.isRunning.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val maxSteps by viewModel.maxSteps.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    val finalResult by viewModel.finalResult.collectAsState()

    // Get localized strings
    val s = if (language == "zh") I18n.Chinese else I18n.English

    // Auto-scroll to bottom
    val listState = rememberLazyListState()
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = s.TASK_TITLE,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Service status check
        val serviceEnabled = AutoGLMAccessibilityService.isRunning()
//        if (!serviceEnabled) {
//            Card(
//                modifier = Modifier.fillMaxWidth(),
//                colors = CardDefaults.cardColors(
//                    containerColor = MaterialTheme.colorScheme.errorContainer
//                )
//            ) {
//                Text(
//                    text = s.ERROR_SERVICE_NOT_ENABLED,
//                    modifier = Modifier.padding(16.dp),
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.onErrorContainer
//                )
//            }
//        }

        // Task input
        OutlinedTextField(
            value = taskDescription,
            onValueChange = { taskDescription = it },
            label = { Text(s.TASK_DESCRIPTION) },
            placeholder = { Text(s.TASK_DESCRIPTION_HINT) },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            enabled = !isRunning,
            maxLines = 5
        )

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isRunning) {
                Button(
                    onClick = {
                        if (taskDescription.isBlank()) return@Button
                        // Start task via ViewModel
                        viewModel.startTask(taskDescription, context)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning && taskDescription.isNotBlank()
                ) {
                    Text(s.TASK_START)
                }
            } else {
                Button(
                    onClick = {
                        // Stop task via ViewModel
                        viewModel.stopTask()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(s.TASK_STOP)
                }
            }
        }

        // Status indicator
        if (isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = s.TASK_RUNNING,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${s.TASK_STEP}: $currentStep / $maxSteps",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Final result
        finalResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.startsWith("Error", ignoreCase = true)) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Log display with tabs
        var selectedTabIndex by remember { mutableStateOf(0) }
        val conversationDisplayLog by viewModel.conversationDisplayLog.collectAsState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Tab row
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(s.LOG_TITLE, style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(if (language == "zh") "对话历史" else "Conversation", style = MaterialTheme.typography.labelMedium) }
                    )
                }

                // Tab content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    when (selectedTabIndex) {
                        0 -> {
                            // Execution Steps tab
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "${s.LOG_TITLE}:",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (logEntries.isEmpty()) {
                                    Text(
                                        text = s.LOG_EMPTY,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(logEntries) { entry ->
                                            Text(
                                                text = entry,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Conversation History tab
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = if (language == "zh") "对话历史:" else "Conversation:",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (conversationDisplayLog.isEmpty()) {
                                    Text(
                                        text = s.LOG_EMPTY,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(conversationDisplayLog) { entry ->
                                            Text(
                                                text = entry,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
