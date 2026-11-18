package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.CountryDialCodeOption
import com.example.data.model.PhoneNumberFormatHelper
import com.example.data.model.ServantMemberProfile
import com.example.data.model.Student
import com.example.ui.theme.KamWhatsAppGreen
import com.example.ui.theme.LocalKamExtendedColors
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

private enum class PhoneWizardStep {
    STEP_1_PRIMARY_INPUT,
    STEP_2_CONFIRM_PRIMARY,
    STEP_3_ASK_SECONDARY,
    STEP_3B_SECONDARY_INPUT,
    STEP_3C_CONFIRM_SECONDARY,
    STEP_4_DONE
}

/**
 * Step-by-step phone onboarding & editing wizard (1 question per screen):
 * 1) "رقم الواتساب بتاعك":
 *    - Large button "اختيار رقمي من الموبايل" using Google Phone Number Hint API
 *    - Below it: "أو اكتب الرقم" with country code selector (default Egypt +20) and manual input
 *    - "لاحقًا" button to skip
 * 2) "ده رقمك على واتساب؟":
 *    - Displays the international number (+20...) with "أيوه" and "لأ، رقم تاني"
 * 3) "عندك رقم تاني؟":
 *    - "أيوه" (adds optional second phone number) or "لأ"
 * 4) Final screen:
 *    - "تمام" and displays the saved phone number(s).
 */
@Composable
fun ServantPhoneOnboardingWizardDialog(
    initialPrimaryPhone: String = "",
    initialSecondaryPhone: String = "",
    onSavePhones: (primaryPhone: String, secondaryPhone: String) -> Unit,
    onSkipOrDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by rememberSaveable { mutableStateOf(PhoneWizardStep.STEP_1_PRIMARY_INPUT) }

    var selectedCountry by remember {
        mutableStateOf(PhoneNumberFormatHelper.countryOptions.first())
    }
    var countryMenuExpanded by remember { mutableStateOf(false) }

    var manualPhoneInput by rememberSaveable {
        mutableStateOf(
            if (initialPrimaryPhone.startsWith("+20")) {
                "0" + initialPrimaryPhone.removePrefix("+20")
            } else {
                initialPrimaryPhone
            }
        )
    }
    var confirmedPrimaryPhone by rememberSaveable {
        mutableStateOf(
            if (initialPrimaryPhone.isNotBlank()) {
                PhoneNumberFormatHelper.toInternationalPhone(initialPrimaryPhone)
            } else {
                ""
            }
        )
    }

    var secondaryCountry by remember {
        mutableStateOf(PhoneNumberFormatHelper.countryOptions.first())
    }
    var secondaryCountryMenuExpanded by remember { mutableStateOf(false) }
    var manualSecondaryInput by rememberSaveable {
        mutableStateOf(
            if (initialSecondaryPhone.startsWith("+20")) {
                "0" + initialSecondaryPhone.removePrefix("+20")
            } else {
                initialSecondaryPhone
            }
        )
    }
    var confirmedSecondaryPhone by rememberSaveable {
        mutableStateOf(
            if (initialSecondaryPhone.isNotBlank()) {
                PhoneNumberFormatHelper.toInternationalPhone(initialSecondaryPhone)
            } else {
                ""
            }
        )
    }

    var focusManualFieldTrigger by remember { mutableStateOf(0) }
    val manualFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusManualFieldTrigger) {
        if (focusManualFieldTrigger > 0) {
            runCatching { manualFieldFocusRequester.requestFocus() }
        }
    }

    // Google Phone Number Hint API launcher for Primary Phone
    val primaryHintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val hintedPhone = runCatching {
                Identity.getSignInClient(context).getPhoneNumberFromIntent(result.data)
            }.getOrNull().orEmpty()
            val formatted = PhoneNumberFormatHelper.toInternationalPhone(
                rawInput = hintedPhone,
                defaultDialCode = selectedCountry.dialCode
            )
            if (formatted.isNotBlank()) {
                manualPhoneInput = formatted
                confirmedPrimaryPhone = formatted
                currentStep = PhoneWizardStep.STEP_2_CONFIRM_PRIMARY
            } else {
                focusManualFieldTrigger++
            }
        } else {
            // Silent fallback to manual field without any annoying error message
            focusManualFieldTrigger++
        }
    }

    // Google Phone Number Hint API launcher for Secondary Phone
    val secondaryHintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val hintedPhone = runCatching {
                Identity.getSignInClient(context).getPhoneNumberFromIntent(result.data)
            }.getOrNull().orEmpty()
            val formatted = PhoneNumberFormatHelper.toInternationalPhone(
                rawInput = hintedPhone,
                defaultDialCode = secondaryCountry.dialCode
            )
            if (formatted.isNotBlank()) {
                manualSecondaryInput = formatted
                confirmedSecondaryPhone = formatted
                currentStep = PhoneWizardStep.STEP_3C_CONFIRM_SECONDARY
            } else {
                focusManualFieldTrigger++
            }
        } else {
            focusManualFieldTrigger++
        }
    }

    fun launchPhoneHint(isSecondary: Boolean) {
        val request = GetPhoneNumberHintIntentRequest.builder().build()
        runCatching {
            Identity.getSignInClient(context)
                .getPhoneNumberHintIntent(request)
                .addOnSuccessListener { pendingIntent ->
                    runCatching {
                        val senderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        if (isSecondary) {
                            secondaryHintLauncher.launch(senderRequest)
                        } else {
                            primaryHintLauncher.launch(senderRequest)
                        }
                    }.onFailure {
                        focusManualFieldTrigger++
                    }
                }
                .addOnFailureListener {
                    // Silent fallback to manual field without any error message
                    focusManualFieldTrigger++
                }
        }.onFailure {
            focusManualFieldTrigger++
        }
    }

    val stepTitle = when (currentStep) {
        PhoneWizardStep.STEP_1_PRIMARY_INPUT -> "رقم الواتساب بتاعك"
        PhoneWizardStep.STEP_2_CONFIRM_PRIMARY -> "ده رقمك على واتساب؟"
        PhoneWizardStep.STEP_3_ASK_SECONDARY -> "عندك رقم تاني؟"
        PhoneWizardStep.STEP_3B_SECONDARY_INPUT -> "الرقم التاني (اختياري)"
        PhoneWizardStep.STEP_3C_CONFIRM_SECONDARY -> "ده رقمك التاني؟"
        PhoneWizardStep.STEP_4_DONE -> "تمام"
    }

    val extColors = LocalKamExtendedColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                enabled = true,
                onClick = {
                    if (currentStep == PhoneWizardStep.STEP_4_DONE) {
                        onSkipOrDismiss()
                    }
                }
            )
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("servant_phone_onboarding_wizard_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, extColors.hairline),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (extColors.isDark) 0.dp else 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                when (currentStep) {
                    PhoneWizardStep.STEP_1_PRIMARY_INPUT -> {
                        PhoneInputStepContent(
                            contextHintButtonAction = { launchPhoneHint(isSecondary = false) },
                        selectedCountry = selectedCountry,
                        countryMenuExpanded = countryMenuExpanded,
                        onCountryMenuExpandedChange = { countryMenuExpanded = it },
                        onSelectCountry = {
                            selectedCountry = it
                            countryMenuExpanded = false
                        },
                        manualInput = manualPhoneInput,
                        onManualInputChange = { manualPhoneInput = it },
                        manualFieldFocusRequester = manualFieldFocusRequester,
                        hintButtonTag = "phone_hint_button",
                        manualInputTag = "manual_phone_input"
                    )
                }

                PhoneWizardStep.STEP_2_CONFIRM_PRIMARY -> {
                    PhoneConfirmationStepContent(
                        formattedPhone = confirmedPrimaryPhone,
                        subtitle = "هيظهر فوق البلوك بتاعك لأعضاء الجروب بس"
                    )
                }

                PhoneWizardStep.STEP_3_ASK_SECONDARY -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "الرقم الأساسي",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LtrPhoneText(
                                    phone = confirmedPrimaryPhone,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "تحب تضيف رقم موبايل تاني جنب الرقم الأول؟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                PhoneWizardStep.STEP_3B_SECONDARY_INPUT -> {
                    PhoneInputStepContent(
                        contextHintButtonAction = { launchPhoneHint(isSecondary = true) },
                        selectedCountry = secondaryCountry,
                        countryMenuExpanded = secondaryCountryMenuExpanded,
                        onCountryMenuExpandedChange = { secondaryCountryMenuExpanded = it },
                        onSelectCountry = {
                            secondaryCountry = it
                            secondaryCountryMenuExpanded = false
                        },
                        manualInput = manualSecondaryInput,
                        onManualInputChange = { manualSecondaryInput = it },
                        manualFieldFocusRequester = manualFieldFocusRequester,
                        hintButtonTag = "secondary_phone_hint_button",
                        manualInputTag = "secondary_manual_phone_input"
                    )
                }

                PhoneWizardStep.STEP_3C_CONFIRM_SECONDARY -> {
                    PhoneConfirmationStepContent(
                        formattedPhone = confirmedSecondaryPhone,
                        subtitle = "هيتحفظ كرقم تاني فوق البلوك بتاعك"
                    )
                }

                PhoneWizardStep.STEP_4_DONE -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "تم الحفظ",
                            tint = KamWhatsAppGreen,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "اتحفظت أرقامك بنجاح:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SavedPhoneBadgeCard(
                            label = "الرقم الأول",
                            phone = confirmedPrimaryPhone
                        )
                        if (confirmedSecondaryPhone.isNotBlank() && confirmedSecondaryPhone != confirmedPrimaryPhone) {
                            SavedPhoneBadgeCard(
                                label = "الرقم التاني",
                                phone = confirmedSecondaryPhone
                            )
                        }
                    }
                }
                }

                when (currentStep) {
                    PhoneWizardStep.STEP_1_PRIMARY_INPUT -> {
                    val canProceed = PhoneNumberFormatHelper.isValidPhoneInput(
                        rawInput = manualPhoneInput,
                        defaultDialCode = selectedCountry.dialCode
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intl = PhoneNumberFormatHelper.toInternationalPhone(
                                    rawInput = manualPhoneInput,
                                    defaultDialCode = selectedCountry.dialCode
                                )
                                if (intl.isNotBlank()) {
                                    confirmedPrimaryPhone = intl
                                    currentStep = PhoneWizardStep.STEP_2_CONFIRM_PRIMARY
                                }
                            },
                            enabled = canProceed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("phone_next_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("متابعة", fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = onSkipOrDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("phone_skip_button")
                        ) {
                            Text("لاحقًا", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                PhoneWizardStep.STEP_2_CONFIRM_PRIMARY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                currentStep = PhoneWizardStep.STEP_1_PRIMARY_INPUT
                                focusManualFieldTrigger++
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("confirm_primary_no_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("لأ، رقم تاني", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                currentStep = PhoneWizardStep.STEP_3_ASK_SECONDARY
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("confirm_primary_yes_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KamWhatsAppGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Text("أيوه", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                PhoneWizardStep.STEP_3_ASK_SECONDARY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                confirmedSecondaryPhone = ""
                                onSavePhones(confirmedPrimaryPhone, "")
                                currentStep = PhoneWizardStep.STEP_4_DONE
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ask_secondary_no_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("لأ", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                currentStep = PhoneWizardStep.STEP_3B_SECONDARY_INPUT
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ask_secondary_yes_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("أيوه", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                PhoneWizardStep.STEP_3B_SECONDARY_INPUT -> {
                    val canProceedSecondary = PhoneNumberFormatHelper.isValidPhoneInput(
                        rawInput = manualSecondaryInput,
                        defaultDialCode = secondaryCountry.dialCode
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intl = PhoneNumberFormatHelper.toInternationalPhone(
                                    rawInput = manualSecondaryInput,
                                    defaultDialCode = secondaryCountry.dialCode
                                )
                                if (intl.isNotBlank()) {
                                    confirmedSecondaryPhone = intl
                                    currentStep = PhoneWizardStep.STEP_3C_CONFIRM_SECONDARY
                                }
                            },
                            enabled = canProceedSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("secondary_phone_next_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("متابعة", fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                confirmedSecondaryPhone = ""
                                onSavePhones(confirmedPrimaryPhone, "")
                                currentStep = PhoneWizardStep.STEP_4_DONE
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("تخطي الرقم التاني والاكتفاء بالأول")
                        }
                    }
                }

                PhoneWizardStep.STEP_3C_CONFIRM_SECONDARY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                currentStep = PhoneWizardStep.STEP_3B_SECONDARY_INPUT
                                focusManualFieldTrigger++
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("confirm_secondary_no_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("لأ، رقم تاني", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                onSavePhones(confirmedPrimaryPhone, confirmedSecondaryPhone)
                                currentStep = PhoneWizardStep.STEP_4_DONE
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("confirm_secondary_yes_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KamWhatsAppGreen,
                                contentColor = Color.White
                            )
                        ) {
                            Text("أيوه", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                PhoneWizardStep.STEP_4_DONE -> {
                    Button(
                        onClick = onSkipOrDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("phone_wizard_done_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تمام", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PhoneInputStepContent(
    contextHintButtonAction: () -> Unit,
    selectedCountry: CountryDialCodeOption,
    countryMenuExpanded: Boolean,
    onCountryMenuExpandedChange: (Boolean) -> Unit,
    onSelectCountry: (CountryDialCodeOption) -> Unit,
    manualInput: String,
    onManualInputChange: (String) -> Unit,
    manualFieldFocusRequester: FocusRequester,
    hintButtonTag: String,
    manualInputTag: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large Google Phone Number Hint button
        Button(
            onClick = contextHintButtonAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag(hintButtonTag),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KamWhatsAppGreen,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "اختيار رقمي من الموبايل",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "أو اكتب الرقم",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        // Country Code Selector + Manual Phone Input (LTR layout for phone entry)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(
                        onClick = { onCountryMenuExpandedChange(true) },
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("country_code_selector"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${selectedCountry.flagEmoji} ${selectedCountry.dialCode}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DropdownMenu(
                        expanded = countryMenuExpanded,
                        onDismissRequest = { onCountryMenuExpandedChange(false) }
                    ) {
                        PhoneNumberFormatHelper.countryOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text("${option.flagEmoji} ${option.countryNameArabic} (${option.dialCode})")
                                },
                                onClick = { onSelectCountry(option) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = manualInput,
                    onValueChange = onManualInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(manualFieldFocusRequester)
                        .testTag(manualInputTag),
                    placeholder = { Text("01012345678") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        textDirection = TextDirection.Ltr
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun PhoneConfirmationStepContent(
    formattedPhone: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                LtrPhoneText(
                    phone = formattedPhone,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SavedPhoneBadgeCard(
    label: String,
    phone: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LtrPhoneText(
                phone = phone,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun LtrPhoneText(
    phone: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = phone,
            style = style.copy(textDirection = TextDirection.Ltr),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

/**
 * Circular avatar for a Servant (<50KB photo cached locally on disk like children's photos,
 * or first 2 letters of the servant's name if no photo is set).
 */
@Composable
fun ServantAvatarCircle(
    servantUid: String,
    servantName: String,
    groupId: String,
    memberProfile: ServantMemberProfile?,
    accentColor: Color,
    size: Dp = 44.dp,
    isEditable: Boolean = false,
    blockId: String = "",
    onClick: (() -> Unit)? = null
) {
    val cleanServantUid = servantUid.trim()
    val cleanBlockId = blockId.trim()
    val customBlockPhotoRaw = if (cleanBlockId.isNotBlank()) {
        StudentAvatarImageHelper.peekCachedLocalAlbumRaw("servant_block_$cleanBlockId")
    } else {
        ""
    }
    val memberHasOwnPhoto = cleanServantUid.isNotBlank() && (
        memberProfile?.effectiveHasAvatar == true ||
            !memberProfile?.effectiveAvatarBase64.isNullOrBlank() ||
            StudentAvatarImageHelper.peekCachedLocalAlbumRaw("servant_$cleanServantUid").isNotBlank()
        )

    val syntheticStudent = remember(
        cleanServantUid,
        cleanBlockId,
        servantName,
        groupId,
        memberProfile?.effectiveAvatarKey,
        memberProfile?.effectiveHasAvatar,
        memberProfile?.effectiveAvatarBase64,
        customBlockPhotoRaw,
        memberHasOwnPhoto
    ) {
        val useMemberAvatar = cleanServantUid.isNotBlank() && (memberHasOwnPhoto || cleanBlockId.isBlank())
        val resolvedDocId = when {
            useMemberAvatar -> "servant_$cleanServantUid"
            cleanBlockId.isNotBlank() -> "servant_block_$cleanBlockId"
            cleanServantUid.isNotBlank() -> "servant_$cleanServantUid"
            else -> "servant_${servantName.hashCode()}"
        }
        val resolvedBase64 = if (useMemberAvatar) {
            memberProfile?.effectiveAvatarBase64.orEmpty()
        } else {
            customBlockPhotoRaw
        }
        val resolvedHasAvatar = if (useMemberAvatar) {
            memberProfile?.effectiveHasAvatar == true || resolvedBase64.isNotBlank()
        } else {
            cleanBlockId.isNotBlank() || resolvedBase64.isNotBlank()
        }
        val resolvedHash = if (useMemberAvatar) {
            memberProfile?.effectiveAvatarKey.orEmpty()
        } else if (customBlockPhotoRaw.isNotBlank()) {
            Student.computeAvatarHash(customBlockPhotoRaw)
        } else {
            ""
        }
        Student(
            id = resolvedDocId,
            groupId = groupId,
            name = servantName.ifBlank { "خادم" },
            avatarInitials = ArabicInitialsHelper.extractInitials(servantName.ifBlank { "خادم" }),
            avatarBase64 = resolvedBase64,
            hasAvatar = resolvedHasAvatar,
            avatarHash = resolvedHash
        )
    }

    Box(
        contentAlignment = Alignment.BottomEnd
    ) {
        StudentAvatarCircle(
            student = syntheticStudent,
            blockAccent = accentColor,
            size = size,
            onClick = onClick ?: {},
            testTag = "servant_avatar_${cleanServantUid.ifBlank { cleanBlockId.ifBlank { "default" } }}",
            blockLabel = "خادم البلوك: ${servantName.ifBlank { "خادم" }}"
        )
        if (isEditable && onClick != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "تغيير صورة الخادم",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * Photo picker dialog for the Servant's profile photo or a specific Block's custom photo (<50KB compressed and cached locally on disk).
 */
@Composable
fun ServantPhotoPickerDialog(
    servantUid: String,
    servantName: String,
    groupId: String,
    memberProfile: ServantMemberProfile?,
    accentColor: Color,
    customAvatarDocId: String = "",
    onDismiss: () -> Unit,
    onSaveBase64: (String) -> Unit
) {
    val cleanCustomId = customAvatarDocId.trim()
    val customRaw = if (cleanCustomId.isNotBlank()) {
        StudentAvatarImageHelper.peekCachedLocalAlbumRaw(cleanCustomId)
    } else {
        ""
    }
    val syntheticStudent = remember(
        servantUid,
        cleanCustomId,
        servantName,
        groupId,
        memberProfile?.effectiveAvatarKey,
        memberProfile?.effectiveHasAvatar,
        memberProfile?.effectiveAvatarBase64,
        customRaw
    ) {
        val docId = cleanCustomId.ifBlank { "servant_${servantUid.ifBlank { "me" }}" }
        val base64 = if (cleanCustomId.isNotBlank()) {
            customRaw
        } else {
            memberProfile?.effectiveAvatarBase64.orEmpty()
        }
        val hasAv = if (cleanCustomId.isNotBlank()) {
            customRaw.isNotBlank() || true
        } else {
            memberProfile?.effectiveHasAvatar == true
        }
        val hash = if (cleanCustomId.isNotBlank()) {
            if (customRaw.isNotBlank()) Student.computeAvatarHash(customRaw) else ""
        } else {
            memberProfile?.effectiveAvatarKey.orEmpty()
        }
        Student(
            id = docId,
            groupId = groupId,
            name = servantName.ifBlank { "خادم" },
            avatarInitials = ArabicInitialsHelper.extractInitials(servantName.ifBlank { "خادم" }),
            avatarBase64 = base64,
            hasAvatar = hasAv,
            avatarHash = hash
        )
    }

    StudentPhotoPickerDialog(
        student = syntheticStudent,
        blockAccent = accentColor,
        isAdmin = true,
        onDismiss = onDismiss,
        onSaveAvatarBase64 = onSaveBase64
    )
}

/**
 * WhatsApp-style contact rows rendered inside `ServantBlockHeaderCard`:
 * - Phone numbers are always visible to fellow servants in the group.
 * - If no phone numbers are saved: shows calm `"لم يضف رقمه"` (and a quick `"إضافة رقمي"` button for the block owner).
 * - If 1 or 2 phone numbers are saved: renders each number with dedicated `"واتساب"` and `"اتصال"` buttons.
 */
@Composable
fun ServantBlockContactRows(
    blockId: String,
    servantProfile: ServantMemberProfile?,
    isOwnBlock: Boolean,
    blockAccent: Color,
    onAddOwnPhoneClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val phones = servantProfile?.nonEmptyPhones.orEmpty()
    val footerShape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
    HorizontalDivider(color = blockAccent.copy(alpha = 0.22f))

    if (phones.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(blockAccent.copy(alpha = 0.08f), footerShape)
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .testTag("servant_no_phone_row_$blockId"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "لم يضف رقمه",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isOwnBlock) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = KamWhatsAppGreen.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, KamWhatsAppGreen.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable(onClick = onAddOwnPhoneClick)
                        .testTag("servant_add_own_phone_chip_$blockId")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = KamWhatsAppGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "إضافة رقمي",
                            style = MaterialTheme.typography.labelSmall,
                            color = KamWhatsAppGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(blockAccent.copy(alpha = 0.08f), footerShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            phones.forEachIndexed { index, phone ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("servant_phone_row_${blockId}_$index"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = blockAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        LtrPhoneText(
                            phone = phone,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // WhatsApp Button (#25D366)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KamWhatsAppGreen,
                            modifier = Modifier
                                .clickable { openWhatsAppForPhone(context, phone) }
                                .testTag("servant_whatsapp_button_${blockId}_$index")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "واتساب",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Direct Call Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clickable { openDialerForPhone(context, phone) }
                                .testTag("servant_call_button_${blockId}_$index")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "اتصال",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings screen top card for managing the Servant's own profile:
 * - Avatar (<50KB compressed & cached locally)
 * - Editable Display Name (updates across app & own blocks)
 * - Gmail account display
 * - Primary & Secondary Phone numbers (opens the step-by-step wizard)
 * - "حذف الرقم" button
 */
@Composable
fun ServantProfileSettingsCard(
    servantUid: String,
    servantName: String,
    email: String = "",
    groupId: String,
    memberProfile: ServantMemberProfile?,
    primaryPhone: String,
    secondaryPhone: String,
    accentColor: Color,
    onOpenPhoneWizard: () -> Unit,
    onOpenAvatarPicker: () -> Unit,
    onEditDisplayName: (String) -> Unit = {},
    onDeletePhones: () -> Unit
) {
    val hasAnyPhone = primaryPhone.isNotBlank() || secondaryPhone.isNotBlank()
    var showEditNameDialog by rememberSaveable { mutableStateOf(false) }
    val extColors = LocalKamExtendedColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, extColors.hairline, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("servant_profile_settings_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row: Servant Avatar + Editable Name + Gmail + Change Photo Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ServantAvatarCircle(
                    servantUid = servantUid,
                    servantName = servantName,
                    groupId = groupId,
                    memberProfile = memberProfile,
                    accentColor = accentColor,
                    size = 56.dp,
                    isEditable = true,
                    onClick = onOpenAvatarPicker
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEditNameDialog = true }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                            .testTag("settings_edit_own_name_button")
                    ) {
                        Text(
                            text = servantName.ifBlank { "خادم الفصل" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل الاسم",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (email.isNotBlank()) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "اضغط على الاسم لتعديله أو على الصورة لتغييرها",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenAvatarPicker,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("settings_change_servant_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("صورتي")
            }
        }

        HorizontalDivider()

        // Saved Phone Numbers Summary
        if (!hasAnyPhone) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "لم تضف رقم الموبايل / الواتساب بعد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (primaryPhone.isNotBlank()) {
                SavedPhoneBadgeCard(
                    label = "الرقم الأساسي",
                    phone = primaryPhone
                )
            }
            if (secondaryPhone.isNotBlank() && secondaryPhone != primaryPhone) {
                SavedPhoneBadgeCard(
                    label = "الرقم التاني",
                    phone = secondaryPhone
                )
            }
        }

        // Action Buttons: Add/Edit Phone + Delete Phone
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenPhoneWizard,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("settings_edit_phone_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KamWhatsAppGreen,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (hasAnyPhone) Icons.Default.Edit else Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasAnyPhone) "تعديل الرقم" else "إضافة رقم الواتساب",
                    fontWeight = FontWeight.Bold
                )
            }

            if (hasAnyPhone) {
                OutlinedButton(
                    onClick = onDeletePhones,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("settings_delete_phone_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف الرقم",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف الرقم", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showEditNameDialog) {
        var nameDraft by rememberSaveable(servantName) { mutableStateOf(servantName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = {
                Text(
                    text = "تعديل اسمك في التطبيق",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "سيتم تحديث اسمك في حسابك الشخصي وفي البلوكات والمحادثات الخاصة بك.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.take(80) },
                        label = { Text("الاسم") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_edit_own_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = nameDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onEditDisplayName(trimmed)
                            showEditNameDialog = false
                        }
                    },
                    enabled = nameDraft.trim().isNotBlank(),
                    modifier = Modifier.testTag("dialog_save_own_name_button")
                ) {
                    Text("حفظ الاسم")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

/**
 * Opens WhatsApp chat (`https://wa.me/<digits>`) for the given international phone number.
 */
fun openWhatsAppForPhone(context: Context, phone: String) {
    val digits = PhoneNumberFormatHelper.toWhatsAppDigits(phone)
    if (digits.isBlank()) return
    val uri = Uri.parse("https://wa.me/$digits")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/**
 * Opens the phone dialer (`tel:<phone>`) for the given international phone number.
 */
fun openDialerForPhone(context: Context, phone: String) {
    val clean = PhoneNumberFormatHelper.toInternationalPhone(phone)
    if (clean.isBlank()) return
    val uri = Uri.parse("tel:$clean")
    val intent = Intent(Intent.ACTION_DIAL, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
