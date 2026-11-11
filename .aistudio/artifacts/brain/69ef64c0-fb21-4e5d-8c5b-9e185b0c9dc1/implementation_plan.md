# تطوير التحديث التلقائي الذكي وبصمة الـ APK على GitHub (نجم الكارت v1.6)

تضمن هذه الخطة أن يكتشف التطبيق أي تعديل جديد يُرفع على مستودع GitHub (`Kett45/Y`) فوراً—سواء بتغيير رقم الإصدار أو بتغير البصمة الرقمية للملف (`apkSha256` / `commitSha`)—مع إصلاح سير عمل GitHub Actions ليعمل بنجاح 100% في ثوانٍ، ومنع أي كاش قديم عند التحميل مع بقاء الرابط الظاهر للمستخدم نظيفاً وثابتاً.

## User Review & Critical Decisions

> [!IMPORTANT]
> القرارات المعتمدة بناءً على إجاباتك:

- **Confirmed Decision 1 (اكتشاف التحديثات ببصمة الملف ورقم الإصدار معاً)**: سيفحص التطبيق كلاً من `versionCode` والبصمة الرقمية للـ APK (`apkSha256` و`commitSha`). بمجرد رفع أي ملف APK جديد على GitHub (حتى لو كان بنفس رقم الإصدار `v1.6`)، يكتشف التطبيق اختلاف البصمة ويبدأ تنزيل وتثبيت التحديث الجديد تلقائياً.
- **Confirmed Decision 2 (اعتماد ملف الـ APK الجاهز في GitHub Actions بدون فشل)**: تعديل سير عمل GitHub Actions (`release-apk.yml`) ليعتمد ملف `releases/kAM-Lite.apk` المبني والجاهز من بيئة التطوير مباشرة، ويقوم بحساب بصمته الرقمية (`SHA-256`) وتحديث `releases/version.json` و`releases/delta-sync.json` ونشر الـ Release فوراً في ثوانٍ بدون محاولة إعادة بناء تفشل على سيرفر GitHub.
- **Confirmed Decision 3 (رابط ظاهر نظيف + منع الكاش خفياً عند التحميل)**: يظل الرابط الوحيد الظاهر للمستخدم ثابتاً ونظيفاً (`https://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk`)، بينما يضيف محرك التحميل داخل التطبيق معامل منع الكاش (`?t=timestamp` وترويسات `Cache-Control: no-cache, no-store`) خفياً أثناء الاتصال لضمان جلب أحدث نسخة من السيرفر في كل تحميلة.

---

## 1. Overview & Core Concept

- **What It Does**: يجعل نظام التحديث التلقائي في **نجم الكارت v1.6** يعمل بدقة 100% في كل مرة يتم فيها رفع تعديل جديد إلى GitHub، سواء قام المستخدم بالضغط على الرابط المباشر أو فتح التطبيق على هاتفه.
- **Target Audience / Persona**: أمين الخدمة والخدام الذين يعتمدون على وصول أي تعديل أو ميزة جديدة لهواتفهم تلقائياً دون تعقيد.
- **Key Value**: القضاء النهائي على مشكلة تحميل نسخة قديمة من الكاش أو عدم تنبه التطبيق للتعديلات الفرعية على نفس الإصدار، مع نجاح دائم لسير عمل GitHub Actions.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **التحميل المباشر من الرابط الوحيد**: يضغط المستخدم على الرابط النظيف `https://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk` فيحصل دائماً على أحدث حزمة APK تم رفعها.
  2. **التحديث التلقائي فور فتح التطبيق**: يفحص التطبيق في الخلفية ملف `releases/version.json` (مع كاسر كاش خفي)، ويقارن رقم الإصدار (`versionCode`) وبصمة ملف الـ APK (`apkSha256` / `commitSha`) بالبصمة المثبتة؛ وعند وجود أي اختلاف يبدأ تنزيل التحديث داخل التطبيق بشريط تقدم حي ويفتح شاشة التثبيت مباشرة.
  3. **بطاقة المزامنة في الإعدادات**: تعرض رقم الإصدار المتزامن وبصمة التحديث الحالية والرابط الوحيد النظيف مع زر «فحص وتحديث الآن» وزر «تنزيل وتثبيت APK».
- **Visual Identity & Theme**:
  - متوافق بالكامل مع السمتين المعتمدتين في صفحة المظهر: **داكن (الافتراضي)** و**فاتح (للشمس والإضاءة القوية)**.
- **Interactive Feedback & Motion**: إشعار عائم في أعلى الشاشة يعرض نسبة التنزيل المئوية (`0% .. 100%`) ويفتح مثبت النظام تلقائياً فور اكتمال التحميل.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: الفحص المزدوج (`versionCode` + `apkSha256` / `commitSha`)**
  - *Chosen Approach*: إضافة حقل `apkSha256` داخل `releases/version.json` و`releases/delta-sync.json` وحفظ بصمة آخر APK تم تثبيته أو مزامنته داخل التطبيق (`BuildConfig.APK_BUILD_FINGERPRINT` + `SharedPreferences`).
  - *Why*: يضمن أنه حتى لو تم تعديل سطر واحد في التطبيق ورفعه دون زيادة `versionCode`، يعرف التطبيق فوراً أن ملف الـ APK على GitHub قد تغيّر وينزّله تلقائياً.
- **Decision 2: سير عمل GitHub Actions سريع ومضمون النجاح 100%**
  - *Chosen Approach*: يقوم `release-apk.yml` بالتحقق من وجود `releases/kAM-Lite.apk` الجاهز، ويحسب بصمة `sha256sum` ورقم الـ Commit (`github.sha`) والتوقيت الحالي، ويحدّث `releases/version.json` و`releases/delta-sync.json` وينشر إصدار GitHub Release (`latest`) مباشرة دون الاعتماد على بيئة Gradle غير المتوافقة على `ubuntu-latest`.
  - *Why*: يتجنب فشل GitHub Actions بسبب عدم توفر `AGP 9.1.1` و`SDK 36` على سيرفرات GitHub العامة، ويجعل التحديث يكتمل خلال 15 ثانية فقط من أي Push.
- **Decision 3: كاسر الكاش الخفي (`Hidden Cache-Buster`)**
  - *Chosen Approach*: إبقاء الرابط المعروض للمستخدم وفي `README.md` خالياً من أي إضافات (`https://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk`)، وإضافة `?cb=<timestamp>` وترويسات `Cache-Control: no-cache, no-store, must-revalidate` و`Pragma: no-cache` داخل دوال الجلب والتحميل في `GitHubSmartSyncEngine`.
  - *Why*: يحافظ على شكل الرابط نظيفاً وسهل المشاركة، ويمنع في نفس الوقت كاش GitHub CDN (الذي يدوم 5 دقائق) من تقديم ملف قديم.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   GitHub Repository (Kett45/Y)                             │
│  1. AI Studio Syncs Latest Built APK ──► releases/kAM-Lite.apk             │
│  2. GitHub Actions (release-apk.yml)                                       │
│      ├── Computes SHA-256 of releases/kAM-Lite.apk + Commit SHA            │
│      ├── Updates releases/version.json & releases/delta-sync.json          │
│      └── Publishes GitHub Release (tag: latest) in ~15s (0% failure)       │
└───────────────────────────────────┬────────────────────────────────────────┘
                                    │
                                    ▼ (Hidden ?cb=timestamp + no-cache headers)
┌────────────────────────────────────────────────────────────────────────────┐
│             In-App Auto-Update Engine (GitHubSmartSyncEngine)              │
│  1. Fetches releases/version.json bypassing CDN cache                      │
│  2. Compares Remote (versionCode + apkSha256/commitSha) vs Local           │
│  3. If Newer Version OR Changed APK SHA-256:                               │
│      ├── Downloads releases/kAM-Lite.apk with live progress (0%..100%)     │
│      ├── Records synced apkSha256 & commitSha upon download completion     │
│      └── Launches Android Package Installer via FileProvider               │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `GitHubVersionManifest`: يشمل `versionCode`، `versionName`، `commitSha`، `apkSha256`، `updatedAt`، `apkDirectUrl`، و`releaseNotesArabic`.
  - `GitHubSmartSyncEngine`: يقارن `manifest.versionCode > BuildConfig.VERSION_CODE` **أو** تغيّر `manifest.apkSha256` عن البصمة المحلية المسجلة لتشغيل التنزيل والتثبيت التلقائي.
