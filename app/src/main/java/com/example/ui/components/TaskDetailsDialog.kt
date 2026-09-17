package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.DownloadTaskEntity
import com.example.ui.theme.AccentBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskDetailsDialog(
    task: DownloadTaskEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Media Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DetailRow("Title", task.title)
                DetailRow("Platform", task.platform)
                DetailRow("Quality", task.selectedQuality)
                DetailRow("Format", task.mimeType.ifBlank { "video/mp4" })
                if (task.totalBytes > 0) {
                    val sizeMb = task.totalBytes.toDouble() / (1024 * 1024)
                    DetailRow("File Size", String.format("%.1f MB", sizeMb))
                }
                if (task.completedAt > 0) {
                    val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(task.completedAt))
                    DetailRow("Downloaded", date)
                }
                DetailRow("Engine Used", task.engineUsed)
                if (task.filePath.isNotBlank()) {
                    DetailRow("Save Location", task.filePath)
                }
                DetailRow("Source URL", task.originalUrl)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentBlue, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (count > 1) "Delete $count items?" else "Delete download?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "This will remove the selected downloaded file(s) from your device storage. Your download history will be preserved.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
