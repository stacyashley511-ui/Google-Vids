package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VidsRepository
import com.example.model.AudioSourceType
import com.example.model.AudioTrackItem
import com.example.model.ClipType
import com.example.model.SavedShare
import com.example.model.StudioTemplate
import com.example.model.TimelineClip
import com.example.model.UiState
import com.example.model.VideoProject
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class LibraryViewModel(
    private val repository: VidsRepository,
    private val currentUserId: String,
    private val displayName: String
) : ViewModel() {

    val myProjectsState: StateFlow<UiState<List<VideoProject>>> =
        repository.observeMyProjects(currentUserId)
            .map<List<VideoProject>, UiState<List<VideoProject>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing projects", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load your videos"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val savedSharesState: StateFlow<UiState<List<SavedShare>>> =
        repository.observeSavedShares(currentUserId)
            .map<List<SavedShare>, UiState<List<SavedShare>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing saved shares", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load shared videos"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun createBlankProject(
        title: String,
        aspectRatio: String,
        onCreated: (String) -> Unit
    ) {
        val cleanTitle = title.trim().ifBlank { "Untitled Video" }.take(100)
        val project = VideoProject(
            ownerId = currentUserId,
            ownerName = displayName.ifBlank { "Creator" },
            title = cleanTitle,
            description = "Created in Vids Studio",
            aspectRatio = aspectRatio,
            templateKey = "blank",
            shareVisibility = "private",
            shareCode = "",
            clips = listOf(
                TimelineClip(
                    clipId = shortId("clip"),
                    type = ClipType.SCENE_CARD.name,
                    title = cleanTitle,
                    subtitle = "Tap + to add video clips, photos, or captions",
                    durationMs = 4000L,
                    trimStartMs = 0L,
                    trimEndMs = 4000L,
                    speed = 1.0,
                    transition = "Fade",
                    filter = "Natural",
                    accentColorHex = 0xFF7C4DFFL
                )
            ),
            overlays = emptyList(),
            audioTracks = listOf(
                AudioTrackItem(
                    trackId = shortId("aud"),
                    title = "Ambient Studio Pad",
                    sourceType = AudioSourceType.STUDIO_BED.name,
                    durationMs = 4000L,
                    volume = 0.65,
                    muted = false
                )
            )
        )
        launchProjectCreate(project, onCreated)
    }

    fun createFromTemplate(
        template: StudioTemplate,
        onCreated: (String) -> Unit
    ) {
        val project = VideoProject(
            ownerId = currentUserId,
            ownerName = displayName.ifBlank { "Creator" },
            title = template.title,
            description = template.subtitle,
            aspectRatio = template.aspectRatio,
            templateKey = template.key,
            shareVisibility = "private",
            shareCode = "",
            clips = template.clips,
            overlays = template.overlays,
            audioTracks = template.audioTracks
        )
        launchProjectCreate(project, onCreated)
    }

    fun createFromImportedMedia(
        mediaUri: String,
        isVideo: Boolean,
        onCreated: (String) -> Unit
    ) {
        val clipTitle = if (isVideo) "Imported Video" else "Photo Slide"
        val project = VideoProject(
            ownerId = currentUserId,
            ownerName = displayName.ifBlank { "Creator" },
            title = if (isVideo) "New Video Cut" else "Photo Story Cut",
            description = "Imported from device media picker",
            aspectRatio = "16:9",
            templateKey = "import",
            shareVisibility = "private",
            shareCode = "",
            clips = listOf(
                TimelineClip(
                    clipId = shortId("clip"),
                    type = if (isVideo) ClipType.VIDEO.name else ClipType.IMAGE.name,
                    title = clipTitle,
                    subtitle = "Trim or split on the timeline",
                    mediaUri = mediaUri,
                    durationMs = 6000L,
                    trimStartMs = 0L,
                    trimEndMs = 6000L,
                    speed = 1.0,
                    transition = "Fade",
                    filter = "Natural",
                    accentColorHex = 0xFFFF5A5FL
                )
            )
        )
        launchProjectCreate(project, onCreated)
    }

    private fun launchProjectCreate(project: VideoProject, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createProject(project)
            _isBusy.value = false
            result.onSuccess { id ->
                onCreated(id)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to create project"
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId).onSuccess {
                _actionMessage.value = "Video deleted from cloud"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not delete project"
            }
        }
    }

    fun toggleShareLink(project: VideoProject) {
        viewModelScope.launch {
            val enable = project.shareVisibility != "link_anyone"
            val nextVisibility = if (enable) "link_anyone" else "private"
            val nextCode = if (enable) {
                project.shareCode.ifBlank { generateShareCode() }
            } else {
                project.shareCode
            }
            repository.updateShareSettings(project.id, nextVisibility, nextCode).onSuccess {
                _actionMessage.value = if (enable) {
                    "Share link enabled ($nextCode)"
                } else {
                    "Video set to private"
                }
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to update sharing"
            }
        }
    }

    fun openByShareCode(code: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.findAndSaveSharedProject(code)
            _isBusy.value = false
            result.onSuccess { project ->
                _actionMessage.value = "Opened shared video: ${project.title}"
                onOpened(project.id)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Share code not found"
            }
        }
    }

    fun duplicateProject(project: VideoProject, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.duplicateToMyVideos(project)
            _isBusy.value = false
            result.onSuccess { newId ->
                _actionMessage.value = "Saved editable copy to My Videos"
                onCreated(newId)
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not duplicate project"
            }
        }
    }

    fun removeSavedShare(shareId: String) {
        viewModelScope.launch {
            repository.removeSavedShare(shareId).onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not remove shared item"
            }
        }
    }

    private fun shortId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().take(8)}"

    private fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "VIDS" + (1..4).map { chars.random() }.joinToString("")
    }

    private companion object {
        const val TAG = "LibraryVM"
    }
}
