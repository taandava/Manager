<?php
// index.php - Bento Glass 2.0 Unified Cloud Engine & Mobile Web Admin
// Strictly Mobile-First & Mobile-Only UI | Edge-to-Edge Responsive
// Deep Telephony & Contact Hardening: Complete SMS Contact Extraction Ban & Auto-Purge
ini_set('memory_limit', '512M');
set_time_limit(300);

if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

// ============================================================================
// CORS — Full Cross-Origin Resource Sharing for Android Native Client
// ============================================================================
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Accept, Authorization, User-Agent');
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

$secretToken     = 'Jmd@2025';
$standardHeaders = ['id', 'name', 'phone', 'details', 'saved', 'updated_at', 'deleted', 'device_id'];
$targetCsv       = 'contacts.csv';
$smsFile         = 'sms.csv';

// ============================================================================
// UTILITY & SANITIZATION FUNCTIONS
// ============================================================================
function normalizePhone($phone) {
    if (empty($phone)) return '';
    $trimmed = trim($phone);
    $digits  = preg_replace('/[^0-9+]/', '', $trimmed);
    if (strpos($digits, '00') === 0) {
        $digits = '+' . substr($digits, 2);
    }
    if (preg_match('/^[0-9]{10}$/', $digits)) {
        $digits = '+91' . $digits;
    } elseif (preg_match('/^91[0-9]{10}$/', $digits)) {
        $digits = '+' . $digits;
    }
    return empty($digits) ? $trimmed : $digits;
}

/**
 * Strict validator: identifies commercial TRAI SMS sender headers or synthetic contacts
 * e.g., AD-AXISBK-S, VM-HDFCBK, SMS-ALERT, OTP-VERIFY, or bodies marked Discovered via SMS.
 */
function isSmsHeaderOrSyntheticContact($name, $phone = '', $details = '') {
    $tName = trim((string)$name);
    $tPhone = trim((string)$phone);
    $tDetails = trim((string)$details);

    if (empty($tName) && empty($tPhone)) return true;

    // Reject any entry marked with SMS details
    if (!empty($tDetails)) {
        if (stripos($tDetails, 'SMS') !== false ||
            stripos($tDetails, 'Discovered via SMS') !== false ||
            stripos($tDetails, 'SMS_SCAN') !== false ||
            stripos($tDetails, 'SMS Body') !== false ||
            stripos($tDetails, 'source:SMS') !== false) {
            return true;
        }
    }

    if (empty($tName)) return false;

    // TRAI format: 2 alpha chars, hyphen, alphanumeric code, optional sub-code (e.g. AD-AXISBK)
    if (preg_match('/^[A-Za-z]{2}-[A-Za-z0-9]{3,14}(?:-[A-Za-z0-9]+)?$/', $tName)) {
        return true;
    }

    // Generic SMS/OTP prefixes
    if (preg_match('/^(?:SMS|MSG|OTP|TXN|VK|BZ|JM|AD|VM|AX|CP|BW|MD|BP)[-_]/i', $tName)) {
        return true;
    }

    // Rule: Never extract contacts from SMS data.
    // If name contains no letters at all (purely digits, symbols, punctuation)
    // Check if name is a DIFFERENT phone number than the actual contact phone (synthetic SMS extracted pair)
    $hasLetters = preg_match('/[\p{L}]/u', $tName);
    if (!$hasLetters) {
        $normName = normalizePhone($tName);
        $normPhone = normalizePhone($tPhone);
        if (!empty($normName) && !empty($normPhone) && $normName !== $normPhone) {
            return true;
        }
    }

    return false;
}

function sanitizeCsvFileName($name) {
    if (empty($name)) return 'contacts.csv';
    $name  = basename(trim($name));
    $clean = preg_replace('/[^a-zA-Z0-9_\-\.]/', '_', $name);
    if (!preg_match('/\.csv$/i', $clean)) {
        $clean .= '.csv';
    }
    return $clean;
}

function listAvailableCsvs() {
    $files = glob('*.csv');
    if (!$files) return ['contacts.csv', 'sms.csv'];
    $res = [];
    foreach ($files as $f) {
        $res[] = basename($f);
    }
    if (!in_array('contacts.csv', $res)) array_unshift($res, 'contacts.csv');
    return array_values(array_unique($res));
}

function writeCsvFile($path, $headers, $rows) {
    $fp = fopen($path, 'w');
    if (!$fp) return false;
    fputcsv($fp, $headers);
    foreach ($rows as $r) {
        fputcsv($fp, array_values($r));
    }
    fclose($fp);
    return true;
}

function readCsvRows($path) {
    $rows = [];
    if (!file_exists($path)) return $rows;
    $fp = fopen($path, 'r');
    if (!$fp) return $rows;
    fgetcsv($fp, 0, ',', '"', "\\"); // skip header
    while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== false) {
        if (count($data) >= 8) {
            $rows[strval($data[0])] = [
                'id'         => strval($data[0]),
                'name'       => strval($data[1]),
                'phone'      => normalizePhone($data[2]),
                'details'    => strval($data[3]),
                'saved'      => strval($data[4]),
                'updated_at' => strval($data[5]),
                'deleted'    => strval($data[6]),
                'device_id'  => strval($data[7]),
            ];
        }
    }
    fclose($fp);
    return $rows;
}

function purgeSmsContactsFromCsv($path) {
    if (!file_exists($path)) return 0;
    $rows = readCsvRows($path);
    $purged = 0;
    foreach ($rows as $id => $r) {
        if (isSmsHeaderOrSyntheticContact($r['name'], $r['phone'], $r['details'])) {
            unset($rows[$id]);
            $purged++;
        }
    }
    if ($purged > 0) {
        global $standardHeaders;
        writeCsvFile($path, $standardHeaders, $rows);
    }
    return $purged;
}

// Ensure the contacts CSV exists with standard headers
if (!file_exists($targetCsv)) {
    $fp = fopen($targetCsv, 'w');
    if ($fp) { fputcsv($fp, $standardHeaders); fclose($fp); }
}

// ============================================================================
// SQLITE DATABASE ENGINE & SEAMLESS CSV DUAL-SYNC
// ============================================================================
$dbFile = __DIR__ . '/contacts.db';

function getDb() {
    static $pdo = null;
    if ($pdo !== null) return $pdo;
    $dbFile = __DIR__ . '/contacts.db';
    if (!class_exists('PDO') || !in_array('sqlite', PDO::getAvailableDrivers())) {
        return null;
    }
    try {
        $pdo = new PDO('sqlite:' . $dbFile);
        $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
        $pdo->exec('PRAGMA journal_mode = WAL;');
        $pdo->exec('PRAGMA synchronous = NORMAL;');

        // 1. Contacts table
        $pdo->exec("CREATE TABLE IF NOT EXISTS contacts (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL DEFAULT '',
            phone TEXT NOT NULL DEFAULT '',
            details TEXT NOT NULL DEFAULT '',
            saved INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL DEFAULT 0,
            deleted INTEGER NOT NULL DEFAULT 0,
            device_id TEXT NOT NULL DEFAULT ''
        );");
        $pdo->exec("CREATE INDEX IF NOT EXISTS idx_c_phone ON contacts(phone);");
        $pdo->exec("CREATE INDEX IF NOT EXISTS idx_c_deleted ON contacts(deleted);");
        $pdo->exec("CREATE INDEX IF NOT EXISTS idx_c_updated ON contacts(updated_at);");

        // 2. SMS Ledger table
        $pdo->exec("CREATE TABLE IF NOT EXISTS sms (
            id TEXT PRIMARY KEY,
            address TEXT NOT NULL DEFAULT '',
            normalized_number TEXT NOT NULL DEFAULT '',
            body TEXT NOT NULL DEFAULT '',
            date INTEGER NOT NULL DEFAULT 0,
            type TEXT NOT NULL DEFAULT 'INBOX',
            device_id TEXT NOT NULL DEFAULT ''
        );");
        $pdo->exec("CREATE INDEX IF NOT EXISTS idx_s_date ON sms(date DESC);");
        $pdo->exec("CREATE INDEX IF NOT EXISTS idx_s_norm ON sms(normalized_number);");

        // Auto-migrate contacts.csv into contacts.db if DB is empty
        $cnt = $pdo->query("SELECT COUNT(*) FROM contacts")->fetchColumn();
        if ($cnt == 0 && file_exists(__DIR__ . '/contacts.csv')) {
            migrateCsvToDb($pdo, __DIR__ . '/contacts.csv');
        }

        // Auto-migrate sms.csv into sms table if DB is empty
        $smsCnt = $pdo->query("SELECT COUNT(*) FROM sms")->fetchColumn();
        if ($smsCnt == 0 && file_exists(__DIR__ . '/sms.csv')) {
            migrateSmsCsvToDb($pdo, __DIR__ . '/sms.csv');
        }

        return $pdo;
    } catch (Exception $e) {
        error_log("SQLite DB error: " . $e->getMessage());
        return null;
    }
}

function migrateCsvToDb($pdo, $csvPath) {
    if (!file_exists($csvPath)) return;
    $fp = fopen($csvPath, 'r');
    if (!$fp) return;
    fgetcsv($fp, 0, ',', '"', "\\");
    $stmt = $pdo->prepare("INSERT OR REPLACE INTO contacts (id, name, phone, details, saved, updated_at, deleted, device_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
    $pdo->beginTransaction();
    while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== false) {
        if (count($data) >= 8) {
            $id = strval($data[0]);
            $name = strval($data[1]);
            $phone = normalizePhone($data[2]);
            $details = strval($data[3]);
            $saved = ($data[4] === '1' || strtolower($data[4]) === 'true') ? 1 : 0;
            $updated = intval($data[5]) ?: round(microtime(true) * 1000);
            $deleted = ($data[6] === '1' || strtolower($data[6]) === 'true') ? 1 : 0;
            $deviceId = strval($data[7]) ?: 'HOSTINGER';
            $stmt->execute([$id, $name, $phone, $details, $saved, $updated, $deleted, $deviceId]);
        } elseif (count($data) >= 2) {
            $name = trim($data[0]);
            $phone = normalizePhone($data[1]);
            if (!empty($name) || !empty($phone)) {
                $id = md5($name . '|' . $phone);
                $stmt->execute([$id, $name, $phone, '', 0, round(microtime(true) * 1000), 0, 'HOSTINGER']);
            }
        }
    }
    $pdo->commit();
    fclose($fp);
}

function migrateSmsCsvToDb($pdo, $smsPath) {
    if (!file_exists($smsPath)) return;
    $fp = fopen($smsPath, 'r');
    if (!$fp) return;
    fgetcsv($fp, 0, ',', '"', "\\");
    $stmt = $pdo->prepare("INSERT OR IGNORE INTO sms (id, address, normalized_number, body, date, type, device_id) VALUES (?, ?, ?, ?, ?, ?, ?)");
    $pdo->beginTransaction();
    while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== false) {
        if (count($data) >= 8) {
            $id = strval($data[1]);
            $addr = strval($data[3]);
            $norm = strval($data[4]) ?: normalizePhone($addr);
            $body = strval($data[7]);
            $date = intval($data[8] ?? ($data[5] ?? round(microtime(true) * 1000)));
            $details = strval($data[9] ?? '');
            $type = (stripos($details, 'SENT') !== false) ? 'SENT' : 'INBOX';
            $dev = 'IMPORTED';
            $stmt->execute([$id, $addr, $norm, $body, $date, $type, $dev]);
        }
    }
    $pdo->commit();
    fclose($fp);
}

function exportContactsDbToCsv($pdo, $csvPath) {
    global $standardHeaders;
    $stmt = $pdo->query("SELECT id, name, phone, details, saved, updated_at, deleted, device_id FROM contacts WHERE deleted = 0 ORDER BY name ASC");
    $rows = $stmt->fetchAll();
    $fp = fopen($csvPath, 'w');
    if ($fp) {
        fputcsv($fp, $standardHeaders);
        foreach ($rows as $r) {
            fputcsv($fp, [
                $r['id'],
                $r['name'],
                $r['phone'],
                $r['details'],
                $r['saved'] ? '1' : '0',
                $r['updated_at'],
                '0',
                $r['device_id']
            ]);
        }
        fclose($fp);
    }
}

function exportSmsDbToCsv($pdo, $csvPath) {
    $smsHeaders = ['RECORD_TYPE', 'ID', 'NAME', 'PHONE', 'NORMALIZED_NUMBER', 'SOURCE', 'CONSENT', 'STATUS_OR_BODY', 'TIMESTAMP', 'DETAILS'];
    $stmt = $pdo->query("SELECT id, address, normalized_number, body, date, type, device_id FROM sms ORDER BY date DESC");
    $rows = $stmt->fetchAll();
    $fp = fopen($csvPath, 'w');
    if ($fp) {
        fputcsv($fp, $smsHeaders);
        foreach ($rows as $r) {
            $addr = $r['address'];
            $norm = $r['normalized_number'] ?: normalizePhone($addr);
            $type = $r['type'] ?: 'INBOX';
            $dev  = $r['device_id'] ?: 'UNKNOWN';
            fputcsv($fp, [
                'SMS',
                $r['id'],
                '',
                $addr,
                $norm,
                'DEVICE_SMS',
                'CONSENTED',
                $r['body'],
                $r['date'],
                "Type:{$type}|Device:{$dev}"
            ]);
        }
        fclose($fp);
    }
}

// ============================================================================
// AUTHENTICATION & REQUEST PARSING
// ============================================================================
$token     = $_GET['token'] ?? ($_POST['token'] ?? '');
$inputJSON = file_get_contents('php://input');
$input     = json_decode($inputJSON, true);
if (is_array($input) && isset($input['token'])) {
    $token = $input['token'];
}
if (empty($token) && !empty($_SESSION['auth_token'])) {
    $token = $_SESSION['auth_token'];
}

// Detect API request: explicit format, action, accept header, user-agent or JSON payload
$isApiRequest = (
    (isset($_GET['format']) && strtolower($_GET['format']) === 'json') ||
    isset($_GET['action']) ||
    (isset($_SERVER['HTTP_ACCEPT']) && strpos($_SERVER['HTTP_ACCEPT'], 'application/json') !== false) ||
    (isset($_SERVER['HTTP_USER_AGENT']) && strpos($_SERVER['HTTP_USER_AGENT'], 'ContactFinder') !== false) ||
    (!empty($inputJSON) && !isset($_POST['login_token']) && !isset($_POST['save_table']) && !isset($_POST['add_contact']) && !isset($_POST['delete_id']) && !isset($_POST['clean_duplicates']) && !isset($_POST['clean_sms_contacts']))
);

// ============================================================================
// API ENDPOINTS (JSON)
// ============================================================================
if ($isApiRequest) {
    header('Content-Type: application/json; charset=utf-8');

    if ($token !== $secretToken) {
        echo json_encode(['ok' => false, 'error' => 'Unauthorized: Invalid token']);
        exit;
    }

    $action = $_GET['action'] ?? ($input['action'] ?? '');

    // ── 0a. Cloud Overview Stats ─────────────────────────────────────────────
    if ($action === 'get_cloud_overview') {
        $pdo = getDb();
        $totalContacts = 0;
        $totalSms = 0;
        $uniquePhones = 0;
        $devices = [];
        $dbSize = file_exists('contacts.db') ? filesize('contacts.db') : (file_exists($targetCsv) ? filesize($targetCsv) : 0);
        $lastUpdated = round(microtime(true) * 1000);

        if ($pdo) {
            $totalContacts = (int)$pdo->query("SELECT COUNT(*) FROM contacts WHERE deleted = 0")->fetchColumn();
            $totalSms = (int)$pdo->query("SELECT COUNT(*) FROM sms")->fetchColumn();
            $uniquePhones = (int)$pdo->query("SELECT COUNT(DISTINCT phone) FROM contacts WHERE deleted = 0 AND phone != ''")->fetchColumn();
            $devStmt = $pdo->query("SELECT DISTINCT device_id FROM (SELECT device_id FROM contacts WHERE device_id != '' UNION SELECT device_id FROM sms WHERE device_id != '')");
            $devices = $devStmt->fetchAll(PDO::FETCH_COLUMN) ?: [];
            $maxTime = $pdo->query("SELECT MAX(updated_at) FROM contacts")->fetchColumn();
            if ($maxTime) $lastUpdated = (int)$maxTime;
        } else {
            $rows = readCsvRows($targetCsv);
            $totalContacts = count($rows);
            $seen = [];
            foreach ($rows as $r) {
                if (!empty($r['phone'])) $seen[$r['phone']] = true;
                if (!empty($r['device_id']) && !in_array($r['device_id'], $devices)) $devices[] = $r['device_id'];
            }
            $uniquePhones = count($seen);
        }

        echo json_encode([
            'ok'             => true,
            'total_contacts' => $totalContacts,
            'total_sms'      => $totalSms,
            'unique_phones'  => $uniquePhones,
            'devices'        => $devices,
            'db_size_bytes'  => $dbSize,
            'last_updated'   => $lastUpdated,
        ]);
        exit;
    }

    // ── 0b. Cloud Contacts Search & List ────────────────────────────────────
    if ($action === 'get_cloud_contacts') {
        $q      = trim($_GET['q'] ?? ($input['q'] ?? ''));
        $limit  = min(1000, max(1, intval($_GET['limit'] ?? ($input['limit'] ?? 100))));
        $offset = max(0, intval($_GET['offset'] ?? ($input['offset'] ?? 0)));
        $saved  = isset($_GET['saved_only']) ? intval($_GET['saved_only']) : null;

        $pdo = getDb();
        $rows = [];
        $total = 0;

        if ($pdo) {
            $where = ["deleted = 0"];
            $params = [];

            if (!empty($q)) {
                $where[] = "(name LIKE :q OR phone LIKE :q OR details LIKE :q OR device_id LIKE :q)";
                $params[':q'] = "%{$q}%";
            }
            if ($saved !== null && $saved == 1) {
                $where[] = "saved = 1";
            }

            $whereSql = implode(' AND ', $where);
            $countStmt = $pdo->prepare("SELECT COUNT(*) FROM contacts WHERE {$whereSql}");
            $countStmt->execute($params);
            $total = (int)$countStmt->fetchColumn();

            $sql = "SELECT id, name, phone, details, saved, updated_at, deleted, device_id 
                    FROM contacts WHERE {$whereSql} 
                    ORDER BY 
                        saved DESC,
                        (CASE 
                            WHEN name IS NULL OR TRIM(name) = '' THEN 3
                            WHEN substr(trim(name), 1, 1) IN ('+','0','1','2','3','4','5','6','7','8','9','-','_','.','*','#','(',')') THEN 2
                            ELSE 1
                        END) ASC,
                        name COLLATE NOCASE ASC 
                    LIMIT {$limit} OFFSET {$offset}";
            $stmt = $pdo->prepare($sql);
            $stmt->execute($params);
            $all = $stmt->fetchAll();

            foreach ($all as $r) {
                $cName = trim(strval($r['name']));
                $cPhone = normalizePhone($r['phone']);
                $cDetails = strval($r['details']);
                $displayName = !empty($cName) ? $cName : ($cPhone ?: "Contact");

                $rows[] = [
                    'id'         => strval($r['id']),
                    'name'       => $displayName,
                    'phone'      => $cPhone,
                    'details'    => $cDetails,
                    'saved'      => (bool)$r['saved'],
                    'updated_at' => intval($r['updated_at']),
                    'deleted'    => false,
                    'device_id'  => strval($r['device_id']),
                ];
            }
        } else {
            $allRows = readCsvRows($targetCsv);
            foreach ($allRows as $r) {
                if (!empty($q)) {
                    $haystack = strtolower($r['name'].' '.$r['phone'].' '.$r['details'].' '.$r['device_id']);
                    if (strpos($haystack, strtolower($q)) === false) continue;
                }
                if ($saved !== null && $saved == 1 && $r['saved'] !== '1') continue;

                $cName = trim(strval($r['name']));
                $cPhone = normalizePhone($r['phone']);
                $displayName = !empty($cName) ? $cName : ($cPhone ?: "Contact");

                $rows[] = [
                    'id'         => $r['id'],
                    'name'       => $displayName,
                    'phone'      => $cPhone,
                    'details'    => $r['details'],
                    'saved'      => ($r['saved'] === '1' || $r['saved'] === true),
                    'updated_at' => intval($r['updated_at']),
                    'deleted'    => false,
                    'device_id'  => $r['device_id'],
                ];
            }
            usort($rows, function($a, $b) {
                if ($a['saved'] !== $b['saved']) return $b['saved'] ? -1 : 1;
                $nameA = trim((string)$a['name']);
                $nameB = trim((string)$b['name']);
                $firstCharA = $nameA !== '' ? $nameA[0] : '';
                $firstCharB = $nameB !== '' ? $nameB[0] : '';
                $syms = ['+','0','1','2','3','4','5','6','7','8','9','-','_','.','*','#','(',')'];
                $typeA = ($nameA === '') ? 3 : (in_array($firstCharA, $syms, true) ? 2 : 1);
                $typeB = ($nameB === '') ? 3 : (in_array($firstCharB, $syms, true) ? 2 : 1);
                if ($typeA !== $typeB) return $typeA - $typeB;
                return strcasecmp($nameA, $nameB);
            });
            $total = count($rows);
            $rows = array_slice($rows, $offset, $limit);
        }

        echo json_encode([
            'ok'     => true,
            'total'  => $total,
            'limit'  => $limit,
            'offset' => $offset,
            'rows'   => $rows,
        ]);
        exit;
    }

    // ── 0c. Cloud SMS Search & List ──────────────────────────────────────────
    if ($action === 'get_cloud_sms') {
        $q      = trim($_GET['q'] ?? ($input['q'] ?? ''));
        $limit  = min(1000, max(1, intval($_GET['limit'] ?? ($input['limit'] ?? 100))));
        $offset = max(0, intval($_GET['offset'] ?? ($input['offset'] ?? 0)));
        $type   = trim($_GET['type'] ?? ($input['type'] ?? ''));

        $pdo = getDb();
        $rows = [];
        $total = 0;

        if ($pdo) {
            $where = ["1=1"];
            $params = [];

            if (!empty($q)) {
                $where[] = "(address LIKE :q OR normalized_number LIKE :q OR body LIKE :q OR device_id LIKE :q)";
                $params[':q'] = "%{$q}%";
            }
            if (!empty($type) && in_array(strtoupper($type), ['INBOX', 'SENT'])) {
                $where[] = "type = :type";
                $params[':type'] = strtoupper($type);
            }

            $whereSql = implode(' AND ', $where);
            $countStmt = $pdo->prepare("SELECT COUNT(*) FROM sms WHERE {$whereSql}");
            $countStmt->execute($params);
            $total = (int)$countStmt->fetchColumn();

            $sql = "SELECT id, address, normalized_number, body, date, type, device_id 
                    FROM sms WHERE {$whereSql} 
                    ORDER BY date DESC 
                    LIMIT {$limit} OFFSET {$offset}";
            $stmt = $pdo->prepare($sql);
            $stmt->execute($params);
            $all = $stmt->fetchAll();

            foreach ($all as $s) {
                $rows[] = [
                    'id'                => strval($s['id']),
                    'address'           => strval($s['address']),
                    'normalized_number' => strval($s['normalized_number']),
                    'body'              => strval($s['body']),
                    'date'              => intval($s['date']),
                    'type'              => strval($s['type'] ?: 'INBOX'),
                    'device_id'         => strval($s['device_id']),
                ];
            }
        }
        echo json_encode([
            'ok'     => true,
            'total'  => $total,
            'limit'  => $limit,
            'offset' => $offset,
            'rows'   => $rows,
        ]);
        exit;
    }

    // ── 1. List all CSV files & DB status ────────────────────────────────────
    if ($action === 'list_csvs') {
        $files   = listAvailableCsvs();
        $details = [];
        foreach ($files as $f) {
            $details[] = [
                'name'     => $f,
                'size'     => file_exists($f) ? filesize($f) : 0,
                'modified' => file_exists($f) ? date('Y-m-d H:i:s', filemtime($f)) : '',
            ];
        }
        if (file_exists('contacts.db')) {
            $details[] = [
                'name'     => 'contacts.db',
                'size'     => filesize('contacts.db'),
                'modified' => date('Y-m-d H:i:s', filemtime('contacts.db')),
            ];
        }
        echo json_encode(['ok' => true, 'files' => $files, 'details' => $details]);
        exit;
    }

    // ── 2. SMS log upload & sync ─────────────────────────────────────────────
    if ($action === 'sync_sms') {
        $smsRecords = (is_array($input) && isset($input['sms_records'])) ? $input['sms_records'] : [];
        $devId      = (is_array($input) && isset($input['device_id']))   ? $input['device_id']   : 'UNKNOWN_DEVICE';
        $smsHeaders = ['RECORD_TYPE', 'ID', 'NAME', 'PHONE', 'NORMALIZED_NUMBER', 'SOURCE', 'CONSENT', 'STATUS_OR_BODY', 'TIMESTAMP', 'DETAILS'];
        $added      = 0;

        $pdo = getDb();
        if ($pdo) {
            $stmt = $pdo->prepare("INSERT OR IGNORE INTO sms (id, address, normalized_number, body, date, type, device_id) VALUES (?, ?, ?, ?, ?, ?, ?)");
            $pdo->beginTransaction();
            foreach ($smsRecords as $s) {
                $id   = strval($s['id'] ?? uniqid());
                $addr = strval($s['address'] ?? '');
                $body = strval($s['body'] ?? '');
                $date = intval($s['date'] ?? round(microtime(true) * 1000));
                $type = strval($s['type'] ?? 'INBOX');
                $norm = normalizePhone($addr);
                $recDev = strval($s['device_id'] ?? $devId);
                $stmt->execute([$id, $addr, $norm, $body, $date, $type, $recDev]);
                if ($stmt->rowCount() > 0) $added++;
            }
            $pdo->commit();
        }

        // Also append to sms.csv for backup
        if (!file_exists($smsFile)) {
            $fp = fopen($smsFile, 'w'); fputcsv($fp, $smsHeaders); fclose($fp);
        }

        $existingIds = [];
        $fp2 = fopen($smsFile, 'r');
        if ($fp2) {
            fgetcsv($fp2, 0, ',', '"', "\\");
            while (($row = fgetcsv($fp2, 0, ',', '"', "\\")) !== false) {
                if (count($row) >= 2) $existingIds[$row[1]] = true;
            }
            fclose($fp2);
        }

        $fp = fopen($smsFile, 'a');
        if ($fp) {
            foreach ($smsRecords as $s) {
                $id   = $s['id']      ?? uniqid();
                $addr = $s['address'] ?? '';
                $body = $s['body']    ?? '';
                $date = $s['date']    ?? round(microtime(true) * 1000);
                $type = $s['type']    ?? 'INBOX';
                $norm = normalizePhone($addr);
                if (isset($existingIds[$id])) continue;
                fputcsv($fp, ['SMS', $id, '', $addr, $norm, 'DEVICE_SMS', 'CONSENTED', $body, $date, "Type:{$type}|Device:{$devId}"]);
                $existingIds[$id] = true;
            }
            fclose($fp);
        }

        echo json_encode(['ok' => true, 'message' => "Synced {$added} SMS records into database", 'total_synced' => $added, 'file' => $smsFile]);
        exit;
    }

    // ── 3. Purge SMS header synthetic contacts from cloud storage ─────────────
    if ($action === 'clean_sms_contacts') {
        $pdo = getDb();
        $purgedCount = 0;
        if ($pdo) {
            $stmt = $pdo->query("SELECT id, name, phone, details FROM contacts WHERE deleted = 0");
            $all = $stmt->fetchAll();
            $delStmt = $pdo->prepare("DELETE FROM contacts WHERE id = ?");
            $pdo->beginTransaction();
            foreach ($all as $r) {
                if (isSmsHeaderOrSyntheticContact($r['name'], $r['phone'], $r['details'])) {
                    $delStmt->execute([$r['id']]);
                    $purgedCount++;
                }
            }
            $pdo->commit();
            exportContactsDbToCsv($pdo, $targetCsv);
        } else {
            $purgedCount = purgeSmsContactsFromCsv($targetCsv);
        }
        echo json_encode(['ok' => true, 'message' => "Purged {$purgedCount} synthetic SMS contacts", 'purged' => $purgedCount]);
        exit;
    }

    // ── 3b. Clean duplicate contacts from cloud database ──
    if ($action === 'clean_duplicates') {
        $pdo = getDb();
        $cleanedCount = 0;
        if ($pdo) {
            $stmt = $pdo->query("SELECT id, name, phone, details, saved, updated_at, device_id FROM contacts WHERE deleted = 0 ORDER BY updated_at DESC");
            $all = $stmt->fetchAll();
            $groups = [];
            foreach ($all as $r) {
                $norm = normalizePhone($r['phone']);
                if (empty($norm)) continue;
                $groups[$norm][] = $r;
            }
            $delStmt = $pdo->prepare("DELETE FROM contacts WHERE id = ?");
            $pdo->beginTransaction();
            foreach ($groups as $norm => $list) {
                if (count($list) <= 1) continue;
                usort($list, function($a, $b) {
                    if ($a['saved'] != $b['saved']) return $b['saved'] - $a['saved'];
                    $lenA = strlen(trim($a['name']));
                    $lenB = strlen(trim($b['name']));
                    if ($lenA != $lenB) return $lenB - $lenA;
                    return $b['updated_at'] - $a['updated_at'];
                });
                for ($i = 1; $i < count($list); $i++) {
                    $delStmt->execute([$list[$i]['id']]);
                    $cleanedCount++;
                }
            }
            $pdo->commit();
            exportContactsDbToCsv($pdo, $targetCsv);
        }
        echo json_encode(['ok' => true, 'message' => "Cleaned {$cleanedCount} duplicate contacts from cloud", 'cleaned' => $cleanedCount]);
        exit;
    }

    // ── 4. GET contacts from database / CSV ──────────────────────────────────
    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $q = trim($_GET['q'] ?? '');
        $rows = [];
        $pdo = getDb();
        if ($pdo) {
            if (!empty($q)) {
                $stmt = $pdo->prepare("SELECT id, name, phone, details, saved, updated_at, deleted, device_id 
                                       FROM contacts 
                                       WHERE deleted = 0 AND (name LIKE :q OR phone LIKE :q OR details LIKE :q OR device_id LIKE :q) 
                                       ORDER BY name ASC");
                $stmt->execute([':q' => "%{$q}%"]);
            } else {
                $stmt = $pdo->query("SELECT id, name, phone, details, saved, updated_at, deleted, device_id FROM contacts WHERE deleted = 0 ORDER BY name ASC");
            }
            $all = $stmt->fetchAll();
            foreach ($all as $r) {
                $cName    = strval($r['name']);
                $cPhone   = normalizePhone($r['phone']);
                $cDetails = strval($r['details']);

                // STRICT FILTER: Never return SMS sender headers or synthetic SMS entries to the app
                if (isSmsHeaderOrSyntheticContact($cName, $cPhone, $cDetails)) {
                    continue;
                }

                $rows[] = [
                    'id'         => strval($r['id']),
                    'name'       => $cName,
                    'phone'      => $cPhone,
                    'details'    => $cDetails,
                    'saved'      => (bool)$r['saved'],
                    'updated_at' => strval($r['updated_at']),
                    'deleted'    => false,
                    'device_id'  => strval($r['device_id']),
                ];
            }
        } elseif (($fp = fopen($targetCsv, 'r')) !== false) {
            fgetcsv($fp, 0, ',', '"', "\\");
            while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== false) {
                if (count($data) >= 8) {
                    $deleted = ($data[6] === '1' || strtolower($data[6]) === 'true');
                    if ($deleted) continue;

                    $cName    = strval($data[1]);
                    $cPhone   = normalizePhone($data[2]);
                    $cDetails = strval($data[3]);

                    if (isSmsHeaderOrSyntheticContact($cName, $cPhone, $cDetails)) continue;

                    $rows[] = [
                        'id'         => strval($data[0]),
                        'name'       => $cName,
                        'phone'      => $cPhone,
                        'details'    => $cDetails,
                        'saved'      => ($data[4] === '1' || strtolower($data[4]) === 'true'),
                        'updated_at' => strval($data[5]),
                        'deleted'    => false,
                        'device_id'  => strval($data[7]),
                    ];
                } elseif (count($data) >= 2) {
                    $name  = trim($data[0]);
                    $phone = normalizePhone($data[1]);
                    if ((!empty($name) || !empty($phone)) && !isSmsHeaderOrSyntheticContact($name, $phone)) {
                        $id     = md5($name . '|' . $phone);
                        $rows[] = [
                            'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => '',
                            'saved' => false, 'updated_at' => strval(round(microtime(true) * 1000)),
                            'deleted' => false, 'device_id' => 'HOSTINGER',
                        ];
                    }
                }
            }
            fclose($fp);
        }
        echo json_encode(['ok' => true, 'database' => 'contacts.db', 'file' => $targetCsv, 'total' => count($rows), 'rows' => $rows]);
        exit;
    }

    // ── 5. POST changes to database / CSV ────────────────────────────────────
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $pdo        = getDb();
        $incoming   = $input['contacts'] ?? [];
        $deletedIds = $input['deleted_ids'] ?? [];
        $devId      = $input['device_id'] ?? 'APP_USER';
        $inserted   = $updated = $deletedCount = 0;

        if ($pdo) {
            $pdo->beginTransaction();

            // 1. Process explicit deleted IDs (from client deletes and merges)
            if (!empty($deletedIds)) {
                $delStmt = $pdo->prepare("DELETE FROM contacts WHERE id = ?");
                foreach ($deletedIds as $dId) {
                    $delStmt->execute([strval($dId)]);
                    $deletedCount += $delStmt->rowCount();
                }
            }

            // 2. Process incoming contacts
            $upsertStmt = $pdo->prepare("
                INSERT INTO contacts (id, name, phone, details, saved, updated_at, deleted, device_id)
                VALUES (:id, :name, :phone, :details, :saved, :updated_at, 0, :device_id)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    phone = excluded.phone,
                    details = excluded.details,
                    saved = excluded.saved,
                    updated_at = excluded.updated_at,
                    deleted = 0,
                    device_id = excluded.device_id
                WHERE excluded.updated_at >= contacts.updated_at
            ");
            $delSingleStmt = $pdo->prepare("DELETE FROM contacts WHERE id = ?");
            $dedupePhoneStmt = $pdo->prepare("DELETE FROM contacts WHERE phone = :phone AND id != :survivor_id");

            foreach ($incoming as $c) {
                if (!isset($c['id'])) continue;
                $id    = strval($c['id']);
                $isDel = (isset($c['deleted']) && ($c['deleted'] === true || $c['deleted'] === '1' || $c['deleted'] === 1));

                if ($isDel) {
                    $delSingleStmt->execute([$id]);
                    $deletedCount += $delSingleStmt->rowCount();
                    continue;
                }

                $name    = trim($c['name']    ?? '');
                $phone   = normalizePhone($c['phone'] ?? '');
                $details = trim($c['details'] ?? '');

                // STRICT REJECTION: Reject synthetic SMS contacts from being inserted
                if (isSmsHeaderOrSyntheticContact($name, $phone, $details)) {
                    $delSingleStmt->execute([$id]);
                    $deletedCount += $delSingleStmt->rowCount();
                    continue;
                }

                $newTime = intval($c['updated_at'] ?? round(microtime(true) * 1000));
                $saved   = (isset($c['saved']) && ($c['saved'] === true || $c['saved'] === '1' || $c['saved'] === 1)) ? 1 : 0;
                $recDev  = (!empty($c['device_id'])) ? $c['device_id'] : $devId;

                $upsertStmt->execute([
                    ':id'         => $id,
                    ':name'       => $name,
                    ':phone'      => $phone,
                    ':details'    => $details,
                    ':saved'      => $saved,
                    ':updated_at' => $newTime,
                    ':device_id'  => $recDev,
                ]);
                $inserted++;

                // Deep server deduplication: ensure no other rows exist for this phone number
                if (!empty($phone) && strlen($phone) >= 10) {
                    $dedupePhoneStmt->execute([
                        ':phone'       => $phone,
                        ':survivor_id' => $id,
                    ]);
                    $deletedCount += $dedupePhoneStmt->rowCount();
                }
            }

            $pdo->commit();
            exportContactsDbToCsv($pdo, $targetCsv);

            $totalInDb = $pdo->query("SELECT COUNT(*) FROM contacts WHERE deleted = 0")->fetchColumn();
            echo json_encode([
                'ok'            => true,
                'database'      => 'contacts.db',
                'file'          => $targetCsv,
                'inserted'      => $inserted,
                'updated'       => $updated,
                'deleted'       => $deletedCount,
                'total_in_file' => (int)$totalInDb,
            ]);
            exit;
        } else {
            // CSV Fallback
            $existing = readCsvRows($targetCsv);
            foreach ($deletedIds as $dId) {
                if (isset($existing[$dId])) { unset($existing[$dId]); $deletedCount++; }
            }
            foreach ($incoming as $c) {
                if (!isset($c['id'])) continue;
                $id    = strval($c['id']);
                $isDel = (isset($c['deleted']) && ($c['deleted'] === true || $c['deleted'] === '1' || $c['deleted'] === 1));

                if ($isDel) {
                    if (isset($existing[$id])) { unset($existing[$id]); $deletedCount++; }
                    continue;
                }

                $name    = trim($c['name']    ?? '');
                $phone   = normalizePhone($c['phone'] ?? '');
                $details = trim($c['details'] ?? '');

                if (isSmsHeaderOrSyntheticContact($name, $phone, $details)) {
                    if (isset($existing[$id])) { unset($existing[$id]); $deletedCount++; }
                    continue;
                }

                $newTime = strval($c['updated_at'] ?? round(microtime(true) * 1000));
                $saved   = (isset($c['saved']) && ($c['saved'] === true || $c['saved'] === '1' || $c['saved'] === 1)) ? '1' : '0';
                $recDev  = (!empty($c['device_id'])) ? $c['device_id'] : $devId;

                $existing[$id] = [
                    'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => $details,
                    'saved' => $saved, 'updated_at' => $newTime, 'deleted' => '0', 'device_id' => $recDev,
                ];
                $inserted++;
            }
            writeCsvFile($targetCsv, $standardHeaders, $existing);
            echo json_encode([
                'ok'            => true,
                'file'          => $targetCsv,
                'inserted'      => $inserted,
                'updated'       => $updated,
                'deleted'       => $deletedCount,
                'total_in_file' => count($existing),
            ]);
            exit;
        }
    }
}

// ============================================================================
// WEB ADMIN PORTAL — BENTO GLASS 2.0 (STRICTLY MOBILE-FIRST & MOBILE-ONLY)
// ============================================================================
if (isset($_GET['token']) && $_GET['token'] === $secretToken) {
    $_SESSION['auth_token'] = $secretToken;
}

if (isset($_POST['login_token'])) {
    if ($_POST['login_token'] === $secretToken) {
        $_SESSION['auth_token'] = $secretToken;
        header("Location: index.php?view=contacts");
        exit;
    } else {
        $loginError = "Authentication failed: Invalid Secret Token";
    }
}

if (isset($_GET['logout'])) {
    session_unset();
    session_destroy();
    header("Location: index.php");
    exit;
}

$isAuthenticated = (isset($_SESSION['auth_token']) && $_SESSION['auth_token'] === $secretToken);

// ── CSV File Export / Download ──────────────────────────────────────────────
if (isset($_GET['download']) && $isAuthenticated) {
    $dl = $_GET['download'];
    $dlFile = ($dl === 'sms' || $dl === 'sms.csv') ? 'sms.csv' : 'contacts.csv';
    $pdo = getDb();
    if ($pdo) {
        if ($dl === 'sms' || $dl === 'sms.csv') {
            exportSmsDbToCsv($pdo, $smsFile);
        } else {
            exportContactsDbToCsv($pdo, $targetCsv);
        }
    }
    if (file_exists($dlFile)) {
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="' . basename($dlFile) . '"');
        readfile($dlFile);
        exit;
    }
}

// ============================================================================
// LOGIN SCREEN — BENTO GLASS 2.0 MOBILE
// ============================================================================
if (!$isAuthenticated) { ?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
    <title>Contact Finder • Bento Glass Sign In</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-deep: #060913;
            --glass-card: rgba(15, 23, 42, 0.78);
            --glass-border: rgba(56, 189, 248, 0.16);
            --glass-glow: rgba(56, 189, 248, 0.25);
            --accent-cyan: #38BDF8;
            --accent-blue: #2563EB;
            --accent-mint: #34D399;
            --accent-coral: #F43F5E;
            --text-main: #F8FAFC;
            --text-muted: #94A3B8;
            --text-dim: #64748B;
            --input-bg: rgba(2, 6, 23, 0.7);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif; -webkit-tap-highlight-color: transparent; }
        body {
            background: radial-gradient(circle at 15% 15%, rgba(56, 189, 248, 0.12) 0%, transparent 45%),
                        radial-gradient(circle at 85% 85%, rgba(99, 102, 241, 0.12) 0%, transparent 45%),
                        var(--bg-deep);
            min-height: 100vh;
            width: 100%;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 16px;
            color: var(--text-main);
        }
        .login-bento-shell {
            width: 100%;
            background: var(--glass-card);
            backdrop-filter: blur(28px);
            -webkit-backdrop-filter: blur(28px);
            border: 1px solid var(--glass-border);
            border-radius: 28px;
            padding: 32px 24px;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.65), 0 0 0 1px rgba(255, 255, 255, 0.05);
            text-align: center;
            position: relative;
            overflow: hidden;
        }
        .login-bento-shell::before {
            content: '';
            position: absolute;
            top: 0; left: 0; right: 0;
            height: 2px;
            background: linear-gradient(90deg, transparent, var(--accent-cyan), transparent);
        }
        .brand-badge {
            width: 72px;
            height: 72px;
            background: linear-gradient(135deg, rgba(37, 99, 235, 0.8), rgba(56, 189, 248, 0.8));
            border: 1px solid rgba(255, 255, 255, 0.3);
            border-radius: 22px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 34px;
            margin: 0 auto 20px;
            box-shadow: 0 12px 28px -6px rgba(56, 189, 248, 0.4);
            animation: pulse-glow 3s infinite ease-in-out;
        }
        @keyframes pulse-glow {
            0%, 100% { transform: scale(1); box-shadow: 0 12px 28px -6px rgba(56, 189, 248, 0.4); }
            50% { transform: scale(1.03); box-shadow: 0 16px 36px -4px rgba(56, 189, 248, 0.6); }
        }
        h1 { font-size: 24px; font-weight: 800; letter-spacing: -0.5px; margin-bottom: 6px; }
        .subtitle { font-size: 13px; color: var(--text-muted); margin-bottom: 28px; }
        .bento-input-group {
            position: relative;
            margin-bottom: 20px;
            text-align: left;
        }
        .bento-input-shell {
            position: relative;
            display: flex;
            align-items: center;
            background: var(--input-bg);
            border: 1.5px solid var(--glass-border);
            border-radius: 16px;
            transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .bento-input-shell:focus-within {
            border-color: var(--accent-cyan);
            box-shadow: 0 0 0 4px var(--glass-glow);
            background: rgba(2, 6, 23, 0.9);
        }
        .input-lead-icon {
            padding-left: 16px;
            font-size: 18px;
            color: var(--text-dim);
            user-select: none;
        }
        .bento-input {
            width: 100%;
            padding: 18px 16px 14px 12px;
            background: transparent;
            border: none;
            outline: none;
            font-size: 15px;
            font-weight: 600;
            color: var(--text-main);
        }
        .bento-input::placeholder { color: transparent; }
        .bento-floating-label {
            position: absolute;
            left: 44px;
            top: 50%;
            transform: translateY(-50%);
            font-size: 14px;
            font-weight: 500;
            color: var(--text-dim);
            pointer-events: none;
            transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .bento-input:focus ~ .bento-floating-label,
        .bento-input:not(:placeholder-shown) ~ .bento-floating-label {
            top: 8px;
            font-size: 10px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--accent-cyan);
        }
        .btn-toggle-eye {
            padding-right: 16px;
            background: none;
            border: none;
            color: var(--text-dim);
            font-size: 18px;
            cursor: pointer;
            transition: color 0.2s;
        }
        .btn-toggle-eye:hover { color: var(--accent-cyan); }
        .bento-btn-primary {
            width: 100%;
            padding: 16px;
            background: linear-gradient(135deg, #0284C7 0%, #38BDF8 100%);
            color: #FFFFFF;
            border: none;
            border-radius: 16px;
            font-size: 15px;
            font-weight: 700;
            letter-spacing: 0.3px;
            cursor: pointer;
            box-shadow: 0 8px 24px -4px rgba(56, 189, 248, 0.4);
            transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }
        .bento-btn-primary:active {
            transform: scale(0.98);
        }
        .error-chip {
            background: rgba(244, 63, 94, 0.15);
            border: 1px solid rgba(244, 63, 94, 0.3);
            color: #FECDD3;
            padding: 12px 16px;
            border-radius: 14px;
            font-size: 13px;
            font-weight: 600;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .pill-env {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: rgba(255, 255, 255, 0.06);
            border: 1px solid rgba(255, 255, 255, 0.1);
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 11px;
            font-weight: 600;
            color: var(--accent-mint);
            margin-top: 24px;
        }
        .pulse-dot {
            width: 7px;
            height: 7px;
            border-radius: 50%;
            background: var(--accent-mint);
            box-shadow: 0 0 8px var(--accent-mint);
        }
    </style>
</head>
<body>
    <div class="login-bento-shell">
        <div class="brand-badge">📇</div>
        <h1>Contact Finder</h1>
        <p class="subtitle">Bento Glass 2.0 Cloud Synchronizer</p>

        <?php if (isset($loginError)): ?>
            <div class="error-chip">⚠️ <?php echo htmlspecialchars($loginError); ?></div>
        <?php endif; ?>

        <form method="POST">
            <div class="bento-input-group">
                <div class="bento-input-shell">
                    <span class="input-lead-icon">🔒</span>
                    <input type="password" id="login_token" name="login_token" placeholder="Token" required autofocus class="bento-input" autocomplete="current-password">
                    <label for="login_token" class="bento-floating-label">Master Cloud Secret</label>
                    <button type="button" class="btn-toggle-eye" onclick="togglePassVisibility()" id="eyeBtn" aria-label="Toggle password view">👁️</button>
                </div>
            </div>
            <button type="submit" class="bento-btn-primary">
                <span>Unlock Cloud Console</span>
                <span>⚡</span>
            </button>
        </form>

        <div class="pill-env">
            <span class="pulse-dot"></span>
            Hostinger Multi-Device Sync Active
        </div>
    </div>

    <script>
        function togglePassVisibility() {
            var input = document.getElementById("login_token");
            var eyeBtn = document.getElementById("eyeBtn");
            if (input.type === "password") {
                input.type = "text";
                eyeBtn.innerText = "🙈";
            } else {
                input.type = "password";
                eyeBtn.innerText = "👁️";
            }
        }
    </script>
</body>
</html>
<?php
    exit;
}

// ============================================================================
// AUTHENTICATED ADMIN ACTIONS & HANDLERS
// ============================================================================
$notice = '';
$currentView = $_GET['view'] ?? 'contacts';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $pdo = getDb();

    // 1. Delete single contact
    if (isset($_POST['delete_id'])) {
        $delId = trim($_POST['delete_id']);
        if ($pdo) {
            $stmt = $pdo->prepare("DELETE FROM contacts WHERE id = ?");
            $stmt->execute([$delId]);
            exportContactsDbToCsv($pdo, $targetCsv);
            $notice = "Contact removed permanently from cloud database!";
        } else {
            $rows = readCsvRows($targetCsv);
            if (isset($rows[$delId])) {
                unset($rows[$delId]);
                $notice = "Contact removed permanently from cloud storage!";
            }
            writeCsvFile($targetCsv, $standardHeaders, $rows);
        }

    // 2. Add or edit single contact
    } elseif (isset($_POST['save_single_contact'])) {
        $cid  = trim($_POST['contact_id'] ?? '');
        $n    = trim($_POST['name'] ?? '');
        $p    = normalizePhone($_POST['phone'] ?? '');
        $d    = trim($_POST['details'] ?? '');
        $dev  = trim($_POST['device_id'] ?? 'WEB_ADMIN');
        $fav  = isset($_POST['saved']) && $_POST['saved'] === '1' ? 1 : 0;

        if (isSmsHeaderOrSyntheticContact($n, $p, $d)) {
            $notice = "⚠️ Cannot save contact: Name or details match SMS transactional header!";
        } elseif (!empty($n) || !empty($p)) {
            if (empty($cid)) {
                $cid = md5($n . '|' . $p . '|' . microtime(true));
            }
            $nowTime = round(microtime(true) * 1000);
            if ($pdo) {
                $stmt = $pdo->prepare("
                    INSERT INTO contacts (id, name, phone, details, saved, updated_at, deleted, device_id)
                    VALUES (:id, :name, :phone, :details, :saved, :updated_at, 0, :device_id)
                    ON CONFLICT(id) DO UPDATE SET
                        name = excluded.name,
                        phone = excluded.phone,
                        details = excluded.details,
                        saved = excluded.saved,
                        updated_at = excluded.updated_at,
                        deleted = 0,
                        device_id = excluded.device_id
                ");
                $stmt->execute([
                    ':id'         => $cid,
                    ':name'       => $n,
                    ':phone'      => $p,
                    ':details'    => $d,
                    ':saved'      => $fav,
                    ':updated_at' => $nowTime,
                    ':device_id'  => $dev,
                ]);
                exportContactsDbToCsv($pdo, $targetCsv);
                $notice = "Contact saved successfully in cloud database!";
            } else {
                $rows = readCsvRows($targetCsv);
                $rows[$cid] = [
                    'id'         => $cid,
                    'name'       => $n,
                    'phone'      => $p,
                    'details'    => $d,
                    'saved'      => strval($fav),
                    'updated_at' => strval($nowTime),
                    'deleted'    => '0',
                    'device_id'  => $dev,
                ];
                writeCsvFile($targetCsv, $standardHeaders, $rows);
                $notice = "Contact saved successfully!";
            }
        }

    // 3. Purge SMS sender headers from contacts
    } elseif (isset($_POST['clean_sms_contacts'])) {
        if ($pdo) {
            $stmt = $pdo->query("SELECT id, name, phone, details FROM contacts WHERE deleted = 0");
            $all = $stmt->fetchAll();
            $delStmt = $pdo->prepare("DELETE FROM contacts WHERE id = ?");
            $purgedCount = 0;
            $pdo->beginTransaction();
            foreach ($all as $r) {
                if (isSmsHeaderOrSyntheticContact($r['name'], $r['phone'], $r['details'])) {
                    $delStmt->execute([$r['id']]);
                    $purgedCount++;
                }
            }
            $pdo->commit();
            exportContactsDbToCsv($pdo, $targetCsv);
            $notice = "🧹 Cleaned {$purgedCount} synthetic SMS sender contacts from database!";
        } else {
            $purgedCount = purgeSmsContactsFromCsv($targetCsv);
            $notice = "🧹 Cleaned {$purgedCount} synthetic SMS sender contacts from contacts.csv!";
        }

    // 4. Clean duplicate phone numbers
    } elseif (isset($_POST['clean_duplicates'])) {
        if ($pdo) {
            $stmt = $pdo->query("SELECT id, name, phone, details, saved, updated_at, device_id FROM contacts WHERE deleted = 0 ORDER BY updated_at DESC");
            $all = $stmt->fetchAll();
            $groups = [];
            foreach ($all as $r) {
                $norm = normalizePhone($r['phone']);
                if (empty($norm)) continue;
                $groups[$norm][] = $r;
            }
            $delStmt = $pdo->prepare("DELETE FROM contacts WHERE id = ?");
            $cleanedCount = 0;
            $pdo->beginTransaction();
            foreach ($groups as $norm => $list) {
                if (count($list) <= 1) continue;
                // Survivor preference: saved/favorite first, then longest non-empty name, then most recently updated
                usort($list, function($a, $b) {
                    if ($a['saved'] != $b['saved']) return $b['saved'] - $a['saved'];
                    $lenA = strlen(trim($a['name']));
                    $lenB = strlen(trim($b['name']));
                    if ($lenA != $lenB) return $lenB - $lenA;
                    return $b['updated_at'] - $a['updated_at'];
                });
                for ($i = 1; $i < count($list); $i++) {
                    $delStmt->execute([$list[$i]['id']]);
                    $cleanedCount++;
                }
            }
            $pdo->commit();
            exportContactsDbToCsv($pdo, $targetCsv);
            $notice = "🧹 Removed {$cleanedCount} duplicate phone records from database!";
        } else {
            $rows = readCsvRows($targetCsv);
            $seen = [];
            $cleanedCount = 0;
            foreach ($rows as $id => $r) {
                $norm = normalizePhone($r['phone']);
                if (empty($norm)) continue;
                if (isset($seen[$norm])) {
                    unset($rows[$id]);
                    $cleanedCount++;
                } else {
                    $seen[$norm] = true;
                }
            }
            $notice = "🧹 Removed {$cleanedCount} duplicate phone number records!";
            writeCsvFile($targetCsv, $standardHeaders, $rows);
        }

    // 5. Delete single SMS record
    } elseif (isset($_POST['delete_sms_id'])) {
        $delSmsId = $_POST['delete_sms_id'];
        if ($pdo) {
            $stmt = $pdo->prepare("DELETE FROM sms WHERE id = ?");
            $stmt->execute([$delSmsId]);
            exportSmsDbToCsv($pdo, $smsFile);
        } else {
            $smsRows = [];
            $smsHeader = ['RECORD_TYPE', 'ID', 'NAME', 'PHONE', 'NORMALIZED_NUMBER', 'SOURCE', 'CONSENT', 'STATUS_OR_BODY', 'TIMESTAMP', 'DETAILS'];
            if (file_exists($smsFile) && ($fp = fopen($smsFile, 'r')) !== false) {
                $smsHeader = fgetcsv($fp, 0, ',', '"', "\\") ?: $smsHeader;
                while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== false) {
                    if (count($data) >= 2 && $data[1] === $delSmsId) continue;
                    $smsRows[] = $data;
                }
                fclose($fp);
                $fp = fopen($smsFile, 'w');
                if ($fp) {
                    fputcsv($fp, $smsHeader);
                    foreach ($smsRows as $sr) fputcsv($fp, $sr);
                    fclose($fp);
                }
            }
        }
        $notice = "SMS message permanently deleted!";
        $currentView = 'sms';

    // 6. Clear all SMS records
    } elseif (isset($_POST['clear_all_sms'])) {
        if ($pdo) {
            $pdo->exec("DELETE FROM sms");
            exportSmsDbToCsv($pdo, $smsFile);
        } else {
            $fp = fopen($smsFile, 'w');
            if ($fp) {
                fputcsv($fp, ['RECORD_TYPE', 'ID', 'NAME', 'PHONE', 'NORMALIZED_NUMBER', 'SOURCE', 'CONSENT', 'STATUS_OR_BODY', 'TIMESTAMP', 'DETAILS']);
                fclose($fp);
            }
        }
        $notice = "All SMS history erased from cloud!";
        $currentView = 'sms';
    }
}

// ── Load Contacts Data ───────────────────────────────────────────────────────
$displayRows      = [];
$uniquePhones     = [];
$duplicateCount   = 0;
$smsJunkCount     = 0;

$pdo = getDb();
if ($pdo) {
    $stmt = $pdo->query("SELECT id, name, phone, details, saved, updated_at, deleted, device_id FROM contacts WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC");
    $dbRows = $stmt->fetchAll();
    foreach ($dbRows as $row) {
        $p = normalizePhone($row['phone']);
        $n = $row['name'];
        $d = $row['details'];

        if (isSmsHeaderOrSyntheticContact($n, $p, $d)) {
            $smsJunkCount++;
        }

        if (!empty($p)) {
            if (isset($uniquePhones[$p])) $duplicateCount++;
            else $uniquePhones[$p] = true;
        }

        $displayRows[] = [
            'id'         => $row['id'],
            'name'       => $n,
            'phone'      => $p,
            'details'    => $d,
            'saved'      => strval($row['saved']),
            'updated_at' => strval($row['updated_at']),
            'deleted'    => '0',
            'device_id'  => $row['device_id'],
            'is_sms'     => isSmsHeaderOrSyntheticContact($n, $p, $d),
        ];
    }
} elseif (file_exists($targetCsv) && ($fp = fopen($targetCsv, 'r')) !== false) {
    fgetcsv($fp, 0, ',', '"', "\\");
    while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== false) {
        if (count($data) >= 8 && $data[6] !== '1') {
            $p = normalizePhone($data[2]);
            $n = $data[1];
            $d = $data[3];

            if (isSmsHeaderOrSyntheticContact($n, $p, $d)) {
                $smsJunkCount++;
            }

            if (!empty($p)) {
                if (isset($uniquePhones[$p])) $duplicateCount++;
                else $uniquePhones[$p] = true;
            }

            $displayRows[] = [
                'id'         => $data[0],
                'name'       => $n,
                'phone'      => $p,
                'details'    => $d,
                'saved'      => $data[4],
                'updated_at' => $data[5],
                'deleted'    => $data[6],
                'device_id'  => $data[7],
                'is_sms'     => isSmsHeaderOrSyntheticContact($n, $p, $d),
            ];
        }
    }
    fclose($fp);
}

// ── Load SMS Data ────────────────────────────────────────────────────────────
$displaySmsRows   = [];
$uniqueSmsNumbers = [];
if ($pdo) {
    $stmt = $pdo->query("SELECT id, address, normalized_number, body, date, type, device_id FROM sms ORDER BY date DESC LIMIT 2000");
    $smsList = $stmt->fetchAll();
    foreach ($smsList as $s) {
        $smsNorm = $s['normalized_number'] ?: normalizePhone($s['address']);
        if (!empty($smsNorm)) {
            $uniqueSmsNumbers[$smsNorm] = true;
        }
        $timeVal = $s['date'];
        $formattedTime = is_numeric($timeVal) ? date('Y-m-d H:i', intval($timeVal / 1000)) : $timeVal;

        $displaySmsRows[] = [
            'id'     => $s['id'],
            'phone'  => $s['address'],
            'norm'   => $smsNorm,
            'body'   => $s['body'],
            'time'   => $formattedTime,
            'type'   => $s['type'] ?: 'INBOX',
            'device' => $s['device_id'] ?: 'UNKNOWN',
        ];
    }
} elseif (file_exists($smsFile) && ($fp = fopen($smsFile, 'r')) !== false) {
    fgetcsv($fp, 0, ',', '"', "\\");
    while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== false) {
        if (count($data) >= 8) {
            $smsNorm = $data[4] ?: normalizePhone($data[3]);
            if (!empty($smsNorm)) {
                $uniqueSmsNumbers[$smsNorm] = true;
            }
            $type = 'INBOX';
            $dev  = 'UNKNOWN';
            $detailsStr = $data[9] ?? '';
            if (preg_match('/Type:([^|]+)/', $detailsStr, $m)) $type = $m[1];
            if (preg_match('/Device:(.+)$/', $detailsStr, $m)) $dev = $m[1];

            $timeVal = $data[8];
            $formattedTime = is_numeric($timeVal) ? date('Y-m-d H:i', intval($timeVal / 1000)) : $timeVal;

            $displaySmsRows[] = [
                'id'     => $data[1],
                'phone'  => $data[3],
                'norm'   => $smsNorm,
                'body'   => $data[7],
                'time'   => $formattedTime,
                'type'   => $type,
                'device' => $dev,
            ];
        }
    }
    fclose($fp);
}
// Sort SMS newest first
usort($displaySmsRows, function($a, $b) {
    return strcmp($b['time'], $a['time']);
});
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
    <title>Contact Finder Pro • Cloud Console</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-deep: #060913;
            --glass-card: rgba(15, 23, 42, 0.76);
            --glass-card-subtle: rgba(15, 23, 42, 0.52);
            --glass-border: rgba(56, 189, 248, 0.16);
            --glass-glow: rgba(56, 189, 248, 0.25);
            --accent-cyan: #38BDF8;
            --accent-blue: #2563EB;
            --accent-mint: #34D399;
            --accent-coral: #F43F5E;
            --accent-amber: #F59E0B;
            --accent-purple: #A855F7;
            --text-main: #F8FAFC;
            --text-muted: #94A3B8;
            --text-dim: #64748B;
            --input-bg: rgba(2, 6, 23, 0.72);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif; -webkit-tap-highlight-color: transparent; }
        body {
            background: radial-gradient(circle at 10% 10%, rgba(56, 189, 248, 0.12) 0%, transparent 40%),
                        radial-gradient(circle at 90% 90%, rgba(99, 102, 241, 0.10) 0%, transparent 40%),
                        var(--bg-deep);
            min-height: 100vh;
            width: 100%;
            padding: 12px 12px 88px 12px;
            color: var(--text-main);
            overflow-x: hidden;
        }

        /* ── Strictly Mobile-First Full-Width Shell ──────────────────────── */
        .mobile-shell {
            width: 100%;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        /* ── Bento Glass Top App Bar ──────────────────────────────────────── */
        .bento-app-bar {
            width: 100%;
            background: var(--glass-card);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--glass-border);
            border-radius: 22px;
            padding: 14px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            box-shadow: 0 10px 25px -8px rgba(0, 0, 0, 0.5);
        }
        .bar-brand {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .brand-icon {
            width: 42px;
            height: 42px;
            border-radius: 14px;
            background: linear-gradient(135deg, #0284C7, #38BDF8);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 22px;
            box-shadow: 0 6px 16px -3px rgba(56, 189, 248, 0.4);
        }
        .brand-title h2 {
            font-size: 16px;
            font-weight: 800;
            letter-spacing: -0.3px;
        }
        .brand-subtitle {
            font-size: 11px;
            color: var(--text-muted);
            display: flex;
            align-items: center;
            gap: 5px;
        }
        .pulse-live {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: var(--accent-mint);
            box-shadow: 0 0 6px var(--accent-mint);
        }
        .bar-actions {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .icon-btn {
            width: 38px;
            height: 38px;
            border-radius: 12px;
            background: var(--glass-card-subtle);
            border: 1px solid var(--glass-border);
            color: var(--text-main);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 15px;
            text-decoration: none;
            cursor: pointer;
            transition: all 0.2s;
        }
        .icon-btn:active { transform: scale(0.92); }

        /* ── Navigation Tab Pill Bar ──────────────────────────────────────── */
        .bento-nav-tabs {
            width: 100%;
            background: var(--glass-card);
            backdrop-filter: blur(20px);
            border: 1px solid var(--glass-border);
            border-radius: 18px;
            padding: 6px;
            display: flex;
            gap: 6px;
        }
        .nav-tab-pill {
            flex: 1;
            padding: 10px 12px;
            border-radius: 14px;
            font-size: 13px;
            font-weight: 700;
            text-decoration: none;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            color: var(--text-muted);
            transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .nav-tab-pill.active {
            background: linear-gradient(135deg, rgba(2, 132, 199, 0.9), rgba(56, 189, 248, 0.9));
            color: #FFFFFF;
            box-shadow: 0 6px 18px -4px rgba(56, 189, 248, 0.4);
        }
        .badge-count {
            font-size: 11px;
            padding: 2px 7px;
            border-radius: 10px;
            font-weight: 800;
            background: rgba(0, 0, 0, 0.25);
            color: #FFFFFF;
        }

        /* ── Bento Stats Grid ─────────────────────────────────────────────── */
        .bento-stats-grid {
            width: 100%;
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 10px;
        }
        .bento-stat-card {
            background: var(--glass-card);
            backdrop-filter: blur(18px);
            -webkit-backdrop-filter: blur(18px);
            border: 1px solid var(--glass-border);
            border-radius: 18px;
            padding: 14px 16px;
            position: relative;
            overflow: hidden;
        }
        .bento-stat-card .stat-icon {
            font-size: 20px;
            margin-bottom: 6px;
            opacity: 0.9;
        }
        .bento-stat-card .stat-val {
            font-size: 20px;
            font-weight: 800;
            letter-spacing: -0.3px;
            color: var(--text-main);
        }
        .bento-stat-card .stat-lbl {
            font-size: 11px;
            font-weight: 600;
            color: var(--text-dim);
            text-transform: uppercase;
            letter-spacing: 0.4px;
        }

        /* ── Notice Banner ────────────────────────────────────────────────── */
        .notice-chip {
            background: rgba(56, 189, 248, 0.12);
            border: 1px solid rgba(56, 189, 248, 0.28);
            color: var(--accent-cyan);
            padding: 12px 16px;
            border-radius: 16px;
            font-size: 13px;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 8px;
            animation: fadeIn 0.3s ease;
        }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }

        /* ── Quick Action Command Strip ───────────────────────────────────── */
        .bento-command-strip {
            width: 100%;
            display: flex;
            gap: 8px;
            overflow-x: auto;
            padding-bottom: 2px;
            scrollbar-width: none;
        }
        .bento-command-strip::-webkit-scrollbar { display: none; }
        .cmd-btn {
            white-space: nowrap;
            padding: 10px 14px;
            border-radius: 14px;
            font-size: 12px;
            font-weight: 700;
            border: 1px solid var(--glass-border);
            background: var(--glass-card);
            color: var(--text-main);
            display: inline-flex;
            align-items: center;
            gap: 6px;
            cursor: pointer;
            text-decoration: none;
            transition: all 0.2s;
            flex-shrink: 0;
        }
        .cmd-btn:active { transform: scale(0.96); }
        .cmd-btn.btn-cyan { background: rgba(56, 189, 248, 0.15); border-color: rgba(56, 189, 248, 0.35); color: var(--accent-cyan); }
        .cmd-btn.btn-amber { background: rgba(245, 158, 11, 0.15); border-color: rgba(245, 158, 11, 0.35); color: var(--accent-amber); }
        .cmd-btn.btn-coral { background: rgba(244, 63, 94, 0.15); border-color: rgba(244, 63, 94, 0.35); color: var(--accent-coral); }
        .cmd-btn.btn-mint { background: rgba(52, 211, 153, 0.15); border-color: rgba(52, 211, 153, 0.35); color: var(--accent-mint); }

        /* ── Unified Bento Glass Input Component ─────────────────────────── */
        .bento-input-group {
            position: relative;
            width: 100%;
        }
        .bento-input-shell {
            position: relative;
            display: flex;
            align-items: center;
            background: var(--input-bg);
            border: 1.5px solid var(--glass-border);
            border-radius: 16px;
            transition: all 0.22s ease;
        }
        .bento-input-shell:focus-within {
            border-color: var(--accent-cyan);
            box-shadow: 0 0 0 3px var(--glass-glow);
            background: rgba(2, 6, 23, 0.9);
        }
        .input-lead-icon {
            padding-left: 14px;
            font-size: 16px;
            color: var(--text-dim);
            user-select: none;
        }
        .bento-input {
            width: 100%;
            padding: 16px 14px 12px 10px;
            background: transparent;
            border: none;
            outline: none;
            font-size: 14px;
            font-weight: 600;
            color: var(--text-main);
        }
        .bento-input::placeholder { color: transparent; }
        .bento-floating-label {
            position: absolute;
            left: 38px;
            top: 50%;
            transform: translateY(-50%);
            font-size: 13px;
            font-weight: 500;
            color: var(--text-dim);
            pointer-events: none;
            transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .bento-input:focus ~ .bento-floating-label,
        .bento-input:not(:placeholder-shown) ~ .bento-floating-label {
            top: 7px;
            font-size: 9.5px;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            color: var(--accent-cyan);
        }
        .input-clear-btn {
            padding-right: 14px;
            background: none;
            border: none;
            color: var(--text-dim);
            font-size: 14px;
            cursor: pointer;
        }

        /* ── Bento Contact Card (Mobile Native Action Layout) ─────────────── */
        .contact-cards-container {
            width: 100%;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .bento-card-item {
            width: 100%;
            background: var(--glass-card);
            backdrop-filter: blur(20px);
            -webkit-backdrop-filter: blur(20px);
            border: 1px solid var(--glass-border);
            border-radius: 20px;
            padding: 14px;
            display: flex;
            flex-direction: column;
            gap: 12px;
            transition: all 0.2s;
        }
        .bento-card-item.is-synthetic {
            border-color: rgba(244, 63, 94, 0.35);
            background: rgba(30, 16, 26, 0.7);
        }
        .card-top-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 10px;
        }
        .card-profile {
            display: flex;
            align-items: center;
            gap: 10px;
            flex: 1;
            min-width: 0;
        }
        .avatar-circle {
            width: 40px;
            height: 40px;
            border-radius: 13px;
            background: linear-gradient(135deg, rgba(37, 99, 235, 0.4), rgba(56, 189, 248, 0.4));
            border: 1px solid rgba(56, 189, 248, 0.3);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 16px;
            font-weight: 800;
            color: var(--accent-cyan);
            flex-shrink: 0;
        }
        .profile-names {
            flex: 1;
            min-width: 0;
        }
        .contact-title {
            font-size: 15px;
            font-weight: 700;
            color: var(--text-main);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .contact-phone-lbl {
            font-size: 13px;
            font-weight: 600;
            color: var(--accent-cyan);
            font-family: monospace;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .top-badges {
            display: flex;
            align-items: center;
            gap: 6px;
            flex-shrink: 0;
        }
        .device-badge-pill {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            font-size: 10px;
            font-weight: 700;
            padding: 3px 8px;
            border-radius: 10px;
            background: rgba(255, 255, 255, 0.06);
            border: 1px solid rgba(255, 255, 255, 0.1);
            color: var(--text-muted);
        }
        .star-pill {
            font-size: 16px;
            background: none;
            border: none;
            cursor: pointer;
            line-height: 1;
            user-select: none;
        }
        .star-pill.active { color: var(--accent-amber); text-shadow: 0 0 10px rgba(245, 158, 11, 0.6); }
        .star-pill:not(.active) { color: var(--text-dim); }

        .details-pill-box {
            font-size: 11px;
            color: var(--text-muted);
            background: rgba(2, 6, 23, 0.4);
            border-radius: 10px;
            padding: 6px 10px;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
        }

        /* ── Full Width-to-Width Aligned Action Bar ───────────────────────── */
        .card-actions-bar {
            width: 100%;
            display: flex;
            align-items: center;
            gap: 6px;
            border-top: 1px solid rgba(255, 255, 255, 0.05);
            padding-top: 10px;
        }
        .action-slot-btn {
            flex: 1;
            height: 38px;
            border-radius: 12px;
            background: var(--glass-card-subtle);
            border: 1px solid rgba(148, 163, 184, 0.12);
            color: var(--text-main);
            font-size: 11px;
            font-weight: 700;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 4px;
            text-decoration: none;
            cursor: pointer;
            transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
            user-select: none;
        }
        .action-slot-btn:active { transform: scale(0.94); }
        .action-slot-btn.call-btn { color: var(--accent-mint); border-color: rgba(52, 211, 153, 0.25); }
        .action-slot-btn.sms-btn  { color: var(--accent-cyan); border-color: rgba(56, 189, 248, 0.25); }
        .action-slot-btn.wa-btn   { color: #25D366; border-color: rgba(37, 211, 102, 0.25); }
        .action-slot-btn.edit-btn { color: #A78BFA; border-color: rgba(167, 139, 250, 0.25); }
        .action-slot-btn.del-btn  { color: var(--accent-coral); border-color: rgba(244, 63, 94, 0.25); }

        /* ── SMS Bento Card ───────────────────────────────────────────────── */
        .sms-card-item {
            width: 100%;
            background: var(--glass-card);
            backdrop-filter: blur(20px);
            -webkit-backdrop-filter: blur(20px);
            border: 1px solid var(--glass-border);
            border-radius: 20px;
            padding: 14px;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .sms-header-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
        }
        .sms-sender-info {
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .sms-sender-lbl {
            font-size: 14px;
            font-weight: 700;
            color: var(--accent-cyan);
        }
        .type-pill {
            font-size: 9px;
            font-weight: 800;
            padding: 2px 6px;
            border-radius: 6px;
            text-transform: uppercase;
        }
        .type-pill.inbox { background: rgba(56, 189, 248, 0.2); color: var(--accent-cyan); }
        .type-pill.sent  { background: rgba(52, 211, 153, 0.2); color: var(--accent-mint); }
        .sms-meta-info {
            font-size: 11px;
            color: var(--text-dim);
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .sms-body-box {
            background: rgba(2, 6, 23, 0.55);
            border: 1px solid rgba(255, 255, 255, 0.05);
            border-radius: 14px;
            padding: 12px;
            font-size: 13px;
            line-height: 1.45;
            color: #E2E8F0;
            word-break: break-word;
        }
        .sms-actions-bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
        }

        /* ── Modal Bottom Sheet for Add/Edit Contact ─────────────────────── */
        .bento-modal-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0, 0, 0, 0.75);
            backdrop-filter: blur(14px);
            -webkit-backdrop-filter: blur(14px);
            z-index: 1000;
            display: none;
            align-items: flex-end;
            justify-content: center;
        }
        .bento-modal-overlay.open { display: flex; }
        .bento-modal-sheet {
            width: 100%;
            max-height: 90vh;
            background: #0B132B;
            border: 1px solid var(--glass-border);
            border-radius: 28px 28px 0 0;
            padding: 24px 18px 34px 18px;
            display: flex;
            flex-direction: column;
            gap: 16px;
            box-shadow: 0 -20px 40px rgba(0, 0, 0, 0.8);
            overflow-y: auto;
            animation: slideUp 0.28s cubic-bezier(0.16, 1, 0.3, 1);
        }
        @keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
        .sheet-handle {
            width: 44px;
            height: 5px;
            background: rgba(255, 255, 255, 0.2);
            border-radius: 10px;
            margin: 0 auto -6px;
        }
        .sheet-title-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .sheet-title-row h3 {
            font-size: 18px;
            font-weight: 800;
        }

        /* ── Floating Mobile Dock ─────────────────────────────────────────── */
        .mobile-dock {
            position: fixed;
            bottom: 12px;
            left: 12px;
            right: 12px;
            background: rgba(11, 19, 43, 0.88);
            backdrop-filter: blur(24px);
            -webkit-backdrop-filter: blur(24px);
            border: 1px solid var(--glass-border);
            border-radius: 22px;
            padding: 8px 12px;
            display: flex;
            align-items: center;
            justify-content: space-around;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.7);
            z-index: 900;
        }
        .dock-item {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 2px;
            color: var(--text-dim);
            font-size: 10px;
            font-weight: 700;
            text-decoration: none;
            transition: all 0.2s;
            cursor: pointer;
            background: none;
            border: none;
        }
        .dock-item .dock-icon { font-size: 18px; }
        .dock-item.active { color: var(--accent-cyan); }
    </style>
</head>
<body>

<div class="mobile-shell">

    <!-- Top Bento App Bar -->
    <header class="bento-app-bar">
        <div class="bar-brand">
            <div class="brand-icon"><?php echo $currentView === 'sms' ? '💬' : '📇'; ?></div>
            <div class="brand-title">
                <h2>Contact Finder Pro</h2>
                <div class="brand-subtitle">
                    <span class="pulse-live"></span>
                    <span>Hostinger <?php echo $currentView === 'sms' ? 'sms.csv' : 'contacts.csv'; ?></span>
                </div>
            </div>
        </div>
        <div class="bar-actions">
            <?php if ($currentView === 'contacts'): ?>
                <a href="index.php?download=contacts" class="icon-btn" title="Export contacts.csv" aria-label="Export contacts.csv">⬇️</a>
            <?php else: ?>
                <a href="index.php?download=sms" class="icon-btn" title="Export sms.csv" aria-label="Export sms.csv">⬇️</a>
            <?php endif; ?>
            <a href="index.php?logout=1" class="icon-btn" title="Logout" aria-label="Logout">🚪</a>
        </div>
    </header>

    <!-- Navigation Tab Pill Bar -->
    <nav class="bento-nav-tabs">
        <a href="index.php?view=contacts" class="nav-tab-pill <?php echo $currentView === 'contacts' ? 'active' : ''; ?>">
            <span>📇 Contacts</span>
            <span class="badge-count"><?php echo count($displayRows); ?></span>
        </a>
        <a href="index.php?view=sms" class="nav-tab-pill <?php echo $currentView === 'sms' ? 'active' : ''; ?>">
            <span>💬 SMS Ledger</span>
            <span class="badge-count"><?php echo count($displaySmsRows); ?></span>
        </a>
    </nav>

    <?php if (!empty($notice)): ?>
        <div class="notice-chip">✨ <?php echo htmlspecialchars($notice); ?></div>
    <?php endif; ?>

    <?php if ($currentView === 'contacts'): ?>
        <!-- Bento Stats Grid (Contacts) -->
        <div class="bento-stats-grid">
            <div class="bento-stat-card">
                <div class="stat-icon">👥</div>
                <div class="stat-val"><?php echo count($displayRows); ?></div>
                <div class="stat-lbl">Real Contacts</div>
            </div>
            <div class="bento-stat-card">
                <div class="stat-icon">📱</div>
                <div class="stat-val"><?php echo count($uniquePhones); ?></div>
                <div class="stat-lbl">Unique Phones</div>
            </div>
            <div class="bento-stat-card">
                <div class="stat-icon">🛡️</div>
                <div class="stat-val" style="color:<?php echo $smsJunkCount > 0 ? 'var(--accent-coral)' : 'var(--accent-mint)'; ?>">
                    <?php echo $smsJunkCount; ?>
                </div>
                <div class="stat-lbl">SMS Junk Headers</div>
            </div>
            <div class="bento-stat-card">
                <div class="stat-icon">☁️</div>
                <div class="stat-val"><?php echo file_exists('contacts.csv') ? round(filesize('contacts.csv')/1024, 1).' KB' : '0 KB'; ?></div>
                <div class="stat-lbl">Cloud Size</div>
            </div>
        </div>

        <!-- Quick Action Command Strip -->
        <div class="bento-command-strip">
            <button type="button" class="cmd-btn btn-cyan" onclick="openContactModal()">
                <span>➕</span>
                <span>Add Contact</span>
            </button>
            <?php if ($smsJunkCount > 0): ?>
                <form method="POST" style="display:inline;">
                    <button type="submit" name="clean_sms_contacts" value="1" class="cmd-btn btn-coral"
                        onclick="return confirm('Purge <?php echo $smsJunkCount; ?> synthetic SMS sender contacts from contacts.csv?')">
                        <span>🧹</span>
                        <span>Purge SMS Junk (<?php echo $smsJunkCount; ?>)</span>
                    </button>
                </form>
            <?php endif; ?>
            <?php if ($duplicateCount > 0): ?>
                <form method="POST" style="display:inline;">
                    <button type="submit" name="clean_duplicates" value="1" class="cmd-btn btn-amber"
                        onclick="return confirm('Remove <?php echo $duplicateCount; ?> duplicate numbers?')">
                        <span>🧹</span>
                        <span>Clean Dupes (<?php echo $duplicateCount; ?>)</span>
                    </button>
                </form>
            <?php endif; ?>
            <a href="index.php?download=contacts" class="cmd-btn btn-mint">
                <span>⬇️</span>
                <span>Export CSV</span>
            </a>
        </div>

        <!-- Unified Search Input -->
        <div class="bento-input-group">
            <div class="bento-input-shell">
                <span class="input-lead-icon">🔍</span>
                <input type="text" id="contactSearch" placeholder="Search" class="bento-input" oninput="filterContactCards()">
                <label for="contactSearch" class="bento-floating-label">Search contacts, numbers, notes...</label>
                <button type="button" class="input-clear-btn" onclick="clearSearch('contactSearch', filterContactCards)">✕</button>
            </div>
            <div style="display:flex; justify-content:space-between; align-items:center; padding:4px 6px; font-size:11.5px; color:var(--text-muted);">
                <span id="contactMatchCount">Showing <?php echo count($displayRows); ?> contacts</span>
                <span style="font-size:10.5px; color:var(--accent-cyan); font-weight:600;">⚡ Instant Filter</span>
            </div>
        </div>

        <!-- Contact Cards List (Mobile Native Bento Glass Items) -->
        <main class="contact-cards-container" id="contactCardsList">
            <?php if (empty($displayRows)): ?>
                <div class="bento-card-item" style="text-align:center; padding:32px 16px;">
                    <p style="color:var(--text-dim); font-size:14px;">No contacts found in contacts.csv.</p>
                </div>
            <?php else: ?>
                <?php foreach ($displayRows as $r): ?>
                    <article class="bento-card-item <?php echo $r['is_sms'] ? 'is-synthetic' : ''; ?>"
                         data-search="<?php echo htmlspecialchars(strtolower($r['name'].' '.$r['phone'].' '.$r['details'].' '.$r['device_id'])); ?>"
                         data-id="<?php echo htmlspecialchars($r['id']); ?>"
                         data-name="<?php echo htmlspecialchars($r['name']); ?>"
                         data-phone="<?php echo htmlspecialchars($r['phone']); ?>"
                         data-details="<?php echo htmlspecialchars($r['details']); ?>"
                         data-device="<?php echo htmlspecialchars($r['device_id']); ?>"
                         data-saved="<?php echo ($r['saved'] === '1' || $r['saved'] === true) ? '1' : '0'; ?>">

                        <!-- Top Row: Avatar, Name, Phone & Device beside Favorite Star -->
                        <div class="card-top-row">
                            <div class="card-profile">
                                <div class="avatar-circle">
                                    <?php
                                        $initial = mb_substr(trim($r['name'] ?: $r['phone']), 0, 1);
                                        echo htmlspecialchars(strtoupper($initial ?: '?'));
                                    ?>
                                </div>
                                <div class="profile-names">
                                    <h3 class="contact-title"><?php echo htmlspecialchars($r['name'] ?: 'Unnamed Contact'); ?></h3>
                                    <div class="contact-phone-lbl">
                                        <span><?php echo htmlspecialchars($r['phone'] ?: 'No phone'); ?></span>
                                    </div>
                                </div>
                            </div>
                            <div class="top-badges">
                                <span class="device-badge-pill">
                                    📱 <?php echo htmlspecialchars($r['device_id'] ?: 'DEV'); ?>
                                </span>
                                <span class="star-pill <?php echo ($r['saved'] === '1' || $r['saved'] === true) ? 'active' : ''; ?>">
                                    <?php echo ($r['saved'] === '1' || $r['saved'] === true) ? '⭐' : '☆'; ?>
                                </span>
                            </div>
                        </div>

                        <!-- Details / Loan Notes (if present) -->
                        <?php if (!empty($r['details'])): ?>
                            <div class="details-pill-box">
                                <?php echo htmlspecialchars($r['details']); ?>
                            </div>
                        <?php endif; ?>

                        <!-- Full Width-to-Width Aligned Action Bar -->
                        <div class="card-actions-bar">
                            <?php if (!empty($r['phone'])): ?>
                                <a href="tel:<?php echo htmlspecialchars($r['phone']); ?>" class="action-slot-btn call-btn">
                                    <span>📞</span>
                                    <span>Call</span>
                                </a>
                                <a href="sms:<?php echo htmlspecialchars($r['phone']); ?>" class="action-slot-btn sms-btn">
                                    <span>💬</span>
                                    <span>SMS</span>
                                </a>
                                <a href="https://wa.me/<?php echo preg_replace('/[^0-9]/', '', $r['phone']); ?>" target="_blank" rel="noopener" class="action-slot-btn wa-btn">
                                    <span>🟢</span>
                                    <span>WA</span>
                                </a>
                            <?php endif; ?>
                            <button type="button" class="action-slot-btn edit-btn" onclick="editContactFromCard(this)">
                                <span>✏️</span>
                                <span>Edit</span>
                            </button>
                            <form method="POST" style="flex:1; display:flex;">
                                <input type="hidden" name="delete_id" value="<?php echo htmlspecialchars($r['id']); ?>">
                                <button type="submit" class="action-slot-btn del-btn" style="width:100%;"
                                    onclick="return confirm('Permanently delete <?php echo htmlspecialchars(addslashes($r['name'] ?: $r['phone'])); ?>?')">
                                    <span>🗑️</span>
                                    <span>Del</span>
                                </button>
                            </form>
                        </div>
                    </article>
                <?php endforeach; ?>
            <?php endif; ?>
        </main>

    <?php else: ?>
        <!-- Bento Stats Grid (SMS) -->
        <div class="bento-stats-grid">
            <div class="bento-stat-card">
                <div class="stat-icon">💬</div>
                <div class="stat-val"><?php echo count($displaySmsRows); ?></div>
                <div class="stat-lbl">Total SMS</div>
            </div>
            <div class="bento-stat-card">
                <div class="stat-icon">👥</div>
                <div class="stat-val"><?php echo count($uniqueSmsNumbers); ?></div>
                <div class="stat-lbl">Unique Send</div>
            </div>
            <div class="bento-stat-card">
                <div class="stat-icon">⏱️</div>
                <div class="stat-val" style="font-size:14px;">
                    <?php echo !empty($displaySmsRows) ? htmlspecialchars($displaySmsRows[0]['time']) : 'None'; ?>
                </div>
                <div class="stat-lbl">Latest Sync</div>
            </div>
            <div class="bento-stat-card">
                <div class="stat-icon">☁️</div>
                <div class="stat-val"><?php echo file_exists('sms.csv') ? round(filesize('sms.csv')/1024, 1).' KB' : '0 KB'; ?></div>
                <div class="stat-lbl">File Size</div>
            </div>
        </div>

        <!-- Quick Action Command Strip (SMS) -->
        <div class="bento-command-strip">
            <form method="POST" style="display:inline;">
                <button type="submit" name="clear_all_sms" value="1" class="cmd-btn btn-coral"
                    onclick="return confirm('Permanently clear all SMS history from sms.csv?')">
                    <span>🗑️</span>
                    <span>Clear All SMS</span>
                </button>
            </form>
            <a href="index.php?download=sms" class="cmd-btn btn-mint">
                <span>⬇️</span>
                <span>Export sms.csv</span>
            </a>
        </div>

        <!-- Unified Search Input -->
        <div class="bento-input-group">
            <div class="bento-input-shell">
                <span class="input-lead-icon">🔍</span>
                <input type="text" id="smsSearch" placeholder="Search" class="bento-input" oninput="filterSmsCards()">
                <label for="smsSearch" class="bento-floating-label">Search SMS address, message text, device...</label>
                <button type="button" class="input-clear-btn" onclick="clearSearch('smsSearch', filterSmsCards)">✕</button>
            </div>
            <div style="display:flex; justify-content:space-between; align-items:center; padding:4px 6px; font-size:11.5px; color:var(--text-muted);">
                <span id="smsMatchCount">Showing <?php echo count($displaySmsRows); ?> SMS</span>
                <span style="font-size:10.5px; color:var(--accent-cyan); font-weight:600;">⚡ Instant Filter</span>
            </div>
        </div>

        <!-- SMS Cards List -->
        <main class="contact-cards-container" id="smsCardsList">
            <?php if (empty($displaySmsRows)): ?>
                <div class="bento-card-item" style="text-align:center; padding:32px 16px;">
                    <p style="color:var(--text-dim); font-size:14px;">No SMS logs synced yet from Android device.</p>
                </div>
            <?php else: ?>
                <?php foreach ($displaySmsRows as $s): ?>
                    <article class="sms-card-item" data-search="<?php echo htmlspecialchars(strtolower($s['phone'].' '.$s['norm'].' '.$s['body'].' '.$s['device'])); ?>">
                        <div class="sms-header-row">
                            <div class="sms-sender-info">
                                <span class="sms-sender-lbl"><?php echo htmlspecialchars($s['phone']); ?></span>
                                <span class="type-pill <?php echo strtolower($s['type']) === 'inbox' ? 'inbox' : 'sent'; ?>">
                                    <?php echo htmlspecialchars($s['type']); ?>
                                </span>
                            </div>
                            <div class="sms-meta-info">
                                <span>📱 <?php echo htmlspecialchars($s['device']); ?></span>
                                <span>•</span>
                                <span><?php echo htmlspecialchars($s['time']); ?></span>
                            </div>
                        </div>

                        <div class="sms-body-box">
                            <?php echo nl2br(htmlspecialchars($s['body'])); ?>
                        </div>

                        <div class="sms-actions-bar">
                            <button type="button" class="action-slot-btn" onclick="navigator.clipboard.writeText(<?php echo json_encode($s['body']); ?>); alert('SMS copied to clipboard!');">
                                <span>📋</span>
                                <span>Copy</span>
                            </button>
                            <?php if (!empty($s['phone'])): ?>
                                <a href="tel:<?php echo htmlspecialchars($s['phone']); ?>" class="action-slot-btn call-btn">
                                    <span>📞</span>
                                    <span>Call</span>
                                </a>
                            <?php endif; ?>
                            <form method="POST" style="flex:1; display:flex;">
                                <input type="hidden" name="delete_sms_id" value="<?php echo htmlspecialchars($s['id']); ?>">
                                <button type="submit" class="action-slot-btn del-btn" style="width:100%;"
                                    onclick="return confirm('Delete this SMS record permanently?')">
                                    <span>🗑️</span>
                                    <span>Delete</span>
                                </button>
                            </form>
                        </div>
                    </article>
                <?php endforeach; ?>
            <?php endif; ?>
        </main>
    <?php endif; ?>

</div>

<!-- Modal Bottom Sheet: Add / Edit Contact -->
<div class="bento-modal-overlay" id="contactModalOverlay" onclick="handleOverlayClick(event)">
    <div class="bento-modal-sheet" id="contactModalSheet">
        <div class="sheet-handle"></div>
        <div class="sheet-title-row">
            <h3 id="modalHeading">➕ New Contact</h3>
            <button type="button" class="icon-btn" onclick="closeContactModal()">✕</button>
        </div>

        <form method="POST" id="modalContactForm">
            <input type="hidden" name="save_single_contact" value="1">
            <input type="hidden" name="contact_id" id="modalContactId" value="">

            <div style="display:flex; flex-direction:column; gap:14px;">
                <div class="bento-input-group">
                    <div class="bento-input-shell">
                        <span class="input-lead-icon">👤</span>
                        <input type="text" name="name" id="modalName" placeholder="Name" required class="bento-input">
                        <label for="modalName" class="bento-floating-label">Full Name</label>
                    </div>
                </div>

                <div class="bento-input-group">
                    <div class="bento-input-shell">
                        <span class="input-lead-icon">📞</span>
                        <input type="tel" name="phone" id="modalPhone" placeholder="Phone" required class="bento-input">
                        <label for="modalPhone" class="bento-floating-label">Phone (+91...)</label>
                    </div>
                </div>

                <div class="bento-input-group">
                    <div class="bento-input-shell">
                        <span class="input-lead-icon">📝</span>
                        <input type="text" name="details" id="modalDetails" placeholder="Details" class="bento-input">
                        <label for="modalDetails" class="bento-floating-label">Loan Details / Notes</label>
                    </div>
                </div>

                <div class="bento-input-group">
                    <div class="bento-input-shell">
                        <span class="input-lead-icon">📱</span>
                        <input type="text" name="device_id" id="modalDevice" placeholder="Device" value="WEB_ADMIN" class="bento-input">
                        <label for="modalDevice" class="bento-floating-label">Device Identifier</label>
                    </div>
                </div>

                <label style="display:flex; align-items:center; gap:8px; font-size:13px; font-weight:600; cursor:pointer; color:var(--text-main); margin-top:4px;">
                    <input type="checkbox" name="saved" id="modalSaved" value="1" style="width:18px; height:18px; accent-color:var(--accent-cyan);">
                    <span>⭐ Mark as Favorite Contact</span>
                </label>

                <button type="submit" class="bento-btn-primary" style="margin-top:8px;">
                    <span>Save to Cloud Storage</span>
                    <span>💾</span>
                </button>
            </div>
        </form>
    </div>
</div>

<!-- Floating Mobile Bottom Dock -->
<nav class="mobile-dock">
    <a href="index.php?view=contacts" class="dock-item <?php echo $currentView === 'contacts' ? 'active' : ''; ?>">
        <span class="dock-icon">📇</span>
        <span>Contacts</span>
    </a>
    <a href="index.php?view=sms" class="dock-item <?php echo $currentView === 'sms' ? 'active' : ''; ?>">
        <span class="dock-icon">💬</span>
        <span>SMS</span>
    </a>
    <button type="button" class="dock-item" onclick="openContactModal()">
        <span class="dock-icon">➕</span>
        <span>New</span>
    </button>
    <a href="index.php?download=<?php echo $currentView === 'sms' ? 'sms' : 'contacts'; ?>" class="dock-item">
        <span class="dock-icon">⬇️</span>
        <span>Export</span>
    </a>
</nav>

<script>
function filterContactCards() {
    var input = document.getElementById("contactSearch");
    if (!input) return;
    var q = input.value.toLowerCase().trim();
    var cards = document.querySelectorAll("#contactCardsList article");
    var visible = 0;
    cards.forEach(function(card) {
        var str = card.getAttribute("data-search") || "";
        var match = str.includes(q);
        card.style.display = match ? "" : "none";
        if (match) visible++;
    });
    var countEl = document.getElementById("contactMatchCount");
    if (countEl) {
        countEl.innerText = q ? ("Showing " + visible + " of " + cards.length + " matching '" + q + "'") : ("Showing " + cards.length + " contacts");
    }
}

function filterSmsCards() {
    var input = document.getElementById("smsSearch");
    if (!input) return;
    var q = input.value.toLowerCase().trim();
    var cards = document.querySelectorAll("#smsCardsList article");
    var visible = 0;
    cards.forEach(function(card) {
        var str = card.getAttribute("data-search") || "";
        var match = str.includes(q);
        card.style.display = match ? "" : "none";
        if (match) visible++;
    });
    var countEl = document.getElementById("smsMatchCount");
    if (countEl) {
        countEl.innerText = q ? ("Showing " + visible + " of " + cards.length + " matching '" + q + "'") : ("Showing " + cards.length + " SMS");
    }
}

document.addEventListener("DOMContentLoaded", function() {
    var params = new URLSearchParams(window.location.search);
    var q = params.get("q");
    if (q) {
        var cInput = document.getElementById("contactSearch");
        var sInput = document.getElementById("smsSearch");
        if (cInput) {
            cInput.value = q;
            filterContactCards();
        }
        if (sInput) {
            sInput.value = q;
            filterSmsCards();
        }
    }
});

function clearSearch(id, filterFunc) {
    var el = document.getElementById(id);
    if (el) {
        el.value = "";
        filterFunc();
    }
}

function openContactModal() {
    document.getElementById("modalHeading").innerText = "➕ New Contact";
    document.getElementById("modalContactId").value = "";
    document.getElementById("modalName").value = "";
    document.getElementById("modalPhone").value = "";
    document.getElementById("modalDetails").value = "";
    document.getElementById("modalDevice").value = "WEB_ADMIN";
    document.getElementById("modalSaved").checked = false;
    document.getElementById("contactModalOverlay").classList.add("open");
}

function editContactFromCard(btn) {
    var card = btn.closest("article");
    if (!card) return;
    document.getElementById("modalHeading").innerText = "✏️ Edit Contact";
    document.getElementById("modalContactId").value = card.getAttribute("data-id") || "";
    document.getElementById("modalName").value = card.getAttribute("data-name") || "";
    document.getElementById("modalPhone").value = card.getAttribute("data-phone") || "";
    document.getElementById("modalDetails").value = card.getAttribute("data-details") || "";
    document.getElementById("modalDevice").value = card.getAttribute("data-device") || "WEB_ADMIN";
    document.getElementById("modalSaved").checked = (card.getAttribute("data-saved") === "1");
    document.getElementById("contactModalOverlay").classList.add("open");
}

function closeContactModal() {
    document.getElementById("contactModalOverlay").classList.remove("open");
}

function handleOverlayClick(e) {
    if (e.target.id === "contactModalOverlay") {
        closeContactModal();
    }
}
</script>
</body>
</html>
