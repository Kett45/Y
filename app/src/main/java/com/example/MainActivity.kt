package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.cache.LocalClassroomCache
import com.example.data.repository.ChatRepository
import com.example.data.repository.KamRepository
import com.example.data.sync.GitHubSmartSyncEngine
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.KamFloatingBottomNavBar
import com.example.ui.components.LocalStudentQuickPeekController
import com.example.ui.components.ServantPhoneOnboardingWizardDialog
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.components.StudentHoldQuickPeekRootOverlay
import com.example.ui.components.StudentQuickPeekController
import com.example.ui.screens.GroupInvitationsScreen
import com.example.ui.screens.GroupOnboardingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VisitationSwipeScreen
import com.example.ui.theme.KamLiteTheme
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalAppAppearanceController
import com.example.ui.theme.LocalIsTabActive
import com.example.ui.theme.LocalKamExtendedColors
import com.example.ui.theme.LocalUiScaleController
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.KamScreenState
import com.example.ui.viewmodel.KamViewModel
import com.example.util.KamPerformanceTracker
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class KamBottomTab(val routeId: String, val labelRes: Int) {
    HOME("home", R.string.tab_home),
    VISITATION("visitation", R.string.tab_visitation),
    MESSAGES("messages", R.string.tab_messages),
    GROUP("group", R.string.tab_group),
    SETTINGS("settings", R.string.tab_settings)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {

    private var frameMetricsListener: android.view.Window.OnFrameMetricsAvailableListener? = null
    private var frameMetricsHandlerThread: android.os.HandlerThread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        KamPerformanceTracker.markActivityOnCreate()
        if (BuildConfig.ENABLE_STRICT_MODE_BY_DEFAULT) {
            setupStrictMode()
        }
        super.onCreate(savedInstanceState)
        GitHubSmartSyncEngine.attachActivity(this)
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(applicationContext)
            }
        }
        enableEdgeToEdge()
        LocalClassroomCache.init(applicationContext)
        com.example.ui.theme.AppAppearanceController.preloadAsync(applicationContext)
        com.example.ui.theme.UiScaleController.preloadAsync(applicationContext)
        setContent {
            KamLiteTheme {
                AppNavigation()
            }
        }
        window.decorView.post {
            KamPerformanceTracker.onDiagnosticsModeChanged = { enabled ->
                runOnUiThread {
                    if (enabled) setupFrameMetricsListener() else teardownFrameMetricsListener()
                }
            }
            if (BuildConfig.ENABLE_STRICT_MODE_BY_DEFAULT || KamPerformanceTracker.isLiveDiagnosticsEnabled) {
                KamPerformanceTracker.setLiveDiagnosticsEnabled(true)
                setupFrameMetricsListener()
            }
            runCatching {
                Firebase.auth.currentUser?.uid?.let { uid ->
                    LocalClassroomCache.preloadForUser(applicationContext, uid)
                }
            }
            GitHubSmartSyncEngine.initializeFromLocalCache(applicationContext)
        }
    }

    override fun onResume() {
        super.onResume()
        GitHubSmartSyncEngine.onActivityResumed(this)
    }

    private fun setupStrictMode() {
        runCatching {
            KamPerformanceTracker.isStrictModeActive = true
            val threadPolicyBuilder = android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                threadPolicyBuilder.penaltyListener(mainExecutor) { violation ->
                    KamPerformanceTracker.recordStrictModeViolation(violation)
                }
            }
            android.os.StrictMode.setThreadPolicy(threadPolicyBuilder.build())
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    private fun setupFrameMetricsListener() {
        if (frameMetricsListener != null) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            runCatching {
                val thread = android.os.HandlerThread("KamFrameMetricsThread").apply { start() }
                frameMetricsHandlerThread = thread
                val handler = android.os.Handler(thread.looper)
                val listener = android.view.Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                    val durationNanos = frameMetrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION)
                    KamPerformanceTracker.recordFrameDuration(durationNanos)
                }
                frameMetricsListener = listener
                window.addOnFrameMetricsAvailableListener(listener, handler)
            }
        }
    }

    private fun teardownFrameMetricsListener() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            frameMetricsListener?.let { listener ->
                runCatching { window.removeOnFrameMetricsAvailableListener(listener) }
            }
        }
        frameMetricsListener = null
        runCatching { frameMetricsHandlerThread?.quitSafely() }
        frameMetricsHandlerThread = null
    }

    override fun onDestroy() {
        super.onDestroy()
        KamPerformanceTracker.onDiagnosticsModeChanged = null
        teardownFrameMetricsListener()
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var authSessionId by rememberSaveable { mutableIntStateOf(0) }
    val navScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isDarkTheme = LocalKamExtendedColors.current.isDark

    DisposableEffect(context, isDarkTheme) {
        val activity = context as? ComponentActivity
        activity?.enableEdgeToEdge(
            statusBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
        )
        onDispose {}
    }

    LaunchedEffect(Unit) {
        KamPerformanceTracker.markFirstUiFrameRendered()
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val updatedUser = firebaseAuth.currentUser
            Log.d("Auth", "[AuthStateListener] user changed: uid=${updatedUser?.uid}, email=${updatedUser?.email}")
            if (currentUser?.uid != updatedUser?.uid) {
                authSessionId++
            }
            currentUser = updatedUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = {
                val signedIn = auth.currentUser
                Log.d("Auth", "[AppNavigation] onAuthSuccess triggered: uid=${signedIn?.uid}")
                authSessionId++
                currentUser = signedIn
            },
            externalScope = navScope
        )
    } else {
        key(user.uid, authSessionId) {
            AuthenticatedKamApp(
                user = user,
                sessionKey = "${user.uid}_$authSessionId",
                authScope = navScope,
                onSignedOut = {
                    authSessionId++
                    currentUser = null
                }
            )
        }
    }
}

@Composable
fun AuthenticatedKamApp(
    user: FirebaseUser,
    sessionKey: String = user.uid,
    authScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope(),
    onSignedOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val appearanceController = LocalAppAppearanceController.current
    val uiScaleController = LocalUiScaleController.current

    val sharedKamRepository = remember(sessionKey) {
        val app = context.applicationContext as android.app.Application
        val databaseId = runCatching { app.getString(R.string.firestore_database_id) }.getOrDefault("")
        require(databaseId.isNotBlank()) { "firestore_database_id must not be blank" }
        val db = FirebaseFirestore.getInstance(databaseId)
        KamRepository(db) to db
    }

    val viewModel: KamViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                LocalClassroomCache.init(app)
                LocalClassroomCache.preloadForUser(app, user.uid)
                KamViewModel(
                    repository = sharedKamRepository.first,
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "خادم الفصل",
                    initialEmail = user.email ?: "${user.uid}@kamlite.app",
                    isOnlineFlow = KamViewModel.observeNetworkConnectivity(app),
                    chatRepository = ChatRepository(sharedKamRepository.second)
                )
            }
        }
    )

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$sessionKey",
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "خادم الفصل",
                    chatRepository = ChatRepository(sharedKamRepository.second),
                    kamRepository = sharedKamRepository.first
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable(sessionKey) { mutableStateOf(KamBottomTab.HOME) }
    var visitedTabNames by rememberSaveable(sessionKey) {
        mutableStateOf(listOf(KamBottomTab.HOME.name))
    }
    val selectTab: (KamBottomTab) -> Unit = remember(chatViewModel) {
        { targetTab ->
            if (targetTab != KamBottomTab.MESSAGES && chatViewModel.chatState.value.activeRoom != null) {
                chatViewModel.closeActiveChat()
            }
            if (targetTab.name !in visitedTabNames) {
                visitedTabNames = visitedTabNames + targetTab.name
            }
            currentTab = targetTab
        }
    }

    val ownServantName = remember(state.blocks, state.userProfile, user.uid, user.displayName) {
        state.blocks.firstOrNull { it.servantId == user.uid }?.servantName?.takeIf { it.isNotBlank() }
            ?: state.userProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: user.displayName
            ?: "خادم الفصل"
    }

    LaunchedEffect(state.group, state.memberProfiles, state.blocks, ownServantName, state.servantAliases) {
        chatViewModel.syncWithClassroomState(
            group = state.group,
            memberProfiles = state.memberProfiles,
            blocks = state.blocks,
            ownDisplayName = ownServantName,
            servantAliases = state.servantAliases
        )
        if (state.group != null) {
            chatViewModel.touchOwnLastSeenThrottled()
        }
    }

    val studentsPhotoSyncSignature = remember(state.students) {
        state.students.fold(state.students.size) { acc, stu ->
            if (stu.effectiveHasAvatar || stu.avatarBase64.isNotBlank()) {
                (acc * 31) + stu.id.hashCode() + stu.effectiveAvatarKey.hashCode()
            } else {
                acc
            }
        }
    }

    LaunchedEffect(state.group?.inviteCode, studentsPhotoSyncSignature) {
        if (state.group != null && state.students.isNotEmpty()) {
            StudentAvatarImageHelper.syncAllStudentPhotosToLocalDisk(context, state.students)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.group?.inviteCode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP) {
                chatViewModel.touchOwnLastSeenThrottled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Wire appearance & UI scale changes to sync with user's cloud profile & local cache
    DisposableEffect(appearanceController, uiScaleController, viewModel) {
        appearanceController.onCloudPreferenceSyncRequested = { themeId, showFab ->
            viewModel.syncAppearanceAndScaleToCloud(
                themeId = themeId,
                showFloatingAddButton = showFab
            )
        }
        uiScaleController.onCloudScaleSyncRequested = { mode, scale ->
            viewModel.syncAppearanceAndScaleToCloud(
                uiScaleMode = mode,
                uiScaleFactor = scale
            )
        }
        onDispose {
            appearanceController.onCloudPreferenceSyncRequested = null
            uiScaleController.onCloudScaleSyncRequested = null
        }
    }

    // Apply cloud-restored personal preferences when logging in on any device
    LaunchedEffect(
        state.userProfile?.themeId,
        state.userProfile?.uiScaleMode,
        state.userProfile?.uiScaleFactor,
        state.userProfile?.showFloatingAddButton
    ) {
        val profile = state.userProfile ?: return@LaunchedEffect
        appearanceController.applyCloudSyncedPreferences(
            themeId = profile.themeId,
            showFloatingAdd = profile.showFloatingAddButton
        )
        uiScaleController.applyCloudSyncedScale(
            modeStr = profile.uiScaleMode,
            scaleFactor = profile.uiScaleFactor.toFloat()
        )
    }

    // Silent background GitHub Delta Sync:
    // Checks `releases/version.json` & `releases/delta-sync.json` when online without interrupting the user.
    LaunchedEffect(sessionKey, state.isOnline) {
        if (state.isOnline) {
            delay(900L)
            GitHubSmartSyncEngine.checkAndSyncSilently(context, forceManualCheck = false)
        }
    }

    GitHubDeltaBannerBridge(
        onShowBanner = viewModel::showBanner
    )

    // Edge-triggered transient connectivity toast:
    // Appears ONLY when internet disconnects or reconnects (for 2.5 seconds), never permanently.
    var previousOnlineState by remember(sessionKey) { mutableStateOf<Boolean?>(null) }
    var showConnectivityToast by remember(sessionKey) { mutableStateOf(false) }

    LaunchedEffect(state.isOnline) {
        val prev = previousOnlineState
        if (prev == null) {
            previousOnlineState = state.isOnline
            if (!state.isOnline) {
                showConnectivityToast = true
                delay(2500L)
                showConnectivityToast = false
            }
        } else if (prev != state.isOnline) {
            previousOnlineState = state.isOnline
            showConnectivityToast = true
            delay(2500L)
            showConnectivityToast = false
        }
    }

    if (currentTab != KamBottomTab.HOME) {
        BackHandler {
            currentTab = KamBottomTab.HOME
        }
    }

    DisposableEffect(sessionKey) {
        onDispose {
            viewModel.clearSession()
        }
    }

    val handleSignOut: () -> Unit = remember(viewModel, context, credentialManager, onSignedOut, authScope) {
        {
            viewModel.clearSession()
            signOut(
                context = context,
                credentialManager = credentialManager,
                onSignOutComplete = onSignedOut,
                scope = authScope
            )
        }
    }

    if (state.isLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري تحميل بيانات الفصل...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        return
    }

    if (state.userProfile?.groupId.isNullOrBlank() || state.group == null) {
        val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
        GroupOnboardingScreen(
            defaultUserName = state.userProfile?.displayName ?: user.displayName.orEmpty(),
            isBusy = isBusy,
            errorMessage = state.errorMessage,
            canNavigateBack = state.canReturnToPreviousGroup,
            onNavigateBack = viewModel::returnToPreviousGroup,
            onCreateGroup = { groupName, adminName ->
                viewModel.createGroup(groupName, adminName)
            },
            onJoinGroup = { inviteCode, servantName ->
                viewModel.joinGroupByCode(inviteCode, servantName)
            },
            onSignOut = handleSignOut
        )
        return
    }

    // Root Box: Layer 1 is the fixed Scaffold (never shifts down when notifications appear);
    // Layer 2 is FloatingNotificationsOverlay (zIndex = 10f);
    // Layer 3 is StudentHoldQuickPeekRootOverlay (zIndex = 20f, full screen edge-to-edge).
    val quickPeekController = remember { StudentQuickPeekController() }
    var showPhoneWizardModal by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var isInitialFrameSettled by remember(sessionKey) { mutableStateOf(false) }
    LaunchedEffect(sessionKey) {
        delay(600L)
        isInitialFrameSettled = true
    }
    var isBottomBarVisibleByScroll by rememberSaveable(sessionKey) { mutableStateOf(true) }
    var homeResetSignal by remember(sessionKey) { mutableStateOf(0) }
    var lastHomeTapTimeMs by remember(sessionKey) { mutableStateOf(0L) }
    LaunchedEffect(currentTab) {
        quickPeekController.dismiss()
        isBottomBarVisibleByScroll = true
    }
    val bottomBarScrollConnection = remember {
        object : NestedScrollConnection {
            private var accumulatedDelta = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < -1f) {
                    // User scrolling down (content moving up) -> hide bottom bar smoothly
                    accumulatedDelta = if (accumulatedDelta > 0f) dy else accumulatedDelta + dy
                    if (accumulatedDelta <= -22f && isBottomBarVisibleByScroll) {
                        isBottomBarVisibleByScroll = false
                        accumulatedDelta = 0f
                    }
                } else if (dy > 1f) {
                    // User scrolling up (content moving down) -> bring bottom bar smoothly back
                    accumulatedDelta = if (accumulatedDelta < 0f) dy else accumulatedDelta + dy
                    if (accumulatedDelta >= 14f && !isBottomBarVisibleByScroll) {
                        isBottomBarVisibleByScroll = true
                        accumulatedDelta = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
    CompositionLocalProvider(LocalStudentQuickPeekController provides quickPeekController) {
        Box(modifier = Modifier.fillMaxSize()) {
            val navChatState by chatViewModel.chatState.collectAsStateWithLifecycle()
            val isInsideActiveChatRoom = currentTab == KamBottomTab.MESSAGES && navChatState.activeRoom != null
            LaunchedEffect(isInsideActiveChatRoom) {
                if (!isInsideActiveChatRoom) {
                    isBottomBarVisibleByScroll = true
                }
            }
            val showFloatingBottomBar = !isInsideActiveChatRoom && isBottomBarVisibleByScroll
            val visitationBottomPadding by animateDpAsState(
                targetValue = if (showFloatingBottomBar) 82.dp else 0.dp,
                animationSpec = tween(durationMillis = 200),
                label = "visitation_bottom_nav_clearance"
            )

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                // Use systemBars (excluding IME) so ChatConversationView applies imePadding() cleanly ONCE without jumping
                contentWindowInsets = WindowInsets.systemBars
            ) { innerPadding ->
                val onOpenPhoneWizardRemembered = remember { { showPhoneWizardModal = true } }
                val onSendTextMessageRemembered: (String) -> Unit = remember(chatViewModel) {
                    { text -> chatViewModel.sendMessage(text) }
                }
                val onSendImageMessageRemembered: (android.net.Uri, String) -> Unit = remember(chatViewModel, context) {
                    { uri, caption -> chatViewModel.sendImageMessage(context, uri, caption) }
                }
                val onSendEditedImageMessageRemembered: (android.graphics.Bitmap, String) -> Unit = remember(chatViewModel, context) {
                    { bmp, caption -> chatViewModel.sendEditedImageMessage(context, bmp, caption) }
                }
                val onSendAudioMessageRemembered: (java.io.File, Int) -> Unit = remember(chatViewModel, context) {
                    { file, durationSec -> chatViewModel.sendAudioMessage(context, file, durationSec) }
                }
                val onSendInvitationRemembered: (String) -> Unit = remember(viewModel) {
                    { name -> viewModel.sendServantInvitation(name) }
                }

                RetainedBottomTabContainer(
                    activeTab = currentTab,
                    visitedTabNames = visitedTabNames,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .nestedScroll(bottomBarScrollConnection)
                ) { tab, isTabActive ->
                val tabState = rememberFrozenWhileInactive(state, isTabActive)
                when (tab) {
                    KamBottomTab.HOME -> {
                        HomeScreen(
                            state = tabState,
                            currentUserId = user.uid,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onMonthOffset = viewModel::selectMonthOffset,
                            onSelectMonthAndWeek = viewModel::selectMonthAndWeek,
                            onAwardCard = viewModel::awardCardToStudent,
                            onUndoCardEvent = viewModel::undoCardEvent,
                            onAddSingleStudent = viewModel::addSingleStudent,
                            onAnalyzeBulkImport = viewModel::analyzeBulkImportText,
                            onConfirmBulkImport = viewModel::importBulkStudents,
                            onCreateBlock = viewModel::createServantBlock,
                            onUpdateBlock = viewModel::updateServantBlock,
                            onUpdateBlockAvatar = viewModel::updateBlockCustomAvatar,
                            onMoveStudentToBlock = viewModel::moveStudentToBlock,
                            onReorderStudent = viewModel::reorderStudentWithinBlock,
                            onUpdateStudent = viewModel::updateStudentDetails,
                            onUpdateStudentAvatar = viewModel::updateStudentAvatar,
                            onDeleteStudent = viewModel::deleteStudent,
                            onToggleBlockCollapsed = viewModel::toggleBlockCollapsed,
                            onMergeDuplicateStudents = viewModel::mergeDuplicateStudentRecords,
                            onKeepBothDuplicateStudents = viewModel::keepBothDuplicateStudents,
                            onUpdateOwnServantAvatar = viewModel::updateOwnServantAvatar,
                            onOpenPhoneWizard = onOpenPhoneWizardRemembered,
                            onAddMemorizationNote = viewModel::addMemorizationNote,
                            onAddChildComment = viewModel::addChildComment,
                            onDeleteChildNote = viewModel::deleteChildNote,
                            onPostChildGroupNotification = viewModel::postChildGroupNotification,
                            homeResetSignal = homeResetSignal,
                            onRefresh = viewModel::forceFullCloudSync
                        )
                    }

                    KamBottomTab.VISITATION -> {
                        VisitationSwipeScreen(
                            state = tabState,
                            currentUserId = user.uid,
                            onRecordVisitation = viewModel::recordVisitationNote,
                            onUpdateStudent = viewModel::updateStudentDetails,
                            onDeleteNote = viewModel::deleteChildNote,
                            modifier = Modifier.padding(bottom = visitationBottomPadding)
                        )
                    }

                    KamBottomTab.MESSAGES -> {
                        val liveChatState by chatViewModel.chatState.collectAsStateWithLifecycle()
                        val tabChatState = rememberFrozenWhileInactive(liveChatState, isTabActive)
                        MessagesScreen(
                            chatState = tabChatState,
                            memberProfiles = tabState.memberProfiles,
                            students = tabState.students,
                            currentUserId = user.uid,
                            mediaStorage = chatViewModel.mediaStorage,
                            onOpenGroupChat = chatViewModel::openGroupChat,
                            onOpenDirectChat = chatViewModel::openDirectChat,
                            onOpenDirectChatByEmail = chatViewModel::openDirectChatByEmail,
                            onClearEmailLookupError = chatViewModel::clearEmailLookupError,
                            onCloseActiveChat = chatViewModel::closeActiveChat,
                            onSendMessage = onSendTextMessageRemembered,
                            onSendImageMessage = onSendImageMessageRemembered,
                            onSendEditedImageMessage = onSendEditedImageMessageRemembered,
                            onSendAudioMessage = onSendAudioMessageRemembered,
                            onClearMediaError = chatViewModel::clearMediaError,
                            onDeleteOwnMessage = chatViewModel::deleteOwnMessage,
                            onForwardMessage = chatViewModel::forwardMessage,
                            onClearForwardStatus = chatViewModel::clearForwardStatus,
                            onUpdateGroupAvatar = viewModel::updateGroupAvatar,
                            onSavePersonalAlias = viewModel::savePersonalServantAlias,
                            onLoadOlderMessages = chatViewModel::loadOlderMessages
                        )
                    }

                    KamBottomTab.GROUP -> {
                        GroupInvitationsScreen(
                            state = tabState,
                            onSendInvitation = onSendInvitationRemembered,
                            onRespondToInvitation = viewModel::respondToInvitation,
                            onDeleteInvitation = viewModel::deleteInvitation,
                            onDeleteBlock = viewModel::deleteServantBlock,
                            onCopyBanner = viewModel::showBanner,
                            onSwitchGroup = viewModel::switchActiveGroup,
                            onJoinGroupByCode = viewModel::joinGroupByCode,
                            onCreateNewGroup = viewModel::createGroup,
                            onToggleBlockHidden = viewModel::toggleBlockHidden,
                            onRefreshDailyInviteCode = viewModel::refreshDailyInviteCode,
                            onRemoveAndBanServant = viewModel::removeAndBanServantFromBlock,
                            onUnbanServant = viewModel::unbanServant,
                            currentUserId = user.uid,
                            onCreateBlock = viewModel::createServantBlock,
                            onUpdateBlock = viewModel::updateServantBlock,
                            onDeleteBlockKeepingChildren = viewModel::deleteBlockKeepingChildren,
                            onDeleteBlockWithChildren = viewModel::deleteBlockWithChildren,
                            onSavePersonalServantAlias = viewModel::savePersonalServantAlias,
                            onEditOwnDisplayName = viewModel::editOwnDisplayName,
                            onToggleAssistantAdmin = viewModel::toggleAssistantAdmin
                        )
                    }

                    KamBottomTab.SETTINGS -> {
                        SettingsScreen(
                            state = tabState,
                            onToggleCardVisibility = viewModel::toggleCardVisibility,
                            onSaveGroupPolicies = viewModel::updateGroupPolicySettings,
                            onLeaveGroup = viewModel::leaveCurrentGroup,
                            onSignOut = handleSignOut,
                            onSwitchGroup = viewModel::switchActiveGroup,
                            onJoinGroupByCode = viewModel::joinGroupByCode,
                            onCreateNewGroup = viewModel::createGroup,
                            onToggleBlockHidden = viewModel::toggleBlockHidden,
                            currentUserId = user.uid,
                            currentUserEmail = user.email.orEmpty(),
                            onOpenPhoneWizard = onOpenPhoneWizardRemembered,
                            onUpdateOwnServantAvatar = viewModel::updateOwnServantAvatar,
                            onDeleteOwnPhones = viewModel::deleteOwnPhoneNumbers,
                            onEditOwnDisplayName = viewModel::editOwnDisplayName,
                            onSaveCustomCardSettings = viewModel::updateCustomCardSettings
                        )
                    }
                }
            }
        }

        // Layer 1.5: Sleek Floating Animated Bottom Navigation Bar (scroll-aware & auto-hides inside chat rooms)
        KamFloatingBottomNavBar(
            visible = showFloatingBottomBar,
            selectedIndex = currentTab.ordinal,
            unreadMessagesCount = navChatState.totalUnreadCount,
            onSelectIndex = { idx ->
                val targetTab = KamBottomTab.entries.getOrElse(idx) { KamBottomTab.HOME }
                isBottomBarVisibleByScroll = true
                if (targetTab == KamBottomTab.HOME) {
                    val nowMs = System.currentTimeMillis()
                    if (currentTab == KamBottomTab.HOME) {
                        if (nowMs - lastHomeTapTimeMs in 1L..450L) {
                            lastHomeTapTimeMs = 0L
                            homeResetSignal += 1
                            viewModel.forceFullCloudSync()
                        } else {
                            lastHomeTapTimeMs = nowMs
                            homeResetSignal += 1
                        }
                    } else {
                        lastHomeTapTimeMs = nowMs
                        homeResetSignal += 1
                        selectTab(KamBottomTab.HOME)
                    }
                } else {
                    lastHomeTapTimeMs = 0L
                    if (targetTab == KamBottomTab.MESSAGES) {
                        chatViewModel.closeActiveChat()
                    }
                    selectTab(targetTab)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(8f)
        )

        // Layer 2: Floating Top Notification Overlay (renders ON TOP of the UI without pushing it down)
        FloatingNotificationsOverlay(
            showConnectivityToast = showConnectivityToast,
            isOnline = state.isOnline,
            hasPendingWrites = state.hasPendingWrites,
            actionBannerMessage = state.actionBannerMessage,
            errorMessage = state.errorMessage,
            onDismissConnectivityToast = { showConnectivityToast = false },
            onRetryError = {
                viewModel.clearError()
                viewModel.ensureProfileInitialized()
            },
            onDismissError = { viewModel.clearError() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

            // Layer 3: Full-screen edge-to-edge Student Quick Peek Preview (centered in RTL/LTR, covers status & nav bars)
            StudentHoldQuickPeekRootOverlay(
                request = quickPeekController.activeRequest,
                modifier = Modifier.zIndex(20f)
            )

            // Layer 4: Step-by-step WhatsApp Phone Onboarding & Editing Wizard Overlay (in-window, no separate DialogWindow)
            if (showPhoneWizardModal || (isInitialFrameSettled && state.shouldPromptPhoneOnboarding)) {
                Box(modifier = Modifier.fillMaxSize().zIndex(25f)) {
                    ServantPhoneOnboardingWizardDialog(
                        initialPrimaryPhone = state.ownPrimaryPhone,
                        initialSecondaryPhone = state.ownSecondaryPhone,
                        onSavePhones = { primary, secondary ->
                            viewModel.saveOwnPhoneNumbers(
                                primaryPhone = primary,
                                secondaryPhone = secondary
                            )
                        },
                        onSkipOrDismiss = {
                            showPhoneWizardModal = false
                            viewModel.skipPhoneOnboarding()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> rememberFrozenWhileInactive(value: T, isActive: Boolean): T {
    var latest by remember { mutableStateOf(value) }
    if (isActive) {
        latest = value
    }
    return latest
}

@Composable
private fun RetainedBottomTabContainer(
    activeTab: KamBottomTab,
    visitedTabNames: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable (tab: KamBottomTab, isActive: Boolean) -> Unit
) {
    val stateHolder = rememberSaveableStateHolder()
    val renderedTabs = remember(visitedTabNames, activeTab) {
        KamBottomTab.entries.filter { it == activeTab || it.name in visitedTabNames }
    }
    Layout(
        modifier = modifier,
        content = {
            renderedTabs.forEach { tab ->
                val isSelected = tab == activeTab
                key(tab) {
                    CompositionLocalProvider(LocalIsTabActive provides isSelected) {
                        stateHolder.SaveableStateProvider(tab.routeId) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                content(tab, isSelected)
                            }
                        }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val activeIndex = renderedTabs.indexOf(activeTab)
        val activePlaceable: Placeable? = if (activeIndex in measurables.indices) {
            measurables[activeIndex].measure(constraints)
        } else {
            null
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            activePlaceable?.placeRelative(0, 0)
        }
    }
}

@Composable
private fun GitHubDeltaBannerBridge(onShowBanner: (String) -> Unit) {
    val githubSyncState by GitHubSmartSyncEngine.syncState.collectAsStateWithLifecycle()
    LaunchedEffect(githubSyncState.silentDeltaBannerMessage) {
        val msg = githubSyncState.silentDeltaBannerMessage
        if (!msg.isNullOrBlank()) {
            onShowBanner(msg)
            GitHubSmartSyncEngine.clearSilentDeltaBanner()
        }
    }
}

@Composable
private fun FloatingNotificationsOverlay(
    showConnectivityToast: Boolean,
    isOnline: Boolean,
    hasPendingWrites: Boolean,
    actionBannerMessage: String?,
    errorMessage: String?,
    onDismissConnectivityToast: () -> Unit,
    onRetryError: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val githubSyncState by GitHubSmartSyncEngine.syncState.collectAsStateWithLifecycle()
    val majorUpdatePrompt = githubSyncState.majorUpdatePrompt
    val majorUpdateReasonArabic = githubSyncState.majorUpdateReasonArabic
    val isDownloadingApk = githubSyncState.isDownloadingApk
    val downloadProgressPercent = githubSyncState.downloadProgressPercent
    val downloadedApkReady = githubSyncState.downloadedApkReady
    val onInstallMajorUpdate: () -> Unit = remember(context) {
        { GitHubSmartSyncEngine.openLatestFullApkDownload(context) }
    }
    val onDismissMajorUpdate: () -> Unit = remember(context) {
        { GitHubSmartSyncEngine.dismissMajorUpdatePrompt(context) }
    }
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 10.dp, start = 16.dp, end = 16.dp)
            .widthIn(max = 460.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 0. Non-intrusive 1-Tap In-App Update Pill with live download progress bar
        AnimatedVisibility(
            visible = majorUpdatePrompt != null || isDownloadingApk,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            val prompt = majorUpdatePrompt
            val extOverlayColors = LocalKamExtendedColors.current
            if (prompt != null || isDownloadingApk) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = extOverlayColors.elevatedSurface.copy(alpha = 0.97f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    shadowElevation = if (extOverlayColors.isDark) 0.dp else 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_major_update_pill")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        isDownloadingApk -> "جاري تنزيل التحديث داخل التطبيق ($downloadProgressPercent%)"
                                        downloadedApkReady -> "التحديث جاهز للتثبيت المباشر (v${prompt?.versionName.orEmpty()})"
                                        else -> "تحديث جديد متاح (v${prompt?.versionName.orEmpty()})"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = majorUpdateReasonArabic ?: "يتم تنزيل التحديث وتثبيته مباشرة داخل التطبيق",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = onInstallMajorUpdate,
                                    enabled = !isDownloadingApk,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("github_install_update_button")
                                ) {
                                    Text(
                                        text = when {
                                            isDownloadingApk -> "$downloadProgressPercent%"
                                            downloadedApkReady -> "تثبيت الآن"
                                            else -> "تحديث الآن"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (!isDownloadingApk) {
                                    TextButton(
                                        onClick = onDismissMajorUpdate,
                                        modifier = Modifier.testTag("github_dismiss_update_button")
                                    ) {
                                        Text("لاحقاً")
                                    }
                                }
                            }
                        }
                        if (isDownloadingApk) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (downloadProgressPercent / 100f).coerceIn(0.02f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }

        // 1. Transient Connectivity Pill (only appears for 2.5s on disconnect or reconnect)
        AnimatedVisibility(
            visible = showConnectivityToast,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            val dotColor = if (isOnline) {
                KamSemanticColors.onlineIndicator
            } else {
                KamSemanticColors.offlineIndicator
            }
            val statusText = when {
                !isOnline -> stringResource(R.string.status_offline)
                hasPendingWrites -> stringResource(R.string.status_syncing)
                else -> stringResource(R.string.status_online)
            }

            val extOverlayColors = LocalKamExtendedColors.current
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = extOverlayColors.elevatedSurface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, dotColor.copy(alpha = 0.55f)),
                shadowElevation = if (extOverlayColors.isDark) 0.dp else 4.dp,
                modifier = Modifier
                    .clickable(onClick = onDismissConnectivityToast)
                    .testTag("connectivity_status_strip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = dotColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. Floating Action Feedback Pill (appears on top of the screen for 2.5s without pushing layout down)
        AnimatedVisibility(
            visible = actionBannerMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            val extOverlayColors = LocalKamExtendedColors.current
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.97f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                shadowElevation = if (extOverlayColors.isDark) 0.dp else 4.dp,
                modifier = Modifier.testTag("floating_action_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actionBannerMessage.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Floating Error Banner with Retry
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            val extOverlayColors = LocalKamExtendedColors.current
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.98f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                shadowElevation = if (extOverlayColors.isDark) 0.dp else 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        TextButton(onClick = onRetryError) {
                            Text(
                                text = stringResource(R.string.retry),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = onDismissError) {
                            Text(
                                text = "إغلاق",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
