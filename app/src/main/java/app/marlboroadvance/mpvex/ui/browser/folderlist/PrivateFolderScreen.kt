package app.marlboroadvance.mpvex.ui.browser.folderlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import kotlinx.serialization.Serializable

@Serializable
object PrivateFolderScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val backstack = LocalBackStack.current

        // PIN gate state
        var isUnlocked by remember { mutableStateOf(false) }
        var pinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf(false) }
        val hasPinSet = remember { PrivateFolderManager.isPinSet(context) }

        if (!isUnlocked) {
            // ── PIN entry screen ──────────────────────────────────
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Private Folders") },
                        navigationIcon = {
                            IconButton(onClick = { backstack.removeLast() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = if (hasPinSet) "Enter your PIN" else "Set a 4-digit PIN",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinError = false
                            }
                        },
                        label = { Text("4-digit PIN") },
                        isError = pinError,
                        supportingText = if (pinError) {
                            { Text("Wrong PIN. Try again.") }
                        } else null,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                        ),
                    )
                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (pinInput.length != 4) return@Button
                            if (hasPinSet) {
                                if (PrivateFolderManager.verifyPin(context, pinInput)) {
                                    isUnlocked = true
                                } else {
                                    pinError = true
                                    pinInput = ""
                                }
                            } else {
                                // First time — set the PIN and unlock
                                PrivateFolderManager.setPin(context, pinInput)
                                isUnlocked = true
                            }
                        },
                        enabled = pinInput.length == 4,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (hasPinSet) "Unlock" else "Set PIN & Continue")
                    }
                }
            }
        } else {
            // ── Unlocked — show private folders ───────────────────
            PrivateFolderListContent(
                onBack = { backstack.removeLast() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateFolderListContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val backstack = LocalBackStack.current

    // Load private folders from MediaStore
    var privateFolders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val allFolders = app.marlboroadvance.mpvex.repository.MediaFileRepository
                .getAllVideoFoldersFast(context)
            val privatePaths = PrivateFolderManager.getPrivateFolderPaths(context)
            privateFolders = allFolders.filter { it.path in privatePaths }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private Folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                privateFolders.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.Lock,
                        title = "No private folders",
                        message = "Long-press any folder and choose \"Make Private\" to hide it here",
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(privateFolders) { folder ->
                            FolderCard(
                                folder = folder,
                                isSelected = false,
                                isRecentlyPlayed = false,
                                onClick = {
                                    backstack.add(
                                        app.marlboroadvance.mpvex.ui.browser.videolist.VideoListScreen(
                                            folder.bucketId, folder.name,
                                        ),
                                    )
                                },
                                onLongClick = {
                                    // Remove from private
                                    PrivateFolderManager.removePrivateFolder(context, folder.path)
                                    // Refresh list
                                    privateFolders = privateFolders.filter { it.path != folder.path }
                                    android.widget.Toast.makeText(
                                        context,
                                        "${folder.name} moved back to public",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onThumbClick = {
                                    backstack.add(
                                        app.marlboroadvance.mpvex.ui.browser.videolist.VideoListScreen(
                                            folder.bucketId, folder.name,
                                        ),
                                    )
                                },
                                newVideoCount = 0,
                                isGridMode = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
