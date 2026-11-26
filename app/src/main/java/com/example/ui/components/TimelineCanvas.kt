package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.model.AudioSourceType
import com.example.model.AudioTrackItem
import com.example.model.ClipType
import com.example.model.TextOverlay
import com.example.model.TimelineClip
import com.example.model.VideoProject
import com.example.ui.theme.RoseOverlayTrack
import com.example.ui.theme.SkyTrack
import com.example.ui.viewmodel.SelectedTrackItem
import kotlin.math.roundToInt

@Composable
fun TimelineCanvas(
    project: VideoProject,
    playheadMs: Long,
    zoom: Float,
    selectedItem: SelectedTrackItem?,
    isEditable: Boolean,
    onSeek: (Long) -> Unit,
    onSelectClip: (String) -> Unit,
    onSelectOverlay: (String) -> Unit,
    onSelectAudio: (String) -> Unit,
    onAddClipClick: () -> Unit,
    onAddOverlayClick: () -> Unit,
    onAddAudioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerSecondBase = 48.dp
    val dpPerSecond = pxPerSecondBase * zoom
    val totalDurationMs = project.computedDurationMs().coerceAtLeast(4000L)
    val totalTrackWidthDp = ((totalDurationMs / 1000f) * dpPerSecond.value).dp.coerceAtLeast(280.dp) + 84.dp
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val dpPerMs = with(density) { dpPerSecond.toPx() / 1000f }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left fixed track labels column
            Column(
                modifier = Modifier
                    .width(58.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(top = 28.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TrackHeaderBadge(
                    label = "V1",
                    color = MaterialTheme.colorScheme.primary,
                    canAdd = isEditable,
                    testTag = "add_clip_track_button",
                    onAdd = onAddClipClick
                )
                TrackHeaderBadge(
                    label = "T1",
                    color = RoseOverlayTrack,
                    canAdd = isEditable,
                    testTag = "add_overlay_track_button",
                    onAdd = onAddOverlayClick
                )
                TrackHeaderBadge(
                    label = "A1",
                    color = MaterialTheme.colorScheme.tertiary,
                    canAdd = isEditable,
                    testTag = "add_audio_track_button",
                    onAdd = onAddAudioClick
                )
            }

            // Right horizontally scrollable multi-track canvas with unified playhead
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .padding(end = 16.dp)
            ) {
                Box(modifier = Modifier.width(totalTrackWidthDp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Interactive Timecode Ruler
                        TimelineRuler(
                            totalDurationMs = totalDurationMs,
                            dpPerSecond = dpPerSecond,
                            dpPerMs = dpPerMs,
                            onSeek = onSeek
                        )

                        // V1: Video & Scene Clips Track
                        Row(
                            modifier = Modifier
                                .height(54.dp)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            project.clips.forEach { clip ->
                                val widthDp = ((clip.effectiveDurationMs() / 1000f) * dpPerSecond.value)
                                    .dp
                                    .coerceAtLeast(52.dp)
                                val isSelected = selectedItem == SelectedTrackItem.Clip(clip.clipId)
                                VideoClipBlock(
                                    clip = clip,
                                    width = widthDp,
                                    isSelected = isSelected,
                                    onClick = { onSelectClip(clip.clipId) }
                                )
                            }
                            if (isEditable) {
                                AddTrackSlotButton(
                                    label = "+ Clip",
                                    onClick = onAddClipClick,
                                    testTag = "timeline_add_clip_slot"
                                )
                            }
                        }

                        // T1: Text Overlays Track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 4.dp)
                        ) {
                            if (project.overlays.isEmpty()) {
                                EmptyTrackStrip(
                                    hint = if (isEditable) "Tap + Text to place timed titles" else "No text overlays",
                                    onClick = if (isEditable) onAddOverlayClick else null
                                )
                            } else {
                                project.overlays.forEach { overlay ->
                                    val xOffsetDp = ((overlay.startMs / 1000f) * dpPerSecond.value).dp
                                    val widthDp = ((overlay.durationMs / 1000f) * dpPerSecond.value)
                                        .dp
                                        .coerceAtLeast(58.dp)
                                    val isSelected = selectedItem == SelectedTrackItem.Overlay(overlay.overlayId)
                                    OverlayTrackBlock(
                                        overlay = overlay,
                                        isSelected = isSelected,
                                        modifier = Modifier
                                            .offset(x = xOffsetDp)
                                            .width(widthDp),
                                        onClick = { onSelectOverlay(overlay.overlayId) }
                                    )
                                }
                            }
                        }

                        // A1: Audio & Voiceover Track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 4.dp)
                        ) {
                            if (project.audioTracks.isEmpty()) {
                                EmptyTrackStrip(
                                    hint = if (isEditable) "Tap + Audio or Record Voiceover" else "No audio tracks",
                                    onClick = if (isEditable) onAddAudioClick else null
                                )
                            } else {
                                project.audioTracks.forEach { track ->
                                    val xOffsetDp = ((track.startMs / 1000f) * dpPerSecond.value).dp
                                    val widthDp = ((track.durationMs / 1000f) * dpPerSecond.value)
                                        .dp
                                        .coerceAtLeast(64.dp)
                                    val isSelected = selectedItem == SelectedTrackItem.Audio(track.trackId)
                                    AudioTrackBlock(
                                        track = track,
                                        isSelected = isSelected,
                                        modifier = Modifier
                                            .offset(x = xOffsetDp)
                                            .width(widthDp),
                                        onClick = { onSelectAudio(track.trackId) }
                                    )
                                }
                            }
                        }
                    }

                    // Playhead vertical needle overlay
                    val playheadOffsetPx = (playheadMs * dpPerMs).roundToInt()
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(playheadOffsetPx, 0) }
                            .width(16.dp)
                            .height(194.dp)
                            .pointerInput(totalDurationMs, dpPerMs) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaMs = (dragAmount / dpPerMs).toLong()
                                    onSeek((playheadMs + deltaMs).coerceIn(0L, totalDurationMs))
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderBadge(
    label: String,
    color: Color,
    canAdd: Boolean,
    testTag: String,
    onAdd: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.height(48.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        if (canAdd) {
            Surface(
                color = color.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAdd)
                    .testTag(testTag)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add to $label",
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineRuler(
    totalDurationMs: Long,
    dpPerSecond: Dp,
    dpPerMs: Float,
    onSeek: (Long) -> Unit
) {
    val totalSeconds = (totalDurationMs / 1000L).toInt() + 2
    val rulerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .pointerInput(totalDurationMs, dpPerMs) {
                detectTapGestures { offset ->
                    val targetMs = (offset.x / dpPerMs).toLong().coerceIn(0L, totalDurationMs)
                    onSeek(targetMs)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pxPerSec = dpPerSecond.toPx()
            for (sec in 0..totalSeconds) {
                val x = sec * pxPerSec
                val tickHeight = if (sec % 2 == 0) 12.dp.toPx() else 6.dp.toPx()
                drawLine(
                    color = rulerColor,
                    start = Offset(x, size.height - tickHeight),
                    end = Offset(x, size.height),
                    strokeWidth = 1.5f
                )
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            for (sec in 0..totalSeconds step 2) {
                Text(
                    text = "${sec}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(dpPerSecond * 2)
                        .padding(start = 4.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoClipBlock(
    clip: TimelineClip,
    width: Dp,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent = Color(clip.accentColorHex)
    val icon = when (ClipType.fromRaw(clip.type)) {
        ClipType.VIDEO -> Icons.Rounded.Movie
        ClipType.IMAGE -> Icons.Rounded.Image
        ClipType.SCENE_CARD -> Icons.Rounded.Slideshow
    }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else accent.copy(alpha = 0.65f)

    Surface(
        color = accent.copy(alpha = 0.24f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = clip.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDurationShort(clip.effectiveDurationMs()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                if (clip.speed != 1.0) {
                    Text(
                        text = "${clip.speed}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayTrackBlock(
    overlay: TextOverlay,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color.White else RoseOverlayTrack.copy(alpha = 0.7f)
    Surface(
        color = RoseOverlayTrack.copy(alpha = 0.25f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.TextFields,
                contentDescription = null,
                tint = RoseOverlayTrack,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = overlay.text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AudioTrackBlock(
    track: AudioTrackItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val trackColor = if (AudioSourceType.fromRaw(track.sourceType) == AudioSourceType.VOICEOVER) {
        SkyTrack
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val borderColor = if (isSelected) Color.White else trackColor.copy(alpha = 0.7f)

    Surface(
        color = trackColor.copy(alpha = if (track.muted) 0.08f else 0.22f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (AudioSourceType.fromRaw(track.sourceType) == AudioSourceType.VOICEOVER) {
                    Icons.Rounded.Mic
                } else {
                    Icons.Rounded.GraphicEq
                },
                contentDescription = null,
                tint = trackColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (track.muted) "${track.title} (Muted)" else track.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddTrackSlotButton(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyTrackStrip(
    hint: String,
    onClick: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
