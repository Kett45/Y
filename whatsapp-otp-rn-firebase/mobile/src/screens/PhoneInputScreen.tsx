import React, { useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import {
  COUNTRIES,
  CountryOption,
  DEFAULT_COUNTRY,
  normalizeToE164,
} from "../constants/countries";
import { LanguageCode, TRANSLATIONS } from "../i18n/translations";
import { saveUserPhoneNumber } from "../services/firebaseAuthService";

interface PhoneInputScreenProps {
  language: LanguageCode;
  onToggleLanguage: () => void;
  onPhoneSaved: (e164Phone: string) => void;
}

export const PhoneInputScreen: React.FC<PhoneInputScreenProps> = ({
  language,
  onToggleLanguage,
  onPhoneSaved,
}) => {
  const t = TRANSLATIONS[language];
  const isRtl = language === "ar";

  const [selectedCountry, setSelectedCountry] =
    useState<CountryOption>(DEFAULT_COUNTRY);
  const [localPhone, setLocalPhone] = useState<string>("");
  const [isCountryModalOpen, setIsCountryModalOpen] = useState<boolean>(false);
  const [countrySearch, setCountrySearch] = useState<string>("");
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const filteredCountries = useMemo(() => {
    const q = countrySearch.trim().toLowerCase();
    if (!q) {
      return COUNTRIES;
    }
    return COUNTRIES.filter(
      (c) =>
        c.nameEn.toLowerCase().includes(q) ||
        c.nameAr.includes(q) ||
        c.dialCode.includes(q) ||
        c.code.toLowerCase().includes(q)
    );
  }, [countrySearch]);

  const { e164, isValid } = useMemo(
    () => normalizeToE164(selectedCountry, localPhone),
    [selectedCountry, localPhone]
  );

  const handleContinue = async () => {
    setErrorMessage(null);

    if (!isValid) {
      setErrorMessage(t.errors.invalidPhone);
      return;
    }

    setIsLoading(true);
    try {
      const saved = await saveUserPhoneNumber(e164, language);
      onPhoneSaved(saved.phoneNumber);
    } catch (err) {
      if (err instanceof Error && err.message) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage(t.errors.saveFailed);
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        {/* Top Bar with App Title & Arabic/English Toggle */}
        <View
          style={[
            styles.topBar,
            { flexDirection: isRtl ? "row-reverse" : "row" },
          ]}
        >
          <View style={styles.brandBadge}>
            <View style={styles.accentDot} />
            <Text style={styles.brandText}>{t.appName}</Text>
          </View>

          <TouchableOpacity
            onPress={onToggleLanguage}
            style={styles.langToggleBtn}
            activeOpacity={0.75}
            testID="language_toggle_button"
          >
            <Text style={styles.langToggleText}>{t.languageSwitchLabel}</Text>
          </TouchableOpacity>
        </View>

        {/* Main Card */}
        <View style={styles.card}>
          <Text
            style={[
              styles.title,
              { textAlign: isRtl ? "right" : "left" },
            ]}
          >
            {t.phoneTitle}
          </Text>
          <Text
            style={[
              styles.subtitle,
              { textAlign: isRtl ? "right" : "left" },
            ]}
          >
            {t.phoneSubtitle}
          </Text>

          <Text
            style={[
              styles.inputLabel,
              { textAlign: isRtl ? "right" : "left" },
            ]}
          >
            {t.phoneInputLabel}
          </Text>

          {/* Phone Input Row (Always LTR order for dialCode + digits so +20 stays on the left of digits) */}
          <View style={styles.phoneInputRow}>
            <TouchableOpacity
              style={styles.countryPickerBtn}
              onPress={() => setIsCountryModalOpen(true)}
              disabled={isLoading}
              activeOpacity={0.75}
              testID="country_picker_button"
            >
              <Text style={styles.flagText}>{selectedCountry.flag}</Text>
              <Text style={styles.dialCodeText}>
                {selectedCountry.dialCode}
              </Text>
              <Text style={styles.chevronText}>▾</Text>
            </TouchableOpacity>

            <TextInput
              style={styles.phoneInput}
              value={localPhone}
              onChangeText={(val) => {
                setLocalPhone(val.replace(/[^\d\s]/g, ""));
                if (errorMessage) {
                  setErrorMessage(null);
                }
              }}
              placeholder={selectedCountry.placeholder}
              placeholderTextColor="#64748B"
              keyboardType="phone-pad"
              autoFocus
              editable={!isLoading}
              maxLength={15}
              returnKeyType="done"
              onSubmitEditing={handleContinue}
              testID="phone_number_input"
            />
          </View>

          {/* Live Normalized E.164 Preview */}
          {localPhone.trim().length > 0 && (
            <Text
              style={[
                styles.e164Preview,
                { textAlign: isRtl ? "right" : "left" },
              ]}
            >
              E.164: {e164}
            </Text>
          )}

          {/* Error Banner */}
          {errorMessage && (
            <View
              style={[
                styles.errorBanner,
                { flexDirection: isRtl ? "row-reverse" : "row" },
              ]}
              testID="phone_error_banner"
            >
              <Text
                style={[
                  styles.errorText,
                  { textAlign: isRtl ? "right" : "left" },
                ]}
              >
                {errorMessage}
              </Text>
            </View>
          )}

          {/* Primary Continue CTA Button */}
          <TouchableOpacity
            style={[
              styles.sendButton,
              (!isValid || isLoading) && styles.sendButtonDisabled,
            ]}
            onPress={handleContinue}
            disabled={isLoading}
            activeOpacity={0.85}
            testID="continue_phone_button"
          >
            {isLoading ? (
              <View style={styles.loadingRow}>
                <ActivityIndicator color="#06120B" size="small" />
                <Text style={styles.sendButtonText}>{t.savingButton}</Text>
              </View>
            ) : (
              <Text style={styles.sendButtonText}>{t.continueButton}</Text>
            )}
          </TouchableOpacity>
        </View>

        {/* Country Picker Modal */}
        <Modal
          visible={isCountryModalOpen}
          animationType="slide"
          transparent
          onRequestClose={() => setIsCountryModalOpen(false)}
        >
          <View style={styles.modalBackdrop}>
            <View style={styles.modalSheet}>
              <View
                style={[
                  styles.modalHeader,
                  { flexDirection: isRtl ? "row-reverse" : "row" },
                ]}
              >
                <Text style={styles.modalTitle}>{t.countryPickerTitle}</Text>
                <Pressable
                  onPress={() => {
                    setIsCountryModalOpen(false);
                    setCountrySearch("");
                  }}
                  style={styles.modalCloseBtn}
                >
                  <Text style={styles.modalCloseText}>✕</Text>
                </Pressable>
              </View>

              <TextInput
                style={[
                  styles.searchInput,
                  { textAlign: isRtl ? "right" : "left" },
                ]}
                value={countrySearch}
                onChangeText={setCountrySearch}
                placeholder={t.countrySearchPlaceholder}
                placeholderTextColor="#64748B"
                autoCorrect={false}
              />

              <FlatList
                data={filteredCountries}
                keyExtractor={(item) => item.code}
                keyboardShouldPersistTaps="handled"
                renderItem={({ item }) => {
                  const isSelected = item.code === selectedCountry.code;
                  return (
                    <TouchableOpacity
                      style={[
                        styles.countryRow,
                        isSelected && styles.countryRowSelected,
                        { flexDirection: isRtl ? "row-reverse" : "row" },
                      ]}
                      onPress={() => {
                        setSelectedCountry(item);
                        setIsCountryModalOpen(false);
                        setCountrySearch("");
                        setErrorMessage(null);
                      }}
                    >
                      <View
                        style={[
                          styles.countryRowInfo,
                          { flexDirection: isRtl ? "row-reverse" : "row" },
                        ]}
                      >
                        <Text style={styles.countryFlag}>{item.flag}</Text>
                        <Text style={styles.countryName}>
                          {isRtl ? item.nameAr : item.nameEn}
                        </Text>
                      </View>
                      <Text style={styles.countryDial}>{item.dialCode}</Text>
                    </TouchableOpacity>
                  );
                }}
              />
            </View>
          </View>
        </Modal>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: "#090D16",
  },
  container: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 16,
    justifyContent: "center",
  },
  topBar: {
    position: "absolute",
    top: 16,
    left: 20,
    right: 20,
    alignItems: "center",
    justifyContent: "space-between",
    zIndex: 10,
  },
  brandBadge: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#111827",
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: "#1F2937",
    gap: 8,
  },
  accentDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: "#25D366",
  },
  brandText: {
    color: "#E5E7EB",
    fontSize: 13,
    fontWeight: "600",
  },
  langToggleBtn: {
    backgroundColor: "#1E293B",
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: "#334155",
  },
  langToggleText: {
    color: "#F8FAFC",
    fontSize: 13,
    fontWeight: "600",
  },
  card: {
    backgroundColor: "#111827",
    borderRadius: 20,
    padding: 24,
    borderWidth: 1,
    borderColor: "#1F2937",
  },
  title: {
    color: "#F9FAFB",
    fontSize: 24,
    fontWeight: "700",
    marginBottom: 8,
  },
  subtitle: {
    color: "#9CA3AF",
    fontSize: 14,
    lineHeight: 21,
    marginBottom: 24,
  },
  inputLabel: {
    color: "#CBD5E1",
    fontSize: 13,
    fontWeight: "600",
    marginBottom: 10,
  },
  phoneInputRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  countryPickerBtn: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#1E293B",
    borderWidth: 1,
    borderColor: "#334155",
    borderRadius: 12,
    paddingHorizontal: 12,
    height: 54,
    gap: 6,
  },
  flagText: {
    fontSize: 20,
  },
  dialCodeText: {
    color: "#F8FAFC",
    fontSize: 16,
    fontWeight: "700",
  },
  chevronText: {
    color: "#94A3B8",
    fontSize: 12,
    marginLeft: 2,
  },
  phoneInput: {
    flex: 1,
    height: 54,
    backgroundColor: "#0B1120",
    borderWidth: 1,
    borderColor: "#334155",
    borderRadius: 12,
    paddingHorizontal: 16,
    color: "#F9FAFB",
    fontSize: 18,
    fontWeight: "600",
    letterSpacing: 0.8,
    textAlign: "left",
  },
  e164Preview: {
    color: "#25D366",
    fontSize: 12,
    fontWeight: "600",
    marginTop: 8,
  },
  errorBanner: {
    backgroundColor: "#2A1215",
    borderWidth: 1,
    borderColor: "#7F1D1D",
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 11,
    marginTop: 14,
  },
  errorText: {
    color: "#FCA5A5",
    fontSize: 13,
    lineHeight: 19,
    flex: 1,
  },
  sendButton: {
    backgroundColor: "#25D366",
    height: 54,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 20,
  },
  sendButtonDisabled: {
    opacity: 0.55,
  },
  loadingRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  sendButtonText: {
    color: "#051B0E",
    fontSize: 16,
    fontWeight: "700",
  },
  modalBackdrop: {
    flex: 1,
    backgroundColor: "rgba(3, 7, 18, 0.78)",
    justifyContent: "flex-end",
  },
  modalSheet: {
    backgroundColor: "#111827",
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    borderWidth: 1,
    borderColor: "#1F2937",
    maxHeight: "75%",
    paddingHorizontal: 20,
    paddingTop: 18,
    paddingBottom: 28,
  },
  modalHeader: {
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 14,
  },
  modalTitle: {
    color: "#F9FAFB",
    fontSize: 18,
    fontWeight: "700",
  },
  modalCloseBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#1E293B",
    alignItems: "center",
    justifyContent: "center",
  },
  modalCloseText: {
    color: "#CBD5E1",
    fontSize: 16,
    fontWeight: "700",
  },
  searchInput: {
    height: 46,
    backgroundColor: "#0B1120",
    borderWidth: 1,
    borderColor: "#334155",
    borderRadius: 12,
    paddingHorizontal: 14,
    color: "#F8FAFC",
    fontSize: 14,
    marginBottom: 12,
  },
  countryRow: {
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 13,
    paddingHorizontal: 12,
    borderRadius: 12,
    marginBottom: 4,
  },
  countryRowSelected: {
    backgroundColor: "#1E293B",
    borderWidth: 1,
    borderColor: "#25D366",
  },
  countryRowInfo: {
    alignItems: "center",
    gap: 12,
  },
  countryFlag: {
    fontSize: 22,
  },
  countryName: {
    color: "#F1F5F9",
    fontSize: 15,
    fontWeight: "600",
  },
  countryDial: {
    color: "#25D366",
    fontSize: 15,
    fontWeight: "700",
  },
});
