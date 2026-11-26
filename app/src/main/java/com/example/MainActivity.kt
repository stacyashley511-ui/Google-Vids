package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.auth.authStateFlow
import com.example.data.VidsRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LibraryHomeScreen
import com.example.ui.screens.TimelineEditorScreen
import com.example.ui.theme.VidsStudioTheme
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.LibraryViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data class EditorRoute(val projectId: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VidsStudioTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedVidsNavHost(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email ?: "Creator"
            )
        }
    }
}

@Composable
private fun AuthenticatedVidsNavHost(
    currentUserId: String,
    userDisplayName: String
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LibraryRoute
    ) {
        composable<LibraryRoute> {
            val libraryViewModel: LibraryViewModel = viewModel(
                key = "library_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        LibraryViewModel(
                            repository = VidsRepository(db),
                            currentUserId = currentUserId,
                            displayName = userDisplayName
                        )
                    }
                }
            )
            LibraryHomeScreen(
                viewModel = libraryViewModel,
                userDisplayName = userDisplayName,
                onOpenEditor = { projectId ->
                    navController.navigate(EditorRoute(projectId))
                }
            )
        }

        composable<EditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditorRoute>()
            val editorViewModel: EditorViewModel = viewModel(
                key = "editor_${currentUserId}_${route.projectId}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        EditorViewModel(
                            repository = VidsRepository(db),
                            projectId = route.projectId,
                            currentUserId = currentUserId
                        )
                    }
                }
            )
            TimelineEditorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
                onOpenRemixedProject = { newProjectId ->
                    navController.navigate(EditorRoute(newProjectId)) {
                        popUpTo(LibraryRoute)
                    }
                }
            )
        }
    }
}
