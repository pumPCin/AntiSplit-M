package com.abdurazaaqmohammed.AntiSplit.main;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
import static com.abdurazaaqmohammed.AntiSplit.main.DeviceSpecsUtil.getDeviceDpi;
import static com.reandroid.apkeditor.merge.LogUtil.logEnabled;

import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;
import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.LightingColorFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.reandroid.apkeditor.merge.LogUtil;
import com.reandroid.apkeditor.merge.Merger;
import com.starry.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import yuku.ambilwarna.AmbilWarnaDialog;

/** @noinspection deprecation*/
public class MainActivity extends Activity implements Merger.LogListener {
    private static boolean ask;
    private static boolean showDialog;
    private static boolean signApk;
    private static boolean selectSplitsForDevice;
    private Uri splitAPKUri;
    private ArrayList<Uri> uris;
    private boolean urisAreSplitApks = true;
    public static int textColor;
    public static int bgColor;
    public static boolean errorOccurred;

    private void setColor(int color, boolean isTextColor) {
        if(isTextColor) {
            textColor = color;
            ((TextView) findViewById(R.id.oldAndroidInfo)).setTextColor(color);
            ((TextView) findViewById(R.id.errorField)).setTextColor(color);
            ((TextView) findViewById(R.id.logField)).setTextColor(color);
            ((TextView) findViewById(R.id.logToggle)).setTextColor(color);
            ((TextView) findViewById(R.id.ask)).setTextColor(color);
            ((TextView) findViewById(R.id.showDialogToggle)).setTextColor(color);
            ((TextView) findViewById(R.id.signToggle)).setTextColor(color);
            ((TextView) findViewById(R.id.selectSplitsForDeviceToggle)).setTextColor(color);
        } else {
            bgColor = color;
            findViewById(R.id.main).setBackgroundColor(color);
        }
    }

    public Handler getHandler() {
        return handler;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            try {
                Objects.requireNonNull(getActionBar()).hide();
                deleteDir(Objects.requireNonNull(getExternalCacheDir()));
            } catch (NullPointerException ignored) {
            }
        }
        deleteDir(getCacheDir());

        setContentView(R.layout.activity_main);

        // Fetch settings from SharedPreferences
        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);
        setColor(settings.getInt("textColor", 0xffffffff), true);
        setColor(settings.getInt("backgroundColor", 0xff000000), false);

        logEnabled = settings.getBoolean("logEnabled", true);
        LogUtil.setLogListener(this);


        Switch logSwitch = findViewById(R.id.logToggle);
        logSwitch.setChecked(logEnabled);
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> logEnabled = isChecked);

        signApk = settings.getBoolean("signApk", true);
        Switch signToggle = findViewById(R.id.signToggle);
        signToggle.setChecked(signApk);
        signToggle.setOnCheckedChangeListener((buttonView, isChecked) -> signApk = isChecked);

        Switch selectSplitsAutomaticallySwitch = findViewById(R.id.selectSplitsForDeviceToggle);
        Switch showDialogSwitch = findViewById(R.id.showDialogToggle);

        showDialog = settings.getBoolean("showDialog", false);
        showDialogSwitch.setChecked(showDialog);
        showDialogSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showDialog = isChecked;
            if(isChecked) {
                selectSplitsForDevice = false;
                selectSplitsAutomaticallySwitch.setChecked(false);
            }
        });

        selectSplitsForDevice = settings.getBoolean("selectSplitsForDevice", false);
        selectSplitsAutomaticallySwitch.setChecked(selectSplitsForDevice);
        selectSplitsAutomaticallySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selectSplitsForDevice = isChecked;
            if(isChecked) {
                showDialog = false;
                showDialogSwitch.setChecked(false);
            }
        });

        Switch askSwitch = findViewById(R.id.ask);
        if(LegacyUtils.doesNotSupportInbuiltAndroidFilePicker) {
            ask = false;
            askSwitch.setVisibility(View.GONE);
        }
        else {
            ask = settings.getBoolean("ask", true);
            askSwitch.setChecked(ask);
            askSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                ask = isChecked;
                if(!isChecked) checkStoragePerm();
            });
        }

        findViewById(R.id.decodeButton).setOnClickListener(v -> {
            if(LegacyUtils.doesNotSupportInbuiltAndroidFilePicker) {
                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.MULTI_MODE;
                properties.selection_type = DialogConfigs.FILE_SELECT;
                properties.root = Environment.getExternalStorageDirectory();
                properties.error_dir = Environment.getExternalStorageDirectory();
                properties.offset = new File(DialogConfigs.DEFAULT_DIR);
                properties.extensions = new String[] {"apk", "zip", "apks", "aspk", "apks", "xapk", "apkm"};
                FilePickerDialog dialog = new FilePickerDialog(MainActivity.this, properties, textColor, bgColor);
                dialog.setTitle("Select");
                dialog.setDialogSelectionListener(files -> {
                    urisAreSplitApks = !files[0].endsWith(".apk");
                    uris = new ArrayList<>();
                    for(String file : files) {
                        Uri uri = Uri.fromFile(new File(file));
                        if (urisAreSplitApks) processOneSplitApkUri(uri);
                        else uris.add(uri);
                    }
                    dialog.dismiss();
                });
                runOnUiThread(dialog::show);
            }

            else MainActivity.this.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*")
                            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/vnd.android.package-archive", "application/octet-stream"})
                    , 1);
        } // XAPK is octet-stream
        );
        findViewById(R.id.changeBgColor).setOnClickListener(v -> showColorPickerDialog(false, bgColor));
        findViewById(R.id.changeTextColor).setOnClickListener(v -> showColorPickerDialog(true, textColor));
        findViewById(R.id.revanced).setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            TextView title = new TextView(this);
            title.setText(R.string.note);
            title.setTextSize(20);
            title.setTextColor(textColor);
            builder.setCustomTitle(title);

            TextView message = new TextView(this);
            message.setText(R.string.revanced);
            message.setTextSize(15);
            message.setTextColor(textColor);
            builder.setView(message);

            builder.setPositiveButton(R.string.download_fix, (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://github.com/AbdurazaaqMohammed/revanced-antisplit"))))
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

            final AlertDialog ad = builder.create();
            ad.show();
            try {
                Objects.requireNonNull(ad.getWindow()).getDecorView().getBackground().setColorFilter(new LightingColorFilter(0xFF000000, bgColor));
            } catch (NullPointerException ignored) {}
        });

        // Check if user shared or opened file with the app.
        final Intent openIntent = getIntent();
        final String action = openIntent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            splitAPKUri = openIntent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            splitAPKUri = openIntent.getData();
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && openIntent.hasExtra(Intent.EXTRA_STREAM)) {
            uris = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? openIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class) : openIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) {
                final Uri uri = uris.get(0);
                String path = uri.getPath();
                urisAreSplitApks = TextUtils.isEmpty(path) || !path.endsWith(".apk");
                if(urisAreSplitApks) splitAPKUri = uri;
                else selectDirToSaveAPKOrSaveNow();
            }
        }
        if (splitAPKUri != null) {
            if(showDialog) showApkSelectionDialog();
            else selectDirToSaveAPKOrSaveNow();
        }
    }

    private void showColorPickerDialog(boolean isTextColor, int currentColor) {
        new AmbilWarnaDialog(this, currentColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog1, int color) {
                setColor(color, isTextColor);
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog1) {
                // cancel was selected by the user
            }
        }).show();
    }

    final File getAntisplitMFolder() {
        final File antisplitMFolder = new File(Environment.getExternalStorageDirectory(), "AntiSplit-M");
        return antisplitMFolder.exists() || antisplitMFolder.mkdir() ? antisplitMFolder : new File(Environment.getExternalStorageDirectory(), "Download");
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkStoragePerm() {
        if(doesNotHaveStoragePerm(this)) {
            Toast.makeText(this, R.string.grant_storage, Toast.LENGTH_LONG).show();
            if(LegacyUtils.supportsWriteExternalStorage) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            else startActivityForResult(new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())), 0);
        }
    }

    public static boolean doesNotHaveStoragePerm(Context context) {
        if (Build.VERSION.SDK_INT < 23) return false;
        return LegacyUtils.supportsWriteExternalStorage ?
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED :
            !Environment.isExternalStorageManager();
    }

    @Override
    protected void onPause() {
        SharedPreferences.Editor e = getSharedPreferences("set", Context.MODE_PRIVATE).edit()
                .putBoolean("logEnabled", logEnabled)
                .putBoolean("ask", ask)
                .putBoolean("showDialog", showDialog)
                .putBoolean("signApk", signApk)
                .putBoolean("selectSplitsForDevice", selectSplitsForDevice)
                .putInt("textColor", textColor)
                .putInt("backgroundColor", bgColor);
        if (LegacyUtils.supportsArraysCopyOf) e.apply();
        else e.commit();
        super.onPause();
    }

    @Override
    public void onLog(String log) {
        runOnUiThread(() -> {
            ((TextView)findViewById(R.id.logField)).append(log + "\n");
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    public void onLog(int resID) {
        onLog(getString(resID));
    }

    private Handler handler;

    /** @noinspection ResultOfMethodCallIgnored*/
    public static void deleteDir(File dir){
        // There should never be folders in here.
        for (String child : dir.list()) new File(dir, child).delete();
    }

    @Override
    protected void onDestroy() {
        File dir = getCacheDir();
        deleteDir(dir);
        if (LegacyUtils.supportsExternalCacheDir && (dir = getExternalCacheDir()) != null) deleteDir(dir);
        super.onDestroy();
    }

    public boolean shouldIncludeSplit(String name) {
        if (name.equals("base.apk")
                || !name.startsWith("config") && !name.startsWith("split") // this is base.apk hopefully
                || name.contains(Locale.getDefault().getLanguage()) // this should probably not be cached
        ) return true;
        String arch = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;
        if(name.contains(arch) || name.contains(arch.replace('-', '_'))) return true;
        String densityType = getDeviceDpi(this);
        return (name.endsWith(densityType) && !name.replace(densityType, "").endsWith("x")); // ensure that it does not select xxhdpi for xhdpi etc
    }

    private List<String> splitsToUse = null;

    private static class ProcessTask extends AsyncTask<Uri, Void, Void> {
        private final WeakReference<MainActivity> activityReference;

        // only retain a weak reference to the activity
        ProcessTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Uri... uris) {
            MainActivity activity = activityReference.get();
            final File cacheDir = LegacyUtils.supportsExternalCacheDir ? activity.getExternalCacheDir() : activity.getCacheDir();
            if (cacheDir != null && activity.urisAreSplitApks) {
                deleteDir(cacheDir);
            }

            List<String> splits = activity.splitsToUse;
            if(activity.urisAreSplitApks) {
                if(selectSplitsForDevice) {
                    try {
                        splits = DeviceSpecsUtil.getListOfSplits(activity.splitAPKUri, activity);
                        boolean splitApkContainsArch = false;
                        for (int i = 0; i < splits.size(); i++) {
                            final String thisSplit = splits.get(i);
                            if(!splitApkContainsArch && (thisSplit.contains("armeabi") || thisSplit.contains("arm64") || thisSplit.contains("x86") || thisSplit.contains("mips"))) {
                                splitApkContainsArch = true;
                            }
                            if (activity.shouldIncludeSplit(thisSplit)) splits.remove(thisSplit);
                        }
                        if(splitApkContainsArch) {
                            boolean selectedSplitsContainsArch = false;
                            for (int i = 0; i < splits.size(); i++) {
                                final String thisSplit = splits.get(i);
                                if((thisSplit.contains("armeabi") || thisSplit.contains("arm64") || thisSplit.contains("x86") || thisSplit.contains("mips"))) {
                                    selectedSplitsContainsArch = true;
                                    break;
                                }
                            }
                            if(!selectedSplitsContainsArch) {
                                for (int i = 0; i < splits.size(); i++) {
                                    final String thisSplit = splits.get(i);
                                    if((thisSplit.contains("armeabi") || thisSplit.contains("arm64") || thisSplit.contains("x86") || thisSplit.contains("mips"))) {
                                        splits.remove(thisSplit); // select all to be sure
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        activity.showError(e);
                    }
                }
            } else {
                // These are the splits from inside the APK, just copy the splits to cache folder then merger will load it
                for(Uri uri : activity.uris) {
                    try(InputStream is = FileUtils.getInputStream(uri, activity);
                        OutputStream fos = FileUtils.getOutputStream(cacheDir + File.separator + getOriginalFileName(activity, uri))) {
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    } catch (IOException e) {
                        activity.showError(e);
                    }
                }
            }
            activity.runOnUiThread(() -> ((TextView) activity.findViewById(R.id.logField)).setText(""));

            Uri xapkUri;
            try {
                xapkUri = !activity.urisAreSplitApks || !Objects.requireNonNull(activity.splitAPKUri.getPath()).endsWith("xapk") ? null : activity.splitAPKUri;
            } catch (NullPointerException ignored) {
                xapkUri = null;
            }

            try (OutputStream os = FileUtils.getOutputStream(uris[0], activity)) {
                Merger.run(
                        activity.urisAreSplitApks ? FileUtils.getInputStream(activity.splitAPKUri, activity) : null,
                        cacheDir,
                        os,
                        xapkUri,
                        activity,
                        splits,
                        signApk);
            } catch (Exception e) {
                activity.showError(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            MainActivity activity = activityReference.get();
            if(activity.urisAreSplitApks) activity.getHandler().post(() -> {
                try {
                    activity.uris.remove(0);
                    activity.splitAPKUri = activity.uris.get(0);
                    if(showDialog) activity.showApkSelectionDialog();
                    else activity.selectDirToSaveAPKOrSaveNow();
                } catch (IndexOutOfBoundsException | NullPointerException ignored) {
                    // End of list, I don't know why but isEmpty is not working
                    activity.showSuccess();
                }
            });
            else activity.showSuccess();
        }
    }

    private void processOneSplitApkUri(Uri uri) {
        splitAPKUri = uri;
        if (showDialog) showApkSelectionDialog();
        else selectDirToSaveAPKOrSaveNow();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) switch (requestCode) {
            case 0:
                checkStoragePerm();
                break;
            case 1:
                // opened through button in the app

                ClipData clipData = data.getClipData();
                if (clipData == null) processOneSplitApkUri(data.getData());
                else {
                    //multiple files selected
                    Uri first = clipData.getItemAt(0).getUri();
                    try {
                        if (Objects.requireNonNull(first.getPath()).endsWith(".apk")) {
                            urisAreSplitApks = false;
                            uris = new ArrayList<>();
                            uris.add(first);
                        } else processOneSplitApkUri(first);
                    } catch (NullPointerException ignored) {
                    }
                    for (int i = 1; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (urisAreSplitApks) processOneSplitApkUri(uri);
                        else uris.add(uri);
                    }
                    if (!urisAreSplitApks) selectDirToSaveAPKOrSaveNow();
                }
                break;
            case 2:
                // going to process and save a file now
                new ProcessTask(this).execute(data.getData());
                break;
        }
    }

    public void showApkSelectionDialog() {
        try {
            List<String> splits = DeviceSpecsUtil.getListOfSplits(splitAPKUri, this);
            final int initialSize = splits.size();
            String[] apkNames = new String[initialSize + 2];
            boolean[] checkedItems = new boolean[initialSize + 2];

            apkNames[0] = getString(R.string.all);
            apkNames[1] = getString(R.string.for_device);
            for (int i = 2; i < initialSize + 2; i++) {
                apkNames[i] = splits.get(i - 2);
                checkedItems[i] = false;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            TextView title = new TextView(this);
            title.setTextColor(textColor);
            title.setTextSize(20);
            title.setText(R.string.select_splits);
            builder.setCustomTitle(title);

            builder.setMultiChoiceItems(apkNames, checkedItems, (dialog, which, isChecked) -> {
                switch (which) {
                    case 0:
                        // "Select All" option
                        for (int i = 2; i < checkedItems.length; i++) ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = isChecked);
                        break;
                    case 1:
                        // device specs option
                        for (int i = 2; i < checkedItems.length; i++) {
                            ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = (isChecked && shouldIncludeSplit(apkNames[i])));
                        }
                        boolean didNotFindAppropriateDpi = true;
                        for (int i = 2; i < checkedItems.length; i++) {
                            if (checkedItems[i] && apkNames[i].contains("dpi")) {
                                didNotFindAppropriateDpi = false;
                                break;
                            }
                        }
                        if (didNotFindAppropriateDpi) {
                            for (int i = 2; i < checkedItems.length; i++) {
                                if (apkNames[i].contains("hdpi")) {
                                    ((AlertDialog) dialog).getListView().setItemChecked(i, checkedItems[i] = isChecked);
                                    break;
                                }
                            }
                        }
                        break;
                    default:
                        ListView listView = ((AlertDialog) dialog).getListView();
                        if (!isChecked) listView.setItemChecked(0, checkedItems[0] = false); // Uncheck "Select All" if any individual item is unchecked
                        if (checkedItems[1] && !shouldIncludeSplit(apkNames[which])) listView.setItemChecked(1, checkedItems[1] = false); // uncheck device arch if non device arch selected
                        break;
                }
            });
            builder.setPositiveButton("OK", (dialog, which) -> {
                for (int i = 1; i < checkedItems.length; i++) {
                    if (checkedItems[i]) splits.remove(apkNames[i]); // ?????
                }

                if (splits.size() == initialSize) {
                    urisAreSplitApks = true; // reset
                    showError(getString(R.string.nothing));
                } else {
                    splitsToUse = splits;
                    selectDirToSaveAPKOrSaveNow();
                }
            });
            builder.setNegativeButton("Cancel", null);
            runOnUiThread(() -> {
                final AlertDialog ad = builder.create();
                ad.show();
                ad.getListView().setAdapter(new CustomArrayAdapter(this, apkNames, textColor));
                try {
                    Objects.requireNonNull(ad.getWindow()).getDecorView().getBackground().setColorFilter(new LightingColorFilter(0xFF000000, bgColor));
                } catch (NullPointerException ignored) {}
            });
        } catch (IOException e) {
            showError(e);
        }
    }

    private void showSuccess() {
        if(!errorOccurred) {
            LogUtil.logMessage(R.string.success_saved);
            runOnUiThread(() -> Toast.makeText(this, R.string.success_saved, Toast.LENGTH_SHORT).show());
        }
    }

    private void showError(Exception e) {
        errorOccurred = true;
        final String mainErr = e.toString();
        StringBuilder stackTrace = new StringBuilder().append(mainErr).append('\n');
        for(StackTraceElement line : e.getStackTrace()) stackTrace.append(line).append('\n');
        runOnUiThread(() -> {
            TextView errorBox = findViewById(R.id.errorField);
            errorBox.setVisibility(View.VISIBLE);
            errorBox.setText(stackTrace);
            Toast.makeText(this, mainErr, Toast.LENGTH_SHORT).show();
        });
    }

    private void showError(String err) {
        runOnUiThread(() -> {
            TextView errorBox = findViewById(R.id.errorField);
            errorBox.setVisibility(View.VISIBLE);
            errorBox.setText(err);
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
        });
    }

    public static String getOriginalFileName(Context context, Uri uri) {
        String result = null;
        try {
            if (Objects.equals(uri.getScheme(), "content")) {
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                    }
                }
            }
            if (result == null) {
                result = uri.getPath();
                int cut = Objects.requireNonNull(result).lastIndexOf('/'); // Ensure it throw the NullPointerException here to be caught
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
            return result.replaceFirst("\\.(?:xapk|aspk|apk[sm])", "_antisplit.apk");
        } catch (Exception ignored) {
            return "filename_not_found";
        }
    }

    @SuppressLint("InlinedApi")
    private void selectDirToSaveAPKOrSaveNow() {
        if (ask) startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_TITLE, urisAreSplitApks ? getOriginalFileName(this, splitAPKUri) : getNameFromNonSplitApks()), 2);
        else {
            checkStoragePerm();
            try {
                String originalFilePath;
                if(urisAreSplitApks) originalFilePath = FileUtils.getPath(splitAPKUri, this);
                else {
                    String path = FileUtils.getPath(uris.get(0), this);
                    originalFilePath = (TextUtils.isEmpty(path) ? getAntisplitMFolder() : path.substring(0, path.lastIndexOf(File.separator))) + File.separator + getNameFromNonSplitApks();
                }
                File f;
                String newFilePath = TextUtils.isEmpty(originalFilePath) ?
                        getAntisplitMFolder() + File.separator + getOriginalFileName(this, splitAPKUri) // If originalFilePath is null urisAreSplitApks must be true because getNameFromNonSplitApks will always return something
                        : originalFilePath.replaceFirst("\\.(?:xapk|aspk|apk[sm])", "_antisplit.apk");
                if(TextUtils.isEmpty(newFilePath) || newFilePath.startsWith("/data/") || !(f = new File(newFilePath)).canWrite()) { // when shared it in /data/ bruh
                    f = new File(getAntisplitMFolder(), newFilePath.substring(newFilePath.lastIndexOf(File.separator) + 1));
                    showError(getString(R.string.no_filepath));
                }
                LogUtil.logMessage(getString(R.string.output) + f);

                new ProcessTask(this).execute(Uri.fromFile(f));
            } catch (IOException e) {
                showError(e);
            }
        }
    }

    private String getNameFromNonSplitApks() {
        String fileName = null;
        for(Uri uri : uris) {
            final String name = getOriginalFileName(this, uri);
            if (name.equals("base.apk")) {
                try {
                    fileName = Objects.requireNonNull(getPackageManager().getPackageArchiveInfo(Objects.requireNonNull(FileUtils.getPath(uri, this)), 0)).packageName;
                    break;
                } catch (NullPointerException | IOException ignored) {}
            } else if (!name.startsWith("config") && !name.startsWith("split")) {
                // this should hopefully be base.apk renamed to the package name
                fileName = name;
                break;
            }
        }
        return (TextUtils.isEmpty(fileName) ? "unknown" : fileName.replace(".apk", "")) + "_antisplit.apk";
    }
}