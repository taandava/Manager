# Contact Finder 2.2 — Quick Start, Customisation & APK Extraction Guide

## 1. Opening the Project in Android Studio
To customise this app:
1. Open **Android Studio**.
2. Select **File > Open** (or click **Open** on the welcome screen).
3. Navigate to and select the project root folder:
   `C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp`
4. Click **OK**. Wait for Android Studio to finish Gradle sync and index the project.

## 2. Customising the App
All source code and configurations are fully accessible for customisation:
- **Java Source Files:** [`app/src/main/java/com/balajifinance/contactfinder/`](file:///C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/src/main/java/com/balajifinance/contactfinder/)
  - [`MainActivity.java`](file:///C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/src/main/java/com/balajifinance/contactfinder/MainActivity.java): User interface, menus, Google Sheet downloader, cloud sync, backup/restore.
  - [`DatabaseHelper.java`](file:///C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/src/main/java/com/balajifinance/contactfinder/DatabaseHelper.java): SQLite local database tables and queries.
  - [`PhoneUtils.java`](file:///C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/src/main/java/com/balajifinance/contactfinder/PhoneUtils.java): Phone normalization and formatting.
  - [`CsvParser.java`](file:///C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/src/main/java/com/balajifinance/contactfinder/CsvParser.java): CSV parsing engine.
- **Default Google Sheet Source URL:** Defined as `SOURCE_URL` in [`MainActivity.java`](file:///C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/src/main/java/com/balajifinance/contactfinder/MainActivity.java).
- **App Title & Permissions:** [`app/src/main/AndroidManifest.xml`](file:///C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/src/main/AndroidManifest.xml).
- **Themes & Colors:** [`app/src/main/res/values/styles.xml`](file:///C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/src/main/res/values/styles.xml).

## 3. Building and Extracting the APK
After customising the app:
1. In Android Studio, go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. When the build finishes, click **locate** in the notification popup, or find your compiled APK at:
   `C:/Users/JMD/Downloads/ContactFinderApp_v5/ContactFinderApp/app/build/outputs/apk/debug/app-debug.apk`
3. Transfer or install `app-debug.apk` onto any Android device.

## 4. First Launch & Features
- **Offline-first:** Automatically downloads the Google Sheet CSV on first launch and caches contacts locally in SQLite.
- **No Apps Script Required:** Apps Script is optional and only needed if you want two-way private Google Sheet sync.
- **Device Contact Scanning:** Imports unique contacts from your phone into local storage.
- **Backups:** Export and import local backups via Android's native file picker.
