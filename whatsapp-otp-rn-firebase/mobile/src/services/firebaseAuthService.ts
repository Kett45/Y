import { Platform } from "react-native";
import NetInfo from "@react-native-community/netinfo";
import auth from "@react-native-firebase/auth";
import firestore from "@react-native-firebase/firestore";
import { LanguageCode, TRANSLATIONS } from "../i18n/translations";

// Set USE_FIREBASE_EMULATOR = true when testing locally against `firebase emulators:start`
const USE_FIREBASE_EMULATOR = false;
const EMULATOR_HOST = Platform.OS === "android" ? "10.0.2.2" : "127.0.0.1";

let isEmulatorConfigured = false;

export function initializeFirebaseEmulator(): void {
  if (isEmulatorConfigured || !USE_FIREBASE_EMULATOR) {
    return;
  }
  isEmulatorConfigured = true;
  auth().useEmulator(`http://${EMULATOR_HOST}:9099`);
  firestore().useEmulator(EMULATOR_HOST, 8080);
}

/**
 * Saves the normalized E.164 phone number directly to the signed-in user's
 * Firestore document (`users/{uid}`) with `phoneVerified: false`.
 */
export async function saveUserPhoneNumber(
  e164Phone: string,
  lang: LanguageCode
): Promise<{ uid: string; phoneNumber: string; phoneVerified: false }> {
  const strings = TRANSLATIONS[lang].errors;

  const netState = await NetInfo.fetch();
  if (netState.isConnected === false) {
    throw new Error(strings.noInternet);
  }

  const currentUser = auth().currentUser;
  if (!currentUser) {
    throw new Error(strings.notSignedIn);
  }

  try {
    await firestore()
      .collection("users")
      .doc(currentUser.uid)
      .set(
        {
          uid: currentUser.uid,
          phoneNumber: e164Phone,
          phoneVerified: false,
          updatedAt: firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

    return {
      uid: currentUser.uid,
      phoneNumber: e164Phone,
      phoneVerified: false,
    };
  } catch (err: unknown) {
    const firebaseErr = err as { code?: string; message?: string };
    const code = (firebaseErr?.code ?? "").toLowerCase();
    if (code.includes("unavailable") || code.includes("network")) {
      throw new Error(strings.noInternet);
    }
    throw new Error(strings.saveFailed);
  }
}
