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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ads.AdMobBanner
import com.example.data.db.DownloadTaskEntity
import com.example.data.db.MediaType
import com.example.ui.theme.AccentBlue
import com.example.ui.viewmodels.MainViewModel
import com.example.ui.viewmodels.SubScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val completedTasks by viewModel.completedTasks.collectAsState()
    val uncompletedCount by viewModel.uncompletedCount.collectAsState()
    val expandedPlaylists by viewModel.expandedPlaylistIds.collectAsState()
    val multiSelectMode by viewModel.multiSelectMode.collectAsState()
    val selectedTaskIds by viewModel.selectedTaskIds.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Multi-Select Action Bar (shown when long-pressing an item)
        if (multiSelectMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = AccentBlue.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.exitMultiSelect() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close multi-select", tint = AccentBlue)
                        }
                        Text(
                            text = "${selectedTaskIds.size} selected",
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.selectAllTasks(completedTasks.map { it.id }) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = AccentBlue)
                        }
                        IconButton(
                            onClick = {
                                if (selectedTaskIds.isNotEmpty()) {
                                    viewModel.promptDeleteTasks(selectedTaskIds.toList())
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Section 6: Top Summary Card for Uncompleted Tasks
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .testTag("uncompleted_summary_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (uncompletedCount > 0) AccentBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
            ),
            onClick = { viewModel.navigateToSubScreen(SubScreen.UNCOMPLETED_LIST) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uncompletedCount > 0) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentBlue,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uncompletedCount > 0) "$uncompletedCount downloads in progress" else "All downloads completed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (uncompletedCount > 0) "Tap to pause, resume, or view active tasks" else "No active downloads currently",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 6: Directly beneath this card, list Completed downloads inline (most recent first)
        Text(
            text = "Completed (${completedTasks.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )

        if (completedTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No completed downloads yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(completedTasks, key = { it.id }) { task ->
                    val isSelected = selectedTaskIds.contains(task.id)
                    val isExpanded = expandedPlaylists.contains(task.playlistId ?: task.id)

                    CompletedTaskRow(
                        task = task,
                        isMultiSelectMode = multiSelectMode,
                        isSelected = isSelected,
                        isExpanded = isExpanded,
                        onTap = {
                            if (multiSelectMode) {
                                viewModel.toggleTaskSelection(task.id)
                            } else {
                                viewModel.openMediaFile(context, task)
                            }
                        },
                        onLongPress = {
                            if (!multiSelectMode) {
                                viewModel.enterMultiSelect(task.id)
                            }
                        },
                        onToggleExpand = {
                            viewModel.toggleExpandPlaylist(task.playlistId ?: task.id)
                        },
                        onShare = { viewModel.shareMediaFile(context, task) },
                        onDetails = { viewModel.showDetails(task) },
                        onDelete = { viewModel.promptDeleteTasks(listOf(task.id)) }
                    )
                }
            }
        }

        // Section 14: Non-intrusive AdMob Banner at bottom
        AdMobBanner(modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletedTaskRow(
    task: DownloadTaskEntity,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isExpanded: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onToggleExpand: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AccentBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onTap() },
                        colors = CheckboxDefaults.colors(checkedColor = AccentBlue)
                    )
                }

                // Thumbnail with Media Type Icon overlay
                Box(
                    modifier = Modifier
                        .width(80.dp)
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

                    val typeIcon = when (task.mediaType) {
                        MediaType.AUDIO -> Icons.Default.Audiotrack
                        MediaType.PHOTO -> Icons.Default.Photo
                        MediaType.PLAYLIST -> Icons.Default.PlaylistPlay
                        else -> Icons.Default.Videocam
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Subtitle Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
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

                        if (task.totalBytes > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val mb = task.totalBytes.toDouble() / (1024 * 1024)
                            Text(
                                text = String.format("%.1f MB", mb),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${task.platform}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // If Playlist Root, show Expand / Collapse Toggle
                if (task.isPlaylistRoot) {
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand playlist items",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3-Dot Overflow Menu (Share, Details, Delete)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open with Player") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onTap()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Details") },
                            leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuExpanded = false
                                onDetails()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Collapsible Nested Playlist Children
            if (task.isPlaylistRoot && isExpanded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Playlist Contents (${task.playlistTotalItems} items)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All individual items saved in ${task.selectedQuality} resolution.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
