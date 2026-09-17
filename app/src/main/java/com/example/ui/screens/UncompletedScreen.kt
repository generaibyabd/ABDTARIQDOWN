package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.DownloadTaskEntity
import com.example.data.db.TaskStatus
import com.example.ui.theme.AccentBlue
import com.example.ui.viewmodels.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UncompletedScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uncompletedTasks by viewModel.uncompletedTasks.collectAsState()
    val multiSelectMode by viewModel.multiSelectMode.collectAsState()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()
    val expandedPlaylists by viewModel.expandedPlaylistIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Active Downloads (${uncompletedTasks.size})",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (multiSelectMode) {
                        IconButton(onClick = { viewModel.selectAllTasks(uncompletedTasks.map { it.id }) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = AccentBlue)
                        }
                        IconButton(onClick = {
                            if (selectedTaskIds.isNotEmpty()) {
                                viewModel.promptDeleteTasks(selectedTaskIds.toList())
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (uncompletedTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No downloads currently active or paused",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uncompletedTasks, key = { it.id }) { task ->
                        val isSelected = selectedTaskIds.contains(task.id)
                        val isPaused = task.status == TaskStatus.PAUSED
                        val isExpanded = expandedPlaylists.contains(task.playlistId ?: task.id)

                        UncompletedTaskRow(
                            task = task,
                            isPaused = isPaused,
                            isMultiSelectMode = multiSelectMode,
                            isSelected = isSelected,
                            isExpanded = isExpanded,
                            onTogglePauseResume = {
                                if (multiSelectMode) {
                                    viewModel.toggleTaskSelection(task.id)
                                } else {
                                    if (isPaused) viewModel.resumeTask(task) else viewModel.pauseTask(task)
                                }
                            },
                            onLongPress = {
                                if (!multiSelectMode) {
                                    viewModel.enterMultiSelect(task.id)
                                }
                            },
                            onToggleExpand = {
                                viewModel.toggleExpandPlaylist(task.playlistId ?: task.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UncompletedTaskRow(
    task: DownloadTaskEntity,
    isPaused: Boolean,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isExpanded: Boolean,
    onTogglePauseResume: () -> Unit,
    onLongPress: () -> Unit,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onTogglePauseResume,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AccentBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onTogglePauseResume() },
                        colors = CheckboxDefaults.colors(checkedColor = AccentBlue)
                    )
                }

                // Play / Pause Leading Action Icon
                IconButton(
                    onClick = onTogglePauseResume,
                    modifier = Modifier.testTag("pause_resume_button_${task.id}")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = AccentBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Thumbnail
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = task.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Quality
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = task.selectedQuality,
                                color = AccentBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPaused) "Paused" else (task.speedText.ifBlank { "Downloading" }),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isPaused) MaterialTheme.colorScheme.error else AccentBlue
                        )
                    }
                }

                if (task.isPlaylistRoot) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand playlist"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-time Progress Bar
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Progress text details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val percent = (task.progress * 100).roundToInt()
                Text(
                    text = "$percent%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue
                )

                val downloadedMb = task.downloadedBytes.toDouble() / (1024 * 1024)
                val totalMb = task.totalBytes.toDouble() / (1024 * 1024)
                val sizeText = if (task.totalBytes > 0) {
                    String.format("%.1f MB / %.1f MB", downloadedMb, totalMb)
                } else {
                    String.format("%.1f MB", downloadedMb)
                }

                Text(
                    text = sizeText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
