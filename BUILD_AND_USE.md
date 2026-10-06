# Building & Using Contact Finder Pro v3.0

## 1. Hostinger Backend Deployment
In your Hostinger File Manager at `public_html/contacts/`:
- Upload [`cloud_sync/index.php`](file:///c:/Users/JMD/Documents/Contact/cloud_sync/index.php) as `index.php`.
- Because `index.php` is all-inclusive, you can also overwrite `sync.php`, `admin.php`, and `sms.php` with it (or keep aliases) so that any legacy URLs work seamlessly.
- Access the Web Admin Excel Editor in your browser at:
  ```
  https://white-jackal-528611.hostingersite.com/contacts/index.php?token=Jmd@2025
  ```
- All Google Sheets and Apps Script references have been eliminated.

## 2. Installing the Upgraded Android App
- The compiled APK is located at:
  [`app-contactfinder-v3.0.apk`](file:///c:/Users/JMD/Documents/Contact/app/release/app-contactfinder-v3.0.apk)
- Transfer and install this APK onto any Android phone.

## 3. New Features in Settings & App
1. **Device Identification**:
   - Each phone identifies itself by device model (e.g. `Samsung_SM-G991B`).
2. **Auto Scan Contacts & SMS**:
   - From Settings, run "Auto Scan Device Contacts" and "Auto Scan SMS Contacts" to extract unique numbers and sync them to Hostinger.
3. **Save All Contacts to Device**:
   - In Settings -> "Save ALL Contacts to Device", the app adds all cloud/app contacts directly into the phone's address book while skipping duplicates.
4. **Scan & Clean Device Duplicates**:
   - In Settings -> "Scan & Clean Device Duplicate Contacts", the app finds duplicate phone entries on the phone address book and allows one-click cleaning.
5. **Switch CSV Files**:
   - In Settings -> "Select Remote CSV File", choose between `contacts.csv`, `sms.csv`, or device-specific CSVs.
6. **Duplicate Reminders**:
   - Whenever duplicate numbers exist, toast reminders alert you on launch, and the Duplicates tab lets you clean them with one tap.
