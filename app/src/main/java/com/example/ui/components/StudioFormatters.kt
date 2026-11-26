package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.TimelineClip
import com.example.model.VideoProject
import com.example.ui.viewmodel.CloudSyncStatus
import java.util.Locale

data class ActiveClipSlice(
    val clip: TimelineClip,
    val index: Int,
    val startMs: Long,
    val localOffsetMs: Long
)

fun formatTimecode(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val tenths = (safeMs % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}

fun formatDurationShort(ms: Long): String {
    val safeSec = (ms / 1000.0).coerceAtLeast(0.0)
    return if (safeSec >= 60.0) {
        val m = (safeSec / 60).toInt()
        val s = (safeSec % 60).toInt()
        "${m}m ${s}s"
    } else {
        String.format(Locale.US, "%.1fs", safeSec)
    }
}

fun resolveActiveClip(clips: List<TimelineClip>, playheadMs: Long): ActiveClipSlice? {
    if (clips.isEmpty()) return null
    var cursor = 0L
    clips.forEachIndexed { index, clip ->
        val dur = clip.effectiveDurationMs()
        if (playheadMs < cursor + dur || index == clips.lastIndex) {
            return ActiveClipSlice(
                clip = clip,
                index = index,
                startMs = cursor,
                localOffsetMs = (playheadMs - cursor).coerceIn(0L, dur)
            )
        }
        cursor += dur
    }
    return null
}

fun shareVideoProjectViaSystem(context: Context, project: VideoProject) {
    val shareLinkLine = if (project.shareVisibility == "link_anyone" && project.shareCode.isNotBlank()) {
        "\nCloud Share Code: ${project.shareCode}\nOpen in Vids Studio -> Shared -> Enter Share Code"
    } else {
        ""
    }
    val text = buildString {
        append("Check out my video \"${project.title}\" created in Vids Studio!\n")
        append("Format: ${project.aspectRatio} • Duration: ${formatDurationShort(project.computedDurationMs())} • ${project.clips.size} scenes")
        append(shareLinkLine)
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, project.title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, "Share \"${project.title}\"")
    context.startActivity(chooser)
}

@Composable
fun CloudSyncPill(
    status: CloudSyncStatus,
    modifier: Modifier = Modifier
) {
    val targetColor = when (status) {
        CloudSyncStatus.SYNCED -> MaterialTheme.colorScheme.tertiaryContainer
        CloudSyncStatus.SAVING -> MaterialTheme.colorScheme.secondaryContainer
        CloudSyncStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        CloudSyncStatus.VIEW_ONLY -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (status) {
        CloudSyncStatus.SYNCED -> MaterialTheme.colorScheme.onTertiaryContainer
        CloudSyncStatus.SAVING -> MaterialTheme.colorScheme.onSecondaryContainer
        CloudSyncStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        CloudSyncStatus.VIEW_ONLY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bgColor by animateColorAsState(targetValue = targetColor, label = "syncBg")

    val (icon, label) = when (status) {
        CloudSyncStatus.SYNCED -> Icons.Rounded.CloudDone to "Synced"
        CloudSyncStatus.SAVING -> Icons.Rounded.CloudSync to "Syncing..."
        CloudSyncStatus.ERROR -> Icons.Rounded.CloudOff to "Sync Error"
        CloudSyncStatus.VIEW_ONLY -> Icons.Rounded.Visibility to "View Only"
    }

    Surface(
        color = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
