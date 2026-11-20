export interface CountryOption {
  code: string;
  dialCode: string; // e.g. "+20"
  nameEn: string;
  nameAr: string;
  flag: string;
  minDigits: number;
  maxDigits: number;
  placeholder: string;
}

// Default country is Egypt (+20) as requested
export const DEFAULT_COUNTRY: CountryOption = {
  code: "EG",
  dialCode: "+20",
  nameEn: "Egypt",
  nameAr: "مصر",
  flag: "🇪🇬",
  minDigits: 10,
  maxDigits: 10,
  placeholder: "101 234 5678",
};

export const COUNTRIES: CountryOption[] = [
  DEFAULT_COUNTRY,
  {
    code: "SA",
    dialCode: "+966",
    nameEn: "Saudi Arabia",
    nameAr: "السعودية",
    flag: "🇸🇦",
    minDigits: 9,
    maxDigits: 9,
    placeholder: "50 123 4567",
  },
  {
    code: "AE",
    dialCode: "+971",
    nameEn: "United Arab Emirates",
    nameAr: "الإمارات",
    flag: "🇦🇪",
    minDigits: 9,
    maxDigits: 9,
    placeholder: "50 123 4567",
  },
  {
    code: "KW",
    dialCode: "+965",
    nameEn: "Kuwait",
    nameAr: "الكويت",
    flag: "🇰🇼",
    minDigits: 8,
    maxDigits: 8,
    placeholder: "500 12345",
  },
  {
    code: "QA",
    dialCode: "+974",
    nameEn: "Qatar",
    nameAr: "قطر",
    flag: "🇶🇦",
    minDigits: 8,
    maxDigits: 8,
    placeholder: "3312 3456",
  },
  {
    code: "JO",
    dialCode: "+962",
    nameEn: "Jordan",
    nameAr: "الأردن",
    flag: "🇯🇴",
    minDigits: 9,
    maxDigits: 9,
    placeholder: "79 123 4567",
  },
  {
    code: "BH",
    dialCode: "+973",
    nameEn: "Bahrain",
    nameAr: "البحرين",
    flag: "🇧🇭",
    minDigits: 8,
    maxDigits: 8,
    placeholder: "3600 1234",
  },
  {
    code: "OM",
    dialCode: "+968",
    nameEn: "Oman",
    nameAr: "عُمان",
    flag: "🇴🇲",
    minDigits: 8,
    maxDigits: 8,
    placeholder: "9212 3456",
  },
  {
    code: "MA",
    dialCode: "+212",
    nameEn: "Morocco",
    nameAr: "المغرب",
    flag: "🇲🇦",
    minDigits: 9,
    maxDigits: 9,
    placeholder: "612 345678",
  },
  {
    code: "US",
    dialCode: "+1",
    nameEn: "United States / Canada",
    nameAr: "الولايات المتحدة / كندا",
    flag: "🇺🇸",
    minDigits: 10,
    maxDigits: 10,
    placeholder: "555 123 4567",
  },
  {
    code: "GB",
    dialCode: "+44",
    nameEn: "United Kingdom",
    nameAr: "المملكة المتحدة",
    flag: "🇬🇧",
    minDigits: 10,
    maxDigits: 10,
    placeholder: "7700 900123",
  },
];

/**
 * Normalizes a local phone input + selected country dialCode into strict E.164 format.
 * Automatically strips non-digits and leading trunk zero (e.g. Egypt "01012345678" -> "+201012345678").
 */
export function normalizeToE164(
  country: CountryOption,
  rawLocalInput: string
): { e164: string; isValid: boolean } {
  const digitsOnly = rawLocalInput.replace(/\D/g, "");

  // Strip a leading zero if the user typed the local trunk prefix (e.g., 010... in Egypt or 050... in KSA/UAE)
  const nationalDigits =
    digitsOnly.startsWith("0") && digitsOnly.length > country.minDigits
      ? digitsOnly.replace(/^0+/, "")
      : digitsOnly.startsWith("0") &&
        (country.code === "EG" || country.code === "SA" || country.code === "AE" || country.code === "JO")
      ? digitsOnly.replace(/^0/, "")
      : digitsOnly;

  const e164 = `${country.dialCode}${nationalDigits}`;
  const e164Regex = /^\+[1-9]\d{7,14}$/;
  const isLengthValid =
    nationalDigits.length >= country.minDigits &&
    nationalDigits.length <= country.maxDigits;

  return {
    e164,
    isValid: isLengthValid && e164Regex.test(e164),
  };
}
