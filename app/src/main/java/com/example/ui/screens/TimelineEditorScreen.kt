package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AudioSourceType
import com.example.model.UiState
import com.example.model.VideoProject
import com.example.ui.components.CloudSyncPill
import com.example.ui.components.ShareProjectSheet
import com.example.ui.components.StagePreview
import com.example.ui.components.TimelineCanvas
import com.example.ui.components.TrackInspectorPanel
import com.example.ui.components.formatDurationShort
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineEditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenRemixedProject: (String) -> Unit
) {
    val context = LocalContext.current
    val projectState by viewModel.projectState.collectAsStateWithLifecycle()
    val workingProject by viewModel.workingProject.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val timelineZoom by viewModel.timelineZoom.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val editorMessage by viewModel.editorMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var showProjectMetaDialog by remember { mutableStateOf(false) }
    var showAddClipSheet by remember { mutableStateOf(false) }
    var showAddSceneDialog by remember { mutableStateOf(false) }
    var showAddOverlayDialog by remember { mutableStateOf(false) }
    var showAddAudioSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    BackHandler {
        viewModel.pausePlayback()
        onBack()
    }

    LaunchedEffect(editorMessage) {
        editorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val mime = context.contentResolver.getType(uri).orEmpty()
            val isVideo = mime.startsWith("video")
            viewModel.addMediaClip(
                title = if (isVideo) "Video Clip" else "Photo Slide",
                uri = uri.toString(),
                isVideo = isVideo
            )
        }
    }

    val videoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.addMediaClip(
                title = "Camera Recording",
                uri = uri.toString(),
                isVideo = true
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempVideoUri(context)
            pendingCameraUri = uri
            videoCaptureLauncher.launch(uri)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.pausePlayback()
                            onBack()
                        },
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to library")
                    }
                },
                title = {
                    val titleText = workingProject?.title ?: "Loading..."
                    Column(
                        modifier = Modifier.clickable(enabled = viewModel.isEditable()) {
                            showProjectMetaDialog = true
                        }
                    ) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        workingProject?.let { proj ->
                            Text(
                                text = "${proj.aspectRatio} • ${formatDurationShort(proj.computedDurationMs())}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    CloudSyncPill(status = syncStatus, modifier = Modifier.padding(end = 4.dp))
                    if (viewModel.isEditable()) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = canUndo,
                            modifier = Modifier.testTag("editor_undo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
                        }
                    } else if (workingProject != null) {
                        IconButton(
                            onClick = { viewModel.duplicateSharedToMyVideos(onOpenRemixedProject) },
                            modifier = Modifier.testTag("editor_remix_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Remix to My Videos")
                        }
                    }
                    IconButton(
                        onClick = { showShareSheet = true },
                        enabled = workingProject != null,
                        modifier = Modifier.testTag("editor_share_button")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share video")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val project = workingProject
        when {
            projectState is UiState.Loading && project == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            projectState is UiState.Error && project == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (projectState as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            project == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Video project not found.")
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Live Video / Storyboard Stage
                    StagePreview(
                        project = project,
                        playheadMs = playheadMs,
                        isPlaying = isPlaying
                    )

                    // 2. Transport & Quick Timeline Controls
                    TransportBar(
                        isPlaying = isPlaying,
                        isEditable = viewModel.isEditable(),
                        onJumpStart = { viewModel.seekTo(0L) },
                        onTogglePlay = { viewModel.togglePlayback() },
                        onSplitClip = { viewModel.splitClipAtPlayhead() },
                        onZoomOut = { viewModel.adjustZoom(-0.25f) },
                        onZoomIn = { viewModel.adjustZoom(0.25f) }
                    )

                    // 3. Multi-Track Timeline Canvas (V1 / T1 / A1)
                    TimelineCanvas(
                        project = project,
                        playheadMs = playheadMs,
                        zoom = timelineZoom,
                        selectedItem = selectedItem,
                        isEditable = viewModel.isEditable(),
                        onSeek = viewModel::seekTo,
                        onSelectClip = viewModel::selectClip,
                        onSelectOverlay = viewModel::selectOverlay,
                        onSelectAudio = viewModel::selectAudio,
                        onAddClipClick = { showAddClipSheet = true },
                        onAddOverlayClick = { showAddOverlayDialog = true },
                        onAddAudioClick = { showAddAudioSheet = true }
                    )

                    // 4. Context-sensitive Inspector
                    TrackInspectorPanel(
                        project = project,
                        selectedItem = selectedItem,
                        isEditable = viewModel.isEditable(),
                        onUpdateClip = viewModel::updateClip,
                        onMoveClip = viewModel::moveClip,
                        onDuplicateClip = viewModel::duplicateClip,
                        onDeleteClip = viewModel::deleteClip,
                        onUpdateOverlay = viewModel::updateOverlay,
                        onDeleteOverlay = viewModel::deleteOverlay,
                        onUpdateAudio = viewModel::updateAudioTrack,
                        onDeleteAudio = viewModel::deleteAudioTrack
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showProjectMetaDialog && workingProject != null) {
        EditProjectMetaDialog(
            project = workingProject!!,
            onDismiss = { showProjectMetaDialog = false },
            onSave = { title, ratio ->
                showProjectMetaDialog = false
                viewModel.updateTitleAndRatio(title, ratio)
            }
        )
    }

    if (showAddClipSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddClipSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add to Video Track (V1)", style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = {
                        showAddClipSheet = false
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PermMedia, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pick Video or Photo (Zero-Permission)")
                }
                OutlinedButton(
                    onClick = {
                        showAddClipSheet = false
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val uri = createTempVideoUri(context)
                            pendingCameraUri = uri
                            videoCaptureLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Record Video Clip")
                }
                OutlinedButton(
                    onClick = {
                        showAddClipSheet = false
                        showAddSceneDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Slideshow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Title Scene Card")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddSceneDialog) {
        AddSceneCardDialog(
            onDismiss = { showAddSceneDialog = false },
            onAdd = { title, subtitle, colorHex ->
                showAddSceneDialog = false
                viewModel.addSceneCard(title, subtitle, colorHex)
            }
        )
    }

    if (showAddOverlayDialog) {
        AddTextOverlayDialog(
            onDismiss = { showAddOverlayDialog = false },
            onAdd = { text ->
                showAddOverlayDialog = false
                viewModel.addTextOverlayAtPlayhead(text)
            }
        )
    }

    if (showAddAudioSheet) {
        AddAudioOrVoiceoverSheet(
            onDismiss = { showAddAudioSheet = false },
            onAddBed = { title, durationMs ->
                showAddAudioSheet = false
                viewModel.addAudioTrack(
                    title = title,
                    sourceType = AudioSourceType.STUDIO_BED,
                    audioUri = "",
                    durationMs = durationMs
                )
            },
            onAddVoiceover = { uri, durationMs ->
                showAddAudioSheet = false
                viewModel.addAudioTrack(
                    title = "Voiceover",
                    sourceType = AudioSourceType.VOICEOVER,
                    audioUri = uri,
                    durationMs = durationMs
                )
            }
        )
    }

    if (showShareSheet && workingProject != null) {
        ShareProjectSheet(
            project = workingProject!!,
            canManageLink = viewModel.isEditable(),
            onToggleLinkSharing = { viewModel.toggleShareVisibility() },
            onDismiss = { showShareSheet = false }
        )
    }
}

@Composable
private fun TransportBar(
    isPlaying: Boolean,
    isEditable: Boolean,
    onJumpStart: () -> Unit,
    onTogglePlay: () -> Unit,
    onSplitClip: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onJumpStart) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Jump to start")
            }
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePlay)
                    .testTag("play_pause_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (isEditable) {
                OutlinedButton(
                    onClick = onSplitClip,
                    modifier = Modifier.testTag("split_clip_button")
                ) {
                    Icon(
                        Icons.Rounded.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Split")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out timeline")
            }
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in timeline")
            }
        }
    }
}

@Composable
private fun EditProjectMetaDialog(
    project: VideoProject,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(project.title) }
    var aspectRatio by remember { mutableStateOf(project.aspectRatio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("16:9", "9:16", "1:1").forEach { ratio ->
                        FilterChip(
                            selected = aspectRatio == ratio,
                            onClick = { aspectRatio = ratio },
                            label = { Text(ratio) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, aspectRatio) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddSceneCardDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    val swatches = listOf(0xFF7C4DFFL, 0xFFFF5A5FL, 0xFF00D4AAL, 0xFFFFB74DL, 0xFF4FC3F7L)
    var selectedHex by remember { mutableLongStateOf(swatches.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Scene Card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Headline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it.take(120) },
                    label = { Text("Subtitle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    swatches.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { selectedHex = hex }
                        ) {
                            if (selectedHex == hex) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title, subtitle, selectedHex) }) { Text("Add Scene") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddTextOverlayDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Text Overlay") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                label = { Text("Caption or Title") },
                placeholder = { Text("Key takeaway...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onAdd(text) }) { Text("Place at Playhead") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAudioOrVoiceoverSheet(
    onDismiss: () -> Unit,
    onAddBed: (String, Long) -> Unit,
    onAddVoiceover: (String, Long) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }

    fun stopRecordingAndSave() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
        val file = outputFile
        if (file != null && file.exists() && elapsedMs >= 500L) {
            onAddVoiceover(Uri.fromFile(file).toString(), elapsedMs)
        } else {
            statusError = "Hold recording for at least 1 second"
        }
    }

    fun startRecording() {
        try {
            val dir = File(context.filesDir, "studio_media").apply { mkdirs() }
            val target = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(target.absolutePath)
            mr.prepare()
            mr.start()
            outputFile = target
            recorder = mr
            elapsedMs = 0L
            statusError = null
            isRecording = true
        } catch (e: Exception) {
            statusError = e.localizedMessage ?: "Microphone unavailable on this device"
            isRecording = false
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else statusError = "Microphone permission denied"
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(100L)
            elapsedMs += 100L
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isRecording) stopRecordingAndSave() else onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Audio Track (A1)", style = MaterialTheme.typography.headlineSmall)

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isRecording) "Recording Voiceover (${formatDurationShort(elapsedMs)})" else "Record Voiceover",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (isRecording) {
                                stopRecordingAndSave()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) startRecording() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isRecording) "Stop & Add Voiceover" else "Start Recording")
                    }
                    statusError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text(
                text = "Studio Music Beds",
                style = MaterialTheme.typography.titleMedium
            )
            listOf(
                "Synth Horizon" to 12000L,
                "Coastal Lo-Fi" to 10000L,
                "Clean Acoustic" to 12000L,
                "Cinematic Pulse" to 15000L
            ).forEach { (name, dur) ->
                OutlinedButton(
                    onClick = { onAddBed(name, dur) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("$name (${formatDurationShort(dur)})")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun createTempVideoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "studio_cache").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.mp4")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
