package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AudioTrackItem
import com.example.model.OverlayPosition
import com.example.model.TextOverlay
import com.example.model.TimelineClip
import com.example.model.VideoProject
import com.example.ui.viewmodel.SelectedTrackItem

@Composable
fun TrackInspectorPanel(
    project: VideoProject,
    selectedItem: SelectedTrackItem?,
    isEditable: Boolean,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit,
    onMoveClip: (String, Int) -> Unit,
    onDuplicateClip: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onUpdateOverlay: (String, (TextOverlay) -> TextOverlay) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onUpdateAudio: (String, (AudioTrackItem) -> AudioTrackItem) -> Unit,
    onDeleteAudio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when (selectedItem) {
            is SelectedTrackItem.Clip -> {
                val clip = project.clips.firstOrNull { it.clipId == selectedItem.clipId }
                if (clip != null) {
                    ClipInspector(
                        clip = clip,
                        isEditable = isEditable,
                        onUpdate = { updater -> onUpdateClip(clip.clipId, updater) },
                        onMoveLeft = { onMoveClip(clip.clipId, -1) },
                        onMoveRight = { onMoveClip(clip.clipId, 1) },
                        onDuplicate = { onDuplicateClip(clip.clipId) },
                        onDelete = { onDeleteClip(clip.clipId) }
                    )
                }
            }
            is SelectedTrackItem.Overlay -> {
                val overlay = project.overlays.firstOrNull { it.overlayId == selectedItem.overlayId }
                if (overlay != null) {
                    OverlayInspector(
                        overlay = overlay,
                        isEditable = isEditable,
                        totalDurationMs = project.computedDurationMs().coerceAtLeast(4000L),
                        onUpdate = { updater -> onUpdateOverlay(overlay.overlayId, updater) },
                        onDelete = { onDeleteOverlay(overlay.overlayId) }
                    )
                }
            }
            is SelectedTrackItem.Audio -> {
                val track = project.audioTracks.firstOrNull { it.trackId == selectedItem.trackId }
                if (track != null) {
                    AudioInspector(
                        track = track,
                        isEditable = isEditable,
                        onUpdate = { updater -> onUpdateAudio(track.trackId, updater) },
                        onDelete = { onDeleteAudio(track.trackId) }
                    )
                }
            }
            null -> {
                Text(
                    text = "Tap any clip, text overlay, or audio track on the timeline to inspect & trim.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipInspector(
    clip: TimelineClip,
    isEditable: Boolean,
    onUpdate: ((TimelineClip) -> TimelineClip) -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clip Inspector",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = clip.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (isEditable) {
                Row {
                    IconButton(onClick = onMoveLeft) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move clip earlier")
                    }
                    IconButton(onClick = onMoveRight) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move clip later")
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate clip")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete clip",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (isEditable) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = clip.title,
                onValueChange = { next -> onUpdate { it.copy(title = next.take(60)) } },
                label = { Text("Scene Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            val maxRange = clip.durationMs.coerceAtLeast(10000L).toFloat()
            Text(
                text = "Trim End (${formatDurationShort(clip.effectiveDurationMs())})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = (clip.trimEndMs - clip.trimStartMs).toFloat().coerceIn(500f, maxRange),
                onValueChange = { span ->
                    val newEnd = (clip.trimStartMs + span.toLong()).coerceAtLeast(clip.trimStartMs + 500L)
                    onUpdate { it.copy(trimEndMs = newEnd, durationMs = maxOf(it.durationMs, newEnd)) }
                },
                valueRange = 500f..maxRange
            )

            Text(
                text = "Speed & Transition",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(0.5, 1.0, 1.5, 2.0).forEach { spd ->
                    FilterChip(
                        selected = clip.speed == spd,
                        onClick = { onUpdate { it.copy(speed = spd) } },
                        label = { Text("${spd}x") }
                    )
                }
                listOf("Cut", "Fade", "Slide", "Zoom").forEach { tr ->
                    FilterChip(
                        selected = clip.transition == tr,
                        onClick = { onUpdate { it.copy(transition = tr) } },
                        label = { Text(tr) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Color Look",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Natural", "Vivid", "Cinema", "Mono").forEach { look ->
                    FilterChip(
                        selected = clip.filter == look,
                        onClick = { onUpdate { it.copy(filter = look) } },
                        label = { Text(look) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayInspector(
    overlay: TextOverlay,
    isEditable: Boolean,
    totalDurationMs: Long,
    onUpdate: ((TextOverlay) -> TextOverlay) -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Text Overlay Inspector",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (isEditable) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete text overlay",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (isEditable) {
            OutlinedTextField(
                value = overlay.text,
                onValueChange = { next -> onUpdate { it.copy(text = next.take(80)) } },
                label = { Text("Caption Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Start: ${formatDurationShort(overlay.startMs)} • Length: ${formatDurationShort(overlay.durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = overlay.startMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                onValueChange = { v -> onUpdate { it.copy(startMs = v.toLong()) } },
                valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1000f)
            )
            Slider(
                value = overlay.durationMs.toFloat().coerceIn(800f, 10000f),
                onValueChange = { v -> onUpdate { it.copy(durationMs = v.toLong()) } },
                valueRange = 800f..10000f
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = overlay.position == pos.name,
                        onClick = { onUpdate { it.copy(position = pos.name) } },
                        label = { Text(pos.name) }
                    )
                }
                listOf("BOLD_PILL" to "Pill", "MINIMAL_LOWER" to "Minimal", "TITLE_CARD" to "Card").forEach { (k, label) ->
                    FilterChip(
                        selected = overlay.styleKey == k,
                        onClick = { onUpdate { it.copy(styleKey = k) } },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioInspector(
    track: AudioTrackItem,
    isEditable: Boolean,
    onUpdate: ((AudioTrackItem) -> AudioTrackItem) -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio Track Inspector",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (isEditable) {
                Row {
                    IconButton(onClick = { onUpdate { it.copy(muted = !it.muted) } }) {
                        Icon(
                            imageVector = if (track.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (track.muted) "Unmute audio" else "Mute audio",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete audio track",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        if (isEditable) {
            Text(
                text = "Volume: ${(track.volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = track.volume.toFloat().coerceIn(0f, 1f),
                onValueChange = { v -> onUpdate { it.copy(volume = v.toDouble()) } }
            )
        }
    }
}
