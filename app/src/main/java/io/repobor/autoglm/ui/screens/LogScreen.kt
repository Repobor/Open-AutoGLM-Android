package io.repobor.autoglm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.repobor.autoglm.config.I18n
import java.text.SimpleDateFormat
import java.util.*

/**
 * Log entry data class.
 */
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val task: String,
    val status: String,
    val result: String,
    val steps: Int
)

/**
 * Log screen showing execution history.
 * Currently uses in-memory storage. Can be upgraded to use Room database.
 */
@Composable
fun LogScreen() {
    // TODO: Replace with persistent storage (Room database)
    var logEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var selectedEntry by remember { mutableStateOf<LogEntry?>(null) }

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

            if (logEntries.isNotEmpty()) {
                TextButton(
                    onClick = {
                        logEntries = emptyList()
                        selectedEntry = null
                    }
                ) {
                    Text(s.LOG_CLEAR)
                }
            }
        }

        // Log list
        if (logEntries.isEmpty()) {
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
                items(logEntries) { entry ->
                    LogEntryCard(
                        entry = entry,
                        isSelected = entry == selectedEntry,
                        onClick = {
                            selectedEntry = if (selectedEntry == entry) null else entry
                        },
                        language = language
                    )
                }
            }
        }

        // Sample entries for demonstration
        if (logEntries.isEmpty()) {
            LaunchedEffect(Unit) {
                logEntries = listOf(
                    LogEntry(
                        task = "打开微信",
                        status = "成功",
                        result = "已成功打开微信应用",
                        steps = 2,
                        timestamp = System.currentTimeMillis() - 3600000
                    ),
                    LogEntry(
                        task = "搜索淘宝商品",
                        status = "成功",
                        result = "已找到相关商品",
                        steps = 8,
                        timestamp = System.currentTimeMillis() - 7200000
                    ),
                    LogEntry(
                        task = "发送消息",
                        status = "失败",
                        result = "无法找到聊天窗口",
                        steps = 5,
                        timestamp = System.currentTimeMillis() - 10800000
                    )
                )
            }
        }
    }
}

/**
 * Individual log entry card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogEntryCard(
    entry: LogEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    language: String
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
                    text = oldFormatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OldStatusBadge(entry.status)
            }

            // Task description
            Text(
                text = "${s.LOG_TASK}: ${entry.task}",
                style = MaterialTheme.typography.bodyLarge
            )

            // Steps count
            Text(
                text = "${s.TASK_STEP}: ${entry.steps}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Expandable details
            if (isSelected) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "${s.LOG_DETAILS}:",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = entry.result,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Status badge component.
 */
@Composable
private fun OldStatusBadge(status: String) {
    val (color, text) = when (status) {
        "成功", "Success" -> Pair(
            MaterialTheme.colorScheme.primaryContainer,
            "✓ $status"
        )
        "失败", "Failed" -> Pair(
            MaterialTheme.colorScheme.errorContainer,
            "✗ $status"
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
 * Format timestamp to readable string.
 */
private fun oldFormatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
