package com.example.data

import android.content.Context
import com.example.R
import com.example.model.SavedShare
import com.example.model.VideoProject
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VidsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeMyProjects(userId: String): Flow<List<VideoProject>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch" }
        val path = "projects"
        emitAll(
            db.collection(path)
                .whereEqualTo("ownerId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProject(projectId: String): Flow<VideoProject?> = flow {
        val uid = requireUserId()
        val path = "projects/$projectId"
        emitAll(
            db.collection("projects").document(projectId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        VideoProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeSavedShares(userId: String): Flow<List<SavedShare>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch" }
        val path = "users/$uid/saved_shares"
        emitAll(
            db.collection("users").document(uid).collection("saved_shares")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedShare::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.savedAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createProject(project: VideoProject): Result<String> {
        var docPath = "projects"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("projects").document()
            docPath = docRef.path
            val sanitized = project.copy(
                ownerId = uid,
                ownerName = (project.ownerName.ifBlank { auth.currentUser?.displayName ?: "Creator" }).take(80)
            )
            docRef.set(sanitized.toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTimeline(project: VideoProject): Result<Unit> {
        val docPath = "projects/${project.id}"
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank()) { "Project ID cannot be blank" }
            check(project.ownerId == uid) { "Only the project owner can edit this timeline" }
            db.collection("projects").document(project.id)
                .update(project.toTimelineUpdateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateShareSettings(
        projectId: String,
        shareVisibility: String,
        shareCode: String
    ): Result<Unit> {
        val docPath = "projects/$projectId"
        return try {
            requireUserId()
            require(projectId.isNotBlank()) { "Project ID cannot be blank" }
            require(shareVisibility in listOf("private", "link_anyone")) { "Invalid share visibility" }
            if (shareVisibility == "link_anyone") {
                require(shareCode.length in 4..16) { "Share code must be 4-16 characters" }
            }
            db.collection("projects").document(projectId)
                .update(
                    mapOf(
                        "shareVisibility" to shareVisibility,
                        "shareCode" to shareCode.take(16),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val docPath = "projects/$projectId"
        return try {
            requireUserId()
            require(projectId.isNotBlank()) { "Project ID cannot be blank" }
            db.collection("projects").document(projectId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun findAndSaveSharedProject(rawShareCode: String): Result<VideoProject> {
        val normalizedCode = rawShareCode.trim().uppercase()
        val path = "projects"
        return try {
            val uid = requireUserId()
            require(normalizedCode.length in 4..16) { "Enter a valid 4-16 character share code" }
            val querySnap = db.collection(path)
                .whereEqualTo("shareCode", normalizedCode)
                .whereEqualTo("shareVisibility", "link_anyone")
                .limit(1)
                .get()
                .await()

            val project = querySnap.documents.firstOrNull()?.toObject(
                VideoProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalArgumentException("No shared video found for code $normalizedCode"))

            val bookmarkPayload = mapOf(
                "projectId" to project.id,
                "shareCode" to project.shareCode.take(16),
                "title" to project.title.take(100).ifBlank { "Shared Video" },
                "ownerName" to project.ownerName.take(80).ifBlank { "Creator" },
                "aspectRatio" to if (project.aspectRatio in listOf("16:9", "9:16", "1:1")) project.aspectRatio else "16:9",
                "totalDurationMs" to project.totalDurationMs.coerceIn(0L, 3600000L),
                "savedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("saved_shares").document(project.id)
                .set(bookmarkPayload)
                .await()

            Result.success(project)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun duplicateToMyVideos(source: VideoProject): Result<String> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in required"))
        val copyProject = source.copy(
            id = "",
            ownerId = currentUser.uid,
            ownerName = (currentUser.displayName ?: "Creator").take(80),
            title = "${source.title.take(88)} (Remix)",
            shareVisibility = "private",
            shareCode = "",
            createdAt = null,
            updatedAt = null
        )
        return createProject(copyProject)
    }

    suspend fun removeSavedShare(shareId: String): Result<Unit> {
        val docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/saved_shares/$shareId"
        return try {
            val uid = requireUserId()
            require(shareId.isNotBlank()) { "Share ID cannot be blank" }
            db.collection("users").document(uid)
                .collection("saved_shares").document(shareId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
