package com.balajifinance.contactfinder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String DEFAULT_CLOUD_URL = "https://white-jackal-528611.hostingersite.com/sync.php";
    private static final String DEFAULT_CLOUD_TOKEN = "Jmd@2025";
    private static final int REQ_EXPORT = 4101, REQ_IMPORT = 4102, REQ_PERMISSIONS = 4103;

    private DatabaseHelper dbHelper;
    private final ArrayList<Contact> all = new ArrayList<>();
    private final ArrayList<Contact> visible = new ArrayList<>();
    private ContactAdapter adapter;
    private EditText search;
    private ProgressBar progress;
    private SharedPreferences prefs;
    private boolean showingSavedOnly = false;
    private LinearLayout contentContainer;
    private ListView contactListView;
    private int currentTab = 0;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("contact_finder", MODE_PRIVATE);
        dbHelper = new DatabaseHelper(getApplicationContext());
        ensureDeviceId();
        buildUi();
        requestInitialPermissions();
        loadLocalDatabase();
        filter("");
        autoSyncCloudIfNeeded();
    }

    private void requestInitialPermissions() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_PERMISSIONS);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        root.setFitsSystemWindows(true); // Automatically handles status bar, notch, and camera hole
        root.setBackgroundColor(Color.rgb(242, 242, 247)); // iOS system background grey

        // Top App Bar Container (iOS style)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setBackgroundColor(Color.WHITE);
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        topBar.setElevation(dp(4));

        // iOS style rounded search container
        LinearLayout searchContainer = new LinearLayout(this);
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setPadding(dp(12), dp(6), dp(8), dp(6));

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.rgb(229, 229, 234)); // iOS search grey
        searchBg.setCornerRadius(dp(22));
        searchContainer.setBackground(searchBg);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("🔍 Search contacts...");
        search.setTextSize(16);
        search.setBackground(null);
        search.setPadding(dp(6), dp(4), dp(8), dp(4));
        searchContainer.addView(search, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button refreshBtn = createFlatIconButton("🔄", "Sync Hostinger", v -> syncCloud());
        searchContainer.addView(refreshBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        topBar.addView(searchContainer, new LinearLayout.LayoutParams(-1, -2));
        root.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        // Content Container (dynamically hosts Home ListView, Duplicates Page, Stats Page, or Settings Page)
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);

        contactListView = new ListView(this);
        contactListView.setDivider(null);
        contactListView.setPadding(dp(8), dp(8), dp(8), dp(8));
        adapter = new ContactAdapter();
        contactListView.setAdapter(adapter);

        // Load initial home view
        contentContainer.addView(contactListView, new LinearLayout.LayoutParams(-1, -1));
        root.addView(contentContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        // Modern iOS-style Bottom Tab Bar (4 Tabs: Home, Duplicates, Stats, Settings)
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(Color.WHITE);
        bottomNav.setPadding(dp(8), dp(6), dp(8), dp(6));
        bottomNav.setElevation(dp(16));

        Button btnTabHome = createFlatNavButton("🏠", "Home", v -> switchTab(0));
        Button btnTabDuplicates = createFlatNavButton("⚠️", "Duplicates", v -> switchTab(4));
        Button btnTabStats = createFlatNavButton("📊", "Stats", v -> switchTab(5));
        Button btnTabSettings = createFlatNavButton("⚙️", "Settings", v -> switchTab(3));

        bottomNav.addView(btnTabHome, new LinearLayout.LayoutParams(0, dp(50), 1));
        bottomNav.addView(btnTabDuplicates, new LinearLayout.LayoutParams(0, dp(50), 1));
        bottomNav.addView(btnTabStats, new LinearLayout.LayoutParams(0, dp(50), 1));
        bottomNav.addView(btnTabSettings, new LinearLayout.LayoutParams(0, dp(50), 1));

        root.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(60)));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (currentTab == 0 || currentTab == 1) {
                    filter(s.toString());
                }
            }
            public void afterTextChanged(Editable e) {}
        });
    }

    private void switchTab(int tabIndex) {
        currentTab = tabIndex;
        contentContainer.removeAllViews();
        search.setVisibility(View.GONE);

        if (tabIndex == 0) {
            // Home (All Contacts)
            showingSavedOnly = false;
            filter(search.getText().toString());
            contentContainer.addView(contactListView, new LinearLayout.LayoutParams(-1, -1));
            search.setVisibility(View.VISIBLE);
        } else if (tabIndex == 1) {
            // Saved Contacts
            showingSavedOnly = true;
            filter(search.getText().toString());
            contentContainer.addView(contactListView, new LinearLayout.LayoutParams(-1, -1));
            search.setVisibility(View.VISIBLE);
        } else if (tabIndex == 3) {
            // Settings Page
            contentContainer.addView(buildSettingsView(), new LinearLayout.LayoutParams(-1, -1));
        } else if (tabIndex == 4) {
            // Duplicates Page
            contentContainer.addView(buildDuplicatesView(), new LinearLayout.LayoutParams(-1, -1));
        } else if (tabIndex == 5) {
            // Stats Page
            contentContainer.addView(buildStatsView(), new LinearLayout.LayoutParams(-1, -1));
        }
    }

    private View buildDuplicatesView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(242, 242, 247));
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = tv("⚠️ Duplicate Contacts Manager", 20, Color.rgb(28, 28, 30));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(12));
        container.addView(title);

        ArrayList<ArrayList<Contact>> dupGroups = findDuplicateContactGroups();
        if (dupGroups.isEmpty()) {
            LinearLayout emptyCard = new LinearLayout(this);
            emptyCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(16));
            emptyCard.setBackground(bg);
            emptyCard.setPadding(dp(24), dp(32), dp(24), dp(32));

            TextView msg = tv("✨ No Duplicates Found!\nAll your contacts have unique phone numbers.", 16, Color.rgb(100, 100, 105));
            msg.setGravity(Gravity.CENTER);
            emptyCard.addView(msg);
            container.addView(emptyCard);
        } else {
            for (ArrayList<Contact> group : dupGroups) {
                String phone = PhoneUtils.formatIndianPhone(group.get(0).phone);
                
                TextView header = tv("📱 Phone: " + phone + " (" + group.size() + " records)", 13, Color.rgb(100, 100, 105));
                header.setTypeface(Typeface.DEFAULT_BOLD);
                container.addView(header);

                LinearLayout groupCard = new LinearLayout(this);
                groupCard.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(Color.WHITE);
                cardBg.setCornerRadius(dp(16));
                groupCard.setBackground(cardBg);
                groupCard.setPadding(dp(12), dp(8), dp(12), dp(8));

                for (int i = 0; i < group.size(); i++) {
                    Contact c = group.get(i);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(dp(8), dp(10), dp(8), dp(10));
                    row.setGravity(Gravity.CENTER_VERTICAL);

                    LinearLayout info = new LinearLayout(this);
                    info.setOrientation(LinearLayout.VERTICAL);
                    TextView nameView = tv(c.name.isEmpty() ? "Unnamed" : c.name, 16, Color.rgb(28, 28, 30));
                    nameView.setTypeface(Typeface.DEFAULT_BOLD);
                    info.addView(nameView);
                    if (!c.details.isEmpty()) {
                        info.addView(tv(c.details, 12, Color.rgb(142, 142, 147)));
                    }
                    row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

                    Button delBtn = new Button(this);
                    delBtn.setText("Delete");
                    delBtn.setTextSize(14);
                    delBtn.setTextColor(Color.rgb(255, 59, 48)); // iOS Red
                    delBtn.setBackground(null);
                    delBtn.setOnClickListener(v -> {
                        deleteContact(c);
                        switchTab(4); // Refresh duplicates page
                    });
                    row.addView(delBtn, new LinearLayout.LayoutParams(-2, -2));

                    groupCard.addView(row);

                    if (i < group.size() - 1) {
                        View div = new View(this);
                        div.setBackgroundColor(Color.rgb(229, 229, 234));
                        groupCard.addView(div, new LinearLayout.LayoutParams(-1, 1));
                    }
                }

                container.addView(groupCard);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) groupCard.getLayoutParams();
                lp.setMargins(0, dp(4), 0, dp(16));
                groupCard.setLayoutParams(lp);
            }
        }

        scrollView.addView(container, new ViewGroup.LayoutParams(-1, -1));
        return scrollView;
    }

    private View buildStatsView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(242, 242, 247));
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = tv("📊 Database Statistics", 20, Color.rgb(28, 28, 30));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(12));
        container.addView(title);

        int totalContacts = all.size();
        int activeContacts = 0;
        int savedContacts = 0;
        int pendingSync = 0;
        for (Contact c : all) {
            if (!c.deleted) activeContacts++;
            if (c.saved) savedContacts++;
            if (c.edited || c.syncStatus.equals("PENDING")) pendingSync++;
        }

        container.addView(createStatsCard(new String[]{
            "Total Contacts in Database", String.valueOf(totalContacts),
            "Active Contacts", String.valueOf(activeContacts),
            "Saved / Starred Contacts", String.valueOf(savedContacts),
            "Pending Cloud Sync", String.valueOf(pendingSync)
        }));

        scrollView.addView(container, new ViewGroup.LayoutParams(-1, -1));
        return scrollView;
    }

    private View createStatsCard(String[] stats) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setElevation(dp(1));

        for (int i = 0; i < stats.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = tv(stats[i], 15, Color.rgb(28, 28, 30));
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

            TextView val = tv(stats[i+1], 16, Color.rgb(0, 122, 255));
            val.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(val, new LinearLayout.LayoutParams(-2, -2));

            card.addView(row);

            if (i < stats.length - 2) {
                View div = new View(this);
                div.setBackgroundColor(Color.rgb(229, 229, 234));
                card.addView(div, new LinearLayout.LayoutParams(-1, 1));
            }
        }
        return card;
    }

    private View buildSettingsView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.rgb(242, 242, 247));
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = tv("⚙️ App Settings", 20, Color.rgb(28, 28, 30));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(8));
        container.addView(title);

        container.addView(createSettingsHeader("☁️ HOSTINGER CLOUD SYNC"));
        container.addView(createSettingsCard(new String[]{
            "Sync Cloud Now",
            "Configure Hostinger URL & Token",
            "Test Hostinger Connection"
        }, new Runnable[]{
            this::syncCloud,
            this::cloudSetup,
            this::testHostingerConnection
        }));

        container.addView(createSettingsHeader("📱 DEVICE & BACKUPS"));
        container.addView(createSettingsCard(new String[]{
            "Scan Device Contacts",
            "Export Local Backup (CSV)",
            "Import Local Backup (CSV)"
        }, new Runnable[]{
            this::scanDeviceContacts,
            this::exportBackup,
            this::importBackup
        }));

        container.addView(createSettingsHeader("ℹ️ ABOUT & SUPPORT"));
        container.addView(createSettingsCard(new String[]{
            "About Contact Finder Pro",
            "Hostinger PHP CSV Engine v2.2"
        }, new Runnable[]{
            this::about,
            () -> {}
        }));

        scrollView.addView(container, new ViewGroup.LayoutParams(-1, -1));
        return scrollView;
    }

    private TextView createSettingsHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.rgb(100, 100, 105));
        tv.setPadding(dp(4), dp(16), dp(4), dp(6));
        return tv;
    }

    private View createSettingsCard(String[] titles, Runnable[] actions) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        card.setElevation(dp(1));

        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(dp(16), dp(14), dp(16), dp(14));
            item.setGravity(Gravity.CENTER_VERTICAL);

            TextView tv = new TextView(this);
            tv.setText(titles[idx]);
            tv.setTextSize(16);
            tv.setTextColor(Color.rgb(28, 28, 30));
            item.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));

            if (actions[idx] != null && !titles[idx].contains("v2.2")) {
                TextView arrow = new TextView(this);
                arrow.setText("›");
                arrow.setTextSize(20);
                arrow.setTextColor(Color.rgb(199, 199, 204));
                item.addView(arrow);
                item.setOnClickListener(v -> actions[idx].run());
            }

            card.addView(item);

            if (idx < titles.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(229, 229, 234));
                card.addView(divider, new LinearLayout.LayoutParams(-1, 1));
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) divider.getLayoutParams();
                lp.setMargins(dp(16), 0, 0, 0);
                divider.setLayoutParams(lp);
            }
        }
        return card;
    }

    private Button createFlatIconButton(String icon, String tooltip, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(icon);
        b.setTextSize(18);
        b.setBackground(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setTooltipText(tooltip);
        }
        b.setOnClickListener(listener);
        return b;
    }

    private Button createFlatNavButton(String icon, String tooltip, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(icon);
        b.setTextSize(22);
        b.setBackground(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setTooltipText(tooltip);
        }
        b.setOnClickListener(listener);
        return b;
    }

    private void scanDeviceContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_PERMISSIONS);
            Toast.makeText(this, "Please grant Contacts permission to scan", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true, "Scanning device contacts…");
        new Thread(() -> {
            try {
                HashSet<String> existingPhones = new HashSet<>();
                for (Contact c : all) {
                    String norm = PhoneUtils.normalizePhone(c.phone);
                    if (!norm.isEmpty()) existingPhones.add(norm);
                }

                List<Contact> newContacts = new ArrayList<>();
                Cursor cursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                        },
                        null, null, null
                );

                int scannedCount = 0, addedCount = 0;
                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            scannedCount++;
                            String name = cursor.getString(0);
                            String rawPhone = cursor.getString(1);
                            String phone = PhoneUtils.normalizePhone(rawPhone);
                            if (name == null) name = "";
                            if (phone.isEmpty()) continue;

                            if (!existingPhones.contains(phone)) {
                                existingPhones.add(phone);
                                Contact c = new Contact();
                                c.name = name.trim();
                                c.phone = phone;
                                c.details = "Imported from device contacts";
                                c.source = "DEVICE_SCAN";
                                c.edited = true;
                                c.syncStatus = "PENDING";
                                newContacts.add(c);
                                addedCount++;
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }

                if (!newContacts.isEmpty()) {
                    dbHelper.upsertContacts(newContacts);
                    loadLocalDatabase();
                    autoSyncCloudIfNeeded();
                }

                final int finalScanned = scannedCount;
                final int finalAdded = addedCount;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false, all.size() + " contacts • scan complete");
                    filter(search.getText().toString());
                    new AlertDialog.Builder(this)
                            .setTitle("Device Contacts Scan")
                            .setMessage(String.format(Locale.US, "Scanned: %d phone records\nNew unique contacts added: %d", finalScanned, finalAdded))
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false, all.size() + " contacts • scan failed");
                    showError(e);
                });
            }
        }).start();
    }

    private void loadDefaultSampleContactsIfEmpty() {
        if (dbHelper.getActiveCount() == 0) {
            List<Contact> samples = new ArrayList<>();
            samples.add(new Contact("SAMPLE-1", "Balaji Finance Support", "+919876543210", "Customer Support & Inquiries • Default Contact"));
            samples.add(new Contact("SAMPLE-2", "Rahul Sharma", "+919811122233", "Loan Account #1002 • Mumbai Branch"));
            samples.add(new Contact("SAMPLE-3", "Priya Verma", "+919822233344", "Gold Loan Department • Delhi Branch"));
            samples.add(new Contact("SAMPLE-4", "Amit Patel", "+919833344455", "Business Partner • Ahmedabad"));
            dbHelper.upsertContacts(samples);
        }
    }

    private void loadLocalDatabase() {
        loadDefaultSampleContactsIfEmpty();
        all.clear();
        all.addAll(dbHelper.getAllContacts());
    }

    // ---------------- Cloud Sync (Hostinger PHP CSV Backend) ----------------
    private void syncCloud() {
        String url = prefs.getString("cloud_url", DEFAULT_CLOUD_URL).trim();
        if (url.isEmpty()) url = DEFAULT_CLOUD_URL;
        setBusy(true, "Syncing with Hostinger…");
        final String finalUrl = url;
        new Thread(() -> {
            try {
                JSONObject remote = getJson(finalUrl);
                if (!remote.optBoolean("ok", false)) {
                    throw new IOException(remote.optString("error", "Cloud returned an error"));
                }
                JSONArray rows = remote.optJSONArray("rows");
                if (rows == null) rows = new JSONArray();
                
                mergeRemote(rows);
                
                JSONArray changes = new JSONArray();
                for (Contact c : all) {
                    if (c.edited || c.syncStatus.equals("PENDING") || c.syncStatus.equals("PENDING_DELETE")) {
                        changes.put(contactToJson(c));
                    }
                }

                if (changes.length() > 0) {
                    JSONObject body = new JSONObject()
                            .put("token", prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN))
                            .put("contacts", changes);
                    JSONObject result = postJson(finalUrl, body);
                    if (!result.optBoolean("ok", false)) {
                        throw new IOException(result.optString("error", "Cloud rejected changes"));
                    }
                }

                for (Contact c : all) {
                    if (c.edited || c.syncStatus.equals("PENDING") || c.syncStatus.equals("PENDING_DELETE")) {
                        c.edited = false;
                        c.syncStatus = "SYNCED";
                        dbHelper.upsertContact(c);
                    }
                }

                prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply();
                loadLocalDatabase();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false, all.size() + " contacts • cloud synced • " + new Date());
                    filter(search.getText().toString());
                    Toast.makeText(MainActivity.this, "☁️ Cloud sync complete", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false, all.size() + " contacts • sync failed • local data safe");
                    showError(e);
                });
            }
        }).start();
    }

    private void testHostingerConnection() {
        String testUrl = prefs.getString("cloud_url", DEFAULT_CLOUD_URL).trim();
        String testToken = prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN).trim();
        setBusy(true, "Testing Hostinger connection…");
        new Thread(() -> {
            try {
                String sep = testUrl.contains("?") ? "&" : "?";
                String fullUrl = testUrl + sep + "token=" + URLEncoder.encode(testToken, "UTF-8");
                HttpURLConnection c = openConnectionWithRedirects(fullUrl, "GET", null, null);
                int code = c.getResponseCode();
                InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String s = is == null ? "" : read(new InputStreamReader(is, StandardCharsets.UTF_8));
                JSONObject res = new JSONObject(s);
                if (!res.optBoolean("ok", false)) {
                    throw new IOException(res.optString("error", "Unauthorized or invalid response"));
                }
                runOnUiThread(() -> {
                    setBusy(false, "Connection successful!");
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("✅ Hostinger Connected")
                            .setMessage("Hostinger CSV backend connected successfully! Two-way sync is fully operational.")
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Connection failed");
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("❌ Connection Failed")
                            .setMessage("Could not connect to Hostinger.\n\nError: " + e.getMessage() + "\n\nTip: Check your URL and token in Settings.")
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private ArrayList<ArrayList<Contact>> findDuplicateContactGroups() {
        HashMap<String, ArrayList<Contact>> map = new LinkedHashMap<>();
        for (Contact c : all) {
            if (c.deleted) continue;
            String p = PhoneUtils.normalizePhone(c.phone);
            if (p.isEmpty()) continue;
            ArrayList<Contact> groupList = map.get(p);
            if (groupList == null) {
                groupList = new ArrayList<>();
                map.put(p, groupList);
            }
            groupList.add(c);
        }
        ArrayList<ArrayList<Contact>> duplicates = new ArrayList<>();
        for (ArrayList<Contact> g : map.values()) {
            if (g.size() > 1) {
                duplicates.add(g);
            }
        }
        return duplicates;
    }

    private HttpURLConnection openConnectionWithRedirects(String urlStr, String method, String contentType, byte[] bodyBytes) throws Exception {
        String currentUrl = urlStr;
        int redirects = 0;
        while (redirects < 5) {
            URL url = new URL(currentUrl);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(30000);
            c.setReadTimeout(45000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod(method);
            c.setRequestProperty("User-Agent", "ContactFinder/2.2");
            if (contentType != null) {
                c.setRequestProperty("Content-Type", contentType);
            }
            if ("POST".equals(method) && bodyBytes != null) {
                c.setDoOutput(true);
                try (OutputStream o = c.getOutputStream()) {
                    o.write(bodyBytes);
                }
            }
            int code = c.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                String loc = c.getHeaderField("Location");
                if (loc != null) {
                    currentUrl = loc;
                    redirects++;
                    c.disconnect();
                    continue;
                }
            }
            return c;
        }
        throw new IOException("Too many redirects");
    }

    private JSONObject getJson(String urlStr) throws Exception {
        String token = prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN);
        String sep = urlStr.contains("?") ? "&" : "?";
        String fullUrl = urlStr + sep + "token=" + URLEncoder.encode(token, "UTF-8");
        HttpURLConnection c = openConnectionWithRedirects(fullUrl, "GET", null, null);
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String s = is == null ? "" : read(new InputStreamReader(is, StandardCharsets.UTF_8));
        String trimmed = s.trim().toLowerCase();
        if (trimmed.startsWith("<!doctype") || trimmed.startsWith("<html")) {
            throw new IOException("Server returned HTML error page. Check your URL path.");
        }
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " " + s);
        }
        return new JSONObject(s);
    }

    private JSONObject postJson(String urlStr, JSONObject body) throws Exception {
        byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = openConnectionWithRedirects(urlStr, "POST", "application/json; charset=UTF-8", b);
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String s = is == null ? "" : read(new InputStreamReader(is, StandardCharsets.UTF_8));
        String trimmed = s.trim().toLowerCase();
        if (trimmed.startsWith("<!doctype") || trimmed.startsWith("<html")) {
            throw new IOException("Server returned HTML error page. Check your URL path.");
        }
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " " + s);
        }
        return new JSONObject(s);
    }

    private void mergeRemote(JSONArray rows) throws Exception {
        String deletedStr = prefs.getString("permanently_deleted_ids", "");
        HashSet<String> deletedSet = new HashSet<>();
        for (String s : deletedStr.split(",")) {
            if (!s.trim().isEmpty()) deletedSet.add(s.trim());
        }

        HashMap<String, Contact> map = new LinkedHashMap<>();
        for (Contact c : all) {
            if (c.id != null && (c.id.startsWith("SAMPLE-") || deletedSet.contains(c.id))) continue;
            map.put(c.id, c);
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.getJSONObject(i);
            String id = r.optString("id").trim();
            if (id.isEmpty() || deletedSet.contains(id)) continue;

            Contact local = map.get(id);
            long rt = parseTime(r.optString("updated_at", "0"));
            boolean del = "1".equals(r.optString("deleted")) || r.optBoolean("deleted");
            if (del) continue;

            boolean svd = "1".equals(r.optString("saved")) || r.optBoolean("saved");
            if (local == null) {
                Contact c = new Contact(id, r.optString("name"), PhoneUtils.normalizePhone(r.optString("phone")), r.optString("details"));
                c.saved = svd;
                c.deleted = false;
                c.updatedAt = rt;
                c.source = "CLOUD";
                c.syncStatus = "SYNCED";
                map.put(id, c);
            } else if (!local.edited && rt > local.updatedAt) {
                local.name = r.optString("name");
                local.phone = PhoneUtils.normalizePhone(r.optString("phone"));
                local.details = r.optString("details");
                local.saved = svd;
                local.deleted = false;
                local.updatedAt = rt;
                local.syncStatus = "SYNCED";
            }
        }
        all.clear();
        all.addAll(map.values());
        dbHelper.clearAll();
        dbHelper.upsertContacts(all);
    }

    private JSONObject contactToJson(Contact c) throws Exception {
        JSONObject j = new JSONObject();
        j.put("id", c.id);
        j.put("name", c.name);
        j.put("phone", c.phone);
        j.put("details", c.details);
        j.put("saved", c.saved ? 1 : 0);
        j.put("deleted", c.deleted ? 1 : 0);
        j.put("updated_at", String.valueOf(c.updatedAt));
        j.put("device_id", deviceId());
        return j;
    }

    private void autoSyncCloudIfNeeded() {
        String url = prefs.getString("cloud_url", DEFAULT_CLOUD_URL).trim();
        if (url.isEmpty()) url = DEFAULT_CLOUD_URL;
        final String finalUrl = url;
        new Thread(() -> {
            try {
                JSONObject remote = getJson(finalUrl);
                if (remote.optBoolean("ok", false)) {
                    JSONArray rows = remote.optJSONArray("rows");
                    if (rows != null) {
                        mergeRemote(rows);
                        JSONArray changes = new JSONArray();
                        for (Contact c : all) {
                            if (c.edited || c.syncStatus.equals("PENDING")) {
                                changes.put(contactToJson(c));
                            }
                        }
                        if (changes.length() > 0) {
                            JSONObject body = new JSONObject()
                                    .put("token", prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN))
                                    .put("contacts", changes);
                            JSONObject result = postJson(finalUrl, body);
                            if (!result.optBoolean("ok", false)) {
                                throw new IOException(result.optString("error", "Cloud rejected changes"));
                            }
                        }
                        prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply();
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            loadLocalDatabase();
                            filter(search.getText().toString());
                        });
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    // ---------------- UI & Actions ----------------
    private void filter(String query) {
        visible.clear();
        String q = PhoneUtils.normalizeSearch(query);
        for (Contact c : all) {
            if (c.deleted) continue;
            if (showingSavedOnly && !(c.saved || c.edited || c.source.equals("LOCAL"))) continue;
            if (q.isEmpty() || c.getSearchable().contains(q)) {
                visible.add(c);
            }
        }
        Collections.sort(visible, (a, b) -> a.name.compareToIgnoreCase(b.name));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void editContact(Contact c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), 0, dp(8), 0);
        EditText n = new EditText(this);
        n.setHint("Name");
        EditText p = new EditText(this);
        p.setHint("Phone");
        EditText d = new EditText(this);
        d.setHint("Details / notes");
        box.addView(n);
        box.addView(p);
        box.addView(d);
        if (c != null) {
            n.setText(c.name);
            p.setText(c.phone);
            d.setText(c.details);
        }
        new AlertDialog.Builder(this)
                .setTitle(c == null ? "➕ Add local contact" : "✏️ Edit contact")
                .setView(box)
                .setPositiveButton("SAVE", (x, w) -> {
                    String name = n.getText().toString().trim();
                    String phone = PhoneUtils.normalizePhone(p.getText().toString());
                    String details = d.getText().toString().trim();
                    if (name.isEmpty() && phone.isEmpty()) {
                        Toast.makeText(this, "Name or phone is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (c == null) {
                        Contact newC = new Contact();
                        newC.name = name;
                        newC.phone = phone;
                        newC.details = details;
                        newC.source = "LOCAL";
                        newC.edited = true;
                        newC.syncStatus = "PENDING";
                        all.add(newC);
                        dbHelper.upsertContact(newC);
                    } else {
                        c.name = name;
                        c.phone = phone;
                        c.details = details;
                        c.edited = true;
                        c.updatedAt = System.currentTimeMillis();
                        c.syncStatus = "PENDING";
                        dbHelper.upsertContact(c);
                    }
                    showingSavedOnly = false;
                    filter(search.getText().toString());
                    Toast.makeText(this, "💾 Saved locally", Toast.LENGTH_SHORT).show();
                    autoSyncCloudIfNeeded();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void deleteContact(Contact c) {
        String label = c.name.isEmpty() ? c.phone : c.name;
        new AlertDialog.Builder(this)
                .setTitle("🗑️ Delete record?")
                .setMessage("Delete " + label + "?\n\nThis will remove the contact permanently from app and cloud.")
                .setPositiveButton("DELETE", (dialog, which) -> {
                    String deletedStr = prefs.getString("permanently_deleted_ids", "");
                    if (!deletedStr.contains(c.id)) {
                        prefs.edit().putString("permanently_deleted_ids", deletedStr + "," + c.id).apply();
                    }

                    all.remove(c);
                    dbHelper.deleteContact(c.id);
                    filter(search.getText().toString());
                    Toast.makeText(this, "🗑️ Contact deleted permanently", Toast.LENGTH_SHORT).show();

                    new Thread(() -> {
                        try {
                            JSONArray changes = new JSONArray();
                            JSONObject j = new JSONObject();
                            j.put("id", c.id);
                            j.put("deleted", 1);
                            j.put("updated_at", String.valueOf(System.currentTimeMillis()));
                            changes.put(j);

                            JSONObject body = new JSONObject()
                                    .put("token", prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN))
                                    .put("contacts", changes);
                            String url = prefs.getString("cloud_url", DEFAULT_CLOUD_URL);
                            postJson(url, body);
                        } catch (Exception ignored) {}
                    }).start();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void saveContactToDevice(Contact c) {
        Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
        intent.putExtra(ContactsContract.Intents.Insert.NAME, c.name);
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, c.phone);
        intent.putExtra(ContactsContract.Intents.Insert.NOTES, c.details);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open device contacts", Toast.LENGTH_SHORT).show();
        }
    }

    private class ContactAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Object getItem(int p) { return visible.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int position, View convertView, ViewGroup parent) {
            Contact c = visible.get(position);
            
            // Modern iOS-style rounded white card with 2-row grid layout:
            // Row 1: Name & Phone
            // Row 2: Action Toolbar with 7 bigger buttons (Call, SMS, WhatsApp, Copy, Save to Device, Edit, Delete)
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.WHITE);
            cardBg.setCornerRadius(dp(16));
            card.setBackground(cardBg);

            // Row 1: Name and Phone Number
            TextView n = tv(c.name.isEmpty() ? "Unnamed" : c.name, 18, Color.rgb(28, 28, 30));
            n.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(n);

            TextView ph = tv(PhoneUtils.formatIndianPhone(c.phone), 16, Color.rgb(0, 122, 255)); // iOS Blue
            ph.setPadding(dp(8), dp(2), dp(8), dp(4));
            card.addView(ph);

            if (!c.details.isEmpty()) {
                TextView d = tv(c.details, 13, Color.rgb(142, 142, 147));
                d.setPadding(dp(8), 0, dp(8), dp(4));
                card.addView(d);
            }
            if (c.edited || c.syncStatus.equals("PENDING")) {
                TextView s = tv("● Local change — pending sync", 12, Color.rgb(52, 199, 89)); // iOS Green
                s.setPadding(dp(8), 0, dp(8), dp(4));
                card.addView(s);
            }

            // Row 2: Action Toolbar with Bigger Buttons (7 actions: Call, SMS, WhatsApp, Copy, Save to Device, Edit, Delete)
            LinearLayout actions = new LinearLayout(MainActivity.this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(10), 0, dp(0));

            button(actions, "📞", "Call", v -> dial(c.phone));
            button(actions, "💬", "SMS", v -> sms(c.phone));
            button(actions, "🟢", "WhatsApp", v -> waMessage(c.phone));
            button(actions, "📋", "Copy", v -> copy(c.phone));
            button(actions, "👤+", "Save to Device", v -> saveContactToDevice(c));
            button(actions, "✏️", "Edit", v -> editContact(c));
            button(actions, "🗑️", "Delete", v -> deleteContact(c));
            
            card.addView(actions);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(dp(16), dp(8), dp(16), dp(8));
            card.setLayoutParams(cp);
            return card;
        }
    }

    private void button(LinearLayout row, String text, String tooltip, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18); // Bigger icon text size
        b.setBackground(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setTooltipText(tooltip);
        }
        b.setOnClickListener(listener);
        row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1)); // Bigger button height (48dp)
    }

    // ---------------- Backup & Restore ----------------
    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "ContactFinder-backup.csv");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("text/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        try {
            Uri u = data.getData();
            if (u == null) return;
            if (requestCode == REQ_EXPORT) {
                try (OutputStream o = getContentResolver().openOutputStream(u)) {
                    StringBuilder sb = new StringBuilder();
                    for (Contact c : all) {
                        sb.append(csv(c.id)).append(',')
                          .append(csv(c.name)).append(',')
                          .append(csv(c.phone)).append(',')
                          .append(csv(c.details)).append(',')
                          .append(0).append(',')
                          .append(c.edited ? 1 : 0).append(',')
                          .append(c.updatedAt).append(',')
                          .append(c.deleted ? 1 : 0).append(',')
                          .append(csv(c.source)).append(',')
                          .append(csv(c.syncStatus)).append('\n');
                    }
                    if (o != null) o.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "📤 Backup exported", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                String csvText = read(new InputStreamReader(getContentResolver().openInputStream(u), StandardCharsets.UTF_8));
                List<String[]> rows = CsvParser.parse(csvText);
                if (rows.isEmpty() || rows.get(0).length < 6) {
                    throw new IOException("Not a valid Contact Finder backup file");
                }
                new AlertDialog.Builder(this)
                        .setTitle("📥 Restore backup?")
                        .setMessage("This will merge or replace contacts with " + rows.size() + " imported records. Choose Merge or Replace.")
                        .setPositiveButton("MERGE", (d, w) -> importRows(rows, false))
                        .setNeutralButton("REPLACE", (d, w) -> importRows(rows, true))
                        .setNegativeButton("CANCEL", null)
                        .show();
            }
        } catch (Exception e) {
            showError(e);
        }
    }

    private void importRows(List<String[]> rows, boolean replace) {
        try {
            if (replace) {
                dbHelper.clearAll();
                all.clear();
            }
            List<Contact> importedList = new ArrayList<>();
            for (String[] a : rows) {
                if (a.length < 4) continue;
                Contact c = new Contact(a[0], a[1], a[2], a[3]);
                if (a.length > 4) c.saved = "1".equals(a[4]);
                if (a.length > 5) c.edited = "1".equals(a[5]);
                if (a.length > 6) c.updatedAt = parseTime(a[6]);
                if (a.length > 7) c.deleted = "1".equals(a[7]);
                if (a.length > 8) c.source = a[8];
                if (a.length > 9) c.syncStatus = a[9];
                importedList.add(c);
            }
            dbHelper.upsertContacts(importedList);
            loadLocalDatabase();
            filter("");
            Toast.makeText(this, "📥 Backup restored successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showError(e);
        }
    }

    // ---------------- Settings & Dialogs ----------------
    private void cloudSetup() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText url = new EditText(this);
        url.setHint("Hostinger sync.php URL");
        url.setText(prefs.getString("cloud_url", DEFAULT_CLOUD_URL));

        EditText token = new EditText(this);
        token.setHint("Sync token (Jmd@2025)");
        token.setText(prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN));

        box.addView(url);
        box.addView(token);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("☁️ Hostinger Cloud Sync Settings")
                .setMessage("Enter your Hostinger sync.php URL and token. Use 'Test Connection' to verify.")
                .setView(box)
                .setPositiveButton("SAVE", (d, w) -> {
                    prefs.edit()
                            .putString("cloud_url", url.getText().toString().trim())
                            .putString("cloud_token", token.getText().toString().trim())
                            .apply();
                    Toast.makeText(this, "⚙️ Cloud settings saved", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("TEST CONNECTION", null)
                .setNegativeButton("CANCEL", null)
                .create();

        dlg.setOnShowListener(dialog -> {
            Button testBtn = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
            testBtn.setOnClickListener(v -> {
                String testUrl = url.getText().toString().trim();
                String testToken = token.getText().toString().trim();
                if (testUrl.isEmpty()) {
                    Toast.makeText(this, "Please enter sync.php URL first", Toast.LENGTH_SHORT).show();
                    return;
                }
                setBusy(true, "Testing connection…");
                new Thread(() -> {
                    try {
                        String sep = testUrl.contains("?") ? "&" : "?";
                        String fullUrl = testUrl + sep + "token=" + URLEncoder.encode(testToken, "UTF-8");
                        HttpURLConnection c = openConnectionWithRedirects(fullUrl, "GET", null, null);
                        int code = c.getResponseCode();
                        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                        String s = is == null ? "" : read(new InputStreamReader(is, StandardCharsets.UTF_8));
                        String trimmed = s.trim().toLowerCase();
                        if (trimmed.startsWith("<!doctype") || trimmed.startsWith("<html")) {
                            throw new IOException("Server returned HTML error page. Check your URL path.");
                        }
                        JSONObject res = new JSONObject(s);
                        if (!res.optBoolean("ok", false)) {
                            throw new IOException(res.optString("error", "Unauthorized or invalid response"));
                        }
                        runOnUiThread(() -> {
                            setBusy(false, "Connection successful!");
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("✅ Connection Successful")
                                    .setMessage("Hostinger CSV backend connected successfully! Two-way sync is fully operational.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            setBusy(false, "Connection failed");
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("❌ Connection Failed")
                                    .setMessage("Could not connect.\n\nError: " + e.getMessage() + "\n\nTip: Check your URL and token.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                }).start();
            });
        });
        dlg.show();
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle("📖 About Contact Finder 2.2")
                .setMessage("Offline-first contact directory for large lists.\n\n"
                        + "• Edits/deletes persist locally in SQLite\n"
                        + "• Two-way cloud sync with Hostinger PHP CSV backend\n"
                        + "• Scan device contacts to import unique numbers\n"
                        + "• Phone numbers formatted for Indian locale\n"
                        + "• Backup/restore through Android file picker")
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------------- Utilities ----------------
    private void ensureDeviceId() {
        if (prefs.getString("device_id", null) == null) {
            prefs.edit().putString("device_id", UUID.randomUUID().toString()).apply();
        }
        SharedPreferences.Editor editor = prefs.edit();
        if (!prefs.contains("cloud_url")) {
            editor.putString("cloud_url", DEFAULT_CLOUD_URL);
        }
        if (!prefs.contains("cloud_token")) {
            editor.putString("cloud_token", DEFAULT_CLOUD_TOKEN);
        }
        editor.apply();
    }

    private String deviceId() {
        return prefs.getString("device_id", "");
    }

    private long parseTime(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        return t;
    }

    private void setBusy(boolean busy, String text) {
        if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showError(Exception e) {
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private void copy(String s) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Phone", s));
        Toast.makeText(this, "📋 Copied", Toast.LENGTH_SHORT).show();
    }

    private void dial(String p) {
        if (p.isEmpty()) return;
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p)));
    }

    private void sms(String p) {
        if (p.isEmpty()) return;
        startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + p)));
    }

    private void waMessage(String p) {
        String n = p.replaceAll("\\D", "");
        if (n.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + n)));
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private String csv(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private String read(InputStreamReader r) throws IOException {
        BufferedReader b = new BufferedReader(r);
        StringBuilder s = new StringBuilder();
        String l;
        while ((l = b.readLine()) != null) s.append(l).append('\n');
        return s.toString();
    }
}
