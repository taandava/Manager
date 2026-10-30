# Building & Using MANAGER v4.0

## 1. Quick Installation
The verified, signed production APK is available at:
[`release/app-manager-v4.0.apk`](file:///d:/GPU-D/apps/Contact/release/app-manager-v4.0.apk)

### Install to Connected Device via ADB:
```powershell
adb -d install -r release/app-manager-v4.0.apk
```

---

## 2. Building from Source

### Prerequisites:
- JDK 17+ (e.g. bundled in Android Studio JBR)
- Android SDK with Platform 34 (Android 14) and Build-Tools 34.0.0+

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
```

### Run Unit Tests:
```powershell
.\gradlew.bat test
```
*(All 61 tests across 9 test suites must pass.)*

### Build Signed Release APK:
```powershell
.\gradlew.bat assembleRelease copyReleaseApk
```
This runs R8 resource and code shrinking, signs with release keystore, and copies the resulting APK to `release/app-manager-v4.0.apk`.

---

## 3. Hostinger Backend Deployment
Deploy [`cloud_sync/index.php`](file:///d:/GPU-D/apps/Contact/cloud_sync/index.php) to your Hostinger server at:
`/home/u533806820/domains/white-jackal-528611.hostingersite.com/public_html/contacts/index.php`

### Web Admin Dashboard:
Accessible in any mobile or desktop browser at:
```
https://white-jackal-528611.hostingersite.com/contacts/index.php?token=Jmd@2025
```
