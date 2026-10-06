package com.balajifinance.contactfinder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
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
    private static final String DEFAULT_CLOUD_URL = "https://white-jackal-528611.hostingersite.com/contacts/index.php";
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
    private String currentActiveCsv = "contacts.csv";

    // Light Theme Glassmorphism Palettes
    private static final int COLOR_BG = Color.parseColor("#F2F5FA");
    private static final int COLOR_GLASS = Color.argb(225, 255, 255, 255);
    private static final int COLOR_GLASS_BORDER = Color.argb(120, 255, 255, 255);
    private static final int COLOR_BORDER_SUBTLE = Color.parseColor("#E2E8F0");
    private static final int COLOR_PRIMARY = Color.parseColor("#007AFF");
    private static final int COLOR_PRIMARY_BG = Color.argb(25, 0, 122, 255);
    private static final int COLOR_TEXT_MAIN = Color.parseColor("#1E293B");
    private static final int COLOR_TEXT_MUTED = Color.parseColor("#64748B");
    private static final int COLOR_TEXT_HINT = Color.parseColor("#94A3B8");
    private static final int COLOR_DANGER = Color.parseColor("#EF4444");
    private static final int COLOR_SUCCESS = Color.parseColor("#10B981");
    private static final int COLOR_AMBER = Color.parseColor("#F59E0B");

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
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_CONTACTS);
        }
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_CONTACTS);
        }
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_SMS);
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(COLOR_BG);

        // Top App Bar with Light Glassmorphism Card
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(COLOR_GLASS);
        topBg.setStroke(dp(1), COLOR_GLASS_BORDER);
        topBar.setBackground(topBg);
        topBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        topBar.setElevation(dp(4));

        LinearLayout searchContainer = new LinearLayout(this);
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setPadding(dp(14), dp(6), dp(8), dp(6));
        searchContainer.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.argb(160, 255, 255, 255));
        searchBg.setStroke(dp(1), COLOR_BORDER_SUBTLE);
        searchBg.setCornerRadius(dp(22));
        searchContainer.setBackground(searchBg);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("🔍 Search contacts, phone, notes…");
        search.setHintTextColor(COLOR_TEXT_HINT);
        search.setTextColor(COLOR_TEXT_MAIN);
        search.setTextSize(15);
        search.setBackground(null);
        search.setPadding(dp(4), dp(4), dp(8), dp(4));
        searchContainer.addView(search, new LinearLayout.LayoutParams(0, dp(40), 1));

        Button refreshBtn = createGlassIconButton("🔄", "Sync Hostinger", v -> syncCloud());
        searchContainer.addView(refreshBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        Button addBtn = createGlassIconButton("➕", "Add Contact", v -> editContact(null));
        searchContainer.addView(addBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        topBar.addView(searchContainer, new LinearLayout.LayoutParams(-1, -2));
        root.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        // Main Content Container
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);

        contactListView = new ListView(this);
        contactListView.setDivider(null);
        contactListView.setPadding(dp(8), dp(8), dp(8), dp(8));
        adapter = new ContactAdapter();
        contactListView.setAdapter(adapter);

        contentContainer.addView(contactListView, new LinearLayout.LayoutParams(-1, -1));
        root.addView(contentContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        // Modern Light Glassmorphic Bottom Navigation Bar (4 tabs)
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(COLOR_GLASS);
        navBg.setStroke(dp(1), COLOR_BORDER_SUBTLE);
        bottomNav.setBackground(navBg);
        bottomNav.setPadding(dp(8), dp(6), dp(8), dp(6));
        bottomNav.setElevation(dp(12));

        Button btnTabHome = createGlassNavButton("🏠", "Home", v -> switchTab(0));
        Button btnTabDuplicates = createGlassNavButton("⚠️", "Duplicates", v -> switchTab(4));
        Button btnTabStats = createGlassNavButton("📊", "Stats", v -> switchTab(5));
        Button btnTabSettings = createGlassNavButton("⚙️", "Settings", v -> switchTab(3));

        bottomNav.addView(btnTabHome, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomNav.addView(btnTabDuplicates, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomNav.addView(btnTabStats, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomNav.addView(btnTabSettings, new LinearLayout.LayoutParams(0, dp(48), 1));

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
            showingSavedOnly = false;
            filter(search.getText().toString());
            contentContainer.addView(contactListView, new LinearLayout.LayoutParams(-1, -1));
            search.setVisibility(View.VISIBLE);
        } else if (tabIndex == 1) {
            showingSavedOnly = true;
            filter(search.getText().toString());
            contentContainer.addView(contactListView, new LinearLayout.LayoutParams(-1, -1));
            search.setVisibility(View.VISIBLE);
        } else if (tabIndex == 3) {
            contentContainer.addView(buildSettingsView(), new LinearLayout.LayoutParams(-1, -1));
        } else if (tabIndex == 4) {
            contentContainer.addView(buildDuplicatesView(), new LinearLayout.LayoutParams(-1, -1));
        } else if (tabIndex == 5) {
            contentContainer.addView(buildStatsView(), new LinearLayout.LayoutParams(-1, -1));
        }
    }

    // ---------------- Light Glassmorphism Component Helpers ----------------
    private GradientDrawable createGlassCardBg() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(COLOR_GLASS);
        gd.setCornerRadius(dp(16));
        gd.setStroke(dp(1), COLOR_BORDER_SUBTLE);
        return gd;
    }

    private Button createGlassIconButton(String icon, String tooltip, View.OnClickListener listener) {
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

    private Button createGlassNavButton(String icon, String tooltip, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(icon);
        b.setTextSize(20);
        b.setBackground(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setTooltipText(tooltip);
        }
        b.setOnClickListener(listener);
        return b;
    }

    private TextView createGlassSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(COLOR_TEXT_MUTED);
        tv.setPadding(dp(6), dp(18), dp(6), dp(6));
        return tv;
    }

    private View createSettingsCard(String[] titles, Runnable[] actions) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createGlassCardBg());
        card.setElevation(dp(2));

        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setPadding(dp(16), dp(14), dp(16), dp(14));
            item.setGravity(Gravity.CENTER_VERTICAL);

            TextView tv = new TextView(this);
            tv.setText(titles[idx]);
            tv.setTextSize(15);
            tv.setTextColor(COLOR_TEXT_MAIN);
            item.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));

            if (actions[idx] != null && !titles[idx].contains("v3.0")) {
                TextView arrow = new TextView(this);
                arrow.setText("›");
                arrow.setTextSize(20);
                arrow.setTextColor(COLOR_TEXT_HINT);
                item.addView(arrow);
                item.setOnClickListener(v -> actions[idx].run());
            }

            card.addView(item);

            if (idx < titles.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(COLOR_BORDER_SUBTLE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
                lp.setMargins(dp(16), 0, 0, 0);
                card.addView(divider, lp);
            }
        }
        return card;
    }

    // ---------------- Views & Tabs ----------------
    private View buildSettingsView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(24));

        TextView title = tv("⚙️ App Settings & Tools", 22, COLOR_TEXT_MAIN);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(4));
        container.addView(title);

        TextView sub = tv("Active CSV: " + currentActiveCsv + " • Device: " + getDeviceModelName(), 13, COLOR_TEXT_MUTED);
        sub.setPadding(0, 0, 0, dp(12));
        container.addView(sub);

        // 1. Hostinger Cloud Backend
        container.addView(createGlassSectionHeader("☁️ HOSTINGER BACKEND"));
        container.addView(createSettingsCard(new String[]{
                "Sync Cloud Now (Two-Way)",
                "Select Remote CSV File (" + currentActiveCsv + ")",
                "Configure Hostinger URL & Token",
                "Test Hostinger Connection"
        }, new Runnable[]{
                this::syncCloud,
                this::showCsvSelectorDialog,
                this::cloudSetup,
                this::testHostingerConnection
        }));

        // 2. Device Contact Tools
        container.addView(createGlassSectionHeader("📱 DEVICE CONTACTS & ACTIONS"));
        container.addView(createSettingsCard(new String[]{
                "Save ALL Contacts to Device",
                "Auto Scan Device Contacts (Add & Upload)",
                "Auto Scan SMS Contacts & Messages",
                "Scan & Clean Device Duplicate Contacts"
        }, new Runnable[]{
                this::saveAllContactsToDevice,
                this::scanDeviceContacts,
                this::scanSmsContacts,
                this::scanAndCleanDeviceDuplicates
        }));

        // 3. Local Backups
        container.addView(createGlassSectionHeader("💾 LOCAL CSV BACKUPS"));
        container.addView(createSettingsCard(new String[]{
                "Export Local Backup (CSV)",
                "Import Local Backup (CSV)"
        }, new Runnable[]{
                this::exportBackup,
                this::importBackup
        }));

        // 4. About & Engine Info
        container.addView(createGlassSectionHeader("ℹ️ SYSTEM & ENGINE"));
        container.addView(createSettingsCard(new String[]{
                "About Contact Finder 3.0",
                "Hostinger All-Inclusive Backend v3.0"
        }, new Runnable[]{
                this::about,
                () -> {}
        }));

        scrollView.addView(container, new ViewGroup.LayoutParams(-1, -1));
        return scrollView;
    }

    private View buildDuplicatesView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(24));

        TextView title = tv("⚠️ Duplicate Contacts Manager", 22, COLOR_TEXT_MAIN);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(4));
        container.addView(title);

        ArrayList<ArrayList<Contact>> dupGroups = findDuplicateContactGroups();

        if (dupGroups.isEmpty()) {
            LinearLayout emptyCard = new LinearLayout(this);
            emptyCard.setOrientation(LinearLayout.VERTICAL);
            emptyCard.setBackground(createGlassCardBg());
            emptyCard.setPadding(dp(24), dp(36), dp(24), dp(36));
            emptyCard.setGravity(Gravity.CENTER);

            TextView msg = tv("✨ Clean! No Duplicates Found.\nAll app contacts have unique phone numbers.", 16, COLOR_TEXT_MUTED);
            msg.setGravity(Gravity.CENTER);
            emptyCard.addView(msg);
            container.addView(emptyCard);
        } else {
            // Action Banner to Auto Merge / Remove All App Duplicates
            LinearLayout banner = new LinearLayout(this);
            banner.setOrientation(LinearLayout.HORIZONTAL);
            banner.setBackground(createGlassCardBg());
            banner.setPadding(dp(14), dp(12), dp(14), dp(12));
            banner.setGravity(Gravity.CENTER_VERTICAL);

            TextView bannerTxt = tv("Found " + dupGroups.size() + " duplicate phone numbers in app database.", 14, COLOR_AMBER);
            bannerTxt.setTypeface(Typeface.DEFAULT_BOLD);
            banner.addView(bannerTxt, new LinearLayout.LayoutParams(0, -2, 1));

            Button autoCleanBtn = new Button(this);
            autoCleanBtn.setText("Clean All");
            autoCleanBtn.setTextSize(13);
            autoCleanBtn.setTextColor(Color.WHITE);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(COLOR_PRIMARY);
            btnBg.setCornerRadius(dp(8));
            autoCleanBtn.setBackground(btnBg);
            autoCleanBtn.setOnClickListener(v -> cleanAllAppDuplicates(dupGroups));
            banner.addView(autoCleanBtn);

            container.addView(banner);

            LinearLayout.LayoutParams blp = (LinearLayout.LayoutParams) banner.getLayoutParams();
            blp.setMargins(0, 0, 0, dp(14));
            banner.setLayoutParams(blp);

            for (ArrayList<Contact> group : dupGroups) {
                String phone = PhoneUtils.formatIndianPhone(group.get(0).phone);

                TextView header = tv("📱 " + phone + " (" + group.size() + " copies)", 13, COLOR_TEXT_MUTED);
                header.setTypeface(Typeface.DEFAULT_BOLD);
                header.setPadding(dp(4), dp(4), dp(4), dp(4));
                container.addView(header);

                LinearLayout groupCard = new LinearLayout(this);
                groupCard.setOrientation(LinearLayout.VERTICAL);
                groupCard.setBackground(createGlassCardBg());
                groupCard.setPadding(dp(12), dp(8), dp(12), dp(8));

                for (int i = 0; i < group.size(); i++) {
                    Contact c = group.get(i);
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(dp(8), dp(10), dp(8), dp(10));
                    row.setGravity(Gravity.CENTER_VERTICAL);

                    LinearLayout info = new LinearLayout(this);
                    info.setOrientation(LinearLayout.VERTICAL);
                    TextView nameView = tv(c.name.isEmpty() ? "Unnamed" : c.name, 15, COLOR_TEXT_MAIN);
                    nameView.setTypeface(Typeface.DEFAULT_BOLD);
                    info.addView(nameView);
                    if (!c.details.isEmpty()) {
                        info.addView(tv(c.details, 12, COLOR_TEXT_MUTED));
                    }
                    row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

                    Button delBtn = new Button(this);
                    delBtn.setText("Delete");
                    delBtn.setTextSize(13);
                    delBtn.setTextColor(COLOR_DANGER);
                    delBtn.setBackground(null);
                    delBtn.setOnClickListener(v -> {
                        deleteContact(c);
                        switchTab(4);
                    });
                    row.addView(delBtn, new LinearLayout.LayoutParams(-2, -2));

                    groupCard.addView(row);

                    if (i < group.size() - 1) {
                        View div = new View(this);
                        div.setBackgroundColor(COLOR_BORDER_SUBTLE);
                        groupCard.addView(div, new LinearLayout.LayoutParams(-1, 1));
                    }
                }

                container.addView(groupCard);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) groupCard.getLayoutParams();
                lp.setMargins(0, dp(4), 0, dp(14));
                groupCard.setLayoutParams(lp);
            }
        }

        scrollView.addView(container, new ViewGroup.LayoutParams(-1, -1));
        return scrollView;
    }

    private View buildStatsView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(24));

        TextView title = tv("📊 Database Analytics", 22, COLOR_TEXT_MAIN);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(12));
        container.addView(title);

        int total = all.size();
        int active = 0, savedContacts = 0, pendingSync = 0, withDetails = 0;
        HashSet<String> uniquePhones = new HashSet<>();
        for (Contact c : all) {
            if (c.deleted) continue;
            active++;
            if (c.saved) savedContacts++;
            if (c.edited || "PENDING".equals(c.syncStatus)) pendingSync++;
            if (!c.details.isEmpty()) withDetails++;
            String p = PhoneUtils.normalizePhone(c.phone);
            if (!p.isEmpty()) uniquePhones.add(p);
        }
        int dupCount = active - uniquePhones.size();
        long lastSync = prefs.getLong("last_sync", 0);
        String lastSyncStr = lastSync == 0 ? "Never" : new Date(lastSync).toString();

        String[] stats = new String[]{
                "Active Contacts", String.valueOf(active),
                "Starred / Saved", String.valueOf(savedContacts),
                "Unique Phone Numbers", String.valueOf(uniquePhones.size()),
                "Duplicate Numbers", String.valueOf(dupCount),
                "With Notes / Details", String.valueOf(withDetails),
                "Pending Cloud Sync", String.valueOf(pendingSync),
                "Active Cloud File", currentActiveCsv,
                "Device Identifier", getDeviceModelName(),
                "Last Sync Time", lastSyncStr
        };

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createGlassCardBg());
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setElevation(dp(2));

        for (int i = 0; i < stats.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = tv(stats[i], 14, COLOR_TEXT_MAIN);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

            TextView val = tv(stats[i + 1], 14, COLOR_PRIMARY);
            val.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(val, new LinearLayout.LayoutParams(-2, -2));

            card.addView(row);

            if (i < stats.length - 2) {
                View div = new View(this);
                div.setBackgroundColor(COLOR_BORDER_SUBTLE);
                card.addView(div, new LinearLayout.LayoutParams(-1, 1));
            }
        }

        container.addView(card);
        scrollView.addView(container, new ViewGroup.LayoutParams(-1, -1));
        return scrollView;
    }

    // ---------------- Contact Adapter (Glassmorphic Cards) ----------------
    private class ContactAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Object getItem(int p) { return visible.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int position, View convertView, ViewGroup parent) {
            Contact c = visible.get(position);

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(createGlassCardBg());
            card.setElevation(dp(2));

            // Row 1: Name and Save/Star toggle
            LinearLayout topRow = new LinearLayout(MainActivity.this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView n = tv(c.name.isEmpty() ? "Unnamed Contact" : c.name, 17, COLOR_TEXT_MAIN);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            topRow.addView(n, new LinearLayout.LayoutParams(0, -2, 1));

            TextView starBtn = tv(c.saved ? "⭐" : "☆", 20, COLOR_AMBER);
            starBtn.setPadding(dp(8), 0, dp(4), 0);
            starBtn.setOnClickListener(v -> toggleStarContact(c));
            topRow.addView(starBtn);

            card.addView(topRow);

            // Row 2: Formatted Phone Number
            TextView ph = tv(PhoneUtils.formatIndianPhone(c.phone), 15, COLOR_PRIMARY);
            ph.setTypeface(Typeface.DEFAULT_BOLD);
            ph.setPadding(dp(8), dp(2), dp(8), dp(4));
            ph.setOnClickListener(v -> dial(c.phone));
            card.addView(ph);

            if (!c.details.isEmpty()) {
                TextView d = tv(c.details, 13, COLOR_TEXT_MUTED);
                d.setPadding(dp(8), 0, dp(8), dp(4));
                card.addView(d);
            }

            if (c.edited || "PENDING".equals(c.syncStatus)) {
                TextView s = tv("● Pending Cloud Sync", 11, COLOR_SUCCESS);
                s.setPadding(dp(8), 0, dp(8), dp(4));
                card.addView(s);
            }

            // Row 3: Action Toolbar with 7 glass icons
            LinearLayout actions = new LinearLayout(MainActivity.this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(8), 0, 0);

            actionButton(actions, "📞", "Call", v -> dial(c.phone));
            actionButton(actions, "💬", "SMS", v -> sms(c.phone));
            actionButton(actions, "🟢", "WhatsApp", v -> waMessage(c.phone));
            actionButton(actions, "📋", "Copy", v -> copy(c.phone));
            actionButton(actions, "👤+", "Save to Device", v -> saveContactToDevice(c));
            actionButton(actions, "✏️", "Edit", v -> editContact(c));
            actionButton(actions, "🗑️", "Delete", v -> deleteContact(c));

            card.addView(actions);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(dp(12), dp(6), dp(12), dp(6));
            card.setLayoutParams(cp);
            return card;
        }
    }

    private void actionButton(LinearLayout row, String text, String tooltip, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setBackground(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setTooltipText(tooltip);
        }
        b.setOnClickListener(listener);
        row.addView(b, new LinearLayout.LayoutParams(0, dp(44), 1));
    }

    private void toggleStarContact(Contact c) {
        c.saved = !c.saved;
        c.edited = true;
        c.updatedAt = System.currentTimeMillis();
        c.syncStatus = "PENDING";
        dbHelper.upsertContact(c);
        filter(search.getText().toString());
        autoSyncCloudIfNeeded();
    }

    // ---------------- Save Single & Batch Contacts to Device Phonebook ----------------
    private void saveContactToDevice(Contact c) {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS}, REQ_PERMISSIONS);
            // Fallback to native contact insertion intent if write permission not yet granted
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
            intent.putExtra(ContactsContract.Intents.Insert.NAME, c.name);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, c.phone);
            intent.putExtra(ContactsContract.Intents.Insert.NOTES, c.details);
            try {
                startActivity(intent);
            } catch (Exception ignored) {}
            return;
        }

        try {
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build());

            if (!c.name.isEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name)
                        .build());
            }

            if (!c.phone.isEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, c.phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build());
            }

            if (!c.details.isEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, c.details)
                        .build());
            }

            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            Toast.makeText(this, "👤 Contact saved to device phonebook!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save to phone: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAllContactsToDevice() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS}, REQ_PERMISSIONS);
            Toast.makeText(this, "Please allow Write Contacts permission first", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("📲 Save All Contacts to Device?")
                .setMessage("This will add all " + all.size() + " contacts into your phone's address book. Unique contacts will be added without duplicates. Continue?")
                .setPositiveButton("SAVE ALL", (dlg, which) -> {
                    setBusy(true, "Saving contacts to device…");
                    new Thread(() -> {
                        int added = 0;
                        int skipped = 0;
                        try {
                            // Find existing device numbers to avoid duplicating on device
                            HashSet<String> existingDevicePhones = new HashSet<>();
                            Cursor cur = getContentResolver().query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                                    null, null, null
                            );
                            if (cur != null) {
                                try {
                                    while (cur.moveToNext()) {
                                        String p = PhoneUtils.normalizePhone(cur.getString(0));
                                        if (!p.isEmpty()) existingDevicePhones.add(p);
                                    }
                                } finally {
                                    cur.close();
                                }
                            }

                            ArrayList<ContentProviderOperation> batch = new ArrayList<>();
                            for (Contact c : all) {
                                if (c.deleted) continue;
                                String p = PhoneUtils.normalizePhone(c.phone);
                                if (p.isEmpty() || existingDevicePhones.contains(p)) {
                                    skipped++;
                                    continue;
                                }

                                int rawIdx = batch.size();
                                batch.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                                        .build());

                                if (!c.name.isEmpty()) {
                                    batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name)
                                            .build());
                                }

                                batch.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, c.phone)
                                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                                        .build());

                                existingDevicePhones.add(p);
                                added++;

                                if (batch.size() >= 250) {
                                    getContentResolver().applyBatch(ContactsContract.AUTHORITY, batch);
                                    batch.clear();
                                }
                            }

                            if (!batch.isEmpty()) {
                                getContentResolver().applyBatch(ContactsContract.AUTHORITY, batch);
                                batch.clear();
                            }

                            final int finalAdded = added;
                            final int finalSkipped = skipped;
                            runOnUiThread(() -> {
                                setBusy(false, "Saved to device");
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("✅ Save Complete")
                                        .setMessage(String.format(Locale.US, "Successfully saved %d contacts to phone address book!\nSkipped (already on device): %d", finalAdded, finalSkipped))
                                        .setPositiveButton("OK", null)
                                        .show();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                setBusy(false, "Save failed");
                                showError(e);
                            });
                        }
                    }).start();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    // ---------------- Scan & Clean Device Duplicates based on Exact Phone ----------------
    private void scanAndCleanDeviceDuplicates() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS}, REQ_PERMISSIONS);
            Toast.makeText(this, "Contacts read & write permissions required", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true, "Scanning device for duplicates…");
        new Thread(() -> {
            try {
                // Map from normalized phone number -> list of rawContactIds on the phone
                HashMap<String, ArrayList<Long>> phoneToRawIds = new LinkedHashMap<>();
                HashMap<Long, String> rawIdToName = new HashMap<>();

                Cursor cur = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{
                                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                                ContactsContract.CommonDataKinds.Phone.NUMBER,
                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        },
                        null, null, null
                );

                if (cur != null) {
                    try {
                        while (cur.moveToNext()) {
                            long rawId = cur.getLong(0);
                            String rawNumber = cur.getString(1);
                            String dispName = cur.getString(2);
                            String norm = PhoneUtils.normalizePhone(rawNumber);
                            if (norm.isEmpty()) continue;

                            rawIdToName.put(rawId, dispName != null ? dispName : "Unknown");
                            ArrayList<Long> ids = phoneToRawIds.get(norm);
                            if (ids == null) {
                                ids = new ArrayList<>();
                                phoneToRawIds.put(norm, ids);
                            }
                            if (!ids.contains(rawId)) {
                                ids.add(rawId);
                            }
                        }
                    } finally {
                        cur.close();
                    }
                }

                ArrayList<String> duplicatePhones = new ArrayList<>();
                int totalRedundantContacts = 0;
                for (String p : phoneToRawIds.keySet()) {
                    ArrayList<Long> ids = phoneToRawIds.get(p);
                    if (ids != null && ids.size() > 1) {
                        duplicatePhones.add(p);
                        totalRedundantContacts += (ids.size() - 1);
                    }
                }

                final int finalRedundant = totalRedundantContacts;
                final int dupPhoneCount = duplicatePhones.size();

                runOnUiThread(() -> {
                    setBusy(false, "Device scan complete");
                    if (dupPhoneCount == 0) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("✨ Device Contacts Clean")
                                .setMessage("No duplicate contacts with the same phone number were found on this device!")
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("⚠️ Device Duplicates Found")
                                .setMessage(String.format(Locale.US, "Found %d duplicate phone numbers with %d redundant contact records on your device.\n\nWould you like to delete the redundant duplicate records now?", dupPhoneCount, finalRedundant))
                                .setPositiveButton("DELETE DUPLICATES", (dlg, w) -> {
                                    deleteDeviceDuplicatesAsync(phoneToRawIds);
                                })
                                .setNegativeButton("CANCEL", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Scan error");
                    showError(e);
                });
            }
        }).start();
    }

    private void deleteDeviceDuplicatesAsync(HashMap<String, ArrayList<Long>> phoneToRawIds) {
        setBusy(true, "Cleaning device duplicates…");
        new Thread(() -> {
            try {
                int deletedCount = 0;
                ArrayList<ContentProviderOperation> ops = new ArrayList<>();

                for (ArrayList<Long> list : phoneToRawIds.values()) {
                    if (list.size() > 1) {
                        // Keep the first record, delete index 1 to end
                        for (int i = 1; i < list.size(); i++) {
                            long rawId = list.get(i);
                            Uri uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                                    .appendPath(String.valueOf(rawId))
                                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                                    .build();
                            ops.add(ContentProviderOperation.newDelete(uri).build());
                            deletedCount++;

                            if (ops.size() >= 100) {
                                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                                ops.clear();
                            }
                        }
                    }
                }

                if (!ops.isEmpty()) {
                    getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                    ops.clear();
                }

                final int finalDeleted = deletedCount;
                runOnUiThread(() -> {
                    setBusy(false, "Cleaning complete");
                    Toast.makeText(MainActivity.this, "🗑️ Deleted " + finalDeleted + " duplicate contacts from device", Toast.LENGTH_LONG).show();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("✅ Device Cleaned")
                            .setMessage("Removed " + finalDeleted + " redundant contact records from device address book.")
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Delete failed");
                    showError(e);
                });
            }
        }).start();
    }

    // ---------------- Scan Device Contacts (Auto save to dedicated device CSV) ----------------
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
                String deviceModel = getDeviceModelName();
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
                                c.details = "Imported from " + deviceModel;
                                c.source = "DEVICE_SCAN";
                                c.deviceId = deviceModel;
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
                    checkAndNotifyDuplicatesOnUse();
                    new AlertDialog.Builder(this)
                            .setTitle("Device Contacts Scan")
                            .setMessage(String.format(Locale.US, "Scanned: %d phone records\nNew unique contacts added: %d\nDevice: %s", finalScanned, finalAdded, deviceModel))
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

    // ---------------- Scan Device SMS & Save Contacts ----------------
    private void scanSmsContacts() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_SMS}, REQ_PERMISSIONS);
            Toast.makeText(this, "Please grant SMS permission to scan messages", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true, "Scanning SMS messages & contacts…");
        new Thread(() -> {
            try {
                HashSet<String> existingPhones = new HashSet<>();
                for (Contact c : all) {
                    String norm = PhoneUtils.normalizePhone(c.phone);
                    if (!norm.isEmpty()) existingPhones.add(norm);
                }

                List<Contact> newContacts = new ArrayList<>();
                JSONArray smsLogArray = new JSONArray();

                Uri smsUri = Uri.parse("content://sms");
                Cursor cursor = getContentResolver().query(
                        smsUri,
                        new String[]{"_id", "address", "body", "date", "type"},
                        null, null, "date DESC"
                );

                int scannedSms = 0;
                int addedContacts = 0;
                String deviceModel = getDeviceModelName();

                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            scannedSms++;
                            String address = cursor.getString(1);
                            String body = cursor.getString(2);
                            long date = cursor.getLong(3);
                            int type = cursor.getInt(4);

                            if (address != null && !address.trim().isEmpty()) {
                                String norm = PhoneUtils.normalizePhone(address);
                                if (!norm.isEmpty() && norm.length() >= 10 && !existingPhones.contains(norm)) {
                                    existingPhones.add(norm);
                                    Contact c = new Contact();
                                    c.name = "SMS: " + address;
                                    c.phone = norm;
                                    c.details = "Discovered via SMS on " + deviceModel;
                                    c.source = "SMS_SCAN";
                                    c.deviceId = deviceModel;
                                    c.edited = true;
                                    c.syncStatus = "PENDING";
                                    newContacts.add(c);
                                    addedContacts++;
                                }
                            }

                            // Keep up to 100 recent SMS for cloud sync logging
                            if (smsLogArray.length() < 100) {
                                JSONObject smsItem = new JSONObject();
                                smsItem.put("id", cursor.getString(0));
                                smsItem.put("address", address != null ? address : "");
                                smsItem.put("body", body != null ? body : "");
                                smsItem.put("date", date);
                                smsItem.put("type", type == 1 ? "INBOX" : "SENT");
                                smsItem.put("device_id", deviceModel);
                                smsLogArray.put(smsItem);
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }

                if (!newContacts.isEmpty()) {
                    dbHelper.upsertContacts(newContacts);
                    loadLocalDatabase();
                }

                // Upload scanned SMS records to Hostinger
                if (smsLogArray.length() > 0) {
                    uploadSmsToCloud(smsLogArray);
                }

                autoSyncCloudIfNeeded();

                final int finalSms = scannedSms;
                final int finalAdded = addedContacts;

                runOnUiThread(() -> {
                    setBusy(false, all.size() + " contacts • SMS scan complete");
                    filter(search.getText().toString());
                    checkAndNotifyDuplicatesOnUse();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("💬 SMS Contacts Scan Complete")
                            .setMessage(String.format(Locale.US, "Scanned: %d SMS messages\nNew unique contacts created: %d\nDevice: %s\nUploaded to Hostinger backend.", finalSms, finalAdded, deviceModel))
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "SMS scan failed");
                    showError(e);
                });
            }
        }).start();
    }

    private void uploadSmsToCloud(JSONArray smsItems) {
        try {
            String url = prefs.getString("cloud_url", DEFAULT_CLOUD_URL).trim();
            if (url.isEmpty()) url = DEFAULT_CLOUD_URL;
            JSONObject body = new JSONObject()
                    .put("token", prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN))
                    .put("action", "sync_sms")
                    .put("device_id", getDeviceModelName())
                    .put("sms_records", smsItems);
            postJson(url, body);
        } catch (Exception ignored) {}
    }

    // ---------------- Cloud Sync (Single All-Inclusive Hostinger Backend) ----------------
    private void syncCloud() {
        String url = prefs.getString("cloud_url", DEFAULT_CLOUD_URL).trim();
        if (url.isEmpty()) url = DEFAULT_CLOUD_URL;
        setBusy(true, "Syncing with Hostinger (" + currentActiveCsv + ")…");
        final String finalUrl = url;
        new Thread(() -> {
            try {
                String sep = finalUrl.contains("?") ? "&" : "?";
                String fetchUrl = finalUrl + sep + "file=" + URLEncoder.encode(currentActiveCsv, "UTF-8");
                JSONObject remote = getJson(fetchUrl);
                if (!remote.optBoolean("ok", false)) {
                    throw new IOException(remote.optString("error", "Cloud returned an error"));
                }
                JSONArray rows = remote.optJSONArray("rows");
                if (rows == null) rows = new JSONArray();

                mergeRemote(rows);

                JSONArray changes = new JSONArray();
                for (Contact c : all) {
                    if (c.edited || "PENDING".equals(c.syncStatus) || "PENDING_DELETE".equals(c.syncStatus)) {
                        changes.put(contactToJson(c));
                    }
                }

                if (changes.length() > 0) {
                    JSONObject body = new JSONObject()
                            .put("token", prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN))
                            .put("file", currentActiveCsv)
                            .put("device_id", getDeviceModelName())
                            .put("contacts", changes);
                    JSONObject result = postJson(finalUrl, body);
                    if (!result.optBoolean("ok", false)) {
                        throw new IOException(result.optString("error", "Cloud rejected changes"));
                    }
                }

                for (Contact c : all) {
                    if (c.edited || "PENDING".equals(c.syncStatus) || "PENDING_DELETE".equals(c.syncStatus)) {
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
                    checkAndNotifyDuplicatesOnUse();
                    Toast.makeText(MainActivity.this, "☁️ Hostinger cloud sync complete", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false, all.size() + " contacts • sync failed • local safe");
                    showError(e);
                });
            }
        }).start();
    }

    private void autoSyncCloudIfNeeded() {
        String url = prefs.getString("cloud_url", DEFAULT_CLOUD_URL).trim();
        if (url.isEmpty()) url = DEFAULT_CLOUD_URL;
        final String finalUrl = url;
        new Thread(() -> {
            try {
                String sep = finalUrl.contains("?") ? "&" : "?";
                String fetchUrl = finalUrl + sep + "file=" + URLEncoder.encode(currentActiveCsv, "UTF-8");
                JSONObject remote = getJson(fetchUrl);
                if (remote.optBoolean("ok", false)) {
                    JSONArray rows = remote.optJSONArray("rows");
                    if (rows != null) {
                        mergeRemote(rows);
                        JSONArray changes = new JSONArray();
                        for (Contact c : all) {
                            if (c.edited || "PENDING".equals(c.syncStatus)) {
                                changes.put(contactToJson(c));
                            }
                        }
                        if (changes.length() > 0) {
                            JSONObject body = new JSONObject()
                                    .put("token", prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN))
                                    .put("file", currentActiveCsv)
                                    .put("device_id", getDeviceModelName())
                                    .put("contacts", changes);
                            postJson(finalUrl, body);
                        }
                        prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply();
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            loadLocalDatabase();
                            filter(search.getText().toString());
                            checkAndNotifyDuplicatesOnUse();
                        });
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    // ---------------- CSV File Selector from Settings ----------------
    private void showCsvSelectorDialog() {
        setBusy(true, "Fetching CSV list from Hostinger…");
        new Thread(() -> {
            try {
                String url = prefs.getString("cloud_url", DEFAULT_CLOUD_URL).trim();
                if (url.isEmpty()) url = DEFAULT_CLOUD_URL;
                String sep = url.contains("?") ? "&" : "?";
                String listUrl = url + sep + "action=list_csvs";
                JSONObject res = getJson(listUrl);
                JSONArray files = res.optJSONArray("files");
                if (files == null || files.length() == 0) {
                    files = new JSONArray();
                    files.put("contacts.csv");
                    files.put("sms.csv");
                }

                ArrayList<String> fileList = new ArrayList<>();
                for (int i = 0; i < files.length(); i++) {
                    fileList.add(files.getString(i));
                }

                runOnUiThread(() -> {
                    setBusy(false, "Ready");
                    final String[] items = fileList.toArray(new String[0]);
                    int selected = 0;
                    for (int i = 0; i < items.length; i++) {
                        if (items[i].equalsIgnoreCase(currentActiveCsv)) {
                            selected = i;
                            break;
                        }
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("📁 Select Hostinger CSV File")
                            .setSingleChoiceItems(items, selected, (dlg, which) -> {
                                currentActiveCsv = items[which];
                                prefs.edit().putString("active_csv", currentActiveCsv).apply();
                                dlg.dismiss();
                                Toast.makeText(this, "Active CSV set to: " + currentActiveCsv, Toast.LENGTH_SHORT).show();
                                syncCloud();
                            })
                            .setNegativeButton("CANCEL", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Could not fetch list");
                    // Offer local options
                    final String[] fallback = new String[]{"contacts.csv", "sms.csv", "contacts_" + getDeviceModelName() + ".csv"};
                    new AlertDialog.Builder(this)
                            .setTitle("📁 Select CSV File")
                            .setItems(fallback, (dlg, which) -> {
                                currentActiveCsv = fallback[which];
                                prefs.edit().putString("active_csv", currentActiveCsv).apply();
                                Toast.makeText(this, "Active CSV set to: " + currentActiveCsv, Toast.LENGTH_SHORT).show();
                                syncCloud();
                            })
                            .setNegativeButton("CANCEL", null)
                            .show();
                });
            }
        }).start();
    }

    // ---------------- Duplicate Reminders & Auto Cleaning ----------------
    private void checkAndNotifyDuplicatesOnUse() {
        ArrayList<ArrayList<Contact>> dups = findDuplicateContactGroups();
        if (!dups.isEmpty()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "⚠️ " + dups.size() + " duplicate numbers detected. Check Duplicates tab!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void cleanAllAppDuplicates(ArrayList<ArrayList<Contact>> dupGroups) {
        new AlertDialog.Builder(this)
                .setTitle("🧹 Clean All App Duplicates?")
                .setMessage("This will keep the first record for each phone number and delete " + dupGroups.size() + " duplicate entries from your app database and sync changes to Hostinger. Proceed?")
                .setPositiveButton("CLEAN ALL", (dlg, which) -> {
                    for (ArrayList<Contact> g : dupGroups) {
                        for (int i = 1; i < g.size(); i++) {
                            Contact toDel = g.get(i);
                            all.remove(toDel);
                            dbHelper.deleteContact(toDel.id);
                        }
                    }
                    filter(search.getText().toString());
                    switchTab(4);
                    autoSyncCloudIfNeeded();
                    Toast.makeText(this, "✨ All app duplicates removed successfully!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("CANCEL", null)
                .show();
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

    // ---------------- Local Database Loading & CRUD ----------------
    private void loadLocalDatabase() {
        all.clear();
        all.addAll(dbHelper.getAllContacts());
        currentActiveCsv = prefs.getString("active_csv", "contacts.csv");
    }

    private void filter(String query) {
        visible.clear();
        String q = PhoneUtils.normalizeSearch(query);
        for (Contact c : all) {
            if (c.deleted) continue;
            if (showingSavedOnly && !(c.saved || c.edited || "LOCAL".equals(c.source))) continue;
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
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText nameEd = new EditText(this);
        nameEd.setHint("Full Name");
        if (c != null) nameEd.setText(c.name);

        EditText phoneEd = new EditText(this);
        phoneEd.setHint("Phone Number (+91...)");
        if (c != null) phoneEd.setText(c.phone);

        EditText detailsEd = new EditText(this);
        detailsEd.setHint("Details / Loan Info / Notes");
        if (c != null) detailsEd.setText(c.details);

        box.addView(nameEd);
        box.addView(phoneEd);
        box.addView(detailsEd);

        new AlertDialog.Builder(this)
                .setTitle(c == null ? "➕ Add New Contact" : "✏️ Edit Contact")
                .setView(box)
                .setPositiveButton("SAVE", (x, w) -> {
                    String name = nameEd.getText().toString().trim();
                    String phone = PhoneUtils.normalizePhone(phoneEd.getText().toString().trim());
                    String details = detailsEd.getText().toString().trim();
                    if (name.isEmpty() && phone.isEmpty()) return;

                    if (c == null) {
                        Contact newC = new Contact();
                        newC.name = name;
                        newC.phone = phone;
                        newC.details = details;
                        newC.source = "LOCAL";
                        newC.deviceId = getDeviceModelName();
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
                .setTitle("🗑️ Delete Contact?")
                .setMessage("Delete " + label + "?\n\nThis will remove the contact permanently from the app and sync deletion to Hostinger.")
                .setPositiveButton("DELETE", (dialog, which) -> {
                    String deletedStr = prefs.getString("permanently_deleted_ids", "");
                    if (!deletedStr.contains(c.id)) {
                        prefs.edit().putString("permanently_deleted_ids", deletedStr + "," + c.id).apply();
                    }

                    all.remove(c);
                    dbHelper.deleteContact(c.id);
                    filter(search.getText().toString());
                    Toast.makeText(this, "🗑️ Contact deleted", Toast.LENGTH_SHORT).show();

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
                                    .put("file", currentActiveCsv)
                                    .put("device_id", getDeviceModelName())
                                    .put("contacts", changes);
                            postJson(prefs.getString("cloud_url", DEFAULT_CLOUD_URL), body);
                        } catch (Exception ignored) {}
                    }).start();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    // ---------------- Network Helpers ----------------
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
            c.setRequestProperty("User-Agent", "ContactFinder/3.0");
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
            throw new IOException("Server returned HTML page. Check index.php path.");
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
            throw new IOException("Server returned HTML page. Check index.php path.");
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
            String dev = r.optString("device_id", "");
            if (local == null) {
                Contact c = new Contact(id, r.optString("name"), PhoneUtils.normalizePhone(r.optString("phone")), r.optString("details"));
                c.saved = svd;
                c.deleted = false;
                c.updatedAt = rt;
                c.source = "CLOUD";
                c.deviceId = dev;
                c.syncStatus = "SYNCED";
                map.put(id, c);
            } else if (!local.edited && rt > local.updatedAt) {
                local.name = r.optString("name");
                local.phone = PhoneUtils.normalizePhone(r.optString("phone"));
                local.details = r.optString("details");
                local.saved = svd;
                local.deleted = false;
                local.updatedAt = rt;
                local.deviceId = dev;
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
        j.put("device_id", getDeviceModelName());
        return j;
    }

    // ---------------- Settings & Connections ----------------
    private void cloudSetup() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText url = new EditText(this);
        url.setHint("Hostinger index.php URL");
        url.setText(prefs.getString("cloud_url", DEFAULT_CLOUD_URL));

        EditText token = new EditText(this);
        token.setHint("Sync token (Jmd@2025)");
        token.setText(prefs.getString("cloud_token", DEFAULT_CLOUD_TOKEN));

        box.addView(url);
        box.addView(token);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("☁️ Hostinger Cloud Settings")
                .setMessage("Enter your Hostinger index.php URL and token.")
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
                    Toast.makeText(this, "Please enter URL first", Toast.LENGTH_SHORT).show();
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
                        JSONObject res = new JSONObject(s);
                        if (!res.optBoolean("ok", false)) {
                            throw new IOException(res.optString("error", "Unauthorized or invalid response"));
                        }
                        runOnUiThread(() -> {
                            setBusy(false, "Connected");
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("✅ Connection Successful")
                                    .setMessage("Hostinger all-inclusive backend connected successfully! Ready to sync.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            setBusy(false, "Failed");
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("❌ Connection Failed")
                                    .setMessage("Could not connect.\n\nError: " + e.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                }).start();
            });
        });
        dlg.show();
    }

    private void testHostingerConnection() {
        cloudSetup();
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle("📖 About Contact Finder 3.0")
                .setMessage("All-Inclusive Hostinger Contact & SMS Ecosystem\n\n"
                        + "• Light Glassmorphism UI\n"
                        + "• Automatic per-device CSV storage based on device model\n"
                        + "• Auto scan device contacts & SMS numbers\n"
                        + "• Scan & clean device duplicate contacts\n"
                        + "• Save all contacts to phone address book\n"
                        + "• Full offline SQLite sync with zero Google Sheets reliance")
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------------- Backup & Export ----------------
    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE, "ContactFinder-backup-" + getDeviceModelName() + ".csv");
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
                    sb.append("id,name,phone,details,saved,edited,updated_at,deleted,source,sync_status,device_id\n");
                    for (Contact c : all) {
                        sb.append(csv(c.id)).append(',')
                                .append(csv(c.name)).append(',')
                                .append(csv(c.phone)).append(',')
                                .append(csv(c.details)).append(',')
                                .append(c.saved ? 1 : 0).append(',')
                                .append(c.edited ? 1 : 0).append(',')
                                .append(c.updatedAt).append(',')
                                .append(c.deleted ? 1 : 0).append(',')
                                .append(csv(c.source)).append(',')
                                .append(csv(c.syncStatus)).append(',')
                                .append(csv(c.deviceId)).append('\n');
                    }
                    if (o != null) o.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "📤 Backup exported", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                String csvText = read(new InputStreamReader(getContentResolver().openInputStream(u), StandardCharsets.UTF_8));
                List<String[]> rows = CsvParser.parse(csvText);
                if (rows.isEmpty() || rows.get(0).length < 3) {
                    throw new IOException("Not a valid Contact Finder backup file");
                }
                new AlertDialog.Builder(this)
                        .setTitle("📥 Restore Backup?")
                        .setMessage("This will merge contacts from " + rows.size() + " imported records. Continue?")
                        .setPositiveButton("MERGE", (d, w) -> importRows(rows))
                        .setNegativeButton("CANCEL", null)
                        .show();
            }
        } catch (Exception e) {
            showError(e);
        }
    }

    private void importRows(List<String[]> rows) {
        try {
            List<Contact> importedList = new ArrayList<>();
            for (String[] a : rows) {
                if (a.length < 3) continue;
                if ("id".equalsIgnoreCase(a[0]) && "name".equalsIgnoreCase(a[1])) continue;
                Contact c = new Contact(a[0], a[1], a[2], a.length > 3 ? a[3] : "");
                if (a.length > 4) c.saved = "1".equals(a[4]);
                if (a.length > 5) c.edited = "1".equals(a[5]);
                if (a.length > 6) c.updatedAt = parseTime(a[6]);
                if (a.length > 7) c.deleted = "1".equals(a[7]);
                if (a.length > 8) c.source = a[8];
                if (a.length > 9) c.syncStatus = a[9];
                if (a.length > 10) c.deviceId = a[10];
                importedList.add(c);
            }
            dbHelper.upsertContacts(importedList);
            loadLocalDatabase();
            filter("");
            autoSyncCloudIfNeeded();
            Toast.makeText(this, "📥 Backup merged successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showError(e);
        }
    }

    // ---------------- Utilities ----------------
    private void ensureDeviceId() {
        if (prefs.getString("device_id", null) == null) {
            prefs.edit().putString("device_id", UUID.randomUUID().toString()).apply();
        }
        if (prefs.getString("device_model", null) == null) {
            String sanitized = (Build.MANUFACTURER + "_" + Build.MODEL).replaceAll("[^a-zA-Z0-9_\\-]", "_");
            prefs.edit().putString("device_model", sanitized).apply();
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

    private String getDeviceModelName() {
        String m = prefs.getString("device_model", null);
        if (m == null || m.isEmpty()) {
            m = (Build.MANUFACTURER + "_" + Build.MODEL).replaceAll("[^a-zA-Z0-9_\\-]", "_");
        }
        return m;
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
        Toast.makeText(this, "📋 Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void dial(String p) {
        if (p == null || p.isEmpty()) return;
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p)));
    }

    private void sms(String p) {
        if (p == null || p.isEmpty()) return;
        startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + p)));
    }

    private void waMessage(String p) {
        if (p == null) return;
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
