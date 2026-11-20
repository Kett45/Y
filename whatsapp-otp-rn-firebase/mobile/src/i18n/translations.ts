export type LanguageCode = "ar" | "en";

export interface TranslationStrings {
  appName: string;
  languageSwitchLabel: string;
  phoneTitle: string;
  phoneSubtitle: string;
  countryPickerTitle: string;
  countrySearchPlaceholder: string;
  phoneInputLabel: string;
  continueButton: string;
  savingButton: string;
  welcomeTitle: string;
  savedSubtitle: string;
  uidLabel: string;
  phoneLabel: string;
  phoneStatusLabel: string;
  unverifiedStatusValue: string;
  editPhoneButton: string;
  signOutButton: string;
  errors: {
    invalidPhone: string;
    noInternet: string;
    notSignedIn: string;
    saveFailed: string;
  };
}

export const TRANSLATIONS: Record<LanguageCode, TranslationStrings> = {
  ar: {
    appName: "رقم الهاتف",
    languageSwitchLabel: "English",
    phoneTitle: "أدخل رقم هاتفك",
    phoneSubtitle: "سيتم حفظ رقم هاتفك مباشرة في حسابك",
    countryPickerTitle: "اختر الدولة",
    countrySearchPlaceholder: "ابحث عن الدولة أو مفتاح الاتصال...",
    phoneInputLabel: "رقم الهاتف المحمول",
    continueButton: "متابعة",
    savingButton: "جاري الحفظ...",
    welcomeTitle: "تم حفظ رقم الهاتف",
    savedSubtitle: "تم حفظ رقم هاتفك في Firestore بنجاح",
    uidLabel: "معرف المستخدم (UID)",
    phoneLabel: "رقم الهاتف المحفوظ",
    phoneStatusLabel: "حالة التوثيق",
    unverifiedStatusValue: "غير موثّق (phoneVerified: false)",
    editPhoneButton: "تعديل الرقم",
    signOutButton: "تسجيل الخروج",
    errors: {
      invalidPhone: "يرجى إدخال رقم هاتف صحيح متوافق مع الدولة المختارة.",
      noInternet: "لا يوجد اتصال بالإنترنت. يرجى التحقق من الشبكة والمحاولة مرة أخرى.",
      notSignedIn: "يرجى تسجيل الدخول أولاً قبل حفظ رقم الهاتف.",
      saveFailed: "تعذر حفظ رقم الهاتف. يرجى المحاولة مرة أخرى.",
    },
  },
  en: {
    appName: "Phone Number",
    languageSwitchLabel: "العربية",
    phoneTitle: "Enter your phone number",
    phoneSubtitle: "Your phone number will be saved directly to your profile",
    countryPickerTitle: "Select Country",
    countrySearchPlaceholder: "Search country or dial code...",
    phoneInputLabel: "Mobile Phone Number",
    continueButton: "Continue",
    savingButton: "Saving...",
    welcomeTitle: "Phone Number Saved",
    savedSubtitle: "Your phone number has been saved to Firestore",
    uidLabel: "Firebase User UID",
    phoneLabel: "Saved Phone Number",
    phoneStatusLabel: "Verification Status",
    unverifiedStatusValue: "Unverified (phoneVerified: false)",
    editPhoneButton: "Change Number",
    signOutButton: "Sign Out",
    errors: {
      invalidPhone: "Please enter a valid phone number for the selected country.",
      noInternet: "No internet connection. Please check your network and try again.",
      notSignedIn: "Please sign in first before saving a phone number.",
      saveFailed: "Could not save phone number. Please try again.",
    },
  },
};
