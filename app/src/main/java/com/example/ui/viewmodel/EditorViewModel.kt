package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VidsRepository
import com.example.model.AudioSourceType
import com.example.model.AudioTrackItem
import com.example.model.ClipType
import com.example.model.OverlayPosition
import com.example.model.TextOverlay
import com.example.model.TimelineClip
import com.example.model.UiState
import com.example.model.VideoProject
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class CloudSyncStatus {
    SYNCED,
    SAVING,
    ERROR,
    VIEW_ONLY
}

sealed interface SelectedTrackItem {
    data class Clip(val clipId: String) : SelectedTrackItem
    data class Overlay(val overlayId: String) : SelectedTrackItem
    data class Audio(val trackId: String) : SelectedTrackItem
}

class EditorViewModel(
    private val repository: VidsRepository,
    private val projectId: String,
    private val currentUserId: String
) : ViewModel() {

    private val _projectState = MutableStateFlow<UiState<VideoProject?>>(UiState.Loading)
    val projectState: StateFlow<UiState<VideoProject?>> = _projectState.asStateFlow()

    private val _workingProject = MutableStateFlow<VideoProject?>(null)
    val workingProject: StateFlow<VideoProject?> = _workingProject.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _timelineZoom = MutableStateFlow(1.0f)
    val timelineZoom: StateFlow<Float> = _timelineZoom.asStateFlow()

    private val _selectedItem = MutableStateFlow<SelectedTrackItem?>(null)
    val selectedItem: StateFlow<SelectedTrackItem?> = _selectedItem.asStateFlow()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus.SYNCED)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val _editorMessage = MutableStateFlow<String?>(null)
    val editorMessage: StateFlow<String?> = _editorMessage.asStateFlow()

    private val undoStack = ArrayDeque<VideoProject>()
    val canUndo: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private var syncJob: Job? = null
    private var playbackJob: Job? = null
    private var hasPendingLocalEdits = false

    init {
        observeRemoteProject()
    }

    private fun observeRemoteProject() {
        viewModelScope.launch {
            repository.observeProject(projectId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Failed observing project $projectId", rootError)
                    _projectState.value = UiState.Error(rootError.message ?: "Failed to load project")
                }
                .collect { remote ->
                    _projectState.value = UiState.Success(remote)
                    if (remote != null && !hasPendingLocalEdits) {
                        _workingProject.value = remote
                        if (remote.ownerId != currentUserId) {
                            _syncStatus.value = CloudSyncStatus.VIEW_ONLY
                        } else if (_syncStatus.value != CloudSyncStatus.SAVING) {
                            _syncStatus.value = CloudSyncStatus.SYNCED
                        }
                        if (_selectedItem.value == null && remote.clips.isNotEmpty()) {
                            _selectedItem.value = SelectedTrackItem.Clip(remote.clips.first().clipId)
                        }
                    }
                }
        }
    }

    fun isEditable(): Boolean = _workingProject.value?.ownerId == currentUserId

    fun clearMessage() {
        _editorMessage.value = null
    }

    fun selectClip(clipId: String) {
        _selectedItem.value = SelectedTrackItem.Clip(clipId)
    }

    fun selectOverlay(overlayId: String) {
        _selectedItem.value = SelectedTrackItem.Overlay(overlayId)
    }

    fun selectAudio(trackId: String) {
        _selectedItem.value = SelectedTrackItem.Audio(trackId)
    }

    fun clearSelection() {
        _selectedItem.value = null
    }

    fun adjustZoom(delta: Float) {
        _timelineZoom.value = (_timelineZoom.value + delta).coerceIn(0.6f, 2.5f)
    }

    fun seekTo(ms: Long) {
        val total = _workingProject.value?.computedDurationMs() ?: 0L
        _playheadMs.value = ms.coerceIn(0L, total.coerceAtLeast(0L))
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun startPlayback() {
        val total = _workingProject.value?.computedDurationMs() ?: 0L
        if (total <= 0L) return
        if (_playheadMs.value >= total) {
            _playheadMs.value = 0L
        }
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive && _isPlaying.value) {
                delay(33L)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTick).coerceAtLeast(16L)
                lastTick = now
                val currentTotal = _workingProject.value?.computedDurationMs() ?: 0L
                val nextPos = _playheadMs.value + elapsed
                if (nextPos >= currentTotal) {
                    _playheadMs.value = 0L
                } else {
                    _playheadMs.value = nextPos
                }
            }
        }
    }

    fun undo() {
        if (!isEditable() || undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        canUndo.value = undoStack.isNotEmpty()
        _workingProject.value = previous
        clampPlayhead()
        scheduleCloudSync(previous)
    }

    private fun applyProjectEdit(transform: (VideoProject) -> VideoProject) {
        val current = _workingProject.value ?: return
        if (current.ownerId != currentUserId) {
            _editorMessage.value = "View-only shared video. Duplicate to My Videos to edit."
            return
        }
        undoStack.addLast(current)
        if (undoStack.size > 20) undoStack.removeFirst()
        canUndo.value = true

        val updated = transform(current).let { proj ->
            proj.copy(totalDurationMs = proj.computedDurationMs())
        }
        _workingProject.value = updated
        clampPlayhead()
        scheduleCloudSync(updated)
    }

    private fun clampPlayhead() {
        val total = _workingProject.value?.computedDurationMs() ?: 0L
        if (_playheadMs.value > total) {
            _playheadMs.value = total
        }
    }

    private fun scheduleCloudSync(project: VideoProject) {
        hasPendingLocalEdits = true
        _syncStatus.value = CloudSyncStatus.SAVING
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(450L)
            val result = repository.updateTimeline(project)
            hasPendingLocalEdits = false
            result.onSuccess {
                _syncStatus.value = CloudSyncStatus.SYNCED
            }.onFailure { err ->
                _syncStatus.value = CloudSyncStatus.ERROR
                _editorMessage.value = err.localizedMessage ?: "Cloud sync failed"
            }
        }
    }

    fun updateTitleAndRatio(title: String, aspectRatio: String) {
        applyProjectEdit { proj ->
            proj.copy(
                title = title.trim().ifBlank { "Untitled Video" }.take(100),
                aspectRatio = aspectRatio
            )
        }
    }

    fun toggleShareVisibility(onResult: (VideoProject) -> Unit = {}) {
        val current = _workingProject.value ?: return
        if (current.ownerId != currentUserId) return
        val enable = current.shareVisibility != "link_anyone"
        val nextVisibility = if (enable) "link_anyone" else "private"
        val nextCode = if (enable) current.shareCode.ifBlank { generateShareCode() } else current.shareCode
        val updated = current.copy(shareVisibility = nextVisibility, shareCode = nextCode)
        _workingProject.value = updated

        viewModelScope.launch {
            _syncStatus.value = CloudSyncStatus.SAVING
            repository.updateShareSettings(current.id, nextVisibility, nextCode)
                .onSuccess {
                    _syncStatus.value = CloudSyncStatus.SYNCED
                    onResult(updated)
                }
                .onFailure { err ->
                    _syncStatus.value = CloudSyncStatus.ERROR
                    _editorMessage.value = err.localizedMessage ?: "Could not update share settings"
                }
        }
    }

    fun addMediaClip(title: String, uri: String, isVideo: Boolean) {
        val newClipId = shortId("clip")
        applyProjectEdit { proj ->
            if (proj.clips.size >= 40) {
                _editorMessage.value = "Timeline limit of 40 clips reached"
                return@applyProjectEdit proj
            }
            val newClip = TimelineClip(
                clipId = newClipId,
                type = if (isVideo) ClipType.VIDEO.name else ClipType.IMAGE.name,
                title = title.take(60),
                subtitle = if (isVideo) "Video Clip" else "Photo Slide",
                mediaUri = uri,
                durationMs = 5000L,
                trimStartMs = 0L,
                trimEndMs = 5000L,
                speed = 1.0,
                transition = "Fade",
                filter = "Natural",
                accentColorHex = if (isVideo) 0xFFFF5A5FL else 0xFF4FC3F7L
            )
            proj.copy(clips = proj.clips + newClip)
        }
        _selectedItem.value = SelectedTrackItem.Clip(newClipId)
    }

    fun addSceneCard(title: String, subtitle: String, accentHex: Long) {
        val newClipId = shortId("clip")
        applyProjectEdit { proj ->
            if (proj.clips.size >= 40) return@applyProjectEdit proj
            val newClip = TimelineClip(
                clipId = newClipId,
                type = ClipType.SCENE_CARD.name,
                title = title.ifBlank { "New Scene" }.take(60),
                subtitle = subtitle.take(120),
                mediaUri = "",
                durationMs = 4000L,
                trimStartMs = 0L,
                trimEndMs = 4000L,
                speed = 1.0,
                transition = "Fade",
                filter = "Vivid",
                accentColorHex = accentHex
            )
            proj.copy(clips = proj.clips + newClip)
        }
        _selectedItem.value = SelectedTrackItem.Clip(newClipId)
    }

    fun updateClip(clipId: String, updater: (TimelineClip) -> TimelineClip) {
        applyProjectEdit { proj ->
            proj.copy(
                clips = proj.clips.map { clip ->
                    if (clip.clipId == clipId) updater(clip) else clip
                }
            )
        }
    }

    fun moveClip(clipId: String, direction: Int) {
        applyProjectEdit { proj ->
            val index = proj.clips.indexOfFirst { it.clipId == clipId }
            val target = index + direction
            if (index < 0 || target !in proj.clips.indices) return@applyProjectEdit proj
            val mutable = proj.clips.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(target, item)
            proj.copy(clips = mutable)
        }
    }

    fun duplicateClip(clipId: String) {
        applyProjectEdit { proj ->
            if (proj.clips.size >= 40) return@applyProjectEdit proj
            val index = proj.clips.indexOfFirst { it.clipId == clipId }
            if (index < 0) return@applyProjectEdit proj
            val copy = proj.clips[index].copy(clipId = shortId("clip"))
            val mutable = proj.clips.toMutableList()
            mutable.add(index + 1, copy)
            proj.copy(clips = mutable)
        }
    }

    fun deleteClip(clipId: String) {
        applyProjectEdit { proj ->
            if (proj.clips.size <= 1) {
                _editorMessage.value = "Keep at least 1 clip in the timeline"
                return@applyProjectEdit proj
            }
            proj.copy(clips = proj.clips.filterNot { it.clipId == clipId })
        }
        _selectedItem.value = _workingProject.value?.clips?.firstOrNull()?.let { SelectedTrackItem.Clip(it.clipId) }
    }

    fun splitClipAtPlayhead() {
        val proj = _workingProject.value ?: return
        if (proj.clips.size >= 40) {
            _editorMessage.value = "Timeline clip limit reached"
            return
        }
        val playhead = _playheadMs.value
        var cursor = 0L
        var targetIndex = -1
        var offsetWithinClip = 0L

        for (i in proj.clips.indices) {
            val dur = proj.clips[i].effectiveDurationMs()
            if (playhead > cursor + 400L && playhead < cursor + dur - 400L) {
                targetIndex = i
                offsetWithinClip = playhead - cursor
                break
            }
            cursor += dur
        }

        if (targetIndex < 0) {
            _editorMessage.value = "Move playhead inside a clip (at least 0.4s from edges) to split"
            return
        }

        val targetClip = proj.clips[targetIndex]
        val sourceSplitOffset = (offsetWithinClip * targetClip.speed).toLong()
        val splitPoint = (targetClip.trimStartMs + sourceSplitOffset)
            .coerceIn(targetClip.trimStartMs + 500L, targetClip.trimEndMs - 500L)

        val firstHalf = targetClip.copy(trimEndMs = splitPoint)
        val secondHalf = targetClip.copy(
            clipId = shortId("clip"),
            title = "${targetClip.title} (Part 2)",
            trimStartMs = splitPoint
        )

        applyProjectEdit { p ->
            val mutable = p.clips.toMutableList()
            mutable[targetIndex] = firstHalf
            mutable.add(targetIndex + 1, secondHalf)
            p.copy(clips = mutable)
        }
        _selectedItem.value = SelectedTrackItem.Clip(secondHalf.clipId)
        _editorMessage.value = "Clip split at playhead"
    }

    fun addTextOverlayAtPlayhead(text: String) {
        val newId = shortId("ov")
        val start = _playheadMs.value
        applyProjectEdit { proj ->
            if (proj.overlays.size >= 30) return@applyProjectEdit proj
            val overlay = TextOverlay(
                overlayId = newId,
                text = text.ifBlank { "Caption" }.take(80),
                startMs = start,
                durationMs = 3000L,
                position = OverlayPosition.BOTTOM.name,
                styleKey = "BOLD_PILL",
                colorHex = 0xFFFFFFFFL
            )
            proj.copy(overlays = proj.overlays + overlay)
        }
        _selectedItem.value = SelectedTrackItem.Overlay(newId)
    }

    fun updateOverlay(overlayId: String, updater: (TextOverlay) -> TextOverlay) {
        applyProjectEdit { proj ->
            proj.copy(
                overlays = proj.overlays.map { ov ->
                    if (ov.overlayId == overlayId) updater(ov) else ov
                }
            )
        }
    }

    fun deleteOverlay(overlayId: String) {
        applyProjectEdit { proj ->
            proj.copy(overlays = proj.overlays.filterNot { it.overlayId == overlayId })
        }
        _selectedItem.value = null
    }

    fun addAudioTrack(
        title: String,
        sourceType: AudioSourceType,
        audioUri: String,
        durationMs: Long
    ) {
        val newId = shortId("aud")
        val start = if (sourceType == AudioSourceType.VOICEOVER) _playheadMs.value else 0L
        applyProjectEdit { proj ->
            if (proj.audioTracks.size >= 10) {
                _editorMessage.value = "Audio track limit of 10 reached"
                return@applyProjectEdit proj
            }
            val item = AudioTrackItem(
                trackId = newId,
                title = title.take(60),
                sourceType = sourceType.name,
                audioUri = audioUri,
                startMs = start,
                durationMs = durationMs.coerceAtLeast(1000L),
                volume = 0.85,
                muted = false
            )
            proj.copy(audioTracks = proj.audioTracks + item)
        }
        _selectedItem.value = SelectedTrackItem.Audio(newId)
    }

    fun updateAudioTrack(trackId: String, updater: (AudioTrackItem) -> AudioTrackItem) {
        applyProjectEdit { proj ->
            proj.copy(
                audioTracks = proj.audioTracks.map { item ->
                    if (item.trackId == trackId) updater(item) else item
                }
            )
        }
    }

    fun deleteAudioTrack(trackId: String) {
        applyProjectEdit { proj ->
            proj.copy(audioTracks = proj.audioTracks.filterNot { it.trackId == trackId })
        }
        _selectedItem.value = null
    }

    fun duplicateSharedToMyVideos(onDuplicated: (String) -> Unit) {
        val current = _workingProject.value ?: return
        viewModelScope.launch {
            repository.duplicateToMyVideos(current)
                .onSuccess { newId ->
                    _editorMessage.value = "Copied to My Videos for editing"
                    onDuplicated(newId)
                }
                .onFailure { err ->
                    _editorMessage.value = err.localizedMessage ?: "Duplicate failed"
                }
        }
    }

    private fun shortId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().take(8)}"

    private fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "VIDS" + (1..4).map { chars.random() }.joinToString("")
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
    }

    private companion object {
        const val TAG = "EditorVM"
    }
}
