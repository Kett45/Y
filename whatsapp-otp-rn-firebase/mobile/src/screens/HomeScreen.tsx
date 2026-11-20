import React, { useState } from "react";
import {
  ActivityIndicator,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import auth, { FirebaseAuthTypes } from "@react-native-firebase/auth";
import { LanguageCode, TRANSLATIONS } from "../i18n/translations";

interface HomeScreenProps {
  user: FirebaseAuthTypes.User | null;
  savedPhone: string;
  language: LanguageCode;
  onToggleLanguage: () => void;
  onEditPhone: () => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  user,
  savedPhone,
  language,
  onToggleLanguage,
  onEditPhone,
}) => {
  const t = TRANSLATIONS[language];
  const isRtl = language === "ar";
  const [isSigningOut, setIsSigningOut] = useState(false);

  const handleSignOut = async () => {
    setIsSigningOut(true);
    try {
      await auth().signOut();
    } finally {
      setIsSigningOut(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.container}>
        <View
          style={[
            styles.topBar,
            { flexDirection: isRtl ? "row-reverse" : "row" },
          ]}
        >
          <View style={styles.statusPill}>
            <View style={styles.onlineDot} />
            <Text style={styles.statusText}>{t.appName}</Text>
          </View>

          <TouchableOpacity
            onPress={onToggleLanguage}
            style={styles.langToggleBtn}
          >
            <Text style={styles.langToggleText}>{t.languageSwitchLabel}</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.card}>
          <View style={styles.checkBadge}>
            <Text style={styles.checkIcon}>✓</Text>
          </View>

          <Text style={styles.title}>{t.welcomeTitle}</Text>
          <Text style={styles.subtitle}>{t.savedSubtitle}</Text>

          {user && (
            <View style={styles.infoBox}>
              <Text
                style={[
                  styles.infoLabel,
                  { textAlign: isRtl ? "right" : "left" },
                ]}
              >
                {t.uidLabel}
              </Text>
              <Text
                style={[
                  styles.infoValue,
                  { textAlign: isRtl ? "right" : "left" },
                ]}
              >
                {user.uid}
              </Text>
            </View>
          )}

          <View style={styles.infoBox}>
            <Text
              style={[
                styles.infoLabel,
                { textAlign: isRtl ? "right" : "left" },
              ]}
            >
              {t.phoneLabel}
            </Text>
            <Text
              style={[
                styles.infoValueHighlight,
                { textAlign: isRtl ? "right" : "left" },
              ]}
            >
              {savedPhone}
            </Text>
          </View>

          <View style={styles.infoBox}>
            <Text
              style={[
                styles.infoLabel,
                { textAlign: isRtl ? "right" : "left" },
              ]}
            >
              {t.phoneStatusLabel}
            </Text>
            <Text
              style={[
                styles.infoValueMuted,
                { textAlign: isRtl ? "right" : "left" },
              ]}
            >
              {t.unverifiedStatusValue}
            </Text>
          </View>

          <TouchableOpacity
            style={styles.editPhoneButton}
            onPress={onEditPhone}
            activeOpacity={0.8}
            testID="edit_phone_button"
          >
            <Text style={styles.editPhoneText}>{t.editPhoneButton}</Text>
          </TouchableOpacity>

          {user && (
            <TouchableOpacity
              style={styles.signOutButton}
              onPress={handleSignOut}
              disabled={isSigningOut}
              activeOpacity={0.8}
              testID="sign_out_button"
            >
              {isSigningOut ? (
                <ActivityIndicator color="#F87171" size="small" />
              ) : (
                <Text style={styles.signOutText}>{t.signOutButton}</Text>
              )}
            </TouchableOpacity>
          )}
        </View>
      </View>
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
    justifyContent: "center",
  },
  topBar: {
    position: "absolute",
    top: 16,
    left: 20,
    right: 20,
    alignItems: "center",
    justifyContent: "space-between",
  },
  statusPill: {
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
  onlineDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: "#25D366",
  },
  statusText: {
    color: "#25D366",
    fontSize: 12,
    fontWeight: "700",
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
    alignItems: "center",
  },
  checkBadge: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: "rgba(37, 211, 102, 0.14)",
    borderWidth: 1.5,
    borderColor: "#25D366",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 16,
  },
  checkIcon: {
    color: "#25D366",
    fontSize: 30,
    fontWeight: "700",
  },
  title: {
    color: "#F9FAFB",
    fontSize: 22,
    fontWeight: "700",
    marginBottom: 6,
    textAlign: "center",
  },
  subtitle: {
    color: "#9CA3AF",
    fontSize: 14,
    textAlign: "center",
    lineHeight: 20,
    marginBottom: 24,
  },
  infoBox: {
    width: "100%",
    backgroundColor: "#0B1120",
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#1F2937",
    paddingHorizontal: 16,
    paddingVertical: 12,
    marginBottom: 12,
  },
  infoLabel: {
    color: "#64748B",
    fontSize: 12,
    fontWeight: "600",
    marginBottom: 4,
  },
  infoValue: {
    color: "#E2E8F0",
    fontSize: 15,
    fontWeight: "600",
  },
  infoValueHighlight: {
    color: "#25D366",
    fontSize: 16,
    fontWeight: "700",
  },
  infoValueMuted: {
    color: "#FBBF24",
    fontSize: 14,
    fontWeight: "600",
  },
  editPhoneButton: {
    width: "100%",
    height: 50,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#334155",
    backgroundColor: "#1E293B",
    alignItems: "center",
    justifyContent: "center",
    marginTop: 8,
  },
  editPhoneText: {
    color: "#F8FAFC",
    fontSize: 15,
    fontWeight: "600",
  },
  signOutButton: {
    width: "100%",
    height: 50,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#7F1D1D",
    backgroundColor: "#2A1215",
    alignItems: "center",
    justifyContent: "center",
    marginTop: 12,
  },
  signOutText: {
    color: "#FCA5A5",
    fontSize: 15,
    fontWeight: "700",
  },
});
