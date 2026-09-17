package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.engine.ExtractedMedia
import com.example.engine.FormatOption
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.QualityCellBgDark
import com.example.ui.theme.QualityCellBgLight
import com.example.ui.theme.QualityCellBorderDark
import com.example.ui.theme.QualityCellBorderLight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QualityPickerBottomSheet(
    media: ExtractedMedia,
    selectedOption: FormatOption?,
    selectedPhotoIds: Set<String>,
    playlistItemCount: Int? = null,
    onOptionSelected: (FormatOption) -> Unit,
    onPhotoToggled: (String) -> Unit,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Left 16:9 Thumbnail with Duration Pill, Right Title & Platform Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = media.thumbnailUrl,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (media.durationSeconds > 0) {
                        val min = media.durationSeconds / 60
                        val sec = media.durationSeconds % 60
                        val durationStr = String.format("%02d:%02d", min, sec)
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = durationStr,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(media.platform.badgeColor).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = media.platform.displayName,
                                color = Color(media.platform.badgeColor),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (playlistItemCount != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentBlue.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "$playlistItemCount videos selected",
                                    color = AccentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Body: Photos Grid OR Video/Audio Quality Selection
            if (media.isPhotoPost) {
                Text(
                    text = "Select Photos to Download (${selectedPhotoIds.size}/${media.photos.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    media.photos.forEach { photo ->
                        val isSelected = selectedPhotoIds.contains(photo.id)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onPhotoToggled(photo.id) }
                        ) {
                            AsyncImage(
                                model = photo.url,
                                contentDescription = "Photo ${photo.index}",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onPhotoToggled(photo.id) },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(2.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentBlue,
                                    uncheckedColor = Color.White
                                )
                            )
                        }
                    }
                }
            } else {
                // Video Qualities Section
                if (media.videoFormats.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download as Video",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        media.videoFormats.forEach { format ->
                            val isSelected = selectedOption?.id == format.id
                            QualityCell(
                                label = format.qualityLabel,
                                subtitle = if (format.filesizeBytes > 0) formatFileSize(format.filesizeBytes) else "${format.fps} FPS",
                                isSelected = isSelected,
                                onClick = { onOptionSelected(format) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Audio Qualities Section
                if (media.audioFormats.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download as Audio",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        media.audioFormats.forEach { format ->
                            val isSelected = selectedOption?.id == format.id
                            QualityCell(
                                label = format.qualityLabel,
                                subtitle = format.resolution,
                                isSelected = isSelected,
                                onClick = { onOptionSelected(format) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom CTA: Download Button
            val isButtonEnabled = if (media.isPhotoPost) {
                selectedPhotoIds.isNotEmpty()
            } else {
                selectedOption != null
            }

            val buttonLabel = when {
                media.isPhotoPost -> "Download (${selectedPhotoIds.size} Photos)"
                playlistItemCount != null -> "Download Playlist ($playlistItemCount Videos)"
                selectedOption != null -> "Download ${selectedOption.qualityLabel}"
                else -> "Select a Quality"
            }

            Button(
                onClick = onConfirmDownload,
                enabled = isButtonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_download_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun QualityCell(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface == com.example.ui.theme.DarkSurface
    val bgColor = if (isSelected) {
        AccentBlue.copy(alpha = 0.12f)
    } else {
        if (isDark) QualityCellBgDark else QualityCellBgLight
    }
    val borderColor = if (isSelected) {
        AccentBlue
    } else {
        if (isDark) QualityCellBorderDark else QualityCellBorderLight
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1000) {
        String.format("%.1f GB", mb / 1024)
    } else {
        String.format("%.0f MB", mb)
    }
}
