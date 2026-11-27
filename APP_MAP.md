# خريطة تطبيق نجم الكارت (APP_MAP.md)

## وصف التطبيق
تطبيق Android (Kotlin + Jetpack Compose + Material 3 + Firebase Auth & Firestore) لإدارة حضور الأطفال والكروت والنقاط الأسبوعية والشهرية، وتوزيعهم على بلوكات الخدام، مع شاشة السحب الذكي، شاشة الافتقاد، والمراسلة الفورية بين الخدام، والعمل أوفلاين ومزامنة التحديثات.

---

## نقاط الدخول والـ Navigation
- `app/src/main/java/com/example/MainActivity.kt`:
  - `KamBottomTab` (سطر 130): 4 تبويبات (`HOME`, `VISITATION`, `MESSAGES`, `SETTINGS`) بعد دمج إعدادات المجموعة داخل الإعدادات.
  - `AppNavigation` (سطر 255) و`AuthenticatedKamApp` (سطر 325): إدارة الدخول، الربط مع `KamViewModel` و`ChatViewModel`، والتنقل بين التبويبات، وشريط الإشعار العلوي بدون رقم إصدار.
- `app/src/main/java/com/example/ui/components/KamBottomNavigationBar.kt`: `KamFloatingBottomNavBar` (سطر 94، 4 تبويبات مقفولة لأسفل الشاشة).
- `app/src/main/java/com/example/ui/components/BlockColorSliderBar.kt`: شريط اختيار لون البلوك والكارت الإضافي المتدرج مع المعاينة الحية.

---

## الشاشات الرئيسية والفرعية
- **تسجيل الدخول**: `app/src/main/java/com/example/ui/auth/AuthScreen.kt` — `AuthScreen` (سطر 390).
- **إعداد/انضمام المجموعة**: `app/src/main/java/com/example/ui/screens/GroupOnboardingScreen.kt` — `GroupOnboardingScreen` (سطر 62).
- **الرئيسية**: `app/src/main/java/com/example/ui/screens/HomeScreen.kt`:
  - `HomeScreen` (سطر 137)، الشريط العلوي `HomeTopControlCard`، تحريك البلوكات (`⏫` للأول، `↑` لأعلى، `↓` لأسفل) مع إعادة ترقيم الأطفال (`1, 2, 3...`) في `ServantBlockHeaderCard`، ولمبة الحضور `AttendanceIndicatorLamp`.
  - حوارات الرئيسية والبحث والشجرة: `app/src/main/java/com/example/ui/screens/HomeDialogsAndSections.kt`.
- **السحب الذكي (الحضور والكروت)**: `app/src/main/java/com/example/ui/screens/SmartAttendanceScreen.kt`:
  - `SmartAttendanceScreen` (سطر 445)، الهيدر وزر النجمة `SmartAttendanceTopHeader` (سطر 1399)، التظليل التوضيحي `StarButtonSpotlightOverlay` (سطر 1784)، كارت السحب `SmartAttendanceSwipeCardContent` (سطر 1951)، الملخص `SmartAttendanceSummaryView` (سطر 2623).
- **لوحة الأوائل والتقارير**: `app/src/main/java/com/example/ui/screens/FullClassLeaderboardScreen.kt` (سطر 114).
- **الافتقاد**: `app/src/main/java/com/example/ui/screens/VisitationSwipeScreen.kt` — `VisitationSwipeScreen` (سطر 206).
- **الرسائل والدردشة**: `app/src/main/java/com/example/ui/screens/MessagesScreen.kt`:
  - `MessagesScreen` (سطر 160)، عرض تقارير الحضور والكروت والافتقاد بنفس قائمة وترتيب الشاشة الرئيسية (بلوك تحت بلوك، كل طفل في سطره برقمه واسمه، والغائب/المشكلة بصورته جنب اسمه وملحوظته بخط أكبر) (أسطر 3450–4440).
- **الإعدادات وصفحاتها الداخلية**: `app/src/main/java/com/example/ui/screens/GroupAndSettingsScreens.kt`:
  - `SettingsScreen` (سطر 1875): بروفايل الخادم المصغر، زر المظهر والسمات، مجموعاتي والتبديل بينها (في مكان واحد فقط)، وصفحات داخلية لـ `إعدادات المجموعة` (`GroupInvitationsScreen` مع ترتيب البلوكات)، `إعدادات وإظهار الكروت` (مع شريط الألوان الحر للكارت الإضافي)، قواعد الفصل (مع `قفل نقل الطلاب`)، `المزامنة مع GitHub`، و`سجل الأحداث` للأمين مع زر `تراجع`.
- **المظهر والأيقونة والألوان والصور**:
  - `app/src/main/java/com/example/ui/components/AppearanceAndIconSettingsSection.kt` (`AppearanceSettingsEntryCard`، `AppearanceFullScreen`)، `app/src/main/java/com/example/ui/theme/Color.kt` و`Theme.kt` (دعم أي لون Hex زاهي للبلوكات).
  - `app/src/main/java/com/example/ui/components/StudentAvatarComponents.kt` و`ServantPhoneAndProfileComponents.kt`: فك وتحميل الصور الكاملة مسبقاً في الكاش (`fullPhotoCache` / `fullPhotoByStudentIdCache`) وعرضها فوراً في المعاينة السريعة ونوافذ البروفايل بدون المرور بالصورة المصغرة.

---

## الـ ViewModels وقاعدة البيانات والمزامنة
- **ViewModels**:
  - `app/src/main/java/com/example/ui/viewmodel/KamViewModel.kt` (`KamViewModel` سطر 761، المزامنة `forceFullCloudSync` سطر 2870، نص دعوة الخدام `buildInvitationShareText` سطر 3692).
  - `app/src/main/java/com/example/ui/viewmodel/ChatViewModel.kt` (`ChatViewModel` سطر 81).
- **Repositories & Models**:
  - `app/src/main/java/com/example/data/repository/KamRepository.kt` (سطر 51).
  - `app/src/main/java/com/example/data/repository/ChatRepository.kt` (سطر 39).
  - `app/src/main/java/com/example/data/model/KamModels.kt` و`ChatModels.kt`.
  - الكاش المحلي: `app/src/main/java/com/example/data/cache/LocalClassroomCache.kt`.
  - قواعد الأمان: `firestore.rules` (السماح للخادم بإنشاء وتلوين بلوكه الشخصي فقط، والأمين لكل البلوكات).
- **تحديثات GitHub والنسخة**: `app/src/main/java/com/example/data/sync/GitHubSmartSyncEngine.kt` (سطر 122).

---

## الاسم، الأيقونة، الـ Version، والـ Build
- **اسم التطبيق الظاهر تحت الأيقونة الخارجية**: `app/src/main/res/values/strings.xml` (`app_name` = `نجم الكارت`) و`metadata.json` (`name` = `نجم الكارت`).
- **الأيقونة الخارجية**: `app/src/main/AndroidManifest.xml` (`@mipmap/ic_launcher_star`)، `app/src/main/java/com/example/ui/theme/AppAppearanceManager.kt` (`KamLauncherIconPreset` سطر 73).
- **إعدادات البيلد والإصدار**: `app/build.gradle.kts` (`versionCode` و`versionName` سطور 23–28، ومهمة `syncReleaseApk` سطر 107 لتحديث `releases/kAM-Lite.apk` و`releases/version.json` و`releases/delta-sync.json`).
- **اسم المشروع الجذري**: `settings.gradle.kts` (`rootProject.name = "NajmElKart"`).
