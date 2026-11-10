package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.KamRepository
import com.example.data.sync.GitHubSmartSyncEngine
import com.example.data.sync.GitHubVersionManifest
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.GroupInvitationsScreen
import com.example.ui.screens.GroupOnboardingScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KamLiteTheme
import com.example.ui.theme.KamSemanticColors
import com.example.ui.viewmodel.KamViewModel
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        GitHubSmartSyncEngine.initializeFromLocalCache(applicationContext)
        setContent {
            KamLiteTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var authSessionId by rememberSaveable { mutableIntStateOf(0) }
    val navScope = rememberCoroutineScope()

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

    val viewModel: KamViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                Log.d("Auth", "[Step 4] Creating fresh KamViewModel for sessionKey=$sessionKey, uid=${user.uid}")
                KamViewModel(
                    repository = KamRepository(db),
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "خادم الفصل",
                    initialEmail = user.email ?: "${user.uid}@kamlite.app",
                    isOnlineFlow = KamViewModel.observeNetworkConnectivity(app)
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val githubSyncState by GitHubSmartSyncEngine.syncState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable(sessionKey) { mutableStateOf(KamBottomTab.HOME) }

    // Silent background GitHub Delta Sync:
    // Checks `releases/version.json` & `releases/delta-sync.json` when online without interrupting the user.
    LaunchedEffect(sessionKey, state.isOnline) {
        if (state.isOnline) {
            delay(900L)
            GitHubSmartSyncEngine.checkAndSyncSilently(context, forceManualCheck = false)
        }
    }

    LaunchedEffect(githubSyncState.silentDeltaBannerMessage) {
        val msg = githubSyncState.silentDeltaBannerMessage
        if (!msg.isNullOrBlank()) {
            viewModel.showBanner(msg)
            GitHubSmartSyncEngine.clearSilentDeltaBanner()
        }
    }

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

    val handleSignOut: () -> Unit = {
        viewModel.clearSession()
        signOut(
            context = context,
            credentialManager = credentialManager,
            onSignOutComplete = onSignedOut,
            scope = authScope
        )
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
    // Layer 2 is the Floating Notification Overlay rendered on top (Alignment.TopCenter, zIndex = 10f).
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentTab == KamBottomTab.HOME,
                        onClick = { currentTab = KamBottomTab.HOME },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = stringResource(R.string.tab_home)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_home),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_home")
                    )
                    NavigationBarItem(
                        selected = currentTab == KamBottomTab.GROUP,
                        onClick = { currentTab = KamBottomTab.GROUP },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = stringResource(R.string.tab_group)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_group),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_group")
                    )
                    NavigationBarItem(
                        selected = currentTab == KamBottomTab.SETTINGS,
                        onClick = { currentTab = KamBottomTab.SETTINGS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.tab_settings)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_settings),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    KamBottomTab.HOME -> {
                        HomeScreen(
                            state = state,
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
                            onMoveStudentToBlock = viewModel::moveStudentToBlock,
                            onReorderStudent = viewModel::reorderStudentWithinBlock,
                            onUpdateStudent = viewModel::updateStudentDetails,
                            onUpdateStudentAvatar = viewModel::updateStudentAvatar,
                            onDeleteStudent = viewModel::deleteStudent,
                            onToggleBlockCollapsed = viewModel::toggleBlockCollapsed,
                            onMergeDuplicateStudents = viewModel::mergeDuplicateStudentRecords,
                            onKeepBothDuplicateStudents = viewModel::keepBothDuplicateStudents
                        )
                    }

                    KamBottomTab.GROUP -> {
                        GroupInvitationsScreen(
                            state = state,
                            onSendInvitation = { name -> viewModel.sendServantInvitation(name) },
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
                            onUnbanServant = viewModel::unbanServant
                        )
                    }

                    KamBottomTab.SETTINGS -> {
                        SettingsScreen(
                            state = state,
                            onToggleCardVisibility = viewModel::toggleCardVisibility,
                            onSaveGroupPolicies = viewModel::updateGroupPolicySettings,
                            onLeaveGroup = viewModel::leaveCurrentGroup,
                            onSignOut = handleSignOut,
                            onSwitchGroup = viewModel::switchActiveGroup,
                            onJoinGroupByCode = viewModel::joinGroupByCode,
                            onCreateNewGroup = viewModel::createGroup,
                            onToggleBlockHidden = viewModel::toggleBlockHidden
                        )
                    }
                }
            }
        }

        // Layer 2: Floating Top Notification Overlay (renders ON TOP of the UI without pushing it down)
        FloatingNotificationsOverlay(
            showConnectivityToast = showConnectivityToast,
            isOnline = state.isOnline,
            hasPendingWrites = state.hasPendingWrites,
            actionBannerMessage = state.actionBannerMessage,
            errorMessage = state.errorMessage,
            majorUpdatePrompt = githubSyncState.majorUpdatePrompt,
            majorUpdateReasonArabic = githubSyncState.majorUpdateReasonArabic,
            onInstallMajorUpdate = { GitHubSmartSyncEngine.openLatestFullApkDownload(context) },
            onDismissMajorUpdate = { GitHubSmartSyncEngine.dismissMajorUpdatePrompt(context) },
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
    }
}

@Composable
private fun FloatingNotificationsOverlay(
    showConnectivityToast: Boolean,
    isOnline: Boolean,
    hasPendingWrites: Boolean,
    actionBannerMessage: String?,
    errorMessage: String?,
    majorUpdatePrompt: GitHubVersionManifest? = null,
    majorUpdateReasonArabic: String? = null,
    onInstallMajorUpdate: () -> Unit = {},
    onDismissMajorUpdate: () -> Unit = {},
    onDismissConnectivityToast: () -> Unit,
    onRetryError: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 10.dp, start = 16.dp, end = 16.dp)
            .widthIn(max = 460.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 0. Non-intrusive 1-Tap Full Update Pill (ONLY appears when the version gap is large and delta-only sync isn't enough)
        AnimatedVisibility(
            visible = majorUpdatePrompt != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            val prompt = majorUpdatePrompt
            if (prompt != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.98f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_major_update_pill")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تحديث كامل جاهز (v${prompt.versionName})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = majorUpdateReasonArabic ?: "فارق النسخ كبير؛ اضغط لتحديث الحزمة الكاملة بضغطة واحدة",
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
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("github_install_update_button")
                            ) {
                                Text("تحديث الآن", fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = onDismissMajorUpdate,
                                modifier = Modifier.testTag("github_dismiss_update_button")
                            ) {
                                Text("لاحقاً")
                            }
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

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.5.dp, dotColor),
                shadowElevation = 8.dp,
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
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.97f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 8.dp,
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
                        fontWeight = FontWeight.Bold,
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
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.98f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                shadowElevation = 8.dp,
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
