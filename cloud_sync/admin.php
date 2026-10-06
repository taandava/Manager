<?php
// admin.php - Full-Width Web Excel Spreadsheet Editor & Admin Dashboard for Hostinger
$csvFile = 'contacts.csv';
$secretToken = 'Jmd@2025';

$token = isset($_GET['token']) ? $_GET['token'] : (isset($_POST['token']) ? $_POST['token'] : '');
if ($token !== $secretToken) {
    if (isset($_POST['token_input'])) {
        if ($_POST['token_input'] === $secretToken) {
            header("Location: admin.php?token=" . $secretToken);
            exit;
        } else {
            $error = "Invalid Token!";
        }
    }
    ?>
    <!DOCTYPE html>
    <html>
    <head>
        <title>Contact Finder Admin - Login</title>
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f2f2f7; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
            .login-box { background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); width: 300px; text-align: center; }
            input { width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 6px; box-sizing: border-box; }
            button { background: #007aff; color: white; border: none; padding: 10px; width: 100%; border-radius: 6px; font-weight: bold; cursor: pointer; }
            .error { color: red; font-size: 14px; }
        </style>
    </head>
    <body>
        <div class="login-box">
            <h3>Contact Finder Admin</h3>
            <?php if(isset($error)) echo "<p class='error'>$error</p>"; ?>
            <form method="POST">
                <input type="password" name="token_input" placeholder="Enter Sync Token (Jmd@2025)" required>
                <button type="submit">Login</button>
            </form>
        </div>
    </body>
    </html>
    <?php
    exit;
}

$standardHeaders = ['id', 'name', 'phone', 'details', 'saved', 'updated_at', 'deleted', 'device_id'];

// Handle Actions (Save Table, Delete Row, Add Row)
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $rows = [];
    if (file_exists($csvFile)) {
        if (($fp = fopen($csvFile, 'r')) !== FALSE) {
            fgetcsv($fp); // skip header
            while (($data = fgetcsv($fp)) !== FALSE) {
                if (count($data) >= 8 && $data[6] !== '1') {
                    $rows[$data[0]] = [
                        'id' => $data[0], 'name' => $data[1], 'phone' => $data[2],
                        'details' => $data[3], 'saved' => $data[4], 'updated_at' => $data[5],
                        'deleted' => $data[6], 'device_id' => $data[7]
                    ];
                }
            }
            fclose($fp);
        }
    }

    if (isset($_POST['delete_id'])) {
        $delId = $_POST['delete_id'];
        if (isset($rows[$delId])) {
            unset($rows[$delId]);
        }
        $success = "Contact deleted successfully!";
    } elseif (isset($_POST['add_contact'])) {
        $newName = trim($_POST['new_name']);
        $newPhone = trim($_POST['new_phone']);
        $newDetails = trim($_POST['new_details']);
        if (!empty($newName) || !empty($newPhone)) {
            $newId = md5($newName . '|' . $newPhone . '|' . time());
            $rows[$newId] = [
                'id' => $newId, 'name' => $newName, 'phone' => $newPhone,
                'details' => $newDetails, 'saved' => '0', 'updated_at' => round(microtime(true) * 1000),
                'deleted' => '0', 'device_id' => 'ADMIN_WEB'
            ];
            $success = "Contact added successfully!";
        }
    } elseif (isset($_POST['save_table'])) {
        $ids = $_POST['id'] ?? [];
        $names = $_POST['name'] ?? [];
        $phones = $_POST['phone'] ?? [];
        $details = $_POST['details'] ?? [];
        $saved = $_POST['saved'] ?? [];
        $device_ids = $_POST['device_id'] ?? [];

        for ($i = 0; $i < count($ids); $i++) {
            $id = trim($ids[$i]);
            $name = trim($names[$i]);
            $phone = trim($phones[$i]);
            if (strcasecmp($name, 'name') === 0 && strcasecmp($phone, 'phone') === 0) continue;
            if (empty($id)) continue;

            $detail = trim($details[$i]);
            $sv = isset($saved[$i]) ? '1' : '0';
            $dev = trim($device_ids[$i]);

            if (isset($rows[$id])) {
                $rows[$id]['name'] = $name;
                $rows[$id]['phone'] = $phone;
                $rows[$id]['details'] = $detail;
                $rows[$id]['saved'] = $sv;
                $rows[$id]['device_id'] = $dev;
                $rows[$id]['updated_at'] = round(microtime(true) * 1000);
            }
        }
        $success = "Changes saved successfully!";
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
}

$displayRows = [];
if (file_exists($csvFile)) {
    if (($fp = fopen($csvFile, 'r')) !== FALSE) {
        fgetcsv($fp);
        while (($data = fgetcsv($fp)) !== FALSE) {
            if (count($data) >= 8 && $data[6] !== '1') {
                if (strcasecmp(trim($data[1]), 'name') === 0 && strcasecmp(trim($data[2]), 'phone') === 0) continue;
                $displayRows[] = [
                    'id' => $data[0], 'name' => $data[1], 'phone' => $data[2],
                    'details' => $data[3], 'saved' => $data[4], 'updated_at' => $data[5],
                    'deleted' => $data[6], 'device_id' => $data[7]
                ];
            }
        }
        fclose($fp);
    }
}
?>
<!DOCTYPE html>
<html>
<head>
    <title>Contact Finder - Full-Width Web Excel Editor</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f2f2f7; margin: 0; padding: 10px; box-sizing: border-box; }
        .container { width: 100%; min-height: 95vh; background: white; padding: 20px; border-radius: 12px; box-sizing: border-box; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
        .header-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; flex-wrap: wrap; gap: 10px; }
        h2 { margin: 0; color: #1c1c1e; }
        .success { background: #34c759; color: white; padding: 10px 15px; border-radius: 6px; margin-bottom: 15px; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th, td { border: 1px solid #e5e5ea; padding: 10px 12px; text-align: left; }
        th { background: #f8f8f1; color: #3a3a3c; font-weight: 600; }
        input[type="text"] { width: 100%; border: none; background: transparent; font-size: 14px; outline: none; box-sizing: border-box; }
        input[type="text"]:focus { background: #f2f2f7; border-radius: 4px; padding: 4px; }
        .btn-save { background: #34c759; color: white; border: none; padding: 10px 20px; border-radius: 8px; font-size: 15px; font-weight: bold; cursor: pointer; }
        .btn-save:hover { background: #28a745; }
        .btn-delete { background: #ff3b30; color: white; border: none; padding: 6px 12px; border-radius: 6px; font-size: 13px; font-weight: bold; cursor: pointer; }
        .btn-delete:hover { background: #d32f2f; }
        .add-box { background: #f8f8f1; padding: 15px; border-radius: 8px; margin-bottom: 15px; display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
        .add-box input { padding: 8px; border: 1px solid #ccc; border-radius: 6px; flex: 1; min-width: 150px; background: white; }
        .btn-add { background: #007aff; color: white; border: none; padding: 9px 18px; border-radius: 6px; font-weight: bold; cursor: pointer; }
    </style>
</head>
<body>
<div class="container">
    <div class="header-bar">
        <h2>📊 Contact Finder - Full-Width Web Excel Editor</h2>
        <form method="POST" action="admin.php?token=<?php echo $secretToken; ?>">
            <button type="submit" name="save_table" value="1" class="btn-save">💾 Save All Changes</button>
        </form>
    </div>

    <?php if(isset($success)) echo "<div class='success'>$success</div>"; ?>

    <!-- Add New Contact Form -->
    <form method="POST" action="admin.php?token=<?php echo $secretToken; ?>" class="add-box">
        <strong>➕ Add Contact:</strong>
        <input type="text" name="new_name" placeholder="Name" required>
        <input type="text" name="new_phone" placeholder="Phone Number (+91...)" required>
        <input type="text" name="new_details" placeholder="Details / Notes">
        <button type="submit" name="add_contact" value="1" class="btn-add">Add Contact</button>
    </form>

    <form method="POST" action="admin.php?token=<?php echo $secretToken; ?>" id="mainTableForm">
        <input type="hidden" name="save_table" value="1">
        <table>
            <thead>
                <tr>
                    <th style="width: 25%;">Name</th>
                    <th style="width: 20%;">Phone</th>
                    <th style="width: 30%;">Details / Notes</th>
                    <th style="width: 8%;">Saved</th>
                    <th style="width: 12%;">Device ID</th>
                    <th style="width: 5%; text-align: center;">Action</th>
                </tr>
            </thead>
            <tbody>
                <?php foreach($displayRows as $idx => $r): ?>
                <tr>
                    <input type="hidden" name="id[]" value="<?php echo htmlspecialchars($r['id']); ?>">
                    <td><input type="text" name="name[]" value="<?php echo htmlspecialchars($r['name']); ?>"></td>
                    <td><input type="text" name="phone[]" value="<?php echo htmlspecialchars($r['phone']); ?>"></td>
                    <td><input type="text" name="details[]" value="<?php echo htmlspecialchars($r['details']); ?>"></td>
                    <td><input type="checkbox" name="saved[<?php echo $idx; ?>]" value="1" <?php echo ($r['saved'] === '1' || $r['saved'] === true) ? 'checked' : ''; ?>></td>
                    <td><input type="text" name="device_id[]" value="<?php echo htmlspecialchars($r['device_id']); ?>"></td>
                    <td style="text-align: center;">
                        <button type="submit" name="delete_id" value="<?php echo htmlspecialchars($r['id']); ?>" class="btn-delete" onclick="return confirm('Delete this contact?');">🗑️</button>
                    </td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </form>
</div>
</body>
</html>
