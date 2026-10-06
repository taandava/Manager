<?php
// sync.php - High-Performance CSV Sync Backend for 10,000+ Contacts on Hostinger
ini_set('memory_limit', '512M');
set_time_limit(300);
header('Content-Type: application/json; charset=utf-8');

$csvFile = 'contacts.csv';
$secretToken = 'Jmd@2025';

$token = isset($_GET['token']) ? $_GET['token'] : '';
$inputJSON = file_get_contents('php://input');
$input = json_decode($inputJSON, true);

if (isset($input['token'])) {
    $token = $input['token'];
}

if ($token !== $secretToken) {
    echo json_encode(['ok' => false, 'error' => 'Unauthorized: Invalid token']);
    exit;
}

$method = $_SERVER['REQUEST_METHOD'];
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

if (!file_exists($csvFile)) {
    $fp = fopen($csvFile, 'w');
    fputcsv($fp, $standardHeaders);
    fclose($fp);
}

// Read CSV into associative array $rows[id]
$rows = [];
if (($fp = fopen($csvFile, 'r')) !== FALSE) {
    $headerRow = fgetcsv($fp);
    $isRawTwoColumn = false;
    if ($headerRow && count($headerRow) === 2) {
        $isRawTwoColumn = true;
    } elseif ($headerRow && count($headerRow) >= 8 && strtolower(trim($headerRow[0])) !== 'id') {
        $isRawTwoColumn = true;
    }

    if ($isRawTwoColumn && $headerRow) {
        $name = trim($headerRow[0]);
        $phone = normalizePhone($headerRow[1]);
        if (!empty($name) || !empty($phone)) {
            $id = md5($name . '|' . $phone);
            $rows[$id] = [
                'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => '',
                'saved' => '0', 'updated_at' => strval(round(microtime(true) * 1000)), 'deleted' => '0', 'device_id' => 'HOSTINGER'
            ];
        }
    }

    while (($data = fgetcsv($fp)) !== FALSE) {
        if (count($data) >= 8) {
            $id = strval($data[0]);
            $name = strval($data[1]);
            $phone = normalizePhone($data[2]);
            $details = strval($data[3]);
            $saved = $data[4] === '1' || strtolower($data[4]) === 'true' ? '1' : '0';
            $updated_at = strval($data[5]);
            $deleted = $data[6] === '1' || strtolower($data[6]) === 'true' ? '1' : '0';
            $device_id = strval($data[7]);

            if ($deleted === '1') continue;

            $rows[$id] = [
                'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => $details,
                'saved' => $saved, 'updated_at' => $updated_at, 'deleted' => '0', 'device_id' => $device_id
            ];
        } elseif (count($data) >= 2) {
            $name = trim($data[0]);
            $phone = normalizePhone($data[1]);
            if (!empty($name) || !empty($phone)) {
                $id = md5($name . '|' . $phone);
                $rows[$id] = [
                    'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => '',
                    'saved' => '0', 'updated_at' => strval(round(microtime(true) * 1000)), 'deleted' => '0', 'device_id' => 'HOSTINGER'
                ];
            }
        }
    }
    fclose($fp);
}

if ($method === 'GET') {
    $outputRows = [];
    foreach ($rows as $r) {
        $outputRows[] = [
            'id' => $r['id'],
            'name' => $r['name'],
            'phone' => $r['phone'],
            'details' => $r['details'],
            'saved' => ($r['saved'] === '1' || $r['saved'] === true),
            'updated_at' => $r['updated_at'],
            'deleted' => false,
            'device_id' => $r['device_id']
        ];
    }
    echo json_encode(['ok' => true, 'total' => count($outputRows), 'rows' => $outputRows]);
} elseif ($method === 'POST') {
    $incoming = isset($input['contacts']) ? $input['contacts'] : [];
    $inserted = 0; $updated = 0; $deletedCount = 0; $ignored = 0;

    foreach ($incoming as $c) {
        if (!isset($c['id'])) { $ignored++; continue; }
        $id = strval($c['id']);
        $isDeleted = (isset($c['deleted']) && ($c['deleted'] === true || $c['deleted'] === '1' || $c['deleted'] === 1));

        if ($isDeleted) {
            if (isset($rows[$id])) {
                unset($rows[$id]);
                $deletedCount++;
            }
            continue;
        }

        $newTime = isset($c['updated_at']) ? strval($c['updated_at']) : '0';
        $name = isset($c['name']) ? trim($c['name']) : '';
        $phone = isset($c['phone']) ? normalizePhone($c['phone']) : '';
        $details = isset($c['details']) ? trim($c['details']) : '';
        $saved = (isset($c['saved']) && ($c['saved'] === true || $c['saved'] === '1' || $c['saved'] === 1)) ? '1' : '0';
        $device_id = isset($c['device_id']) ? $c['device_id'] : 'HOSTINGER';

        if (isset($rows[$id])) {
            $oldTime = isset($rows[$id]['updated_at']) ? strval($rows[$id]['updated_at']) : '0';
            if ($newTime >= $oldTime) {
                $rows[$id] = [
                    'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => $details,
                    'saved' => $saved, 'updated_at' => $newTime, 'deleted' => '0', 'device_id' => $device_id
                ];
                $updated++;
            } else {
                $ignored++;
            }
        } else {
            $rows[$id] = [
                'id' => $id, 'name' => $name, 'phone' => $phone, 'details' => $details,
                'saved' => $saved, 'updated_at' => $newTime, 'deleted' => '0', 'device_id' => $device_id
            ];
            $inserted++;
        }
    }

    $fp = fopen($csvFile, 'w');
    fputcsv($fp, $standardHeaders);
    foreach ($rows as $row) {
        fputcsv($fp, [
            $row['id'], $row['name'], $row['phone'], $row['details'],
            $row['saved'], $row['updated_at'], '0', $row['device_id']
        ]);
    }
    fclose($fp);

    echo json_encode(['ok' => true, 'inserted' => $inserted, 'updated' => $updated, 'deleted' => $deletedCount, 'ignored' => $ignored]);
} else {
    echo json_encode(['ok' => false, 'error' => 'Unsupported method']);
}
?>
