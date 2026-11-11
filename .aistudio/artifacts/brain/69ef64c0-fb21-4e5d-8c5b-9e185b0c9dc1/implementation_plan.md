# إصلاح خطأ بناء Gradle وتوافق إعدادات مشروع نجم الكارت v1.6

تهدف هذه الخطة إلى معالجة خطأ البناء (`Build failed: no Gradle settings or build file found in the app directory`) وضمان استقرار عملية التجميع والمزامنة التلقائية مع الحفاظ الكامل على اسم التطبيق **«نجم الكارت v1.6»** والرابط الوحيد الدائم للتحديث التلقائي على GitHub.

## User Review & Critical Decisions

> [!IMPORTANT]
> تم تحديد السبب الجذري لرسالة خطأ البناء في بيئة التشغيل، وجاهزون لتطبيق الإصلاح فوراً بمجرد الضغط على **Proceed**:

- **Confirmed Decision 1 (توافق معرّف مشروع Gradle الداخلي)**: استخدام معرّف لاتيني قياسي آمن لـ `rootProject.name` داخل إعدادات Gradle الداخلية (لتجنب تعارض النقاط والحروف غير اللاتينية في مسارات الكاش الداخلي لـ Gradle)، مع الإبقاء الكامل على اسم التطبيق الظاهر للمستخدم **«نجم الكارت v1.6»** تحت أيقونة الهاتف وفي شريط المنصة وجميع الشاشات.
- **Confirmed Decision 2 (فصل مهمة نسخ الـ APK عن البناء العادي للمنصة)**: إبقاء مهمة التحديث التلقائي للملفات (`syncReleaseApk`) مخصصة لبيئة النشر وGitHub Actions دون إجبار عملية البناء السريع (`assembleDebug`) على إعادة كتابة ملفات المستودع أثناء فحص المنصة.
- **Confirmed Decision 3 (الرابط الوحيد الدائم للتحديث التلقائي)**: الحفاظ على الرابط الوحيد الدائم `https://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk` في المستودع وداخل التطبيق بدون أي روابط إضافية أو زائدة.

---

## 1. Overview & Core Concept

- **What It Does**: يصلح تهيئة مشروع Gradle بحيث تتعرف بيئة البناء والمحاكي الفوري على ملفات الإعدادات والبناء بدون أخطاء، مع استمرار عمل تطبيق **نجم الكارت v1.6** بكامل مميزاته (الفتح الفوري `0ms` من التخزين المحلي، صفحة المظهر بالسمتين الداكنة والفاتحة للشمس، أيقونة النجمة، ومزامنة التفضيلات الشخصية على السحابة).
- **Target Audience / Persona**: أمين الخدمة والخدام في فصول مدارس الأحد.
- **Key Value**: بناء مستقر وخالٍ من الأخطاء مع تحديث تلقائي سلس عبر رابط واحد ثابت.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **تشغيل فوري (0ms)**: يفتح التطبيق مباشرة من `LocalClassroomCache` ويعرض بيانات الفصل والبلوكات والأطفال فوراً.
  2. **صفحة المظهر المستقلة**: من تبويب الإعدادات ← زر **«فتح صفحة المظهر والسمات»** ← اختيار السمة (**داكن** أو **فاتح للشمس والإضاءة القوية**) وضبط حجم الخط وأيقونة النجمة.
  3. **التحديث التلقائي برابط واحد**: فحص وتنزيل التحديثات مباشرة من داخل التطبيق عبر الرابط الوحيد المعتمد.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: تصميم عملي واضح بسمتين متكاملتين (السمة الداكنة الملكية `#0B1120` والسمة الفاتحة عالية التباين للشمس `#FFFFFF` مع نصوص سوداء `#090D16` وأزرار كحلية `#0F2942` وذهبية `#B45309`).
  - *Typography & Hierarchy*: خط Cairo المحلي مع تحجيم ذكي وتلقائي يدعم الهواتف والأجهزة اللوحية.
- **Interactive Feedback & Motion**: استجابة لحظية (`0ms`) عند تفعيل أو إخفاء الكروت والبلوكات دون رجوع المفتاح لحالته السابقة عند التنقل بين التبويبات.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: فصل `rootProject.name` الداخلي لـ Gradle عن اسم التطبيق الظاهر (`app_name`)**
  - *Chosen Approach*: ضبط `rootProject.name` في إعدادات Gradle على معرّف قياسي بسيط بدون نقاط أو رموز خاصة، مع الاحتفاظ بـ **«نجم الكارت v1.6»** في موارد التطبيق (`strings.xml`) وبيانات المنصة (`metadata.json`) وجميع الشاشات.
  - *Why*: يمنع فشل محلل إعدادات Gradle في بيئة الحاوية السحابية عند التعامل مع النقاط (`1.6`) والمسافات العربية في مسار المشروع الجذري.
- **Decision 2: تشغيل `syncReleaseApk` عند الطلب وفي GitHub Actions بدلاً من `finalizedBy` الإجباري**
  - *Chosen Approach*: إزالة ربط `finalizedBy("syncReleaseApk")` الإجباري من كل عملية `assembleDebug` محلية، وتشغيلها عند تحديث الإصدار أو عبر GitHub Actions.
  - *Why*: يمنع تعديل ملفات المستودع أثناء عملية الفحص التلقائي للبناء في المنصة.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────────────────────────────────────────────┐
│                      تطبيق نجم الكارت v1.6                           │
├──────────────────────────────────────────────────────────────────────┤
│  Gradle Build System (Clean Configuration)                           │
│   ├── Root Settings & Build Config (ASCII-safe rootProject ID)       │
│   └── App Module Config (versionCode = 6, versionName = "1.6")       │
├──────────────────────────────────────────────────────────────────────┤
│  Instant Startup & Cloud Sync Layer                                  │
│   ├── LocalClassroomCache (0ms RAM + Disk Snapshot & User Prefs)     │
│   ├── KamRepository & KamViewModel (Optimistic 0ms UI + Firestore)   │
│   └── GitHubSmartSyncEngine (Single Auto-Updating APK URL)           │
└──────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `LocalClassroomCache`: يحفظ بيانات الفصل والتفضيلات الشخصية محلياً للفتح الفوري.
  - `UserProfile`: يزامن `themeId` و`uiScaleMode` و`uiScaleFactor` و`showFloatingAddButton` و`enabledCards` و`collapsedBlockIds` و`hiddenBlockIds` مع حساب المستخدم في السحابة.
- **Interactive Component & State Mapping**:
  - جميع مفاتيح الإعدادات والمظهر تحدث الحالة المحلية فوراً وتُحفظ في السحابة بأمان عبر `SetOptions.merge()`.
