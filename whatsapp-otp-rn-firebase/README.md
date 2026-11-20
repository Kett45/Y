# Direct Phone Save in Firestore (Bare React Native + Firebase)

Minimal Bare React Native CLI (`0.76+`) implementation that captures the user's phone number with a country picker (defaulting to Egypt `+20`), normalizes it to E.164 (`010...` -> `+2010...`), and saves it directly into the signed-in user's Cloud Firestore document (`users/{uid}`) with `phoneVerified: false` — with no OTP screen, no SMS/WhatsApp sending, no Firebase Phone Auth, and no Cloud Functions.

## Folder Structure

```text
whatsapp-otp-rn-firebase/
├── .firebaserc
├── firebase.json
├── firestore.indexes.json
├── firestore.rules
├── README.md
└── mobile/
    ├── App.tsx
    ├── index.js
    ├── package.json
    ├── tsconfig.json
    └── src/
        ├── constants/
        │   └── countries.ts
        ├── i18n/
        │   └── translations.ts
        ├── screens/
        │   ├── HomeScreen.tsx
        │   └── PhoneInputScreen.tsx
        └── services/
            └── firebaseAuthService.ts
```

## Firestore Document & Rules

- Collection path: `users/{uid}` (where `uid = auth().currentUser.uid`)
- Fields written on Continue:
  - `uid`: string (`currentUser.uid`)
  - `phoneNumber`: string (E.164, e.g. `+201012345678`)
  - `phoneVerified`: `false`
  - `updatedAt`: server timestamp
- Rules (`firestore.rules`): Each authenticated user can read and write only their own `users/{userId}` document (`request.auth != null && request.auth.uid == userId`).

## Deploy Firestore Rules & Install Client

```bash
cd whatsapp-otp-rn-firebase
firebase use --add
firebase deploy --only firestore:rules

cd mobile
npm install
```
