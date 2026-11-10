package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.CardType
import com.example.ui.theme.KamSemanticColors
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun showAuthToast(context: Context, message: String) {
    try {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.w("Auth", "Could not show Toast: ${e.message}")
    }
}

private suspend fun signInWithCredentialRetrying(
    authCredential: AuthCredential,
    maxAttempts: Int = 2
) {
    var lastError: FirebaseNetworkException? = null
    for (attempt in 1..maxAttempts) {
        try {
            withTimeout(12_000L) {
                withContext(NonCancellable) {
                    Firebase.auth.signInWithCredential(authCredential).await()
                }
            }
            return
        } catch (e: FirebaseNetworkException) {
            lastError = e
            Log.w("Auth", "[Step 3] Network error on attempt $attempt/$maxAttempts: ${e.message}", e)
            if (attempt < maxAttempts) {
                delay(500L * attempt)
            }
        }
    }
    throw checkNotNull(lastError)
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
): Job? {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return null
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return null
    }

    val activityContext = context.findActivity() ?: context

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    // One option per request for responsive Credential Manager sheet on API 36
    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    return scope.launch {
        try {
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                signInWithCredentialRetrying(authCredential)
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            // Normal scope cancellation
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
): Job? {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        val msg = "خطأ في إعدادات Google (default_web_client_id): ${e.message}"
        Log.w("Auth", "[Step 0] Missing default_web_client_id", e)
        showAuthToast(context, msg)
        onAuthError(msg)
        return null
    }

    val activityContext = context.findActivity() ?: context
    Log.d("Auth", "[Step 1] Starting Google Sign-In with CredentialManager (activity=${activityContext.javaClass.simpleName})")

    // Interactive Google Sign-In option in its own single-option request
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    return scope.launch {
        // Step 1: Select Google Account via CredentialManager
        val result: GetCredentialResponse = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: NoCredentialException) {
            val detail = "[Step 1 - NoCredentialException] type=${e.type} message=${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "لا يوجد حساب Google مسجل على الجهاز (${e.type}). أضف حساب Google من الإعدادات."
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
            return@launch
        } catch (e: GetCredentialCancellationException) {
            val detail = "[Step 1 - GetCredentialCancellationException] type=${e.type} message=${e.message}"
            Log.w("Auth", detail, e)
            onAuthCancelled()
            val userMsg = "تم إلغاء اختيار الحساب (${e.type}: ${e.errorMessage ?: e.message})"
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
            return@launch
        } catch (e: GetCredentialException) {
            val detail = "[Step 1 - GetCredentialException] class=${e.javaClass.simpleName} type=${e.type} message=${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "خطأ في اختيار حساب Google [${e.type}]: ${e.errorMessage ?: e.message}"
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
            return@launch
        } catch (e: CancellationException) {
            Log.w("Auth", "[Step 1 - Coroutine Cancelled] ${e.javaClass.simpleName}: ${e.message}", e)
            onAuthCancelled()
            return@launch
        } catch (e: Exception) {
            val detail = "[Step 1 - Unexpected Exception] ${e.javaClass.simpleName}: ${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "خطأ غير متوقع عند فتح حسابات Google: ${e.javaClass.simpleName} - ${e.message}"
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
            return@launch
        }

        // Step 2: Extract Google ID Token from Credential
        val googleIdToken: String = try {
            val credential = result.credential
            Log.d("Auth", "[Step 2] Received credential type=${credential.type}")
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val tokenCred = GoogleIdTokenCredential.createFrom(credential.data)
                Log.d("Auth", "[Step 2] Extracted Google ID token for id=${tokenCred.id}, tokenLength=${tokenCred.idToken.length}")
                tokenCred.idToken
            } else {
                val msg = "نوع بيانات الاعتماد غير متوقع: ${credential.type}"
                Log.w("Auth", "[Step 2] $msg")
                showAuthToast(context, msg)
                onAuthError(msg)
                return@launch
            }
        } catch (e: Exception) {
            val detail = "[Step 2 - Token Extraction Failed] ${e.javaClass.simpleName}: ${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "تعذر استخراج رمز الدخول من حساب Google: ${e.javaClass.simpleName} - ${e.message}"
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
            return@launch
        }

        // Step 3: Authenticate with Firebase Auth using Google ID Token
        try {
            Log.d("Auth", "[Step 3] Calling Firebase.auth.signInWithCredential...")
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            signInWithCredentialRetrying(authCredential)
            val signedInUser = Firebase.auth.currentUser
            Log.d("Auth", "[Step 3] Firebase signInWithCredential SUCCESS! uid=${signedInUser?.uid}, email=${signedInUser?.email}")
            if (signedInUser == null) {
                val msg = "نجحت المصادقة ولكن Firebase.auth.currentUser يساوي null"
                Log.w("Auth", "[Step 3] $msg")
                showAuthToast(context, msg)
                onAuthError(msg)
                return@launch
            }
            onAuthSuccess()
        } catch (e: TimeoutCancellationException) {
            val detail = "[Step 3 - Timeout] ${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "انتهت مهلة الاتصال بخدمة Firebase Auth (Timeout). تأكد من اتصال الإنترنت."
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
        } catch (e: FirebaseAuthException) {
            val detail = "[Step 3 - FirebaseAuthException] errorCode=${e.errorCode} message=${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "رفض Firebase تسجيل الدخول [${e.errorCode}]: ${e.localizedMessage ?: e.message}"
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
        } catch (e: FirebaseNetworkException) {
            val detail = "[Step 3 - FirebaseNetworkException] message=${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "خطأ شبكة أثناء الاتصال بـ Firebase Auth: ${e.localizedMessage ?: e.message}"
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
        } catch (e: FirebaseException) {
            val detail = "[Step 3 - FirebaseException] ${e.javaClass.simpleName}: ${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "خطأ Firebase أثناء تسجيل الدخول: ${e.javaClass.simpleName} - ${e.message}"
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
        } catch (e: CancellationException) {
            Log.d("Auth", "[Step 3] Scope cancelled after sign-in transition: ${e.message}")
            if (Firebase.auth.currentUser != null) {
                onAuthSuccess()
            } else {
                onAuthCancelled()
            }
        } catch (e: Exception) {
            val detail = "[Step 3 - Unexpected Exception] ${e.javaClass.simpleName}: ${e.message}"
            Log.w("Auth", detail, e)
            val userMsg = "تعذر تسجيل الدخول في Firebase: ${e.javaClass.simpleName} - ${e.message}"
            showAuthToast(context, userMsg)
            onAuthError(userMsg)
        }
    }
}

fun signOut(
    context: Context,
    credentialManager: CredentialManager,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    scope.launch {
        withContext(NonCancellable) {
            Log.d("Auth", "[SignOut] Starting full sign-out and state cleanup...")
            try {
                // 1. Clear CredentialManager cached Google credential state so stale/remembered token isn't reused blindly
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                Log.d("Auth", "[SignOut] CredentialManager state cleared successfully.")
            } catch (e: CancellationException) {
                // Ignore composition scope cancellation during sign-out transition
            } catch (e: Exception) {
                Log.w("Auth", "[SignOut] Could not clear credential state: ${e.javaClass.simpleName} - ${e.message}", e)
            }

            try {
                // 2. Sign out from Firebase Auth
                Firebase.auth.signOut()
                Log.d("Auth", "[SignOut] Firebase.auth.signOut() completed. currentUser=${Firebase.auth.currentUser}")
            } catch (e: Exception) {
                Log.w("Auth", "[SignOut] Error calling Firebase.auth.signOut(): ${e.message}", e)
            }

            withContext(Dispatchers.Main.immediate) {
                try {
                    // 3. Clear Activity ViewModelStore so no stale KamViewModel or old Firestore listeners survive across sessions
                    (context.findActivity() as? ComponentActivity)?.viewModelStore?.clear()
                    Log.d("Auth", "[SignOut] Cleared Activity ViewModelStore.")
                } catch (e: Exception) {
                    Log.w("Auth", "[SignOut] Could not clear ViewModelStore: ${e.message}")
                }
                onSignOutComplete()
            }
        }
    }
}

// Overload preserving backward compatibility
fun signOut(
    credentialManager: CredentialManager,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    externalScope: CoroutineScope? = null
) {
    val context = LocalContext.current
    val internalScope = rememberCoroutineScope()
    val scope = externalScope ?: internalScope
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeSignInJob by remember { mutableStateOf<Job?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Hero Badge
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(26.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "شعار نجم الكارت v1.6",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "نجم الكارت v1.6",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "تسجيل حضور وكروت ونقاط الأطفال بضغطة واحدة وسط الفصل",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                )

                // Highlights Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        FeatureRow(
                            icon = Icons.Default.FlashOn,
                            title = "سرعة الضغط وسط الفصل",
                            subtitle = "٧ كروت معتمدة تحت اسم كل طفل تتحدث فوراً بضغطة واحدة بدون تكرار"
                        )
                        FeatureRow(
                            icon = Icons.Default.Groups,
                            title = "الأمين + ٦ خدام في مجموعة واحدة",
                            subtitle = "بلوك ملوّن لكل خادم (~١٠ أطفال) + قسم بدون بلوك واستيراد ٦٠ اسم دفعة واحدة"
                        )
                        FeatureRow(
                            icon = Icons.Default.CloudOff,
                            title = "يعمل بدون إنترنت مع مزامنة تلقائية",
                            subtitle = "سجّل في وضع الطيران وسيتم حفظ ومزامنة النقاط فور عودة الاتصال"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preview of the 7 Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CardType.entries.take(4).forEach { card ->
                        MiniCardPreviewPill(card = card, modifier = Modifier.weight(1f).padding(horizontal = 3.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CardType.entries.drop(4).forEach { card ->
                        MiniCardPreviewPill(card = card, modifier = Modifier.weight(1f).padding(horizontal = 3.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .testTag("auth_error_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = {
                        activeSignInJob?.cancel()
                        errorMessage = null
                        isLoading = true
                        activeSignInJob = onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "حساب Google",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sign_in_google),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniCardPreviewPill(
    card: CardType,
    modifier: Modifier = Modifier
) {
    val cardColor = KamSemanticColors.cardColor(card)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${card.badgeSymbol} +${card.points}",
                style = MaterialTheme.typography.labelLarge,
                color = cardColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = card.arabicTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
