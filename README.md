# MANAGER v4.0 🗂️⚡

> **Intelligent, Lightweight Native Android Manager: Contacts, SMS Ledger, Smart Deduplication & Hostinger 2-Way Cloud Engine**

MANAGER v4.0 is a modern, privacy-focused Android application engineered with **100% Kotlin and Jetpack Compose**. It unifies contact lifecycle management, precision phone deduplication, same-name multi-number disambiguation, and an advanced **In-App SMS Ledger & Classifier** with automated two-way **Hostinger Cloud synchronization**.

---

## 🌟 Key Highlights & Engineering Features

### 1. App Identity & Single SVG Vector Launcher Icon
- **Official App Name:** **MANAGER**
- **Unified Vector Icon:** Converted entirely to scalable vector graphics (`art/ic_launcher.svg`, `res/drawable/ic_launcher.xml`, and adaptive layers in `res/drawable-anydpi-v26/ic_launcher.xml`).
- **Zero Raster Overhead:** Completely purged legacy raster PNG mipmaps, reducing APK asset overhead and eliminating multi-density distortion.
- **Obsidian Dark Aesthetic:** High-contrast ambient dark theme with cyan/mint glassmorphic cards and hairline borders.

### 2. Precision Duplicate Management & Name Disambiguation
- **Strict Exact-Number Phone Deduplication:** Uses normalized E.164 matching (min 10–15 digits) to group only truly identical telephone numbers, eliminating false-positive merges.
- **Same-Name Multi-Number Disambiguation:** 
  - Tokenizes full contact names and detects honorific prefixes (`Dr.`, `Mr.`, `Mrs.`, `Shri`, `Smt.`, `Prof.`, etc.) and lineage suffixes (`Jr.`, `Sr.`, `II`, `III`, `IV`).
  - Protects prefixes and suffixes while applying clean sentence casing to all name tokens.
  - Deterministically appends $N$ copies of the first root word's trailing alphabet based on duplicate occurrence ($0 \to$ base, $1 \to$ double letter, $2 \to$ triple letter).
- **Interactive Review UI:** Material 3 `PrimaryTabRow`, individual checkboxes, "Select All / Deselect All", merge strategy radio buttons ("Keep Oldest", "Keep Most Complete", "Combine All Info"), and real-time before/after rename previews.

### 3. Automated 2-Way Hostinger Cloud Synchronization
- **Asynchronous & Non-Blocking:** Cloud sync runs automatically in the background on:
  - App startup (`loadAll()`)
  - Contact additions, edits, and star toggles
  - Device contact and SMS scans
  - CSV and vCard (.vcf) imports
  - Real-time incoming SMS broadcasts (`SmsReceiver.kt`) and quick-reply dispatches (`HeadlessSmsSendService.kt`)
- **Dual SQLite WAL & CSV Storage:** Cloud backend manages synchronized storage in `contacts.db` and dual CSV mirrors (`contacts.csv`, `sms.csv`).
- **Cloud Explorer Screen:** Embedded search and remote data viewer for contacts and SMS logs with pagination, search query filtering, and local comparison.

### 4. Intelligent SMS Ledger & Default SMS Capabilities
- Ingests incoming SMS PDUs into an atomic, indexed SQLite table (`sms_ledger`).
- **One-Tap OTP Extraction:** Automatically identifies 4–8 digit verification codes with a one-tap copy button.
- **Transaction Detection:** Color-coded badges for debit (`-₹`) and credit (`+₹`) alerts.
- **Full Android 14 Telephony Support:** Qualified default SMS handler with `SmsReceiver` and `HeadlessSmsSendService` (`RESPOND_VIA_MESSAGE`).

### 5. Universal Backup & Restore
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

- **Production Minified Signed APK:** [`release/app-manager-v4.0.apk`](file:///d:/GPU-D/apps/Contact/release/app-manager-v4.0.apk) (1.91 MB)
- **Unit Test Suite:** 9 Suites, 61 Tests passing with 100% pass rate.

---

## ☁️ Hostinger Backend Deployment

- **Single Engine Script:** [`cloud_sync/index.php`](file:///d:/GPU-D/apps/Contact/cloud_sync/index.php)
- **Production Endpoint:** `https://white-jackal-528611.hostingersite.com/contacts/index.php`
- **Default Auth Token:** `Jmd@2025`
- **Actions Supported:**
  - `list_csvs` — Inventory of CSVs and SQLite databases
  - `get_cloud_overview` — Global contact and SMS telemetry
  - `get_cloud_contacts` — Paginated contact search
  - `get_cloud_sms` — Paginated SMS log search with search filter `q`
  - `sync_contacts` — Batch contact push/merge
  - `sync_sms` — Batch SMS push/merge
  - `clean_duplicates` — Server-side duplicate cleanup
  - `clean_sms_contacts` — Synthetic contact purge
