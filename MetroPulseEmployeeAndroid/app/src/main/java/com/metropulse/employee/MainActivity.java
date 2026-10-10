package com.metropulse.employee;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** MetroPulse staff app. Native Android UI with an offline-first incident queue. */
public class MainActivity extends Activity {
    private static final String PREFS = "metropulse_employee";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_LOCAL_REPORTS = "local_reports";
    private static final String KEY_NAME = "staff_name";
    private static final String KEY_ROLE = "staff_role";
    private static final String KEY_STATION = "staff_station";

    private static final int BG = 0xFFF3F6F1;
    private static final int FOREST = 0xFF103B2E;
    private static final int FOREST_2 = 0xFF1D5944;
    private static final int LIME = 0xFFC7F36A;
    private static final int INK = 0xFF16251D;
    private static final int MUTED = 0xFF6C786F;
    private static final int BORDER = 0xFFE0E8DE;
    private static final int PALE_GREEN = 0xFFEAF2E6;
    private static final int AMBER = 0xFF94601B;
    private static final int RED = 0xFFB4473D;
    private static final int WHITE = Color.WHITE;

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private LinearLayout root, contentFrame, bottomBar;
    private String currentTab = "home";
    private String quickCategoryPreset = "";
    private String quickSeverityPreset = "";

    // These input references belong to whichever screen is currently being rendered.
    private EditText serverUrlInput, nameInput, locationInput, descriptionInput;
    private Spinner roleSpinner, stationSpinner, categorySpinner, severitySpinner;
    private TextView apiStatus;
    private boolean syncInProgress = false;
    private boolean apiConnected = false;
    private String apiStatusMessage = "Server not configured";
    private boolean apiStatusGood = false;
    private JSONArray localReports = new JSONArray();

    private final String[] roles = {
            "Choose your role", "Sweeper / Housekeeping", "Security", "Technician",
            "Ticketing staff", "Station controller", "Operations officer", "Senior officer", "Other"
    };
    private final String[] stations = {
            "Select station", "Mahatma Mandir", "GIFT City", "GNLU", "Koba Circle", "Dholakuva",
            "Infocity", "Sector 1", "Sector 10A", "Sachivalaya", "Akshardham", "Other"
    };
    private final String[] categories = {
            "Choose issue type", "Cleaning & hygiene", "Electrical", "Lift / escalator", "Ticketing gate",
            "Safety / security", "Train / track", "Signage / information", "Other"
    };
    private final String[] severities = {"Low", "Normal", "High", "Critical"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        localReports = readLocalReports();
        getWindow().setStatusBarColor(FOREST);
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildShell();
        renderScreen();
        registerNetworkListener();
        if (!getServerUrl().isEmpty()) testConnection(false);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(10), dp(14), dp(10));
        top.setBackgroundColor(WHITE);

        TextView brandMark = text("M", 22, LIME, true);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setBackground(rounded(FOREST, FOREST, 0, dp(13)));
        top.addView(brandMark, lp(dp(42), dp(42)));

        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        brandCopy.setPadding(dp(10), 0, 0, 0);
        brandCopy.addView(text("MetroPulse", 19, INK, true));
        TextView sub = text("STAFF WORKSPACE", 9, MUTED, true);
        sub.setLetterSpacing(0.13f);
        LinearLayout.LayoutParams subP = lp(-2, -2); subP.topMargin = dp(1);
        brandCopy.addView(sub, subP);
        top.addView(brandCopy, new LinearLayout.LayoutParams(0, -2, 1));

        Button settingsTop = button("⚙", false);
        settingsTop.setTextSize(20);
        settingsTop.setPadding(0, 0, 0, 0);
        top.addView(settingsTop, lp(dp(44), dp(42)));
        settingsTop.setOnClickListener(v -> goTo("settings"));
        root.addView(top, lp(-1, -2));

        contentFrame = new LinearLayout(this);
        contentFrame.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(7), dp(7), dp(7), dp(8));
        bottomBar.setBackground(rounded(WHITE, BORDER, dp(1), dp(18)));
        LinearLayout.LayoutParams navP = lp(-1, dp(66)); navP.setMargins(dp(10), dp(0), dp(10), dp(8));
        root.addView(bottomBar, navP);
        setContentView(root);
    }

    private void renderScreen() {
        if (contentFrame == null) return;
        contentFrame.removeAllViews();
        if ("home".equals(currentTab)) contentFrame.addView(buildHomeScreen(), lp(-1, -1));
        else if ("report".equals(currentTab)) contentFrame.addView(buildReportScreen(), lp(-1, -1));
        else if ("reports".equals(currentTab)) contentFrame.addView(buildReportsScreen(), lp(-1, -1));
        else contentFrame.addView(buildSettingsScreen(), lp(-1, -1));
        renderBottomNavigation();
    }

    private void goTo(String tab) {
        currentTab = tab;
        renderScreen();
    }

    private ScrollView scrollPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        return scroll;
    }

    private LinearLayout pageContent(ScrollView scroll) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(17), dp(17), dp(17), dp(22));
        scroll.addView(content);
        return content;
    }

    private View buildHomeScreen() {
        ScrollView scroll = scrollPage();
        LinearLayout content = pageContent(scroll);

        LinearLayout greetingRow = new LinearLayout(this);
        greetingRow.setOrientation(LinearLayout.HORIZONTAL);
        greetingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout greetingCopy = new LinearLayout(this); greetingCopy.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("FIELD OPERATIONS", 10, 0xFF64805F, true); eyebrow.setLetterSpacing(0.14f);
        greetingCopy.addView(eyebrow);
        String savedName = preferences.getString(KEY_NAME, "").trim();
        String greeting = timeGreeting() + (savedName.isEmpty() ? "" : ", " + firstName(savedName));
        TextView hello = text(greeting, 25, INK, true);
        LinearLayout.LayoutParams helloP = lp(-1, -2); helloP.topMargin = dp(4);
        greetingCopy.addView(hello, helloP);
        greetingRow.addView(greetingCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView shiftBadge = text("●  STAFF", 10, FOREST, true);
        shiftBadge.setPadding(dp(10), dp(8), dp(10), dp(8));
        shiftBadge.setBackground(rounded(PALE_GREEN, 0xFFDCE8D7, dp(1), dp(30)));
        greetingRow.addView(shiftBadge, lp(-2, -2));
        content.addView(greetingRow);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(19), dp(20), dp(19), dp(19));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{FOREST, FOREST_2});
        heroBg.setCornerRadius(dp(22)); hero.setBackground(heroBg);
        LinearLayout.LayoutParams heroP = lp(-1, -2); heroP.topMargin = dp(17);
        content.addView(hero, heroP);
        TextView heroPill = text("ONE NETWORK. EVERY TEAM.", 10, LIME, true);
        heroPill.setLetterSpacing(0.10f);
        hero.addView(heroPill);
        TextView heroTitle = text("See it. Report it.\nTrack it.", 29, WHITE, true);
        LinearLayout.LayoutParams htp = lp(-1, -2); htp.topMargin = dp(10);
        hero.addView(heroTitle, htp);
        TextView heroSub = text("Make every station issue visible to the people who can act.", 13, 0xFFD8E5DD, false);
        LinearLayout.LayoutParams hsp = lp(-1, -2); hsp.topMargin = dp(8);
        hero.addView(heroSub, hsp);
        Button newReport = button("＋   Report an issue", true);
        newReport.setTextColor(FOREST); newReport.setBackground(rounded(LIME, LIME, 0, dp(12)));
        LinearLayout.LayoutParams nrp = lp(-1, dp(49)); nrp.topMargin = dp(18);
        hero.addView(newReport, nrp);
        newReport.setOnClickListener(v -> goTo("report"));

        LinearLayout statusCard = card();
        LinearLayout.LayoutParams statusP = lp(-1, -2); statusP.topMargin = dp(13);
        content.addView(statusCard, statusP);
        LinearLayout statusTop = new LinearLayout(this); statusTop.setOrientation(LinearLayout.HORIZONTAL); statusTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView connectionIcon = text(apiConnected ? "✓" : "↗", 18, apiConnected ? 0xFF26734A : AMBER, true);
        connectionIcon.setGravity(Gravity.CENTER); connectionIcon.setBackground(rounded(apiConnected ? 0xFFE5F4E8 : 0xFFFFF1D6, 0, 0, dp(12)));
        statusTop.addView(connectionIcon, lp(dp(42), dp(42)));
        LinearLayout statusCopy = new LinearLayout(this); statusCopy.setOrientation(LinearLayout.VERTICAL); statusCopy.setPadding(dp(11), 0, dp(5), 0);
        statusCopy.addView(text("OPERATIONS CONNECTION", 9, MUTED, true));
        TextView statusHeadline = text(connectionHeadline(), 14, INK, true);
        LinearLayout.LayoutParams shP = lp(-1, -2); shP.topMargin = dp(3); statusCopy.addView(statusHeadline, shP);
        TextView statusDesc = text(connectionDescription(), 11, MUTED, false);
        LinearLayout.LayoutParams sdP = lp(-1, -2); sdP.topMargin = dp(3); statusCopy.addView(statusDesc, sdP);
        statusTop.addView(statusCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView gear = text("›", 27, MUTED, false); gear.setGravity(Gravity.CENTER);
        statusTop.addView(gear, lp(dp(25), dp(42)));
        statusCard.addView(statusTop);
        statusCard.setOnClickListener(v -> goTo("settings"));

        sectionHeading(content, "Quick report", "Start with what you noticed", "3 SHORTCUTS");
        LinearLayout quickRow = new LinearLayout(this); quickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qrp = lp(-1, -2); qrp.topMargin = dp(10);
        content.addView(quickRow, qrp);
        quickRow.addView(quickAction("✳", "Cleaning", "Spill, hygiene", 0xFFE8F4DF, () -> openQuickReport("Cleaning & hygiene", "Normal")), new LinearLayout.LayoutParams(0, dp(118), 1));
        View gap = new View(this); quickRow.addView(gap, lp(dp(9), 1));
        quickRow.addView(quickAction("⚡", "Equipment", "Lift, gate, lights", 0xFFFFF0D8, () -> openQuickReport("Lift / escalator", "High")), new LinearLayout.LayoutParams(0, dp(118), 1));
        LinearLayout quickRow2 = new LinearLayout(this); quickRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams qr2p = lp(-1, -2); qr2p.topMargin = dp(9); content.addView(quickRow2, qr2p);
        quickRow2.addView(quickAction("!", "Safety concern", "Flag a potential risk", 0xFFFCE7E4, () -> openQuickReport("Safety / security", "High")), new LinearLayout.LayoutParams(0, dp(104), 1));
        View gap2 = new View(this); quickRow2.addView(gap2, lp(dp(9), 1));
        quickRow2.addView(quickAction("▤", "All reports", "View your activity", 0xFFE5EBF8, () -> goTo("reports")), new LinearLayout.LayoutParams(0, dp(104), 1));

        sectionHeading(content, "At a glance", "Your reporting activity", "ON THIS DEVICE");
        LinearLayout statRow = new LinearLayout(this); statRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statP = lp(-1, -2); statP.topMargin = dp(10); content.addView(statRow, statP);
        statRow.addView(statTile(String.valueOf(localReports.length()), "TOTAL REPORTS", FOREST), new LinearLayout.LayoutParams(0, dp(82), 1));
        View sg = new View(this); statRow.addView(sg, lp(dp(9), 1));
        statRow.addView(statTile(String.valueOf(countPendingReports()), "WAITING TO SYNC", countPendingReports() > 0 ? AMBER : 0xFF26734A), new LinearLayout.LayoutParams(0, dp(82), 1));

        sectionHeading(content, "Recent activity", "Latest reports", "VIEW ALL");
        LinearLayout.LayoutParams recentP = lp(-1, -2); recentP.topMargin = dp(10);
        LinearLayout recent = card(); content.addView(recent, recentP);
        addRecentRows(recent, 2, true);
        if (localReports.length() > 2) {
            Button viewAll = button("View all reports  →", false);
            LinearLayout.LayoutParams vap = lp(-1, dp(43)); vap.topMargin = dp(7); recent.addView(viewAll, vap);
            viewAll.setOnClickListener(v -> goTo("reports"));
        }

        TextView footer = text("MetroPulse  ·  Built for every shift", 10, MUTED, true); footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = lp(-1, -2); fp.topMargin = dp(19); content.addView(footer, fp);
        return scroll;
    }

    private View quickAction(String icon, String title, String description, int tint, Runnable action) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setPadding(dp(13), dp(12), dp(10), dp(11));
        tile.setBackground(rounded(WHITE, BORDER, dp(1), dp(15)));
        TextView iconText = text(icon, 17, INK, true); iconText.setGravity(Gravity.CENTER); iconText.setBackground(rounded(tint, tint, 0, dp(10)));
        tile.addView(iconText, lp(dp(34), dp(32)));
        TextView name = text(title, 13, INK, true); LinearLayout.LayoutParams np = lp(-1, -2); np.topMargin = dp(8); tile.addView(name, np);
        TextView desc = text(description, 10, MUTED, false); LinearLayout.LayoutParams dp = lp(-1, -2); dp.topMargin = dp(3); tile.addView(desc, dp);
        tile.setOnClickListener(v -> action.run());
        return tile;
    }

    private View buildReportScreen() {
        ScrollView scroll = scrollPage(); LinearLayout content = pageContent(scroll);
        topPageTitle(content, "NEW INCIDENT", "Log a station issue", "Share what happened so the right team can act.");

        LinearLayout guide = new LinearLayout(this); guide.setOrientation(LinearLayout.HORIZONTAL); guide.setGravity(Gravity.CENTER_VERTICAL);
        guide.setPadding(dp(13), dp(11), dp(13), dp(11)); guide.setBackground(rounded(PALE_GREEN, 0xFFD7E6D2, dp(1), dp(12)));
        TextView guideIcon = text("✓", 16, FOREST, true); guideIcon.setGravity(Gravity.CENTER); guide.addView(guideIcon, lp(dp(28), dp(28)));
        TextView guideTxt = text("Saved on this phone first. Syncs when the server is reachable.", 11, FOREST, true);
        LinearLayout.LayoutParams gtp = lp(0, -2); gtp.leftMargin = dp(9); guide.addView(guideTxt, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams gp = lp(-1, -2); gp.topMargin = dp(12); content.addView(guide, gp);

        LinearLayout profileCard = card(); LinearLayout.LayoutParams pcp = lp(-1, -2); pcp.topMargin = dp(13); content.addView(profileCard, pcp);
        sectionTitle(profileCard, "01  ·  YOUR DETAILS", "Who is reporting?");
        label(profileCard, "Your name (optional)");
        nameInput = editText("e.g. R. Patel", false); nameInput.setText(preferences.getString(KEY_NAME, "")); profileCard.addView(nameInput, fieldParams());
        label(profileCard, "Your role *"); roleSpinner = spinner(roles); profileCard.addView(roleSpinner, fieldParams());
        setSpinnerToValue(roleSpinner, roles, preferences.getString(KEY_ROLE, ""));
        label(profileCard, "Station *"); stationSpinner = spinner(stations); profileCard.addView(stationSpinner, fieldParams());
        setSpinnerToValue(stationSpinner, stations, preferences.getString(KEY_STATION, ""));

        LinearLayout incidentCard = card(); LinearLayout.LayoutParams icp = lp(-1, -2); icp.topMargin = dp(13); content.addView(incidentCard, icp);
        sectionTitle(incidentCard, "02  ·  INCIDENT DETAILS", "What needs attention?");
        label(incidentCard, "Issue type *"); categorySpinner = spinner(categories); incidentCard.addView(categorySpinner, fieldParams());
        if (!quickCategoryPreset.isEmpty()) setSpinnerToValue(categorySpinner, categories, quickCategoryPreset);
        label(incidentCard, "Exact location *"); locationInput = editText("e.g. Concourse, Gate 3, near lift", false); incidentCard.addView(locationInput, fieldParams());
        label(incidentCard, "Priority *"); severitySpinner = spinner(severities); incidentCard.addView(severitySpinner, fieldParams());
        if (!quickSeverityPreset.isEmpty()) setSpinnerToValue(severitySpinner, severities, quickSeverityPreset);
        quickCategoryPreset = ""; quickSeverityPreset = "";
        TextView priorityHint = text("Critical: immediate safety risk · High: urgent attention · Normal/Low: routine issue", 10, MUTED, false);
        LinearLayout.LayoutParams php = lp(-1, -2); php.topMargin = dp(6); incidentCard.addView(priorityHint, php);
        label(incidentCard, "What did you observe? *"); descriptionInput = editText("Describe the issue and anything the response team should know…", true);
        descriptionInput.setMinLines(4); descriptionInput.setMaxLines(7); descriptionInput.setGravity(Gravity.TOP | Gravity.START); incidentCard.addView(descriptionInput, fieldParams());
        TextView safeNote = text("Avoid entering passenger personal data. For immediate danger, alert station control directly.", 10, MUTED, false);
        LinearLayout.LayoutParams snp = lp(-1, -2); snp.topMargin = dp(8); incidentCard.addView(safeNote, snp);
        Button submit = button("Submit incident report   ↗", true); LinearLayout.LayoutParams sbp = lp(-1, dp(52)); sbp.topMargin = dp(17); incidentCard.addView(submit, sbp);
        submit.setOnClickListener(v -> submitReport());
        Button saveDraftHint = button("Submit even if offline", false); LinearLayout.LayoutParams sh = lp(-1, dp(42)); sh.topMargin = dp(7); incidentCard.addView(saveDraftHint, sh);
        saveDraftHint.setOnClickListener(v -> toast("Complete the form, then submit. Reports are queued if offline."));
        return scroll;
    }

    private View buildReportsScreen() {
        ScrollView scroll = scrollPage(); LinearLayout content = pageContent(scroll);
        topPageTitle(content, "MY ACTIVITY", "Your reports", "A local history of reports sent from this phone.");

        LinearLayout metrics = new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mp = lp(-1, -2); mp.topMargin = dp(14); content.addView(metrics, mp);
        metrics.addView(statTile(String.valueOf(localReports.length()), "TOTAL REPORTS", FOREST), new LinearLayout.LayoutParams(0, dp(82), 1));
        View mg = new View(this); metrics.addView(mg, lp(dp(9), 1));
        metrics.addView(statTile(String.valueOf(countPendingReports()), "PENDING SYNC", countPendingReports() > 0 ? AMBER : 0xFF26734A), new LinearLayout.LayoutParams(0, dp(82), 1));

        LinearLayout panel = card(); LinearLayout.LayoutParams pp = lp(-1, -2); pp.topMargin = dp(13); content.addView(panel, pp);
        LinearLayout ph = new LinearLayout(this); ph.setOrientation(LinearLayout.HORIZONTAL); ph.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("REPORT REGISTER", 9, MUTED, true));
        TextView reportTitle = text("Recent activity", 18, INK, true); LinearLayout.LayoutParams rtp = lp(-1, -2); rtp.topMargin = dp(3); titles.addView(reportTitle, rtp);
        ph.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button retry = button("↻ Sync", false); retry.setTextSize(12); ph.addView(retry, lp(dp(88), dp(39)));
        retry.setOnClickListener(v -> syncPendingReports());
        panel.addView(ph);
        View line = new View(this); line.setBackgroundColor(BORDER); LinearLayout.LayoutParams lineP = lp(-1, dp(1)); lineP.topMargin = dp(13); panel.addView(line, lineP);
        addRecentRows(panel, 50, false);

        Button restore = button("↻  Restore local history to current server", false);
        LinearLayout.LayoutParams restoreP = lp(-1, dp(46)); restoreP.topMargin = dp(12); content.addView(restore, restoreP);
        restore.setOnClickListener(v -> reSyncAllLocalReports());
        TextView restoreHint = text("Use this if the server URL or tunnel changed, or the dashboard was reset. Existing reports are safely deduplicated by their local report ID.", 10, MUTED, false);
        LinearLayout.LayoutParams restoreHintP = lp(-1, -2); restoreHintP.topMargin = dp(6); content.addView(restoreHint, restoreHintP);

        Button create = button("＋  Create another report", true); LinearLayout.LayoutParams cp = lp(-1, dp(50)); cp.topMargin = dp(14); content.addView(create, cp);
        create.setOnClickListener(v -> goTo("report"));
        TextView note = text("This screen shows reports queued or submitted from this device. Server-side resolution status is managed in the admin console.", 10, MUTED, false);
        LinearLayout.LayoutParams np = lp(-1, -2); np.topMargin = dp(12); content.addView(note, np);
        return scroll;
    }

    private View buildSettingsScreen() {
        ScrollView scroll = scrollPage(); LinearLayout content = pageContent(scroll);
        topPageTitle(content, "PREFERENCES", "Settings", "Configure the server once. Keep the reporting screen simple.");

        LinearLayout connection = card(); LinearLayout.LayoutParams cP = lp(-1, -2); cP.topMargin = dp(14); content.addView(connection, cP);
        sectionTitle(connection, "01  ·  SERVER", "Operations connection");
        TextView explanation = text("This address is stored on this phone and is not shown on the home or report screen.", 11, MUTED, false); connection.addView(explanation);
        label(connection, "Server URL");
        serverUrlInput = editText("https://your-public-tunnel.example", false);
        serverUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverUrlInput.setText(getServerUrl()); connection.addView(serverUrlInput, fieldParams());
        apiStatus = text(apiStatusMessage, 11, apiStatusGood ? 0xFF26734A : AMBER, true);
        LinearLayout.LayoutParams asp = lp(-1, -2); asp.topMargin = dp(9); connection.addView(apiStatus, asp);
        Button saveTest = button("Save address & test connection", true); LinearLayout.LayoutParams stp = lp(-1, dp(49)); stp.topMargin = dp(13); connection.addView(saveTest, stp);
        saveTest.setOnClickListener(v -> { saveServerUrl(false); testConnection(false); });
        Button clearServer = button("Clear saved server address", false); LinearLayout.LayoutParams clp = lp(-1, dp(42)); clp.topMargin = dp(7); connection.addView(clearServer, clp);
        clearServer.setOnClickListener(v -> {
            preferences.edit().remove(KEY_SERVER).apply(); serverUrlInput.setText(""); apiConnected = false;
            setApiStatus("Server address cleared", false); toast("Server address cleared.");
        });

        LinearLayout profile = card(); LinearLayout.LayoutParams prp = lp(-1, -2); prp.topMargin = dp(13); content.addView(profile, prp);
        sectionTitle(profile, "02  ·  YOUR PROFILE", "Default staff details");
        TextView profileInfo = text("Your role and station will be prefilled every time you report an issue.", 11, MUTED, false); profile.addView(profileInfo);
        label(profile, "Display name"); nameInput = editText("e.g. R. Patel", false); nameInput.setText(preferences.getString(KEY_NAME, "")); profile.addView(nameInput, fieldParams());
        label(profile, "Default role"); roleSpinner = spinner(roles); profile.addView(roleSpinner, fieldParams()); setSpinnerToValue(roleSpinner, roles, preferences.getString(KEY_ROLE, ""));
        label(profile, "Default station"); stationSpinner = spinner(stations); profile.addView(stationSpinner, fieldParams()); setSpinnerToValue(stationSpinner, stations, preferences.getString(KEY_STATION, ""));
        Button saveProfile = button("Save my profile", true); LinearLayout.LayoutParams spp = lp(-1, dp(48)); spp.topMargin = dp(13); profile.addView(saveProfile, spp);
        saveProfile.setOnClickListener(v -> saveProfile());

        LinearLayout about = card(); LinearLayout.LayoutParams abp = lp(-1, -2); abp.topMargin = dp(13); content.addView(about, abp);
        sectionTitle(about, "03  ·  ABOUT", "Designed for the station floor");
        addBullet(about, "Offline queue", "Reports stay on this device until the server can be reached.");
        addBullet(about, "Simple reporting", "Quick shortcuts preselect issue type and suggested priority.");
        addBullet(about, "Responsible use", "For immediate danger, follow official station emergency procedures.");
        TextView version = text("MetroPulse Employee · Hackathon prototype · v1.1", 10, MUTED, false); LinearLayout.LayoutParams vp = lp(-1, -2); vp.topMargin = dp(14); about.addView(version, vp);
        return scroll;
    }

    private void openQuickReport(String category, String severity) {
        quickCategoryPreset = category; quickSeverityPreset = severity; goTo("report");
    }

    private void submitReport() {
        String name = nameInput.getText().toString().trim();
        if (roleSpinner.getSelectedItemPosition() == 0) { toast("Please choose your role."); return; }
        if (stationSpinner.getSelectedItemPosition() == 0) { toast("Please select a station."); return; }
        if (categorySpinner.getSelectedItemPosition() == 0) { toast("Please choose an issue type."); return; }
        String location = locationInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();
        if (location.isEmpty()) { locationInput.setError("Enter the exact location"); locationInput.requestFocus(); return; }
        if (description.length() < 8) { descriptionInput.setError("Please add at least 8 characters"); descriptionInput.requestFocus(); return; }

        JSONObject report = new JSONObject();
        String clientId = UUID.randomUUID().toString();
        String role = String.valueOf(roleSpinner.getSelectedItem());
        String station = String.valueOf(stationSpinner.getSelectedItem());
        String category = String.valueOf(categorySpinner.getSelectedItem());
        String severity = String.valueOf(severitySpinner.getSelectedItem());
        String localRef = "LOCAL-" + new SimpleDateFormat("MMdd-HHmmss", Locale.getDefault()).format(new Date());
        try {
            report.put("clientId", clientId);
            report.put("reporterName", name);
            report.put("role", role);
            report.put("station", station);
            report.put("category", category);
            report.put("location", location);
            report.put("severity", severity);
            report.put("description", description);
            report.put("localTime", new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date()));
            report.put("incidentId", localRef);
            report.put("syncState", "WAITING");
            report.put("status", "Open");
        } catch (Exception e) { toast("Could not prepare this report."); return; }

        JSONArray updated = new JSONArray(); updated.put(report);
        for (int i = 0; i < localReports.length() && i < 49; i++) {
            JSONObject old = localReports.optJSONObject(i); if (old != null) updated.put(old);
        }
        localReports = updated; persistLocalReports();
        preferences.edit().putString(KEY_NAME, name).putString(KEY_ROLE, role).putString(KEY_STATION, station).apply();
        hideKeyboard();
        toast("Report saved on this phone. Syncing when possible…");
        currentTab = "reports"; renderScreen();
        syncPendingReports();
    }

    private void saveServerUrl(boolean showToast) {
        if (serverUrlInput == null) return;
        String raw = serverUrlInput.getText().toString().trim();
        while (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
        if (!raw.isEmpty() && !(raw.startsWith("http://") || raw.startsWith("https://"))) {
            // Local IPs and localhost usually use HTTP; public tunnel/domain URLs should use HTTPS.
            boolean localAddress = raw.startsWith("localhost") || raw.startsWith("127.0.0.1") || raw.matches("\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?");
            raw = (localAddress ? "http://" : "https://") + raw;
        }
        if (raw.contains(" ")) { serverUrlInput.setError("Address must not contain spaces"); return; }
        String previous = getServerUrl();
        preferences.edit().putString(KEY_SERVER, raw).apply();
        serverUrlInput.setText(raw);
        if (!previous.isEmpty() && !raw.isEmpty() && !previous.equalsIgnoreCase(raw)) {
            JSONArray updated = new JSONArray();
            for (int i = 0; i < localReports.length(); i++) {
                JSONObject item = localReports.optJSONObject(i);
                if (item != null) {
                    try { item.put("syncState", "WAITING"); } catch (Exception ignored) { }
                    updated.put(item);
                }
            }
            localReports = updated; persistLocalReports();
            if (showToast) toast("Server changed. Local history queued for re-sync.");
        } else if (showToast) toast(raw.isEmpty() ? "Server address cleared." : "Server address saved.");
    }

    private String getServerUrl() { return preferences.getString(KEY_SERVER, "").trim().replaceAll("/+$", ""); }

    private void testConnection(boolean showMessage) {
        if (serverUrlInput != null && "settings".equals(currentTab)) saveServerUrl(false);
        String base = getServerUrl();
        if (base.isEmpty()) {
            apiConnected = false; setApiStatus("Add a server URL to connect this phone.", false);
            if (showMessage) toast("Server address is not configured.");
            return;
        }
        setApiStatus("Checking connection…", false);
        networkExecutor.execute(() -> {
            try {
                JSONObject response = requestJson(base + "/api/health", "GET", null);
                boolean ok = response.optBoolean("ok", false);
                int serverReportCount = response.optInt("reportCount", -1);
                runOnUiThread(() -> {
                    apiConnected = ok;
                    String connectedMessage = serverReportCount >= 0
                            ? "Connected · server currently has " + serverReportCount + " report(s)."
                            : "Connected · MetroPulse server is reachable.";
                    setApiStatus(ok ? connectedMessage : "Server responded, but health check failed.", ok);
                    if (ok) syncPendingReports();
                    if (showMessage) toast(ok ? "Connection successful." : "Health check failed.");
                    if ("home".equals(currentTab)) renderScreen();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    apiConnected = false;
                    setApiStatus("Can't reach server. Check the URL and tunnel status.", false);
                    if (showMessage) toast("Can't reach server. Check URL and try again.");
                    if ("home".equals(currentTab)) renderScreen();
                });
            }
        });
    }

    private void syncPendingReports() {
        if (syncInProgress) return;
        String base = getServerUrl();
        if (base.isEmpty()) {
            apiConnected = false;
            setApiStatus("Set the server URL in Settings to sync reports.", false);
            return;
        }
        ArrayList<JSONObject> pending = new ArrayList<>();
        for (int i = 0; i < localReports.length(); i++) {
            JSONObject item = localReports.optJSONObject(i);
            if (item != null && !"SYNCED".equals(item.optString("syncState"))) {
                try { pending.add(new JSONObject(item.toString())); } catch (Exception ignored) { }
            }
        }
        if (pending.isEmpty()) {
            if (apiConnected) setApiStatus("Connected · all reports are synced.", true);
            return;
        }
        syncInProgress = true;
        setApiStatus("Syncing " + pending.size() + " pending report(s)…", false);
        networkExecutor.execute(() -> {
            int sent = 0; String lastError = "";
            for (JSONObject pendingReport : pending) {
                try {
                    JSONObject result = requestJson(base + "/api/reports", "POST", pendingReport);
                    JSONObject serverReport = result.optJSONObject("report");
                    String incidentId = serverReport != null ? serverReport.optString("incidentId", "Submitted") : "Submitted";
                    String clientId = pendingReport.optString("clientId", "");
                    runOnUiThread(() -> markReportSynced(clientId, incidentId)); sent++;
                } catch (Exception e) { lastError = e.getMessage() == null ? "Connection failed" : e.getMessage(); }
            }
            final int finalSent = sent; final String finalError = lastError;
            runOnUiThread(() -> {
                syncInProgress = false;
                int remaining = countPendingReports();
                if (remaining == 0) {
                    apiConnected = true; setApiStatus("Connected · all reports are synced.", true);
                    toast(finalSent > 0 ? finalSent + " report(s) synced." : "All reports already synced.");
                } else if (finalSent > 0) {
                    apiConnected = true; setApiStatus(finalSent + " synced; " + remaining + " still pending.", true);
                } else {
                    apiConnected = false; setApiStatus("Reports are safe on this phone. Server unavailable.", false);
                }
                if ("home".equals(currentTab) || "reports".equals(currentTab)) renderScreen();
            });
        });
    }

    /** Re-upload this phone's report history to the currently configured server.
     * The API uses clientId for duplicate detection, so restoring history is idempotent.
     */
    private void reSyncAllLocalReports() {
        if (localReports.length() == 0) {
            toast("There are no local reports to restore.");
            return;
        }
        if (getServerUrl().isEmpty()) {
            toast("Open Settings and save the current server URL first.");
            goTo("settings");
            return;
        }
        JSONArray updated = new JSONArray();
        for (int i = 0; i < localReports.length(); i++) {
            JSONObject item = localReports.optJSONObject(i);
            if (item != null) {
                try { item.put("syncState", "WAITING"); } catch (Exception ignored) { }
                updated.put(item);
            }
        }
        localReports = updated;
        persistLocalReports();
        toast("Restoring " + localReports.length() + " report(s) to the current server…");
        renderScreen();
        syncPendingReports();
    }

    private void markReportSynced(String clientId, String incidentId) {
        JSONArray updated = new JSONArray();
        for (int i = 0; i < localReports.length(); i++) {
            JSONObject item = localReports.optJSONObject(i);
            if (item != null) {
                if (clientId.equals(item.optString("clientId", ""))) {
                    try { item.put("syncState", "SYNCED"); item.put("incidentId", incidentId); } catch (Exception ignored) { }
                }
                updated.put(item);
            }
        }
        localReports = updated; persistLocalReports();
    }

    private int countPendingReports() {
        int count = 0;
        for (int i = 0; i < localReports.length(); i++) {
            JSONObject item = localReports.optJSONObject(i);
            if (item != null && !"SYNCED".equals(item.optString("syncState"))) count++;
        }
        return count;
    }

    private JSONObject requestJson(String urlString, String method, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString); connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method); connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
            connection.setUseCaches(false); connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8); connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(data); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder response = new StringBuilder();
            if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) response.append(line);
            }
            JSONObject json = response.length() > 0 ? new JSONObject(response.toString()) : new JSONObject();
            if (status < 200 || status >= 300) throw new Exception(json.optString("error", "Server returned HTTP " + status));
            return json;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private void registerNetworkListener() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                runOnUiThread(() -> { if (!getServerUrl().isEmpty() && countPendingReports() > 0) syncPendingReports(); });
            }
            @Override public void onLost(Network network) {
                runOnUiThread(() -> {
                    apiConnected = false;
                    if (countPendingReports() > 0) setApiStatus("Connection lost · reports stay on this phone.", false);
                    if ("home".equals(currentTab)) renderScreen();
                });
            }
        };
        try { connectivityManager.registerDefaultNetworkCallback(networkCallback); } catch (Exception ignored) { }
    }

    private void saveProfile() {
        String name = nameInput == null ? "" : nameInput.getText().toString().trim();
        String role = roleSpinner != null && roleSpinner.getSelectedItemPosition() > 0 ? String.valueOf(roleSpinner.getSelectedItem()) : "";
        String station = stationSpinner != null && stationSpinner.getSelectedItemPosition() > 0 ? String.valueOf(stationSpinner.getSelectedItem()) : "";
        preferences.edit().putString(KEY_NAME, name).putString(KEY_ROLE, role).putString(KEY_STATION, station).apply();
        toast("Profile preferences saved.");
    }

    private void setApiStatus(String message, boolean good) {
        apiStatusMessage = message; apiStatusGood = good;
        if (apiStatus != null) { apiStatus.setText(message); apiStatus.setTextColor(good ? 0xFF26734A : AMBER); }
    }

    private String connectionHeadline() {
        if (getServerUrl().isEmpty()) return "Finish setup in Settings";
        return apiConnected ? "Connected to operations" : "Connection needs attention";
    }
    private String connectionDescription() {
        if (getServerUrl().isEmpty()) return "Add the public server URL once for this phone.";
        if (apiConnected) return countPendingReports() == 0 ? "Reports can reach the admin dashboard." : countPendingReports() + " report(s) waiting to sync.";
        return "Reports remain saved locally until the server is reachable.";
    }

    private void renderBottomNavigation() {
        bottomBar.removeAllViews();
        bottomBar.addView(navItem("⌂", "Home", "home"), new LinearLayout.LayoutParams(0, -1, 1));
        bottomBar.addView(navItem("＋", "Report", "report"), new LinearLayout.LayoutParams(0, -1, 1));
        bottomBar.addView(navItem("▤", "My reports", "reports"), new LinearLayout.LayoutParams(0, -1, 1));
        bottomBar.addView(navItem("⚙", "Settings", "settings"), new LinearLayout.LayoutParams(0, -1, 1));
    }

    private View navItem(String icon, String label, String tab) {
        boolean selected = currentTab.equals(tab);
        LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(3), dp(2), dp(2));
        if (selected) item.setBackground(rounded(PALE_GREEN, 0, 0, dp(12)));
        TextView iconText = text(icon, 19, selected ? FOREST : MUTED, true); iconText.setGravity(Gravity.CENTER);
        item.addView(iconText, lp(-1, dp(27)));
        TextView labelText = text(label, 9, selected ? FOREST : MUTED, selected); labelText.setGravity(Gravity.CENTER);
        item.addView(labelText, lp(-1, dp(17)));
        item.setOnClickListener(v -> goTo(tab));
        return item;
    }

    private void addRecentRows(LinearLayout parent, int limit, boolean compact) {
        int count = Math.min(localReports.length(), limit);
        if (count == 0) {
            LinearLayout empty = new LinearLayout(this); empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(20));
            TextView symbol = text("＋", 25, 0xFF7B8A7F, true); symbol.setGravity(Gravity.CENTER);
            empty.addView(symbol, lp(-1, dp(36)));
            TextView msg = text("No reports yet", 14, INK, true); msg.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams mP = lp(-1, -2); mP.topMargin = dp(4); empty.addView(msg, mP);
            TextView sub = text("When you report an issue, it will appear here.", 11, MUTED, false); sub.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sP = lp(-1, -2); sP.topMargin = dp(4); empty.addView(sub, sP);
            parent.addView(empty);
            return;
        }
        for (int i = 0; i < count; i++) {
            JSONObject item = localReports.optJSONObject(i); if (item == null) continue;
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackground(rounded(0xFFF9FBF8, BORDER, dp(1), dp(12)));
            if (i > 0 || !compact) { LinearLayout.LayoutParams rp = lp(-1, -2); rp.topMargin = dp(8); parent.addView(row, rp); } else parent.addView(row);
            LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
            TextView category = text(item.optString("category", "Incident"), 13, INK, true); left.addView(category);
            TextView id = text(item.optString("incidentId", "Local report"), 10, MUTED, false); LinearLayout.LayoutParams idp = lp(-1, -2); idp.topMargin = dp(3); left.addView(id, idp);
            top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            String severity = item.optString("severity", "Normal");
            int sevColor = "Critical".equals(severity) ? RED : ("High".equals(severity) ? AMBER : FOREST);
            TextView sev = text(severity.toUpperCase(Locale.ROOT), 9, sevColor, true); sev.setPadding(dp(8), dp(6), dp(8), dp(6));
            sev.setBackground(rounded("Critical".equals(severity) ? 0xFFFCE6E3 : ("High".equals(severity) ? 0xFFFFF0D8 : PALE_GREEN), 0, 0, dp(20)));
            top.addView(sev, lp(-2, -2)); row.addView(top);
            TextView where = text(item.optString("station", "") + "  ·  " + item.optString("location", ""), 11, MUTED, false);
            LinearLayout.LayoutParams wp = lp(-1, -2); wp.topMargin = dp(8); row.addView(where, wp);
            String reportDescription = item.optString("description", "").trim();
            TextView detailsLabel = text("DESCRIPTION", 9, MUTED, true); detailsLabel.setLetterSpacing(0.06f);
            LinearLayout.LayoutParams dlp = lp(-1, -2); dlp.topMargin = dp(9); row.addView(detailsLabel, dlp);
            TextView details = text(reportDescription.isEmpty() ? "Description was not saved in this report." : reportDescription, 12, reportDescription.isEmpty() ? MUTED : INK, false);
            details.setMaxLines(5);
            details.setOnClickListener(v -> details.setMaxLines(details.getMaxLines() == 5 ? 100 : 5));
            LinearLayout.LayoutParams detailsP = lp(-1, -2); detailsP.topMargin = dp(3); row.addView(details, detailsP);
            LinearLayout foot = new LinearLayout(this); foot.setOrientation(LinearLayout.HORIZONTAL); foot.setGravity(Gravity.CENTER_VERTICAL);
            boolean synced = "SYNCED".equals(item.optString("syncState"));
            TextView state = text(synced ? "✓  Synced to server" : "◷  Saved on phone · pending sync", 10, synced ? 0xFF26734A : AMBER, true);
            foot.addView(state, new LinearLayout.LayoutParams(0, -2, 1));
            String time = item.optString("localTime", ""); if (!time.isEmpty()) foot.addView(text(time, 9, MUTED, false));
            LinearLayout.LayoutParams fp = lp(-1, -2); fp.topMargin = dp(8); row.addView(foot, fp);
        }
    }

    private void sectionHeading(LinearLayout parent, String eyebrow, String title, String action) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.BOTTOM);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView e = text(eyebrow.toUpperCase(Locale.ROOT), 9, MUTED, true); e.setLetterSpacing(0.10f); copy.addView(e);
        TextView t = text(title, 18, INK, true); LinearLayout.LayoutParams tp = lp(-1, -2); tp.topMargin = dp(3); copy.addView(t, tp);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null && !action.isEmpty()) { TextView a = text(action, 9, FOREST, true); a.setGravity(Gravity.CENTER_VERTICAL); row.addView(a); }
        LinearLayout.LayoutParams rp = lp(-1, -2); rp.topMargin = dp(20); parent.addView(row, rp);
    }

    private void topPageTitle(LinearLayout parent, String eyebrow, String title, String subtitle) {
        TextView e = text(eyebrow, 10, 0xFF64805F, true); e.setLetterSpacing(0.12f); parent.addView(e);
        TextView h = text(title, 27, INK, true); LinearLayout.LayoutParams hp = lp(-1, -2); hp.topMargin = dp(4); parent.addView(h, hp);
        TextView s = text(subtitle, 12, MUTED, false); LinearLayout.LayoutParams sp = lp(-1, -2); sp.topMargin = dp(4); parent.addView(s, sp);
    }

    private View statTile(String number, String label, int accent) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setPadding(dp(13), dp(11), dp(10), dp(10)); tile.setBackground(rounded(WHITE, BORDER, dp(1), dp(14)));
        tile.addView(text(number, 24, accent, true));
        TextView lab = text(label, 9, MUTED, true); lab.setLetterSpacing(0.04f); LinearLayout.LayoutParams lp = lp(-1, -2); lp.topMargin = dp(3); tile.addView(lab, lp);
        return tile;
    }

    private void addBullet(LinearLayout parent, String title, String description) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.TOP);
        TextView dot = text("✓", 13, FOREST, true); dot.setGravity(Gravity.CENTER); dot.setBackground(rounded(PALE_GREEN, 0, 0, dp(9))); row.addView(dot, lp(dp(28), dp(28)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(text(title, 13, INK, true)); TextView desc = text(description, 11, MUTED, false); LinearLayout.LayoutParams dp = lp(-1, -2); dp.topMargin = dp(3); copy.addView(desc, dp);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1)); LinearLayout.LayoutParams rp = lp(-1, -2); rp.topMargin = dp(8); parent.addView(row, rp);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(rounded(WHITE, BORDER, dp(1), dp(16))); return card;
    }

    private void sectionTitle(LinearLayout parent, String eyebrow, String title) {
        TextView e = text(eyebrow, 9, 0xFF64805F, true); e.setLetterSpacing(0.08f); parent.addView(e);
        TextView t = text(title, 17, INK, true); LinearLayout.LayoutParams p = lp(-1, -2); p.topMargin = dp(4); p.bottomMargin = dp(4); parent.addView(t, p);
    }

    private void label(LinearLayout parent, String value) {
        TextView label = text(value, 12, 0xFF35433A, true); LinearLayout.LayoutParams p = lp(-1, -2); p.topMargin = dp(13); p.bottomMargin = dp(6); parent.addView(label, p);
    }

    private EditText editText(String hint, boolean multiline) {
        EditText edit = new EditText(this); edit.setHint(hint); edit.setTextSize(13); edit.setTextColor(INK); edit.setHintTextColor(0xFF929B93);
        edit.setPadding(dp(12), dp(multiline ? 11 : 0), dp(12), dp(multiline ? 11 : 0)); edit.setBackground(rounded(0xFFFBFCFA, 0xFFD9E2D7, dp(1), dp(10)));
        if (!multiline) { edit.setSingleLine(true); edit.setMinHeight(dp(47)); }
        else { edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES); edit.setMinHeight(dp(108)); }
        return edit;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent); view.setTextSize(13);
                boolean placeholder = position == 0 && (values[0].startsWith("Choose") || values[0].startsWith("Select"));
                view.setTextColor(placeholder ? 0xFF929B93 : INK); view.setGravity(Gravity.CENTER_VERTICAL); return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent); view.setTextSize(13); view.setTextColor(INK); view.setPadding(dp(12), dp(12), dp(12), dp(12)); return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        spinner.setPadding(dp(6), 0, dp(6), 0); spinner.setMinimumHeight(dp(47)); spinner.setBackground(rounded(0xFFFBFCFA, 0xFFD9E2D7, dp(1), dp(10)));
        return spinner;
    }

    private void setSpinnerToValue(Spinner spinner, String[] values, String saved) {
        if (saved == null || saved.isEmpty()) return;
        for (int i = 0; i < values.length; i++) if (values[i].equals(saved)) { spinner.setSelection(i); return; }
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(13); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(10), 0, dp(10), 0); button.setMinHeight(dp(42));
        button.setBackground(rounded(primary ? FOREST : 0xFFF0F4EE, primary ? FOREST : 0xFFD8E2D5, dp(1), dp(11)));
        button.setTextColor(primary ? WHITE : FOREST); return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return text;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, stroke); return d;
    }

    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private LinearLayout.LayoutParams fieldParams() { LinearLayout.LayoutParams p = lp(-1, dp(47)); p.bottomMargin = dp(1); return p; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard() {
        try { InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); if (imm != null && descriptionInput != null) imm.hideSoftInputFromWindow(descriptionInput.getWindowToken(), 0); }
        catch (Exception ignored) { }
    }
    private String timeGreeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good morning"; if (hour < 17) return "Good afternoon"; return "Good evening";
    }
    private String firstName(String full) { String[] parts = full.trim().split("\\s+"); return parts.length == 0 ? full : parts[0]; }

    private JSONArray readLocalReports() {
        try { return new JSONArray(preferences.getString(KEY_LOCAL_REPORTS, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private void persistLocalReports() { preferences.edit().putString(KEY_LOCAL_REPORTS, localReports.toString()).apply(); }

    @Override protected void onResume() {
        super.onResume();
        if (preferences != null && !getServerUrl().isEmpty() && countPendingReports() > 0) syncPendingReports();
    }

    @Override protected void onDestroy() {
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) { }
        }
        networkExecutor.shutdown(); super.onDestroy();
    }
}
