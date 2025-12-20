package io.repobor.autoglm.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.repobor.autoglm.R
import io.repobor.autoglm.config.I18n
import io.repobor.autoglm.ui.viewmodel.TaskExecutionViewModel

/**
 * Content for the floating window.
 * Shows a small circle icon when collapsed, expands to a control panel when tapped.
 */
@Composable
fun FloatingWindowContent(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    viewModel: TaskExecutionViewModel? = null
) {
    val context = LocalContext.current

    if (isExpanded) {
        ExpandedFloatingWindow(
            viewModel = viewModel,
            onCollapse = onToggleExpand
        )
    } else {
        CollapsedFloatingIcon(
            // 长按关闭服务
            viewModel = viewModel
        )
    }
}

/**
 * Collapsed state: Small circular icon.
 * - Click to expand (handled by View's touch listener in FloatingWindowService)
 * - Long press to close service (handled by View's touch listener)
 * - Drag to move (handled by View's touch listener)
 *
 * Note: This composable should NOT handle clicks directly to avoid conflicts with drag gestures.
 */
@Composable
fun CollapsedFloatingIcon(
    viewModel: TaskExecutionViewModel?
) {
    val isRunning = viewModel?.isRunning?.collectAsState(initial = false)?.value ?: false
    val currentStep = viewModel?.currentStep?.collectAsState(initial = 0)?.value ?: 0

    Box(
        modifier = Modifier
            .size(24.dp)
            .shadow(2.dp, CircleShape)
            .background(
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ),
            // 移除 combinedClickable，点击和长按由 FloatingWindowService 处理
        contentAlignment = Alignment.Center
    ) {
        if (isRunning && currentStep > 0) {
            Text(
                text = "$currentStep",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_open),
                contentDescription = "Expand",
                tint = if (isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Expanded state: Full control panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedFloatingWindow(
    viewModel: TaskExecutionViewModel?,
    onCollapse: () -> Unit
) {
    val language = "zh" // TODO: Get from settings
    val s = if (language == "zh") I18n.Chinese else I18n.English

    val isRunning = viewModel?.isRunning?.collectAsState(initial = false)?.value ?: false
    val currentStep = viewModel?.currentStep?.collectAsState(initial = 0)?.value ?: 0
    val maxSteps = viewModel?.maxSteps?.collectAsState(initial = 30)?.value ?: 30
    val hasError = viewModel?.hasError?.collectAsState(initial = false)?.value ?: false
    val logEntries = viewModel?.logEntries?.collectAsState(initial = emptyList())?.value ?: emptyList()

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .wrapContentHeight()
            .padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with app name and collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.APP_NAME,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher_background),
                        contentDescription = "Collapse",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Progress info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else if (isRunning) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Step counter with percentage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${s.TASK_STEP} $currentStep / $maxSteps",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else if (isRunning) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        Text(
                            text = "${(currentStep * 100 / maxSteps)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else if (isRunning) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { if (maxSteps > 0) currentStep.toFloat() / maxSteps else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = if (hasError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // Running indicator
                    if (isRunning) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Latest log entry
                    if (logEntries.isNotEmpty()) {
                        val latestLog = logEntries.last()
                        Text(
                            text = latestLog.take(80),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = if (hasError) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 4
                        )
                    }
                }
            }

            // Control buttons
            if (isRunning) {
                Button(
                    onClick = { viewModel?.stopTask() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_stop),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(s.TASK_STOP, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                OutlinedButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Text(s.TASK_RETURN_TO_APP ?: "返回应用", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
