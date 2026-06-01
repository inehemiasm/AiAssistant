package com.neo.chevere.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import timber.log.Timber
import com.neo.chevere.R
import com.neo.chevere.core.Constants
import com.neo.chevere.core.PiiUtils
import com.neo.chevere.domain.ChatMessage
import com.neo.chevere.domain.ModelCapability
import com.neo.chevere.domain.ModelTaskType
import com.neo.chevere.ui.chat.components.ActionConfirmationDialog
import com.neo.chevere.ui.chat.components.AgeVerificationDialog
import com.neo.chevere.ui.chat.components.ChatInputBar
import com.neo.chevere.ui.chat.components.ChatTopBar
import com.neo.chevere.ui.chat.components.FullscreenHtmlPreviewDialog
import com.neo.chevere.ui.chat.components.FullscreenImagePreviewDialog
import com.neo.chevere.ui.chat.components.MessageList
import com.neo.chevere.ui.chat.components.ModelInitializationScreen
import com.neo.chevere.ui.common.ChevereHaptic
import com.neo.chevere.ui.common.ErrorSnackbar
import com.neo.chevere.ui.common.hapticForFeedbackMessage
import com.neo.chevere.ui.common.performChevereHaptic
import com.neo.chevere.ui.common.ObserveAsEvents
import com.neo.chevere.ui.designsystem.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
private const val TAG = "ChatScreen"

/**
 * The main Chat screen of the application.
 */
sealed interface FullscreenPreviewState {
    data object None : FullscreenPreviewState
    data class Image(val uri: String) : FullscreenPreviewState
    data class Html(val html: String) : FullscreenPreviewState
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRadarClick: (mode: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.onIntent(ChatIntent.Resume)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Check if the model is initializing to show the full-screen loading state
    val isInitializing = state.runtimeState is RuntimeState.Initializing

    AnimatedContent(
        targetState = isInitializing,
        transitionSpec = {
            fadeIn().togetherWith(fadeOut())
        },
        label = "ChatScreenTransition"
    ) { initializing ->
        if (initializing) {
            ModelInitializationScreen(
                statusMessage = (state.runtimeState as? RuntimeState.Initializing)?.message
                    ?: stringResource(R.string.initializing)
            )
        } else {
            ChatContent(
                state = state,
                effects = viewModel.effect,
                onIntent = { viewModel.onIntent(it) },
                onModelsClick = onModelsClick,
                onSettingsClick = onSettingsClick,
                onRadarClick = onRadarClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    state: ChatState,
    effects: kotlinx.coroutines.flow.Flow<ChatEffect>,
    onIntent: (ChatIntent) -> Unit,
    onModelsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRadarClick: (mode: String) -> Unit
) {
    val viewModel = remember(state, effects, onIntent) {
        object {
            fun onIntent(intent: ChatIntent) {
                onIntent(intent)
            }
            val effect = effects
            val uiState = object {
                val value = state
            }
        }
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val hapticView = LocalView.current
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()


    // Auto-scroll to bottom during token streaming
    LaunchedEffect(state.streamingText) {
        if (state.streamingText.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val lastVisibleItem = visibleItems.last()
                val isLastItemVisible = lastVisibleItem.index == layoutInfo.totalItemsCount - 1
                if (isLastItemVisible) {
                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val lastIndex = layoutInfo.totalItemsCount - 1
                    if (lastVisibleItem.size > viewportHeight) {
                        // Scroll so that the bottom of the last item aligns with the bottom of the viewport
                        listState.scrollToItem(lastIndex, lastVisibleItem.size - viewportHeight)
                    } else {
                        listState.scrollToItem(lastIndex)
                    }
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showImageModelDownloadPrompt by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var pendingPermissionDisclosure by remember { mutableStateOf<PermissionType?>(null) }
    var wasAiBusy by remember { mutableStateOf(false) }
    var fullscreenPreviewState by remember { mutableStateOf<FullscreenPreviewState>(FullscreenPreviewState.None) }
    val showOnboarding = state.localModels.isEmpty() && !state.isLoading
    val isAiBusy = state.isAiBusy
    val busyPhrases = remember {
        listOf(
            R.string.busy_phrase_1,
            R.string.busy_phrase_2,
            R.string.busy_phrase_3,
            R.string.busy_phrase_4,
            R.string.busy_phrase_5,
            R.string.busy_phrase_6,
            R.string.busy_phrase_7,
            R.string.busy_phrase_8
        )
    }
    var busyPhraseIndex by remember { mutableStateOf(0) }

    LaunchedEffect(isAiBusy) {
        if (isAiBusy) {
            busyPhraseIndex = 0
            while (isActive) {
                delay(2500)
                busyPhraseIndex = (busyPhraseIndex + 1) % busyPhrases.size
            }
        }
    }

    val inputBusyMessage = state.loadingMessage ?: when {
        state.sendState is SendState.GeneratingImage -> Constants.UiStatus.GENERATING_IMAGE
        else -> Constants.UiStatus.THINKING
    }

    val resolvedBusyMessage = when {
        inputBusyMessage == "GENERATING..." -> stringResource(R.string.status_generating)
        inputBusyMessage == Constants.UiStatus.PLANNING -> stringResource(R.string.status_planning)
        inputBusyMessage == Constants.UiStatus.GENERATING_IMAGE -> stringResource(R.string.status_generating_image)
        inputBusyMessage.startsWith(Constants.UiStatus.EXECUTING_PREFIX) -> {
            val toolName = inputBusyMessage.removePrefix(Constants.UiStatus.EXECUTING_PREFIX)
            stringResource(R.string.status_executing, toolName)
        }
        inputBusyMessage == Constants.UiStatus.THINKING -> {
            stringResource(busyPhrases[busyPhraseIndex])
        }
        else -> inputBusyMessage
    }

    val suggestions = state.suggestions
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.onIntent(ChatIntent.SelectImage(uri))
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            viewModel.onIntent(ChatIntent.SelectImage(state.tempCameraUri))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val timeStamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())
            val storageDir =
                File(context.cacheDir, "images").apply { mkdirs() }
            val photoFile = File(storageDir, "JPEG_${timeStamp}_.jpg")
            val photoUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            viewModel.onIntent(ChatIntent.SetTempCameraUri(photoUri))
            cameraLauncher.launch(photoUri)
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.camera_permission_required),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isCoarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val isFineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (isCoarseGranted || isFineGranted) {
            Toast.makeText(
                context,
                resources.getString(R.string.location_permission_granted),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.onIntent(ChatIntent.RetryLastMessage)
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.location_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(
                context,
                resources.getString(R.string.contacts_permission_granted),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.onIntent(ChatIntent.RetryLastMessage)
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.contacts_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }



    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] == true
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] == true
        if (readGranted || writeGranted) {
            Toast.makeText(
                context,
                resources.getString(R.string.calendar_permission_granted),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.onIntent(ChatIntent.RetryLastMessage)
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.calendar_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(
                context,
                resources.getString(R.string.microphone_permission_granted),
                Toast.LENGTH_SHORT
            ).show()
            val lastMessage = state.messages.lastOrNull()
            val isAmbientSoundPermission = lastMessage != null && !lastMessage.isUser &&
                lastMessage.text.contains("Microphone permission is required to measure the ambient sound level")
            if (isAmbientSoundPermission) {
                viewModel.onIntent(ChatIntent.RetryLastMessage)
            } else {
                viewModel.onIntent(ChatIntent.StartVoiceInput)
            }
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.microphone_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !showOnboarding,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
                                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f)
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    // Header Area
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 32.dp, top = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    CircleShape
                                )
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CHEVERE AI",
                                style = Typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.4.sp
                            )
                            Text(
                                text = "PRIVATE CORE",
                                style = Typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(24.dp))

                    DrawerNavItem(
                        label = "Models Library",
                        icon = Icons.Default.Storage,
                        onClick = {
                            scope.launch { drawerState.close() }
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            onModelsClick()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DrawerNavItem(
                        label = "Chat History",
                        icon = Icons.Default.History,
                        onClick = {
                            scope.launch { drawerState.close() }
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            showHistorySheet = true
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DrawerNavItem(
                        label = "System Settings",
                        icon = Icons.Default.Person,
                        onClick = {
                            scope.launch { drawerState.close() }
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            onSettingsClick()
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(24.dp))

                    DrawerNavItem(
                        label = "Clear Conversation",
                        icon = Icons.Default.DeleteSweep,
                        tint = MaterialTheme.colorScheme.error,
                        enabled = state.isReady,
                        onClick = {
                            scope.launch { drawerState.close() }
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.ClearConversation)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    isChatReady = state.localModels.any {
                        it.isHealthy && it.taskType != ModelTaskType.IMAGE_GENERATION
                    },
                    isImageReady = state.localModels.any {
                        it.isHealthy && (it.taskType == ModelTaskType.IMAGE_GENERATION || ModelCapability.IMAGE_GEN in it.capabilities)
                    }
                )
            },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                ErrorSnackbar(state.error ?: data.visuals.message) {
                    viewModel.onIntent(ChatIntent.ClearError)
                }
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        val glassBackground = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f)
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(glassBackground)
                .padding(innerPadding)
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (showOnboarding) {
                    EmptyModelState(onModelsClick)
                } else {
                    MessageList(
                        messages = state.messages,
                        listState = listState,
                        streamingText = state.streamingText,
                        streamingModelName = state.selectedModel.replace(
                            Constants.ModelFiles.LITERTLM_EXTENSION,
                            ""
                        ).uppercase(),
                        agentState = state.agentState,
                        onToggleExplicitImageMask = { index ->
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            viewModel.onIntent(ChatIntent.ToggleExplicitImageMask(index))
                        },
                        onShareMessage = { index ->
                            hapticView.performChevereHaptic(ChevereHaptic.Action)
                            viewModel.onIntent(ChatIntent.ShareMessage(index))
                        },
                        onSaveImage = { index ->
                            hapticView.performChevereHaptic(ChevereHaptic.Action)
                            viewModel.onIntent(ChatIntent.SaveImage(index))
                        },
                        onPreviewHtmlFullScreen = { html ->
                            fullscreenPreviewState = FullscreenPreviewState.Html(html)
                        },
                        onImageClick = { uri ->
                            fullscreenPreviewState = FullscreenPreviewState.Image(uri)
                        }
                    )
                }

                if (state.isWaitingForConfirmation) {
                    ActionConfirmationDialog(
                        message = state.confirmationMessage
                            ?: stringResource(R.string.confirm_proceed),
                        onConfirm = {
                            hapticView.performChevereHaptic(ChevereHaptic.Success)
                            viewModel.onIntent(ChatIntent.ConfirmAction)
                        },
                        onDismiss = {
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.CancelAction)
                        }
                    )
                }

                if (state.ageVerificationRequest != null) {
                    AgeVerificationDialog(
                        onSubmit = { year, month, day ->
                            hapticView.performChevereHaptic(ChevereHaptic.Action)
                            viewModel.onIntent(ChatIntent.SubmitBirthdate(year, month, day))
                        },
                        onDismiss = {
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.DismissAgeVerification)
                        }
                    )
                }

                if (showImageModelDownloadPrompt) {
                    AlertDialog(
                        onDismissRequest = { showImageModelDownloadPrompt = false },
                        title = { Text(stringResource(R.string.download_image_model_title)) },
                        text = {
                            Text(stringResource(R.string.download_image_model_body))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                    showImageModelDownloadPrompt = false
                                    onModelsClick()
                                }
                            ) {
                                Text(stringResource(R.string.open_models))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                    showImageModelDownloadPrompt = false
                                }
                            ) {
                                Text(stringResource(R.string.not_now))
                            }
                        }
                    )
                }

                pendingPermissionDisclosure?.let { permissionType ->
                    PermissionDisclosureDialog(
                        permissionType = permissionType,
                        onConfirm = {
                            pendingPermissionDisclosure = null
                            when (permissionType) {
                                PermissionType.LOCATION -> {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                }
                                PermissionType.CONTACTS -> {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                                PermissionType.CALENDAR -> {
                                    calendarPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                }
                                PermissionType.MICROPHONE -> {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        onDismiss = {
                            pendingPermissionDisclosure = null
                            val message = when (permissionType) {
                                PermissionType.LOCATION -> resources.getString(R.string.location_permission_denied)
                                PermissionType.CONTACTS -> resources.getString(R.string.contacts_permission_denied)
                                PermissionType.CALENDAR -> resources.getString(R.string.calendar_permission_denied)
                                PermissionType.MICROPHONE -> resources.getString(R.string.microphone_permission_denied)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            if (!showOnboarding) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                ) {
                    ChatInputBar(
                        text = state.inputText,
                        onTextChange = { viewModel.onIntent(ChatIntent.UpdateInputText(it)) },
                        onSend = {
                            hapticView.performChevereHaptic(ChevereHaptic.Action)
                            viewModel.onIntent(
                                ChatIntent.SendMessage(
                                    state.inputText,
                                    state.selectedImageUri
                                )
                            )
                        },
                        onStop = {
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.StopResponse)
                        },
                        onGalleryClick = {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            imagePickerLauncher.launch("image/*")
                        },
                        onCameraClick = {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            when (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            )) {
                                PackageManager.PERMISSION_GRANTED -> {
                                    val timeStamp = SimpleDateFormat(
                                        "yyyyMMdd_HHmmss",
                                        Locale.US
                                    ).format(Date())
                                    val storageDir =
                                        File(context.cacheDir, "images").apply { mkdirs() }
                                    val photoFile = File(storageDir, "JPEG_${timeStamp}_.jpg")
                                    val photoUri: Uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    viewModel.onIntent(ChatIntent.SetTempCameraUri(photoUri))
                                    cameraLauncher.launch(photoUri)
                                }
                                else -> {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                        selectedImageUri = state.selectedImageUri,
                        onRemoveImage = {
                            hapticView.performChevereHaptic(ChevereHaptic.Warning)
                            viewModel.onIntent(ChatIntent.SelectImage(null))
                        },
                        enabled = state.isReady && !state.isLoading,
                        buttonState = state.composerActionButtonState,
                        busyMessage = resolvedBusyMessage,
                        suggestions = suggestions,
                        onSuggestionClick = { suggestion ->
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            viewModel.onIntent(ChatIntent.UpdateInputText(suggestion))
                        }
                    )
                }
            }

            when (val preview = fullscreenPreviewState) {
                is FullscreenPreviewState.Image -> {
                    FullscreenImagePreviewDialog(
                        imageUri = preview.uri,
                        onDismiss = { fullscreenPreviewState = FullscreenPreviewState.None }
                    )
                }
                is FullscreenPreviewState.Html -> {
                    FullscreenHtmlPreviewDialog(
                        html = preview.html,
                        onDismiss = { fullscreenPreviewState = FullscreenPreviewState.None }
                    )
                }
                FullscreenPreviewState.None -> {}
            }
        }
    }
}

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
                is ChatEffect.ScrollToBottom -> {
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) {
                        listState.animateScrollToItem(lastIndex)
                    }
                }

                is ChatEffect.HideKeyboard -> {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }

                is ChatEffect.ShowToast -> {
                    hapticView.performChevereHaptic(effect.message.hapticForFeedbackMessage())
                    // Scrub toast messages to ensure no PII is accidentally leaked in the UI overlay
                    Toast.makeText(context, PiiUtils.scrub(effect.message), Toast.LENGTH_SHORT).show()
                }

                is ChatEffect.ShareMessage -> {
                    try {
                        shareChatMessage(context, effect)
                    } catch (e: Throwable) {
                        Timber.tag(TAG).e(e, "Failed to share message")
                        Toast.makeText(
                            context,
                            resources.getString(R.string.share_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                is ChatEffect.SaveImage -> {
                    val saved = runCatching {
                        withContext(Dispatchers.IO) {
                            saveImageToGallery(context, effect.imageUri)
                        }
                    }
                        .getOrDefault(false)
                    Toast.makeText(
                        context,
                        if (saved) resources.getString(R.string.image_saved) else resources.getString(
                            R.string.image_save_failed
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    hapticView.performChevereHaptic(if (saved) ChevereHaptic.Success else ChevereHaptic.Warning)
                }


                ChatEffect.ShowImageModelDownloadPrompt -> {
                    hapticView.performChevereHaptic(ChevereHaptic.Warning)
                    showImageModelDownloadPrompt = true
                }

                ChatEffect.RequestLocationPermission -> {
                    pendingPermissionDisclosure = PermissionType.LOCATION
                }

                ChatEffect.RequestContactsPermission -> {
                    pendingPermissionDisclosure = PermissionType.CONTACTS
                }

                ChatEffect.RequestCalendarPermission -> {
                    pendingPermissionDisclosure = PermissionType.CALENDAR
                }

                is ChatEffect.ReadMessageAloud -> Unit
                ChatEffect.RequestMicPermission -> {
                    pendingPermissionDisclosure = PermissionType.MICROPHONE
                }
                is ChatEffect.ShowVoiceError -> Unit
                is ChatEffect.NavigateToRadar -> {
                    onRadarClick(effect.mode)
                }
                ChatEffect.CloseHistorySheet -> {
                    showHistorySheet = false
                }
            }
    }

    LaunchedEffect(isAiBusy, state.error, state.messages.size) {
        if (wasAiBusy && !isAiBusy) {
            hapticView.performChevereHaptic(
                if (state.error == null) ChevereHaptic.Success else ChevereHaptic.Warning
            )
        }
        wasAiBusy = isAiBusy
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            val lastIndex = state.messages.lastIndex
            listState.animateScrollToItem(lastIndex)
            delay(180)
            listState.animateScrollToItem(lastIndex)
        }
    }

    LaunchedEffect(state.error) {
        val errorMessage = state.error
        if (errorMessage != null) {
            try {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Long
                )
            } finally {
                if (viewModel.uiState.value.error != null) {
                    viewModel.onIntent(ChatIntent.ClearError)
                }
            }
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // ── History bottom sheet ──────────────────────────────────────────────────
    if (showHistorySheet) {
        HistoryBottomSheet(
            sessions = state.historySessions,
            currentSessionId = state.currentSessionId,
            onDismiss = { showHistorySheet = false },
            onLoadSession = { sessionId ->
                hapticView.performChevereHaptic(ChevereHaptic.Selection)
                viewModel.onIntent(ChatIntent.LoadSession(sessionId))
            },
            onDeleteSession = { sessionId ->
                hapticView.performChevereHaptic(ChevereHaptic.Warning)
                viewModel.onIntent(ChatIntent.DeleteSession(sessionId))
            },
            onRenameSession = { sessionId, newTitle ->
                hapticView.performChevereHaptic(ChevereHaptic.Action)
                viewModel.onIntent(ChatIntent.RenameSession(sessionId, newTitle))
            },
            onNewConversation = {
                hapticView.performChevereHaptic(ChevereHaptic.Action)
                viewModel.onIntent(ChatIntent.NewConversation)
            }
        )
    }
}

private fun shareChatMessage(context: Context, effect: ChatEffect.ShareMessage) {
    val imageUri = effect.imageUri
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = imageUri?.let { context.contentResolver.getType(it) ?: "image/*" } ?: "text/plain"
        putExtra(Intent.EXTRA_TEXT, effect.text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        imageUri?.let { uri ->
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Chevere AI image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    imageUri?.let { uri ->
        val targets = context.packageManager.queryIntentActivities(
            sendIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        targets.forEach { target ->
            context.grantUriPermission(
                target.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    val chooser = Intent.createChooser(sendIntent, effect.title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        imageUri?.let { uri ->
            clipData = ClipData.newUri(context.contentResolver, "Chevere AI image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(chooser)
}



private fun saveImageToGallery(context: Context, sourceUri: Uri): Boolean {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(sourceUri) ?: "image/png"
    val extension = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
    val values = ContentValues().apply {
        put(
            MediaStore.Images.Media.DISPLAY_NAME,
            "chevere_ai_${System.currentTimeMillis()}.$extension"
        )
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Chevere AI")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val destinationUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false

    return try {
        resolver.openInputStream(sourceUri).use { input ->
            resolver.openOutputStream(destinationUri).use { output ->
                if (input == null || output == null) throw IOException("Could not open image streams")
                input.copyTo(output)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(destinationUri, values, null, null)
        }
        true
    } catch (_: Exception) {
        resolver.delete(destinationUri, null, null)
        false
    }
}

@Composable
private fun EmptyModelState(onModelsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.onboarding_title),
            style = Typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardingPoint(
                icon = Icons.Default.Security,
                title = stringResource(R.string.onboarding_privacy_title),
                body = stringResource(R.string.onboarding_privacy_body)
            )
            OnboardingPoint(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.onboarding_heavy_title),
                body = stringResource(R.string.onboarding_heavy_body)
            )
            OnboardingPoint(
                icon = Icons.Default.Image,
                title = stringResource(R.string.onboarding_image_title),
                body = stringResource(R.string.onboarding_image_body)
            )
            OnboardingPoint(
                icon = Icons.Default.CloudOff,
                title = stringResource(R.string.onboarding_offline_title),
                body = stringResource(R.string.onboarding_offline_body)
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onModelsClick,
            shape = MaterialTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.onboarding_download_cta),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_footer),
            style = Typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingPoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = body,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

enum class PermissionType {
    LOCATION,
    CONTACTS,
    CALENDAR,
    MICROPHONE
}

@Composable
private fun PermissionDisclosureDialog(
    permissionType: PermissionType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (permissionType) {
        PermissionType.LOCATION -> "Location Access Required"
        PermissionType.CONTACTS -> "Contacts Access Required"
        PermissionType.CALENDAR -> "Calendar Access Required"
        PermissionType.MICROPHONE -> "Microphone Access Required"
    }

    val description = when (permissionType) {
        PermissionType.LOCATION -> stringResource(R.string.location_permission_disclosure)
        PermissionType.CONTACTS -> stringResource(R.string.contacts_permission_disclosure)
        PermissionType.CALENDAR -> stringResource(R.string.calendar_permission_disclosure)
        PermissionType.MICROPHONE -> stringResource(R.string.microphone_permission_disclosure)
    }

    val icon = when (permissionType) {
        PermissionType.LOCATION -> Icons.Default.LocationOn
        PermissionType.CONTACTS -> Icons.Default.AccountBox
        PermissionType.CALENDAR -> Icons.Default.DateRange
        PermissionType.MICROPHONE -> Icons.Default.Mic
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = description,
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text("Continue", style = Typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Not Now", style = Typography.labelLarge, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    )
}

@Composable
private fun DrawerNavItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(alpha)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    com.neo.chevere.ui.designsystem.HighTechAiTheme(darkTheme = true) {
        ChatContent(
            state = ChatState(
                messages = listOf(
                    ChatMessage(text = "Hello! Ask me about sensors, system thermals, or compass heading.", isUser = false),
                    ChatMessage(text = "How loud is it in here?", isUser = true),
                    ChatMessage(text = "The room is around forty decibels SPL — Quiet, library level.", isUser = false)
                )
            ),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onModelsClick = {},
            onSettingsClick = {},
            onRadarClick = {}
        )
    }
}

