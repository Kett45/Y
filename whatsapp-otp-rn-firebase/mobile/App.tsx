import React, { useEffect, useState } from "react";
import { ActivityIndicator, StatusBar, StyleSheet, View } from "react-native";
import auth, { FirebaseAuthTypes } from "@react-native-firebase/auth";
import { LanguageCode } from "./src/i18n/translations";
import { initializeFirebaseEmulator } from "./src/services/firebaseAuthService";
import { PhoneInputScreen } from "./src/screens/PhoneInputScreen";
import { HomeScreen } from "./src/screens/HomeScreen";

export default function App(): React.JSX.Element {
  const [initializing, setInitializing] = useState<boolean>(true);
  const [currentUser, setCurrentUser] =
    useState<FirebaseAuthTypes.User | null>(null);
  const [language, setLanguage] = useState<LanguageCode>("ar");
  const [savedPhone, setSavedPhone] = useState<string | null>(null);

  useEffect(() => {
    initializeFirebaseEmulator();

    const unsubscribe = auth().onAuthStateChanged((user) => {
      setCurrentUser(user);
      if (!user) {
        setSavedPhone(null);
      }
      setInitializing(false);
    });

    return unsubscribe;
  }, []);

  const toggleLanguage = () => {
    setLanguage((prev) => (prev === "ar" ? "en" : "ar"));
  };

  if (initializing) {
    return (
      <View style={styles.splashContainer}>
        <StatusBar barStyle="light-content" backgroundColor="#090D16" />
        <ActivityIndicator color="#25D366" size="large" />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <StatusBar barStyle="light-content" backgroundColor="#090D16" />

      {savedPhone ? (
        <HomeScreen
          user={currentUser}
          savedPhone={savedPhone}
          language={language}
          onToggleLanguage={toggleLanguage}
          onEditPhone={() => setSavedPhone(null)}
        />
      ) : (
        <PhoneInputScreen
          language={language}
          onToggleLanguage={toggleLanguage}
          onPhoneSaved={(e164Phone) => setSavedPhone(e164Phone)}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: "#090D16",
  },
  splashContainer: {
    flex: 1,
    backgroundColor: "#090D16",
    alignItems: "center",
    justifyContent: "center",
  },
});
