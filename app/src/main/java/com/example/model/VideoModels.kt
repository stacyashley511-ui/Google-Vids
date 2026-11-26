package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ClipType {
    VIDEO,
    IMAGE,
    SCENE_CARD;

    companion object {
        fun fromRaw(raw: String): ClipType =
            entries.firstOrNull { it.name == raw } ?: SCENE_CARD
    }
}

enum class OverlayPosition {
    TOP,
    CENTER,
    BOTTOM;

    companion object {
        fun fromRaw(raw: String): OverlayPosition =
            entries.firstOrNull { it.name == raw } ?: BOTTOM
    }
}

enum class AudioSourceType {
    STUDIO_BED,
    VOICEOVER,
    SFX;

    companion object {
        fun fromRaw(raw: String): AudioSourceType =
            entries.firstOrNull { it.name == raw } ?: STUDIO_BED
    }
}

@Keep
@IgnoreExtraProperties
data class TimelineClip(
    val clipId: String = "",
    val type: String = ClipType.SCENE_CARD.name,
    val title: String = "Scene",
    val subtitle: String = "",
    val mediaUri: String = "",
    val durationMs: Long = 4000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 4000L,
    val speed: Double = 1.0,
    val transition: String = "Fade",
    val filter: String = "Natural",
    val accentColorHex: Long = 0xFF7C4DFFL
) {
    fun effectiveDurationMs(): Long {
        val span = (trimEndMs - trimStartMs).coerceAtLeast(500L)
        val safeSpeed = speed.coerceIn(0.5, 2.0)
        return (span / safeSpeed).toLong().coerceAtLeast(500L)
    }

    fun toMap(): Map<String, Any> = mapOf(
        "clipId" to clipId,
        "type" to type,
        "title" to title,
        "subtitle" to subtitle,
        "mediaUri" to mediaUri,
        "durationMs" to durationMs,
        "trimStartMs" to trimStartMs,
        "trimEndMs" to trimEndMs,
        "speed" to speed,
        "transition" to transition,
        "filter" to filter,
        "accentColorHex" to accentColorHex
    )
}

@Keep
@IgnoreExtraProperties
data class TextOverlay(
    val overlayId: String = "",
    val text: String = "Title",
    val startMs: Long = 0L,
    val durationMs: Long = 3000L,
    val position: String = OverlayPosition.BOTTOM.name,
    val styleKey: String = "BOLD_PILL",
    val colorHex: Long = 0xFFFFFFFFL
) {
    fun toMap(): Map<String, Any> = mapOf(
        "overlayId" to overlayId,
        "text" to text,
        "startMs" to startMs,
        "durationMs" to durationMs,
        "position" to position,
        "styleKey" to styleKey,
        "colorHex" to colorHex
    )
}

@Keep
@IgnoreExtraProperties
data class AudioTrackItem(
    val trackId: String = "",
    val title: String = "Studio Pulse",
    val sourceType: String = AudioSourceType.STUDIO_BED.name,
    val audioUri: String = "",
    val startMs: Long = 0L,
    val durationMs: Long = 8000L,
    val volume: Double = 0.8,
    val muted: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "trackId" to trackId,
        "title" to title,
        "sourceType" to sourceType,
        "audioUri" to audioUri,
        "startMs" to startMs,
        "durationMs" to durationMs,
        "volume" to volume,
        "muted" to muted
    )
}

@Keep
@IgnoreExtraProperties
data class VideoProject(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerName: String = "Creator",
    val title: String = "Untitled Video",
    val description: String = "",
    val aspectRatio: String = "16:9",
    val templateKey: String = "blank",
    val shareVisibility: String = "private",
    val shareCode: String = "",
    val totalDurationMs: Long = 0L,
    val clips: List<TimelineClip> = emptyList(),
    val overlays: List<TextOverlay> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun computedDurationMs(): Long =
        clips.sumOf { it.effectiveDurationMs() }.coerceAtLeast(0L)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "ownerId" to ownerId,
        "ownerName" to ownerName.take(80).ifBlank { "Creator" },
        "title" to title.take(100).ifBlank { "Untitled Video" },
        "description" to description.take(400),
        "aspectRatio" to if (aspectRatio in listOf("16:9", "9:16", "1:1")) aspectRatio else "16:9",
        "templateKey" to templateKey.take(40),
        "shareVisibility" to if (shareVisibility in listOf("private", "link_anyone")) shareVisibility else "private",
        "shareCode" to shareCode.take(16),
        "totalDurationMs" to computedDurationMs().coerceIn(0L, 3600000L),
        "clips" to clips.take(40).map { it.toMap() },
        "overlays" to overlays.take(30).map { it.toMap() },
        "audioTracks" to audioTracks.take(10).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toTimelineUpdateMap(): Map<String, Any> = mapOf(
        "ownerName" to ownerName.take(80).ifBlank { "Creator" },
        "title" to title.take(100).ifBlank { "Untitled Video" },
        "description" to description.take(400),
        "aspectRatio" to if (aspectRatio in listOf("16:9", "9:16", "1:1")) aspectRatio else "16:9",
        "templateKey" to templateKey.take(40),
        "totalDurationMs" to computedDurationMs().coerceIn(0L, 3600000L),
        "clips" to clips.take(40).map { it.toMap() },
        "overlays" to overlays.take(30).map { it.toMap() },
        "audioTracks" to audioTracks.take(10).map { it.toMap() },
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SavedShare(
    @DocumentId val id: String = "",
    val projectId: String = "",
    val shareCode: String = "",
    val title: String = "",
    val ownerName: String = "",
    val aspectRatio: String = "16:9",
    val totalDurationMs: Long = 0L,
    @ServerTimestamp val savedAt: Timestamp? = null
)

data class StudioTemplate(
    val key: String,
    val title: String,
    val subtitle: String,
    val aspectRatio: String,
    val accentHex: Long,
    val clips: List<TimelineClip>,
    val overlays: List<TextOverlay>,
    val audioTracks: List<AudioTrackItem>
)

object StudioTemplates {
    val all = listOf(
        StudioTemplate(
            key = "launch",
            title = "Product Launch Reel",
            subtitle = "3-scene keynote opener with kinetic captions & synth bed",
            aspectRatio = "16:9",
            accentHex = 0xFF7C4DFFL,
            clips = listOf(
                TimelineClip("c1", ClipType.SCENE_CARD.name, "The Next Chapter", "Introducing our Q4 release", "", 4000L, 0L, 4000L, 1.0, "Fade", "Vivid", 0xFF7C4DFFL),
                TimelineClip("c2", ClipType.SCENE_CARD.name, "Built for Speed", "3x faster cloud workflow", "", 4500L, 0L, 4500L, 1.0, "Slide", "Cinema", 0xFFFF5A5FL),
                TimelineClip("c3", ClipType.SCENE_CARD.name, "Start Creating Today", "Available on all devices", "", 3500L, 0L, 3500L, 1.0, "Zoom", "Vivid", 0xFF00D4AAL)
            ),
            overlays = listOf(
                TextOverlay("o1", "KEYNOTE 2026", 400L, 3000L, OverlayPosition.TOP.name, "BOLD_PILL", 0xFFFFFFFFL),
                TextOverlay("o2", "Tap to explore features", 4500L, 3500L, OverlayPosition.BOTTOM.name, "MINIMAL_LOWER", 0xFFFFFFFFL)
            ),
            audioTracks = listOf(
                AudioTrackItem("a1", "Synth Horizon", AudioSourceType.STUDIO_BED.name, "", 0L, 12000L, 0.75, false)
            )
        ),
        StudioTemplate(
            key = "story",
            title = "Vertical Story Cut",
            subtitle = "9:16 mobile reel with upbeat pacing and bold captions",
            aspectRatio = "9:16",
            accentHex = 0xFFFF5A5FL,
            clips = listOf(
                TimelineClip("s1", ClipType.SCENE_CARD.name, "Golden Hour", "Behind the scenes", "", 3500L, 0L, 3500L, 1.0, "Fade", "Cinema", 0xFFFF5A5FL),
                TimelineClip("s2", ClipType.SCENE_CARD.name, "On Location", "Capturing every frame", "", 4000L, 0L, 4000L, 1.0, "Slide", "Vivid", 0xFFFFB74DL),
                TimelineClip("s3", ClipType.SCENE_CARD.name, "Follow for Part 2", "Shot & edited in Vids Studio", "", 3000L, 0L, 3000L, 1.0, "Fade", "Natural", 0xFF4FC3F7L)
            ),
            overlays = listOf(
                TextOverlay("so1", "DAY IN THE STUDIO", 300L, 2800L, OverlayPosition.CENTER.name, "TITLE_CARD", 0xFFFFFFFFL)
            ),
            audioTracks = listOf(
                AudioTrackItem("sa1", "Coastal Lo-Fi", AudioSourceType.STUDIO_BED.name, "", 0L, 10500L, 0.8, false)
            )
        ),
        StudioTemplate(
            key = "tutorial",
            title = "Quick How-To Guide",
            subtitle = "Square 1:1 walkthrough with clear step-by-step callouts",
            aspectRatio = "1:1",
            accentHex = 0xFF00D4AAL,
            clips = listOf(
                TimelineClip("t1", ClipType.SCENE_CARD.name, "Step 1: Import Clips", "Pick videos or record live", "", 4000L, 0L, 4000L, 1.0, "Cut", "Natural", 0xFF00D4AAL),
                TimelineClip("t2", ClipType.SCENE_CARD.name, "Step 2: Trim & Split", "Dial in frame-accurate timing", "", 4000L, 0L, 4000L, 1.0, "Slide", "Vivid", 0xFF8B6BFFL),
                TimelineClip("t3", ClipType.SCENE_CARD.name, "Step 3: Share Link", "Instant cloud sync across teams", "", 4000L, 0L, 4000L, 1.0, "Fade", "Natural", 0xFFFFB74DL)
            ),
            overlays = listOf(
                TextOverlay("to1", "PRO TIP", 500L, 3000L, OverlayPosition.TOP.name, "BOLD_PILL", 0xFFFFFFFFL)
            ),
            audioTracks = listOf(
                AudioTrackItem("ta1", "Clean Acoustic", AudioSourceType.STUDIO_BED.name, "", 0L, 12000L, 0.65, false)
            )
        )
    )
}
