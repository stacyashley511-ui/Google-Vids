package com.example.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.model.AudioTrackItem
import com.example.model.ClipType
import com.example.model.OverlayPosition
import com.example.model.TextOverlay
import com.example.model.TimelineClip
import com.example.model.VideoProject
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun StagePreview(
    project: VideoProject,
    playheadMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeSlice = remember(project.clips, playheadMs) {
        resolveActiveClip(project.clips, playheadMs)
    }
    val activeOverlays = remember(project.overlays, playheadMs) {
        project.overlays.filter { ov ->
            playheadMs >= ov.startMs && playheadMs <= (ov.startMs + ov.durationMs)
        }
    }
    val activeAudio = remember(project.audioTracks, playheadMs) {
        project.audioTracks.firstOrNull { track ->
            !track.muted && playheadMs >= track.startMs && playheadMs <= (track.startMs + track.durationMs)
        }
    }

    val videoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
        }
    }
    val voicePlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            voicePlayer.release()
        }
    }

    // Sync Media3 ExoPlayer with active VIDEO clip
    val currentClip = activeSlice?.clip
    val currentClipUri = if (currentClip != null &&
        ClipType.fromRaw(currentClip.type) == ClipType.VIDEO &&
        currentClip.mediaUri.isNotBlank()
    ) currentClip.mediaUri else null

    LaunchedEffect(currentClipUri) {
        if (currentClipUri != null) {
            try {
                videoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(currentClipUri)))
                videoPlayer.prepare()
            } catch (_: Exception) {
            }
        } else {
            videoPlayer.stop()
            videoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(currentClip?.clipId, isPlaying, activeSlice?.localOffsetMs, currentClip?.speed) {
        if (currentClipUri != null && activeSlice != null && currentClip != null) {
            val targetPosition = currentClip.trimStartMs +
                (activeSlice.localOffsetMs * currentClip.speed).toLong()
            if (abs(videoPlayer.currentPosition - targetPosition) > 450L) {
                videoPlayer.seekTo(targetPosition.coerceAtLeast(0L))
            }
            videoPlayer.playbackParameters = PlaybackParameters(currentClip.speed.toFloat().coerceIn(0.5f, 2.0f))
            videoPlayer.playWhenReady = isPlaying
        } else {
            videoPlayer.playWhenReady = false
        }
    }

    // Sync voiceover/audio URI playback
    val activeAudioUri = activeAudio?.audioUri?.takeIf { it.isNotBlank() }
    LaunchedEffect(activeAudioUri) {
        if (activeAudioUri != null) {
            try {
                voicePlayer.setMediaItem(MediaItem.fromUri(Uri.parse(activeAudioUri)))
                voicePlayer.prepare()
            } catch (_: Exception) {
            }
        } else {
            voicePlayer.stop()
            voicePlayer.clearMediaItems()
        }
    }

    LaunchedEffect(activeAudio?.trackId, isPlaying, activeAudio?.volume) {
        if (activeAudioUri != null && activeAudio != null) {
            val offset = (playheadMs - activeAudio.startMs).coerceAtLeast(0L)
            if (abs(voicePlayer.currentPosition - offset) > 500L) {
                voicePlayer.seekTo(offset)
            }
            voicePlayer.volume = activeAudio.volume.toFloat().coerceIn(0f, 1f)
            voicePlayer.playWhenReady = isPlaying
        } else {
            voicePlayer.playWhenReady = false
        }
    }

    val ratioFloat = when (project.aspectRatio) {
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        else -> 16f / 9f
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val stageMaxHeight = maxHeight.coerceAtMost(240.dp)
        Box(
            modifier = Modifier
                .height(stageMaxHeight)
                .widthIn(max = maxWidth)
                .aspectRatio(ratioFloat, matchHeightConstraintsFirst = ratioFloat < 1.2f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            if (activeSlice == null) {
                EmptyStagePlaceholder()
            } else {
                AnimatedContent(
                    targetState = activeSlice.clip,
                    transitionSpec = {
                        when (targetState.transition) {
                            "Cut" -> fadeIn(initialAlpha = 0.99f) togetherWith fadeOut(targetAlpha = 0.99f)
                            "Slide" -> slideInHorizontally { it / 3 } + fadeIn() togetherWith fadeOut()
                            "Zoom" -> scaleIn(initialScale = 0.92f) + fadeIn() togetherWith fadeOut()
                            else -> fadeIn() togetherWith fadeOut()
                        }
                    },
                    label = "stageClipTransition"
                ) { clip ->
                    StageClipLayer(
                        clip = clip,
                        videoPlayer = videoPlayer,
                        hasPlayableVideo = currentClipUri != null
                    )
                }

                // Timed text overlays
                activeOverlays.forEach { overlay ->
                    StageOverlayBadge(overlay = overlay)
                }
            }

            // Stage HUD badges (top-left ratio, top-right audio indicator, bottom-right timecode)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StageHudPill(text = project.aspectRatio)
                activeSlice?.let {
                    StageHudPill(text = "${it.index + 1}/${project.clips.size}")
                }
            }

            if (project.audioTracks.isNotEmpty()) {
                val anyUnmuted = project.audioTracks.any { !it.muted }
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (anyUnmuted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = "Audio status",
                            tint = if (anyUnmuted) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp)
                        )
                        if (activeAudio != null) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Text(
                    text = "${formatTimecode(playheadMs)} / ${formatTimecode(project.computedDurationMs())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun StageClipLayer(
    clip: TimelineClip,
    videoPlayer: ExoPlayer,
    hasPlayableVideo: Boolean
) {
    val clipType = ClipType.fromRaw(clip.type)
    val accent = Color(clip.accentColorHex)
    val colorFilter = remember(clip.filter) {
        when (clip.filter) {
            "Mono" -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            "Vivid" -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })
            "Cinema" -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.85f) })
            else -> null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            clipType == ClipType.VIDEO && hasPlayableVideo -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = videoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    },
                    update = { view ->
                        if (view.player !== videoPlayer) {
                            view.player = videoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            clipType == ClipType.IMAGE && clip.mediaUri.isNotBlank() -> {
                AsyncImage(
                    model = clip.mediaUri,
                    contentDescription = clip.title,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.82f),
                                    MaterialTheme.colorScheme.surface,
                                    accent.copy(alpha = 0.45f)
                                )
                            )
                        )
                        .padding(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = clip.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        if (clip.subtitle.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = clip.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.StageOverlayBadge(overlay: TextOverlay) {
    val alignment = when (OverlayPosition.fromRaw(overlay.position)) {
        OverlayPosition.TOP -> Alignment.TopCenter
        OverlayPosition.CENTER -> Alignment.Center
        OverlayPosition.BOTTOM -> Alignment.BottomCenter
    }
    val textColor = Color(overlay.colorHex)
    val bgColor = when (overlay.styleKey) {
        "TITLE_CARD" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
        "MINIMAL_LOWER" -> Color.Black.copy(alpha = 0.45f)
        else -> Color.Black.copy(alpha = 0.72f)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .align(alignment)
            .padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        Text(
            text = overlay.text,
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StageHudPill(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyStagePlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add clips to start editing",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
