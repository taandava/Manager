# Contact Finder 2.2

Offline-first Android contact directory designed for large lists such as 8,000+ contacts.

## IMPORTANT: You do NOT need Apps Script to load the original contacts

The app has the supplied Google Sheet CSV URL built in. On first launch it tries to download that CSV and stores a local cache.

**Apps Script is optional.** It is only required for private two-way cloud editing/deletion/synchronization.

If the app cannot load contacts:
1. Check the Android phone has internet access.
2. Open Menu -> Source URL Settings.
3. Confirm the Google Sheet CSV URL.
4. Use Menu -> Refresh Google Sheet.
5. If the source is unavailable, previously cached/local contacts remain available.

## Initial source

The default source is the published CSV URL supplied for this project. Published Google Sheets data can be read by anyone who has access to that published URL. Do not put private phone data in a published sheet if privacy matters.

## Local features

- Persistent local contact storage.
- Search by name, phone, or details.
- Edit existing records.
- Add local records.
- Save/favorite records inside the app; Android Contacts are never modified.
- Copy / Call / SMS / WhatsApp / WhatsApp Call.
- Delete records.
- Deleted cloud-backed records are stored as tombstones so a source refresh does not silently restore them.
- Export/restore local backup using Android's file picker.
- Native ListView rendering instead of a giant ScrollView for better performance with thousands of records.

## Optional private cloud sync

1. Create a normal/private Google Sheet. Do NOT publish it.
2. Put `Code.gs` into Extensions -> Apps Script.
3. Change `SYNC_TOKEN` to a long random secret.
4. Deploy -> New deployment -> Web app.
5. Execute as: Me.
6. Who has access: Anyone with the web app URL.
7. Copy the Web App URL.
8. Android -> Menu -> Cloud Sync Settings.
9. Enter the Web App URL and the exact same token.
10. Run Sync Cloud.

The first sync can upload the local contacts already loaded from the original CSV. Later syncs exchange only changed records.

## Cloud duplicate handling

Before synchronization, active cloud records are grouped by normalized phone number. If a phone number appears in multiple cloud records, the app asks which record to keep.

- KEEP SELECTED / DELETE OTHERS: creates cloud deletion tombstones for all other records.
- KEEP ALL: leaves the duplicate group unchanged.
- CANCEL SYNC: makes no cloud changes.

Phone formats such as `9876543210`, `91 9876543210`, `+91-9876543210`, and `0091 9876543210` normalize to the same Indian number.

## Cloud deletion

Delete from the Android app:
- local-only record: removed immediately.
- cloud-backed record: marked deleted locally and uploaded during the next Sync Cloud.

The Apps Script backend keeps the deleted row as a tombstone. This prevents another device or a later download from resurrecting it.

## Cloud conflict behavior

Each record has `UPDATED_AT`. The app does not overwrite a newer local edit with an older cloud record. The Apps Script backend also rejects an older incoming version when a newer version is already in the Sheet.

## Build

Open `ContactFinderApp` in Android Studio. Android API 35, minSdk 23, Java, Android SDK only.
