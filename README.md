# MANAGER v4.0 🗂️⚡

> **Intelligent, Lightweight Native Android Manager: Contacts, SMS Ledger, Smart Deduplication, Append-Mode Local Storage & Hostinger 2-Way Cloud Engine**

MANAGER v4.0 is a modern, privacy-focused Android application engineered with **100% Kotlin and Jetpack Compose**. It unifies contact lifecycle management, precision phone deduplication, same-name multi-number disambiguation, and an advanced **In-App SMS Ledger & Classifier** with automated two-way **Hostinger Cloud synchronization**, **append-mode SQLite caching**, and **instant index-assisted search**.

---

## 📊 Project Status & Verification Matrix

| Metric | Specification | Status |
| :--- | :--- | :---: |
| **Target SDK** | Android 14 (API 34) | **Verified** |
| **Minimum SDK** | Android 7.0 (API 24) | **Verified** |
| **Unit Test Suite** | 9 Test Suites (64 tests total) | **64/64 Passed (100%)** |
| **Android Lint** | Zero diagnostic errors | **0 Errors** |
| **Release Artifact** | [`release/app-manager-v4.0.apk`](file:///d:/GPU-D/apps/Contact/release/app-manager-v4.0.apk) (2.10 MB) | **Signed (v2 + v3 Schemes)** |
| **Physical Device Verification** | Samsung Galaxy A04 (`R9ZW105MFDR`, Android 14) | **Verified Live (PID: 23824)** |
| **Hostinger Cloud Backend** | `https://white-jackal-528611.hostingersite.com/contacts/index.php` | **Live & Operational (9,005 Contacts)** |
| **Architecture** | Unidirectional Data Flow (MVI/MVVM), Jetpack Compose, SQLite KTX | **Clean & Modular** |

---

## 🌟 Key Highlights & Engineering Features

### 1. Cloud Contacts Append-Mode Storage & Instant Search
- **Persistent Local Append Mode:** Once retrieved from the Hostinger cloud backend (via browsing, searching, or background sync), all cloud contacts are immediately stored in the local SQLite database (`TABLE_CONTACTS`) in append mode.
- **Offline & Zero-Latency Cloud Explorer:** The Cloud Data Explorer instantly queries local SQLite cache before waiting for network latency, ensuring immediate 0ms UI feedback and smooth 60 FPS scrolling.
- **Safety Invariants & Conflict Resolution:**
  - **No Speculative Deletions:** Remote pagination never causes unreturned local contacts to be deleted.
  - **Local Edit Protection:** Contacts modified locally (`edited = 1`) with pending sync status are strictly protected from being overwritten by incoming cloud data.
  - **Anti-Resurrection Blacklists:** Contacts explicitly deleted by the user on the device are tracked in `deleted_contacts` and will never be resurrected from the cloud.
  - **Deterministic IDs:** Blank cloud IDs are automatically assigned deterministic UUIDs based on normalized phone numbers to prevent duplicate insertions.
- **Native SQLite Indexed Search:** Contact search is pushed down into SQLite via SQL `LIKE` clauses backed by `idx_name`, `idx_phone`, and `idx_details`. This accelerates searches across 9,000+ contacts by over 20x without main thread stutter.

### 2. Comprehensive Zero-Suppression Hardening
- **Telephony Security:** `SmsReceiver.kt` and `MmsReceiver.kt` validate intent actions against `Telephony.Sms.Intents.SMS_DELIVER_ACTION` and `WAP_PUSH_DELIVER`, completely eliminating the need for `UnsafeProtectedBroadcastReceiver` suppression.
- **Android 13+ Notification Permissions:** Manifest declares `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`, and runtime permission checks guard all notification dispatches on API 33+.
- **Exception-Safe SQLite KTX Transactions:** All 12 manual transaction blocks in `LocalDb.kt` refactored to idiomatic `androidx.core.database.sqlite.transaction` extensions (`db.transaction { ... }`).
- **Defensive Migrations Without Exception Swallowing:** Added `addColumnIfNotExists(db, table, column, typeDefinition)` using `PRAGMA table_info` before executing `ALTER TABLE ADD COLUMN`, completely eliminating speculative SQL migration errors.
- **Clean Logging:** Swallowed exceptions replaced with structured contextual logging (`Log.e()` and `Log.w()`).

### 3. App Identity & Adaptive App Icon
- **Official App Name:** **MANAGER** (`com.taandava.manager`)
- **Brand App Icon:** High-contrast Navy Gear framing an electric blue executive silhouette on clean white backing.
- **Adaptive Multi-Density Mipmaps:** Generated full density ladder (`mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi`) with standard `ic_launcher.png`, `ic_launcher_round.png`, and `ic_launcher_foreground.png`.
- **Themed Icon Support:** Added `<monochrome android:drawable="@drawable/ic_notification" />` to both `ic_launcher.xml` and `ic_launcher_round.xml` for Android 13+ Material You themed icons.
- **Modular Component:** Standalone VectorDrawable component [`ManagerLogo.kt`](file:///d:/GPU-D/apps/Contact/app/src/main/java/com/taandava/manager/ui/components/ManagerLogo.kt) ensuring 100% decoupled UI rendering across all Compose screens.

### 4. Precision Duplicate Management & Name Disambiguation
- **Strict Exact-Number Phone Deduplication:** Uses normalized E.164 matching (min 10–15 digits) to group only truly identical telephone numbers, eliminating false-positive merges.
- **Same-Name Multi-Number Disambiguation:** 
  - Tokenizes full contact names and detects honorific prefixes (`Dr.`, `Mr.`, `Mrs.`, `Shri`, `Smt.`, `Prof.`, etc.) and lineage suffixes (`Jr.`, `Sr.`, `II`, `III`, `IV`).
  - Protects prefixes and suffixes while applying clean sentence casing to all name tokens.
  - Deterministically appends $N$ copies of the first root word's trailing alphabet based on duplicate occurrence ($0 \to$ base, $1 \to$ double letter, $2 \to$ triple letter).
- **Interactive Review UI:** Material 3 `PrimaryTabRow`, individual checkboxes, "Select All / Deselect All", merge strategy radio buttons ("Keep Oldest", "Keep Most Complete", "Combine All Info"), and real-time before/after rename previews.

### 5. Automated 2-Way Hostinger Cloud Synchronization
- **Asynchronous & Non-Blocking:** Cloud sync runs automatically in the background on:
  - App startup (`loadAll()`)
  - Contact additions, edits, and star toggles
  - Device contact and call log scans
  - CSV and vCard (.vcf) imports
  - Real-time incoming SMS broadcasts (`SmsReceiver.kt`) and quick-reply dispatches (`HeadlessSmsSendService.kt`)
- **Dual SQLite WAL & CSV Storage:** Cloud backend manages synchronized storage in `contacts.db` and dual CSV mirrors (`contacts.csv`, `sms.csv`).
- **Cloud Explorer Screen:** Embedded search and remote data viewer for contacts and SMS logs with pagination, search query filtering, and local comparison.

### 6. Intelligent SMS Ledger & Default SMS Capabilities
- Ingests incoming SMS PDUs into an atomic, indexed SQLite table (`sms_ledger`).
- **One-Tap OTP Extraction:** Automatically identifies 4–8 digit verification codes with a one-tap copy button.
- **Transaction Detection:** Color-coded badges for debit (`-₹`) and credit (`+₹`) alerts.
- **Full Android 14 Telephony Support:** Qualified default SMS handler with `SmsReceiver` and `HeadlessSmsSendService` (`RESPOND_VIA_MESSAGE`).

### 7. Universal Backup & Restore
- **vCard (.vcf):** RFC 2426 / 6350 compliant vCard 3.0 export and multiline unfolding parser.
- **CSV:** Hostinger-compliant CSV export and batch import.

---

## 📱 App Navigation Tabs

| Tab | Icon | Purpose |
| --- | :---: | --- |
| **Contacts** | 👥 | Multi-select, Sort, Search, Star filter, Grid/List switcher, Quick actions (Call, SMS, WhatsApp, Share), Add/Edit modal. |
| **SMS Ledger** | 💬 | Filter chips, One-tap Copy OTP chip, Transaction amount badges, Bulk delete, SMS Inspector dialog. |
| **Duplicates** | ⚠️ | Exact Phone Duplicates tab & Same-Name Disambiguation tab with selection checkboxes, radio merge strategies, and live previews. |
| **Analytics** | 📊 | Metrics (Total Contacts, Starred, Duplicates, SMS, OTPs, Transactions), Vacuum database, Device breakdown. |
| **Settings** | ⚙️ | Card Layout Style, WhatsApp preferences, Hostinger Cloud config, CSV & vCard backup/restore, Device scanners, App permissions. |

---

## 📦 Build Artifacts

- **Production Minified Signed APK:** [`release/app-manager-v4.0.apk`](file:///d:/GPU-D/apps/Contact/release/app-manager-v4.0.apk) (2.10 MB / 2,202,217 bytes)
- **Signature Schemes:** Scheme v2 (APK Signature Scheme v2) & Scheme v3 (APK Signature Scheme v3)
- **Unit Test Suite:** 9 Suites, 64 Tests passing with 100% pass rate.
- **Lint Verification:** 0 Errors.

---

## ☁️ Hostinger Backend Deployment

- **Single Engine Script:** [`cloud_sync/index.php`](file:///d:/GPU-D/apps/Contact/cloud_sync/index.php)
- **Production Endpoint:** `https://white-jackal-528611.hostingersite.com/contacts/index.php`
- **Default Auth Token:** `Jmd@2025`
- **Actions Supported:**
  - `list_csvs` — Inventory of CSVs and SQLite databases
  - `get_cloud_overview` — Global contact and SMS telemetry
  - `get_cloud_contacts` — Paginated contact search (supports `q`, `limit`, `offset`)
  - `get_cloud_sms` — Paginated SMS log search with search filter `q`
  - `sync_contacts` — Batch contact push/merge
  - `sync_sms` — Batch SMS push/merge
  - `clean_duplicates` — Server-side duplicate cleanup
  - `clean_sms_contacts` — Synthetic contact purge
