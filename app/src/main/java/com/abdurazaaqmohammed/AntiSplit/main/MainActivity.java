package com.abdurazaaqmohammed.AntiSplit.main;

import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static com.abdurazaaqmohammed.utils.LegacyUtils.aboveSdk20;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.widget.NestedScrollView;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.abdurazaaqmohammed.utils.CompareUtils;
import com.abdurazaaqmohammed.utils.DeviceSpecsUtil;
import com.abdurazaaqmohammed.utils.InstallUtil;
import com.abdurazaaqmohammed.utils.LanguageUtil;
import com.abdurazaaqmohammed.utils.LegacyUtils;
import com.abdurazaaqmohammed.utils.RunUtil;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.reandroid.Merger;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apkeditor.Util;
import com.starry.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.github.paul035.LocaleHelper;

/** @noinspection deprecation*/
public class MainActivity extends AppCompatActivity {
    private int saveMode = 0;
    private String outputFolder;
    private boolean showDialog;
    private boolean signApk;
    private boolean selectSplitsForDevice;
    private Uri splitAPKUri;
    private ArrayList<Uri> uris;
    private boolean urisAreSplitApks = true;
    private boolean criticalErrorOccurred;
    private boolean checkForUpdates;
    private String lastVerChecked;
    boolean logEnabled;
    private boolean force;
    private String lang;
    private int theme;
    private int sortMode;
    private DeviceSpecsUtil deviceSpecsUtil;
    private String pkgName;
    private String suffix;
    private boolean systemTheme;

    private Resources rss;

    public Resources getRss() {
        return rss;
    }
    private Handler handler;

    public Handler getHandler() {
        return handler;
    }

    private MyAPKLogger logger;
    public MyAPKLogger getLogger() {
        return logger;
    }
    private String openedFile;

    /** @noinspection AssignmentUsedAsCondition*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        handler = new Handler(Looper.getMainLooper());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);

        String deviceLang = Locale.getDefault().getLanguage();
        boolean supportedLang = deviceLang.equals("ru");
        lang = settings.getString("lang", supportedLang ? deviceLang : "en");
        boolean useDeviceRss = lang.equals(deviceLang);
        rss = useDeviceRss ? getResources() : LocaleHelper.setLocale(this, lang).getResources();

        boolean dark = (rss.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(theme = settings.getInt("theme", dark
                ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light));

        setContentView(R.layout.activity_main);

        if(!useDeviceRss) LanguageUtil.updateMain(rss, this);

        if(theme == R.style.Theme_MyApp_Black) findViewById(R.id.main).setBackgroundColor(Color.BLACK);
        deviceSpecsUtil = new DeviceSpecsUtil(this);

        TextView logField = findViewById(R.id.logField);
        NestedScrollView scrollView = findViewById(R.id.scrollView);
        logger = new MyAPKLogger(this, logField, scrollView);

        RunUtil.runInBackground(() -> Util.deleteDir(getCacheDir()));

        if(aboveSdk20) {
            getWindow().addFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            int transparent = Color.TRANSPARENT;
            getWindow().setNavigationBarColor(transparent);
            getWindow().setStatusBarColor(transparent);
        }

        if (!LegacyUtils.supportsWriteExternalStorage) {
            EdgeToEdge.enable(this);
            getWindow().setStatusBarContrastEnforced(true);
            getWindow().setNavigationBarContrastEnforced(true);
        }

        // Fetch settings from SharedPreferences
        lastVerChecked = getSharedPreferences("set", Context.MODE_PRIVATE).getString("lastVerChecked", null);
        if((checkForUpdates = settings.getBoolean("checkForUpdates", true))) checkForUpdates(false);
        signApk = settings.getBoolean("signApk", true);
        force = settings.getBoolean("force", false);
        showDialog = settings.getBoolean("showDialog", false);
        selectSplitsForDevice = settings.getBoolean("selectSplitsForDevice", false);
        logEnabled = settings.getBoolean("logEnabled", true);
        saveMode = settings.getInt("saveMode", 0);
        systemTheme = settings.getBoolean("systemTheme", true);
        sortMode = settings.getInt("sortMode", 0);
        suffix = settings.getString("suffix", "_antisplit");
        outputFolder = settings.getString("outputFolder", com.abdurazaaqmohammed.utils.FileUtils.getAntisplitMFolder().getPath());

        View selectFromInstalledApps = findViewById(R.id.fromAppsButton);
        if(aboveSdk20) selectFromInstalledApps.setOnClickListener(this::installedAppsClickListener);
        else selectFromInstalledApps.setVisibility(View.GONE);

        findViewById(R.id.settingsButton).setOnClickListener(this::settingsButtonListener);

        findViewById(R.id.decodeButton).setOnClickListener(v -> {
            ((TextView) findViewById(R.id.logField)).setText("");
            ((TextView) findViewById(R.id.errorField)).setText("");
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("*/*")
                                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            // .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/vnd.android.package-archive", "application/octet-stream"}) // XAPK usually octet-stream
                            , 1);
                }
        );
        cleanupAppFolder();

        // Check if user shared or opened file with the app.
        final Intent openIntent = getIntent();
        final String action = openIntent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            splitAPKUri = openIntent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            splitAPKUri = openIntent.getData();
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && openIntent.hasExtra(Intent.EXTRA_STREAM)) {
            uris = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? openIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class) : openIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null && !uris.isEmpty()) {
                final Uri uri = uris.get(0);
                String path = uri.getPath();
                if(urisAreSplitApks = TextUtils.isEmpty(path) || !path.endsWith(".apk")) splitAPKUri = uri;
                else process();
            }
        }
        if (splitAPKUri != null) {
            if(showDialog) showApkSelectionDialog();
            else process();
        }
    }

    public void styleAlertDialog(AlertDialog ad) {
        styleAlertDialog(ad, true);
    }

    public void styleAlertDialog(AlertDialog ad, boolean fixedHeight) {
        Window w = ad.getWindow();
        if(w != null) {
            GradientDrawable border = new GradientDrawable();
            boolean light = theme == R.style.Theme_MyApp_Light;
            border.setColor(light ? Color.WHITE : Color.BLACK); // Background color

            // Border width and color
            border.setStroke(5, rss.getColor(R.color.main_500));

            border.setCornerRadius(24);
            w.setBackgroundDrawable(border);
            if(fixedHeight) {
                double m = 0.8;
                DisplayMetrics displayMetrics = rss.getDisplayMetrics();

                int height = (int) (displayMetrics.heightPixels * m);
                int width = (int) (displayMetrics.widthPixels * m);
                w.setLayout(width, height);
            }
        }
        runOnUiThread(ad::show);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(systemTheme) {
            int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                setTheme(theme = R.style.Theme_MyApp_Dark);
                recreate();
            } else if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
                setTheme(theme = R.style.Theme_MyApp_Light);
                recreate();
            }
        }
    }

    private void checkStoragePerm(int requestCode) {
        if(com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(this)) {
            getHandler().post(() -> Toast.makeText(this, rss.getString(R.string.grant_storage), Toast.LENGTH_LONG).show());
            if(LegacyUtils.supportsWriteExternalStorage) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
            else startActivityForResult(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())), requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 10 && grantResults[0] == PackageManager.PERMISSION_GRANTED) checkUpdateAfterStoragePermission.run();
        else if (requestCode == 9 && com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(this)) saveMode = 0;
    }

    @Override
    protected void onPause() {
        unregisterReceiver(onDownloadComplete);
        getSharedPreferences("set", Context.MODE_PRIVATE).edit()
            .putBoolean("logEnabled", logEnabled)
            .putInt("saveMode", saveMode)
            .putBoolean("showDialog", showDialog)
            .putBoolean("signApk", signApk)
            .putBoolean("force", force)
            .putBoolean("systemTheme", systemTheme)
            .putBoolean("selectSplitsForDevice", selectSplitsForDevice)
            .putInt("theme", theme)
            .putInt("sortMode", sortMode)
            .putBoolean("checkForUpdates", checkForUpdates)
            .putString("lang", lang)
            .putString("lastVerChecked", lastVerChecked)
            .putString("suffix", suffix)
            .putString("outputFolder", outputFolder)
            .apply();
        super.onPause();
    }

    /** @noinspection ResultOfMethodCallIgnored*/
    private void cleanupAppFolder() {
        if(com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(this)) return;
        File appFolder = new File(Environment.getExternalStorageDirectory(), "AntiSplit-M");
        if(!appFolder.exists()) return;
        File[] children = appFolder.listFiles();
        if(children == null) return;
        if (children.length == 0) appFolder.delete();
        else {
            for (File child : children) if (child.isFile() && child.length() == 0) child.delete();
            File[] remainingChildren = appFolder.listFiles();
            if (remainingChildren == null || remainingChildren.length == 0) appFolder.delete();
        }
    }

    @Override
    protected void onDestroy() {
        Util.deleteDir(getCacheDir());
        cleanupAppFolder();
        super.onDestroy();
    }

    File merged;

    private void process() {
        findViewById(R.id.installButton).setVisibility(View.GONE);
        toggleAnimation(true);

        MainActivity context = MainActivity.this;

        final File cacheDir = getCacheDir();
        boolean selectedFromInstalledApps = !TextUtils.isEmpty(pkgName);
        if(selectedFromInstalledApps) urisAreSplitApks = false;

        Merger merger = new Merger(cacheDir, this);

        new RunUtil(handler, context, null).runInBackground(() -> {
            criticalErrorOccurred = false; // reset to make sure success message shows
            try {
                if (selectedFromInstalledApps) {
                    try (ApkBundle bundle = new ApkBundle()) {
                        // Selected from apps list
                        PackageManager packageManager = context.getPackageManager();
                        bundle.loadApkDirectory(new File(packageManager.getPackageInfo(pkgName, 0).applicationInfo.sourceDir).getParentFile());
                        merged = merger.run(bundle, signApk, force);
                        selectDirToSaveAPKOrSaveNow(0);
                    }
                } else {
                    //selected from anything except app list
                    File workingFolder = new File(cacheDir, UUID.randomUUID().toString());
                    if(!workingFolder.mkdir()) workingFolder = cacheDir;
                    merger.setWorkingDirectory(workingFolder);

                    if(urisAreSplitApks) {
                        List<String> splitsToNotInclude = selectSplitsForDevice ? deviceSpecsUtil.getSplitsForDevice(splitAPKUri) : splitsUnselectedInDialog;
                        merged = merger.run(splitAPKUri, splitsToNotInclude, signApk, force);
                        selectDirToSaveAPKOrSaveNow();
                    } else try (ApkBundle bundle = new ApkBundle()) {
                        ArrayList<Uri> uriArrayList = uris;
                        for (int i = 0; i < uriArrayList.size(); i++) {
                            Uri uri = uriArrayList.get(i);
                            try (InputStream is = FileUtils.getInputStream(uri, context)) {
                                String fileName = getOriginalFileName(uri);
                                if (TextUtils.isEmpty(fileName)) fileName = "split_" + i + "_.apk";
                                com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, new File(workingFolder, fileName));
                            }
                        }

                        bundle.loadApkDirectory(workingFolder);
                        merged = merger.run(bundle, signApk, force);
                        selectDirToSaveAPKOrSaveNow();
                    }
                }
                toggleAnimation(false);
                return true;
            } catch (Exception e) {
                showError(e);
                return false;
            }
        }, () -> {
            pkgName = null; // reset
            if(urisAreSplitApks) getHandler().post(() -> {
                try {
                    uris.remove(0);
                    splitAPKUri = uris.get(0);
                    if(showDialog) showApkSelectionDialog();
                    else process();
                } catch (IndexOutOfBoundsException | NullPointerException ignored) {
                    // End of list, I don't know why but isEmpty is not working
                    setUIAfterMerging(merger);
                }
            });
            else setUIAfterMerging(merger); // multiple splits from inside the APK, can only be one go
        }, true);

        findViewById(R.id.fabs).setAlpha(0.5f);
        View cancelButton = findViewById(R.id.cancelButton);
        cancelButton.setVisibility(View.VISIBLE);
        cancelButton.setOnClickListener(v -> {
            try {
                startActivity(Intent.makeRestartActivityTask(Objects.requireNonNull(getPackageManager().getLaunchIntentForPackage(getPackageName())).getComponent()));
                Runtime.getRuntime().exit(0);
            } catch (Exception e) {
                recreate();
            }
        });

        View copyButton = findViewById(R.id.copyButton);
        copyButton.setVisibility(View.VISIBLE);
        copyButton.setOnClickListener(v -> copyText(new StringBuilder().append(((TextView) findViewById(R.id.logField)).getText()).append('\n').append(((TextView) findViewById(R.id.errorField)).getText())));
    }

    private void processOneSplitApkUri(Uri uri) {
        splitAPKUri = uri;
        if (showDialog) showApkSelectionDialog();
        else process();
    }

    public void toggleAnimation(boolean on) {
        runOnUiThread(() -> findViewById(R.id.progressIndicator).setVisibility(on ? View.VISIBLE : View.GONE));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 9 && com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(this)) saveMode = 0;
        else if(resultCode != RESULT_OK) {
            ((TextView) findViewById(R.id.logField)).setText("");
            ((TextView) findViewById(R.id.errorField)).setText("");
            cleanupAppFolder();
        }
        else if (data != null) switch (requestCode) {
            case 1:
                // opened through button in the app
                ClipData clipData = data.getClipData();
                if (clipData == null) {
                    Uri uri = data.getData();
                    if(uri == null) showError("input Uri null");
                    else {
                        String uriString = uri.toString();
                        if(uriString.endsWith(".apk")) {
                            showError(rss.getString(R.string.not_split, uriString));
                        } else processOneSplitApkUri(uri);
                    }
                }
                else {
                    //multiple files selected
                    Uri first = clipData.getItemAt(0).getUri();
                    String path = first.getPath();
                    uris = new ArrayList<>();
                    if (path != null && path.endsWith(".apk")) {
                        urisAreSplitApks = false;
                    } //else processOneSplitApkUri(first);
                    uris.add(first);
                    for (int i = 1; i < clipData.getItemCount(); i++) uris.add(clipData.getItemAt(i).getUri());

                    if (urisAreSplitApks) processOneSplitApkUri(first);
                    else process();
                }
                break;
            case 2:
                // NOT going to process a file now we are saving it
                new RunUtil(handler, this, null).runInBackground(() -> {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(data.getData())) {
                        com.abdurazaaqmohammed.utils.FileUtils.copyFile(merged, outputStream);
                        return true;
                    } catch (Exception e) {
                        showError(e);
                        return false;
                    }
                }, () -> {
                    if(!criticalErrorOccurred) {
                        final String success = rss.getString(R.string.success_saved);
                        logger.logMessage(success);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show());
                    }
                }, true);
                break;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT > 32) {
            registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        } else registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    long downloadId;
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (id == downloadId) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                            int columnIndex1 = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            String fileUri = cursor.getString(columnIndex1);
                            InstallUtil.installApk(MainActivity.this, Uri.parse(fileUri));
                        }
                    }
                }
            }
        }
    };

    private void checkForUpdates(boolean toast) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://api.github.com/repos/AbdurazaaqMohammed/AntiSplit-M/releases").openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0");
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

                try (InputStream inputStream = conn.getInputStream();
                     InputStreamReader in = new InputStreamReader(inputStream);
                     BufferedReader reader = new BufferedReader(in)) {
                    String line;
                    String latestVersion = "";
                    String changelog = "";
                    String dl = "";
                    boolean rightBranch = false;
                    while ((line = reader.readLine()) != null) {
                        if(line.contains("browser_download_url")) {
                            dl = line.split("\"")[3];
                            latestVersion = line.split("/")[7];
                            rightBranch = latestVersion.charAt(0) == '2';
                        } else if(line.contains("body") && rightBranch) {
                            changelog = line.split("\"")[3];
                            break;
                        }
                    }
                    String currentVer;
                    try {
                        currentVer = (MainActivity.this).getPackageManager().getPackageInfo((MainActivity.this).getPackageName(), 0).versionName;
                    } catch (Exception e) {
                        currentVer = null;
                    }
                    boolean newVer = false;
                    char[] curr = TextUtils.isEmpty(currentVer) ? new char[] {'2', '2', '9'} : currentVer.replace(".", "").toCharArray();
                    char[] latest = latestVersion.replace(".", "").toCharArray();

                    int maxLength = Math.max(curr.length, latest.length);
                    for (int i = 0; i < maxLength; i++) {
                        char currChar = i < curr.length ? curr[i] : '0';
                        char latestChar = i < latest.length ? latest[i] : '0';

                        if (latestChar > currChar) {
                            newVer = true;
                            break;
                        } else if (latestChar < currChar) {
                            break;
                        }
                    }

                    if(newVer) {
                        if (!toast && !TextUtils.isEmpty(lastVerChecked) && lastVerChecked.equals(latestVersion)) return;
                        String ending = ".apk";
                        String filename = "AntiSplit-M.v" + latestVersion + ending;
                        String link = dl.endsWith(ending) ? dl : dl + File.separator + filename;
                        MaterialTextView changelogText = new MaterialTextView(MainActivity.this);
                        String linebreak = "<br />";
                        changelogText.setText(Html.fromHtml(rss.getString(R.string.new_ver) + " (" + latestVersion  + ")" + linebreak + linebreak + "Changelog:" + linebreak + changelog.replace("\\r\\n", linebreak)));
                        int padding = 16;
                        changelogText.setPadding(padding, padding, padding, padding);
                        MaterialTextView title = new MaterialTextView(MainActivity.this);
                        title.setText(rss.getString(R.string.update));
                        int size = 20;
                        title.setPadding(size,size,size,size);
                        title.setTextSize(size);
                        title.setGravity(Gravity.CENTER);

                        String finalLatestVersion = latestVersion;
                        getHandler().post(() -> {
                            AlertDialog alertDialog = new MaterialAlertDialogBuilder(MainActivity.this).setCustomTitle(title).setView(changelogText).setPositiveButton(rss.getString(R.string.dl), (dialog, which) -> {
                                if (checkUpdateAfterStoragePermission == null)
                                    checkUpdateAfterStoragePermission = () -> {
                                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link))
                                        .setTitle(filename).setDescription(filename).setMimeType("application/vnd.android.package-archive")
                                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                        downloadId =
                                        ((DownloadManager) MainActivity.this.getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                                    };
                                if (Build.VERSION.SDK_INT < 29 && com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(this))
                                    MainActivity.this.checkStoragePerm(10);
                                else checkUpdateAfterStoragePermission.run();
                            }).setNegativeButton("Go to GitHub Release", (dialog, which) -> MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://github.com/AbdurazaaqMohammed/AntiSplit-M/releases/latest")))).setNeutralButton(rss.getString(R.string.cancel), null).create();
                            alertDialog.setOnDismissListener(dialog -> lastVerChecked = finalLatestVersion);
                            MainActivity.this.runOnUiThread(alertDialog::show);
                        });
                    } else if (toast) getHandler().post(() -> MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, rss.getString(R.string.no_update_found), Toast.LENGTH_SHORT).show()));
                    // return new String[]{ver, changelog, dl};
                }
            } catch (Exception e) {
              //ra  showError(e);
                if (toast) runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to check for update", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private Runnable checkUpdateAfterStoragePermission;

    public void showApkSelectionDialog() {
        try {
            List<String> splitsToNotInclude = deviceSpecsUtil.getListOfSplits(splitAPKUri);
            Collections.sort(splitsToNotInclude, CompareUtils::compareByName);
            final int initialSize = splitsToNotInclude.size();
            String[] apkNames = new String[initialSize + 5];
            boolean[] checkedItems = new boolean[initialSize + 5];

            apkNames[0] = rss.getString(R.string.all);
            apkNames[1] = rss.getString(R.string.for_device);
            apkNames[2] = rss.getString(R.string.arch_for_device);
            apkNames[3] = rss.getString(R.string.dpi_for_device);
            apkNames[4] = rss.getString(R.string.lang_for_device);
            for (int i = 5; i < initialSize + 5; i++) {
                apkNames[i] = splitsToNotInclude.get(i - 5);
                checkedItems[i] = false;
            }

            AlertDialog ad = new MaterialAlertDialogBuilder(this).setTitle(rss.getString(R.string.select_splits)).setMultiChoiceItems(apkNames, checkedItems, (dialog, which, isChecked) -> {
                switch (which) {
                    case 0:
                        // "Select All" option
                        for (int i = 5; i < checkedItems.length; i++)
                            ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = isChecked);
                        break;
                    case 1:
                        // device specs option
                        for (int i = 5; i < checkedItems.length; i++) {
                            ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = (isChecked && deviceSpecsUtil.shouldIncludeSplit(apkNames[i])));
                        }
                        boolean didNotFindAppropriateDpi = true;
                        for (int i = 5; i < checkedItems.length; i++) {
                            if (checkedItems[i] && apkNames[i].contains("dpi")) {
                                didNotFindAppropriateDpi = false;
                                break;
                            }
                        }
                        if (didNotFindAppropriateDpi) {
                            for (int i = 5; i < checkedItems.length; i++) {
                                if (apkNames[i].contains("hdpi")) {
                                    ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = isChecked);
                                    break;
                                }
                            }
                        }
                        break;
                    case 2:
                        //arch for device
                        for (int i = 5; i < checkedItems.length; i++) {
                            if (deviceSpecsUtil.shouldIncludeArch(apkNames[i]))
                                ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = isChecked);
                        }
                        break;
                    case 3:
                        //dpi for device
                        for (int i = 5; i < checkedItems.length; i++) {
                            if (deviceSpecsUtil.shouldIncludeDpi(apkNames[i]))
                                ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = isChecked);
                        }
                        boolean didNotFoundAppropriateDpi = true;
                        for (int i = 5; i < checkedItems.length; i++) {
                            if (checkedItems[i] && apkNames[i].contains("dpi")) {
                                didNotFoundAppropriateDpi = false;
                                break;
                            }
                        }
                        if (didNotFoundAppropriateDpi) {
                            for (int i = 5; i < checkedItems.length; i++) {
                                if (apkNames[i].contains("hdpi")) {
                                    ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = isChecked);
                                    break;
                                }
                            }
                        }
                        break;
                    case 4:
                        //lang for device
                        for (int i = 5; i < checkedItems.length; i++) {
                            if (deviceSpecsUtil.shouldIncludeLang(apkNames[i]))
                                ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = isChecked);
                        }
                        break;
                    default:
                        ListView listView = ((AlertDialog) dialog).getListView();
                        if (!isChecked)
                            listView.setItemChecked(0, checkedItems[0] = false); // Uncheck "Select All" if any individual item is unchecked
                        for (int i = 1; i <= 4; i++) {
                            if (checkedItems[i] && !deviceSpecsUtil.shouldIncludeSplit(apkNames[which])) {
                                listView.setItemChecked(i, checkedItems[i] = false); // uncheck device arch if non device arch selected
                            }
                        }
                        break;
                }
            }).setPositiveButton("OK", (dialog, which) -> {
                for (int i = 1; i < checkedItems.length; i++) {
                    if (checkedItems[i]) splitsToNotInclude.remove(apkNames[i]);
                }

                if (splitsToNotInclude.size() == initialSize) {
                    urisAreSplitApks = true; // reset
                    showError(rss.getString(R.string.nothing));
                } else {
                    splitsUnselectedInDialog = splitsToNotInclude;
                    process();
                }
            }).setNegativeButton("Cancel", null).create();
            styleAlertDialog(ad);

            // Select base.apk by default
            // Not force include base.apk because there may be some use case where you actually need to merge other splits only
            for (int i = 5; i < apkNames.length; i++) {
                if (DeviceSpecsUtil.isBaseApk(apkNames[i])) {
                    ad.getListView().setItemChecked(i, checkedItems[i] = true);
                    break;
                }
            }
        } catch (IOException e) {
            showError(e);
        }
    }

    private List<String> splitsUnselectedInDialog;

    private void setUIAfterMerging(Merger merger) {
        urisAreSplitApks = true; // reset
        findViewById(R.id.cancelButton).setVisibility(View.GONE);
        View installButton = findViewById(R.id.installButton);
        View saveButton = findViewById(R.id.saveButton);
        if(criticalErrorOccurred) {
            installButton.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
        }
        else {
            if(signApk && merger.signedApk != null) {
                installButton.setVisibility(View.VISIBLE);
                installButton.setOnClickListener(v -> InstallUtil.installApk(MainActivity.this, merger.signedApk));
            } else installButton.setVisibility(View.GONE);

            // Now that the prompt to save file is after merging, put a new button to prompt again in case you closed it by accident.
            if(saveMode == 0) {
                saveButton.setVisibility(View.VISIBLE);
                saveButton.setOnClickListener(view -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/vnd.android.package-archive")
                        .putExtra(Intent.EXTRA_TITLE, "merged.apk"), 2));
            }
        }
    }

    private void copyText(CharSequence text) {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("log", text));
        Toast.makeText(this, rss.getString(R.string.copied_log), Toast.LENGTH_SHORT).show();
    }

    public void showError(Throwable e) {
        toggleAnimation(false);
        if (!(e instanceof ClosedByInterruptException)) {
            final String mainErr = e.getMessage();


            final boolean errEmpty = TextUtils.isEmpty(mainErr);
            criticalErrorOccurred = errEmpty || !mainErr.equals(rss.getString(R.string.sign_failed));

            StringBuilder stackTrace = new StringBuilder();
            if(!errEmpty) {
                stackTrace.append(mainErr);
                boolean zipError = mainErr.contains("Failed to find end record");
                if(!zipError && splitAPKUri != null) {
                    String mimeType = getContentResolver().getType(splitAPKUri);
                    zipError = (TextUtils.isEmpty(mimeType) || (!mimeType.equals("application/zip") && !mimeType.equals("application/octet-stream") && !mimeType.equals("application/vnd.apkm")));
                }
                if(zipError) {
                    stackTrace.append(" - ")
                            .append(rss.getString(R.string.check_file_valid))
                            .append(rss.getString(R.string.select_from_installed_apps))
                            .append('\n');
                }
            }
            if(!TextUtils.isEmpty(openedFile)) {
                stackTrace.append(openedFile).append('\n');
                openedFile = null;
            }
            for (StackTraceElement line : e.getStackTrace()) stackTrace.append(line).append('\n');
            StringBuilder fullLog = new StringBuilder(stackTrace).append('\n')
                    .append("SDK ").append(Build.VERSION.SDK_INT).append('\n')
                    .append(rss.getString(R.string.app_name)).append(' ');
            String currentVer;
            try {
                currentVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception ex) {
                currentVer = "2.2.9";
            }
            fullLog.append(currentVer).append('\n')
                    .append("Storage permission granted: ")
                    .append(!com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(this))
                    .append('\n').append(((TextView) findViewById(R.id.logField)).getText());

            getHandler().post(() -> {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_button_layout, null);

                ((TextView) dialogView.findViewById(R.id.errorD)).setText(stackTrace);
                styleAlertDialog(new MaterialAlertDialogBuilder(this)
                        .setTitle(mainErr)
                        .setView(dialogView)
                        .setPositiveButton(rss.getString(R.string.copy_log), (dialog, which) -> {
                            copyText(fullLog);
                            dialog.dismiss();
                        })
                        .setNegativeButton(rss.getString(R.string.create_issue), (dialog, which) -> {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdurazaaqMohammed/AntiSplit-M/issues/new?title=Crash%20Report&body=" + fullLog)));
                            dialog.dismiss();
                        })
                        .setNeutralButton(rss.getString(R.string.cancel), (dialog, which) -> dialog.dismiss())
                        .create());
                ScrollView scrollView = dialogView.findViewById(R.id.errorView);

                ViewGroup.LayoutParams params = scrollView.getLayoutParams();
                params.height = (int) (rss.getDisplayMetrics().heightPixels * 0.5);
                scrollView.setLayoutParams(params);
            });
        }
        cleanupAppFolder();
    }

    private void showError(String err) {
        toggleAnimation(false);
        runOnUiThread(() -> {
            TextView errorBox = findViewById(R.id.errorField);
            errorBox.setVisibility(View.VISIBLE);
            errorBox.setText(err);
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
        });
    }

    public String getOriginalFileName(Uri uri) {
        String result = null;

        String scheme = uri.getScheme();
        if (!TextUtils.isEmpty(scheme) && "content".equals(scheme)) {
            ContentResolver contentResolver = getContentResolver();
            if (contentResolver != null) try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                }
            }
        }

        if (TextUtils.isEmpty(result)) {
            result = uri.getPath();
            if (!TextUtils.isEmpty(result)) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }

        logger.logMessage(openedFile = result);

        if (TextUtils.isEmpty(result)) {
            result = "file_" + System.currentTimeMillis();
        }
        try {
            return result.replaceFirst("\\.(?:zip|xapk|aspk|apk[sm])$", suffix + ".apk");
        } catch (Exception e) {
            return result + suffix + ".apk";
        }
    }

    private void selectDirToSaveAPKOrSaveNow() {
        selectDirToSaveAPKOrSaveNow(saveMode);
    }

    private void selectDirToSaveAPKOrSaveNow(int saveMode) {
        boolean askWhereToSave = saveMode == 0;
        if (askWhereToSave || com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(this)) {
            getHandler().post(() -> Toast.makeText(this, R.string.merged, Toast.LENGTH_LONG).show());
            String filename;
            if(TextUtils.isEmpty(pkgName)) filename = (urisAreSplitApks ? getOriginalFileName(splitAPKUri) : getNameFromNonSplitApks());
            else {
                String versionName;
                try {
                    versionName = " v" + getPackageManager().getPackageInfo(pkgName, 0).versionName;
                } catch (Exception ignored) {
                    versionName = "";
                }
                filename = pkgName + versionName + suffix;
            }

            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/vnd.android.package-archive")
                    .putExtra(Intent.EXTRA_TITLE, filename), 2);
        } else {
            checkStoragePerm(9);
            RunUtil.runInBackground(() -> {
                try { File f;
                String fp;
                if(urisAreSplitApks) {
                    try {
                        fp = FileUtils.getPath(splitAPKUri, MainActivity.this);
                        if(TextUtils.isEmpty(fp)
                                || fp.startsWith("/data/")
                                || fp.contains("/Android/data")) {
                            showError(rss.getString(R.string.no_filepath) + fp);
                            f = new File(outputFolder, getOriginalFileName(splitAPKUri));
                        } else f = new File(fp.replaceFirst("\\.(?:zip|xapk|aspk|apk[sm])", suffix + ".apk"));
                    } catch (Exception e) {
                        f = new File(outputFolder, getOriginalFileName(splitAPKUri));
                    }
                } else {
                    String path;

                    try {
                        path = FileUtils.getPath(uris.get(0), MainActivity.this);
                        if (TextUtils.isEmpty(path))
                            fp = outputFolder + File.separator + getNameFromNonSplitApks();
                        else
                            fp = path.substring(0, path.lastIndexOf(File.separator)) + File.separator + getNameFromNonSplitApks();
                        if (TextUtils.isEmpty(fp)
                                || fp.startsWith("/data/")
                                || fp.contains("/Android/data")) {
                            showError(rss.getString(R.string.no_filepath) + fp);
                            f = new File(outputFolder, getNameFromNonSplitApks());
                        } else f = new File(fp);
                    } catch (Exception e) {
                        f = new File(outputFolder, getOriginalFileName(uris.get(0)));
                    }
                }

                f = com.abdurazaaqmohammed.utils.FileUtils.getUnusedFile(f);
                logger.logMessage(rss.getString(R.string.output) + f);
                com.abdurazaaqmohammed.utils.FileUtils.copyFile(merged, f);
                if(!criticalErrorOccurred) {
                    final String success = rss.getString(R.string.success_saved);
                    logger.logMessage(success);
                    runOnUiThread(() -> Toast.makeText(this, success, Toast.LENGTH_SHORT).show());
                }
                } catch (IOException e) {
                    showError(e);
                }
            });
        }
    }

    private String getNameFromNonSplitApks() {
        String realName = null;
        String base = "base.apk";
        for(Uri uri : uris) {
            final String fileName = getOriginalFileName(uri);
            if (base.equals(fileName)) {
                try {
                    realName = Objects.requireNonNull(getPackageManager().getPackageArchiveInfo(Objects.requireNonNull(FileUtils.getPath(uri, this)), 0)).packageName;
                    break;
                } catch (NullPointerException | IOException ignored) {}
            } else if (!fileName.startsWith("config") && !fileName.startsWith("split")) {
                // this should hopefully be base.apk renamed to the package name
                realName = fileName;
                break;
            }
        }
        return (TextUtils.isEmpty(realName) ? "unknown" + suffix + ".apk" : realName.replaceFirst("\\.apk$", suffix + ".apk"));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void installedAppsClickListener(View v3) {
        ((TextView) findViewById(R.id.logField)).setText("");
        ((TextView) findViewById(R.id.errorField)).setText("");
        AlertDialog ad = new MaterialAlertDialogBuilder(MainActivity.this).setNegativeButton(rss.getString(R.string.cancel), null).create();
        PackageManager pm = getPackageManager();

        List<PackageInfo> packageInfoList = pm.getInstalledPackages(0);
        List<AppInfo> appInfoList = new ArrayList<>();

        for (PackageInfo packageInfo : packageInfoList) {
            try {
                String packageName = packageInfo.packageName;
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                if (ai.splitSourceDirs != null) {
                    appInfoList.add(new AppInfo(
                            (String) pm.getApplicationLabel(ai),
                            pm.getApplicationIcon(ai),
                            packageName,
                            packageInfo.lastUpdateTime,
                            packageInfo.firstInstallTime));
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        if (sortMode == 0) Collections.sort(appInfoList, CompareUtils::compareAppInfoByName);
        else Collections.sort(appInfoList, (p1, p2) -> {
            long field1 = (sortMode == 1) ? p1.lastUpdated : p1.firstInstall;
            long field2 = (sortMode == 1) ? p2.lastUpdated : p2.firstInstall;
            return Long.compare(field2, field1);
        });

        LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
        View dialogView = layoutInflater.inflate(R.layout.dialog_search, null);

        ListView listView = dialogView.findViewById(R.id.list_view);
        final AppListArrayAdapter adapter = new AppListArrayAdapter(MainActivity.this, appInfoList, true);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ad.dismiss();
            pkgName = adapter.filteredAppInfoList.get(position).packageName;
            process();
        });
        EditText searchBar = dialogView.findViewById(R.id.search_bar);

        View clearButton = dialogView.findViewById(R.id.clear_button);
        clearButton.setOnClickListener(v -> searchBar.setText(""));

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed here
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed here
            }
        });

        dialogView.findViewById(R.id.filter_button).setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(this, v);
            popupMenu.getMenuInflater().inflate(R.menu.sort_menu, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.sort_name) {
                    sortMode = 0;
                    Collections.sort(appInfoList, CompareUtils::compareAppInfoByName);
                } else if (itemId == R.id.date_updated) {
                    sortMode = 1;
                    Collections.sort(appInfoList, (p1, p2) -> {
                        long field1 = p1.lastUpdated;
                        long field2 = p2.lastUpdated;
                        return Long.compare(field2, field1);
                    });
                } else {
                    sortMode = 2;
                    Collections.sort(appInfoList, (p1, p2) -> {
                        long field1 = p1.firstInstall;
                        long field2 = p2.firstInstall;
                        return Long.compare(field2, field1);
                    });
                }
                listView.setAdapter(new AppListArrayAdapter(MainActivity.this, appInfoList, true));
                return true;
            });

            popupMenu.show();
        });

        ad.setView(dialogView);
        styleAlertDialog(ad);
    }

    /** @noinspection AssignmentUsedAsCondition*/
    private void settingsButtonListener(View v) {
        ScrollView settingsDialog = (ScrollView) LayoutInflater.from(MainActivity.this).inflate(R.layout.setty, null);
        settingsDialog.setId(R.id.icon_view);
        LanguageUtil.updateSettingsDialog(settingsDialog, rss);

        TextInputEditText suffixInput = settingsDialog.findViewById(R.id.suffixInput);
        suffixInput.setText(suffix);
        suffixInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                suffix = s.toString();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        MaterialButtonToggleGroup themeButtons = settingsDialog.findViewById(R.id.themeToggleGroup);
        themeButtons.check(
                systemTheme ? R.id.systemThemeButton :
                        theme == R.style.Theme_MyApp_Light ? R.id.lightThemeButton :
                                theme == R.style.Theme_MyApp_Dark ? R.id.darkThemeButton :
                                        R.id.blackThemeButton
        );

        //if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O) settingsDialog.findViewById(R.id.blackThemeButton).setVisibility(View.GONE);

        themeButtons.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                systemTheme = false;
                if (checkedId == R.id.lightThemeButton) {
                    themeButtons.check(R.id.lightThemeButton);
                    theme = R.style.Theme_MyApp_Light;
                } else if (checkedId == R.id.darkThemeButton) {
                    themeButtons.findViewById(R.id.darkThemeButton);
                    theme = R.style.Theme_MyApp_Dark;
                } else if (checkedId == R.id.blackThemeButton) {
                    themeButtons.check(R.id.blackThemeButton);
                    theme = R.style.Theme_MyApp_Black;
                } else {
                    systemTheme = true;
                    themeButtons.check(R.id.systemThemeButton);
                    theme = ((rss.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) ?
                            R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light;
                }

                getSharedPreferences("set", Context.MODE_PRIVATE).edit().putInt("theme", theme).apply();
                setTheme(theme);

                // Clear these so that if something was shared with the app it wont try to merge it again upon opening
                getIntent().replaceExtras(new Bundle());
                getIntent().setAction("");
                getIntent().setData(null);
                recreate();
                /*startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();*/
            }
        });

        Button checkUpdateNow = settingsDialog.findViewById(R.id.checkUpdateNow);
        CompoundButton updateSwitch = settingsDialog.findViewById(R.id.updateToggle);
        updateSwitch.setChecked(checkForUpdates);
        updateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> checkUpdateNow.setVisibility((checkForUpdates = isChecked) ? View.GONE : View.VISIBLE));
        checkUpdateNow.setVisibility(checkForUpdates ? View.GONE : View.VISIBLE);
        checkUpdateNow.setOnClickListener(v1 -> MainActivity.this.checkForUpdates(true));

        CompoundButton logSwitch = settingsDialog.findViewById(R.id.logToggle);
        logSwitch.setChecked(logEnabled);
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> logEnabled = isChecked);

        CompoundButton signToggle = settingsDialog.findViewById(R.id.signToggle);
        signToggle.setChecked(signApk);
        signToggle.setOnCheckedChangeListener((buttonView, isChecked) -> signApk = isChecked);

        CompoundButton forceToggle = settingsDialog.findViewById(R.id.forceToggle);
        forceToggle.setChecked(force);
        forceToggle.setOnCheckedChangeListener((buttonView, isChecked) -> force = isChecked);

        CompoundButton selectSplitsAutomaticallySwitch = settingsDialog.findViewById(R.id.selectSplitsForDeviceToggle);
        CompoundButton showDialogSwitch = settingsDialog.findViewById(R.id.showDialogToggle);

        showDialogSwitch.setChecked(showDialog);
        showDialogSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (showDialog = isChecked)
                selectSplitsAutomaticallySwitch.setChecked(selectSplitsForDevice = false);
        });

        selectSplitsAutomaticallySwitch.setChecked(selectSplitsForDevice);
        selectSplitsAutomaticallySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (selectSplitsForDevice = isChecked)
                showDialogSwitch.setChecked(showDialog = false);
        });

        settingsDialog.findViewById(R.id.langPicker).setOnClickListener(v2 -> {
            String[] langs = rss.getStringArray(R.array.langs);
            String[] display = rss.getStringArray(R.array.langs_display);

            AlertDialog ad = new MaterialAlertDialogBuilder(MainActivity.this).setSingleChoiceItems(display, -1, (dialog, which) -> {
                LanguageUtil.updateLang(rss = LocaleHelper.setLocale(MainActivity.this, lang = langs[which]).getResources(), settingsDialog, MainActivity.this);
                dialog.dismiss();
            }).create();
            MainActivity.this.styleAlertDialog(ad);
            ad.getListView().setAdapter(new ArrayAdapter<>(MainActivity.this, android.R.layout.select_dialog_singlechoice, display));
            for (int i = 0; i < langs.length; i++)
                if (lang.equals(langs[i])) {
                    ad.getListView().setItemChecked(i, true);
                    break;
                }
        });
        MaterialTextView title = new MaterialTextView(MainActivity.this);
        title.setText(rss.getString(R.string.settings));
        int size = 20;
        title.setPadding(size, size, size, size);
        title.setTextSize(size);
        title.setGravity(Gravity.CENTER);
        AlertDialog ad = new MaterialAlertDialogBuilder(MainActivity.this).setCustomTitle(title).setView(settingsDialog)
                .setPositiveButton(rss.getString(R.string.close), (dialog, which) -> dialog.dismiss()).create();
        MainActivity.this.styleAlertDialog(ad);

        TextInputLayout dropdownLayout = settingsDialog.findViewById(R.id.dropdown_menu);
        AutoCompleteTextView autoCompleteTextView = settingsDialog.findViewById(R.id.auto_complete_tv);

        String[] items = {rss.getString(R.string.ask), rss.getString(R.string.same), rss.getString(R.string.pick_folder)};
        dropdownLayout.setHint(rss.getString(R.string.file_save_method));

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.dropdownitem, items) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if(convertView == null) convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.dropdownitem, parent, false);
                TextView view = (TextView) convertView;
                view.setTextColor(theme == R.style.Theme_MyApp_Light ? Color.BLACK : Color.WHITE);
                view.setText(items[position]);
                return convertView;
            }
        };
        autoCompleteTextView.setText(items[saveMode]);
        autoCompleteTextView.setAdapter(adapter);
        autoCompleteTextView.setThreshold(1);
        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            saveMode = position;
            if((position != 0) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) checkStoragePerm(9);
            if(position == 2) {
                TextInputEditText editText = new TextInputEditText(this);
                editText.setHint(rss.getString(R.string.pick_folder));
                editText.setText(outputFolder);
                editText.setPadding(16,16,16,16);
                styleAlertDialog(new MaterialAlertDialogBuilder(this).setTitle(rss.getString(R.string.pick_folder)).setView(editText).setNegativeButton(rss.getString(R.string.cancel), null)
                .setPositiveButton("OK", (dialog, which) -> {
                    CharSequence text = editText.getText();
                    if(TextUtils.isEmpty(text)) showError(rss.getString(R.string.no_filepath) + text);
                    else {
                        String string = text.toString();
                        if ((new File(string).exists() || new File(string).mkdirs())) {
                            outputFolder = string;
                        } else {
                            showError(rss.getString(R.string.no_filepath) + text);
                            saveMode = 0;
                        }
                    }
                }).create(), false);
            }
        });
    }
}
