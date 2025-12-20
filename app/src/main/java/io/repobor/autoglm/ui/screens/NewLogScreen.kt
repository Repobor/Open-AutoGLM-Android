package io.repobor.autoglm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.repobor.autoglm.config.I18n
import io.repobor.autoglm.data.ExecutionLogRepository
import io.repobor.autoglm.data.database.ExecutionLogEntity
import io.repobor.autoglm.data.database.StepDetail
import io.repobor.autoglm.util.ConversationFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Log screen with database integration and repeat execution support.
 */
@Composable
fun NewLogScreen(
    onRepeatTask: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { ExecutionLogRepository(context) }
    val scope = rememberCoroutineScope()

    // Collect logs from database
    val logs by repository.getAllLogs().collectAsState(initial = emptyList())
    var selectedEntry by remember { mutableStateOf<ExecutionLogEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Default language (should be from settings)
    val language = "zh"
    val s = if (language == "zh") I18n.Chinese else I18n.English

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.LOG_TITLE,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Refresh button
                IconButton(
                    onClick = {
                        // Logs are automatically refreshed via Flow
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }

                // Clear all button
                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearAllDialog = true }
                    ) {
                        Text(s.LOG_CLEAR)
                    }
                }
            }
        }

        // Log count
        if (logs.isNotEmpty()) {
            Text(
                text = "${s.LOG_TASK}: ${logs.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Log list
        if (logs.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.LOG_EMPTY,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { entry ->
                    LogEntryCard(
                        entry = entry,
                        isSelected = entry == selectedEntry,
                        onClick = {
                            selectedEntry = if (selectedEntry == entry) null else entry
                        },
                        onRepeat = {
                            onRepeatTask(entry.task)
                        },
                        onDelete = {
                            scope.launch {
                                repository.deleteLog(entry)
                            }
                        },
                        language = language,
                        repository = repository
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && selectedEntry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(s.DIALOG_CONFIRM) },
            text = { Text("确定要删除这条日志吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            selectedEntry?.let { repository.deleteLog(it) }
                            selectedEntry = null
                            showDeleteDialog = false
                        }
                    }
                ) {
                    Text(s.DIALOG_CONFIRM)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(s.DIALOG_CANCEL)
                }
            }
        )
    }

    // Clear all confirmation dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(s.DIALOG_CLEAR_LOG_TITLE) },
            text = { Text(s.DIALOG_CLEAR_LOG_MESSAGE) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteAllLogs()
                            selectedEntry = null
                            showClearAllDialog = false
                        }
                    }
                ) {
                    Text(s.DIALOG_CONFIRM)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(s.DIALOG_CANCEL)
                }
            }
        )
    }
}

/**
 * Individual log entry card with repeat and delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogEntryCard(
    entry: ExecutionLogEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
    language: String,
    repository: ExecutionLogRepository
) {
    val s = if (language == "zh") I18n.Chinese else I18n.English

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row with timestamp and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(entry.startTimestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatusBadge(entry.status)
            }

            // Task description
            Text(
                text = "${s.LOG_TASK}: ${entry.task}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            // Steps count and duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${s.TASK_STEP}: ${entry.steps}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val duration = (entry.endTimestamp - entry.startTimestamp) / 1000
                Text(
                    text = "${duration}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Repeat button
                OutlinedButton(
                    onClick = onRepeat,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重复执行")
                }

                // Delete button
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable details
            if (isSelected) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Result
                Text(
                    text = "${s.LOG_DETAILS}:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = entry.result,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Conversation history
                if (entry.conversationHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "对话历史:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val stepDetails = repository.parseStepDetails(entry.stepDetails)
                    // Parse JSON string to List<Map>
                    val conversationList = try {
                        val json = org.json.JSONArray(entry.conversationHistory)
                        val list = mutableListOf<Map<String, Any>>()
                        for (i in 0 until json.length()) {
                            val obj = json.getJSONObject(i)
                            val map = mutableMapOf<String, Any>()
                            val keys = obj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                map[key] = obj.get(key)
                            }
                            list.add(map)
                        }
                        list
                    } catch (e: Exception) {
                        emptyList<Map<String, Any>>()
                    }

                    val conversationItems = if (conversationList.isNotEmpty()) {
                        ConversationFormatter.formatConversationFromList(
                            conversationList,
                            stepDetails,
                            entry.language
                        )
                    } else {
                        emptyList()
                    }

                    if (conversationItems.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            conversationItems.forEach { item ->
                                ConversationItemCard(item, entry.language)
                            }
                        }
                    } else {
                        Text(
                            text = "No conversation history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Step details
                if (entry.stepDetails.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "步骤详情:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val stepDetails = repository.parseStepDetails(entry.stepDetails)
                    stepDetails.forEach { step ->
                        StepDetailItem(step)
                    }
                }
            }
        }
    }
}

/**
 * Step detail item.
 */
@Composable
fun StepDetailItem(step: StepDetail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (step.success) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "步骤 ${step.stepNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (step.success) "✓" else "✗",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (step.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Text(
                text = step.actionDescription,
                style = MaterialTheme.typography.bodySmall
            )

            if (step.thinking.isNotEmpty()) {
                Text(
                    text = "思考: ${step.thinking.take(100)}${if (step.thinking.length > 100) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Status badge component.
 */
@Composable
fun StatusBadge(status: String) {
    val (color, text) = when (status) {
        "成功", "Success" -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            "✓ $status"
        )
        "失败", "Failed" -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            "✗ $status"
        )
        "停止", "Stopped" -> Pair(
            MaterialTheme.colorScheme.tertiaryContainer,
            "■ $status"
        )
        else -> Pair(
            MaterialTheme.colorScheme.surfaceVariant,
            status
        )
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Conversation item card for displaying single conversation entry.
 */
@Composable
fun ConversationItemCard(
    item: ConversationFormatter.ConversationItem,
    language: String
) {
    val backgroundColor = when (item.type) {
        ConversationFormatter.ItemType.USER -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ConversationFormatter.ItemType.THINKING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        ConversationFormatter.ItemType.ACTION -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
    }

    val typeLabel = when (item.type) {
        ConversationFormatter.ItemType.USER -> if (language == "zh") "[用户]" else "[User]"
        ConversationFormatter.ItemType.THINKING -> if (language == "zh") "[思考]" else "[Thinking]"
        ConversationFormatter.ItemType.ACTION -> if (language == "zh") "[动作]" else "[Action]"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        color = backgroundColor,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = when (item.type) {
                    ConversationFormatter.ItemType.USER -> MaterialTheme.colorScheme.primary
                    ConversationFormatter.ItemType.THINKING -> MaterialTheme.colorScheme.tertiary
                    ConversationFormatter.ItemType.ACTION -> MaterialTheme.colorScheme.secondary
                }
            )
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Format timestamp to readable string.
 */
fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
