package com.abdurazaaqmohammed.utils;

import static android.content.Context.DOWNLOAD_SERVICE;

import android.app.DownloadManager;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateUtil {
    public static void checkForUpdates(boolean toast, MainActivity context) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = getHttpURLConnection();

                try (InputStream inputStream = conn.getInputStream();
                     InputStreamReader in = new InputStreamReader(inputStream);
                     BufferedReader reader = new BufferedReader(in)) {
                    String line;
                    String latestVersion = "";
                    String changelog = "";
                    String dl = "";
                    boolean rightBranch = false;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("browser_download_url")) {
                            dl = line.split("\"")[3];
                            latestVersion = line.split("/")[7];
                            rightBranch = latestVersion.charAt(0) == '2';
                        } else if (line.contains("body") && rightBranch) {
                            changelog = line.split("\"")[3];
                            break;
                        }
                    }
                    String currentVer;
                    try {
                        currentVer = (context).getPackageManager().getPackageInfo((context).getPackageName(), 0).versionName;
                    } catch (Exception e) {
                        currentVer = null;
                    }
                    boolean newVer = false;
                    char[] curr = TextUtils.isEmpty(currentVer) ? new char[] { '2', '3', '0' }
                            : currentVer.replace(".", "").toCharArray();
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
                    Resources rss = context.getRss();

                    if (newVer) {
                        if (!toast && !TextUtils.isEmpty(context.lastVerChecked) && context.lastVerChecked.equals(latestVersion))
                            return;
                        String ending = ".apk";
                        String filename = "AntiSplit-M.v" + latestVersion + ending;
                        String link = dl.endsWith(ending) ? dl : dl + File.separator + filename;
                        MaterialTextView changelogText = new MaterialTextView(context);
                        String linebreak = "<br />";
                        changelogText.setText(Html.fromHtml(
                                rss.getString(R.string.new_ver) + " (" + latestVersion + ")" + linebreak + linebreak
                                        + "Changelog:" + linebreak + changelog.replace("\\r\\n", linebreak)));
                        int padding = 16;
                        changelogText.setPadding(padding, padding, padding, padding);
                        MaterialTextView title = new MaterialTextView(context);
                        title.setText(rss.getString(R.string.update));
                        int size = 20;
                        title.setPadding(size, size, size, size);
                        title.setTextSize(size);
                        title.setGravity(Gravity.CENTER);

                        String finalLatestVersion = latestVersion;
                        context.getHandler().post(() -> {
                            AlertDialog alertDialog = new MaterialAlertDialogBuilder(context)
                                    .setCustomTitle(title).setView(changelogText)
                                    .setPositiveButton(rss.getString(R.string.dl), (dialog, which) -> {
                                        if (context.checkUpdateAfterStoragePermission == null)
                                            context.checkUpdateAfterStoragePermission = () -> {
                                                DownloadManager.Request request = new DownloadManager.Request(
                                                        Uri.parse(link))
                                                        .setTitle(filename).setDescription(filename)
                                                        .setMimeType("application/vnd.android.package-archive")
                                                        .setDestinationInExternalPublicDir(
                                                                Environment.DIRECTORY_DOWNLOADS, filename)
                                                        .setNotificationVisibility(
                                                                DownloadManager.Request.VISIBILITY_VISIBLE
                                                                        | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                                context.downloadId = ((DownloadManager) context
                                                        .getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                                            };
                                        if (Build.VERSION.SDK_INT < 29
                                                && com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(context))
                                            context.checkStoragePerm(10);
                                        else
                                            context.checkUpdateAfterStoragePermission.run();
                                    })
                                    .setNegativeButton("Go to GitHub Release", (dialog, which) -> context
                                            .startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(
                                                    "https://github.com/AbdurazaaqMohammed/AntiSplit-M/releases/latest"))))
                                    .setNeutralButton(rss.getString(R.string.cancel), null).create();
                            alertDialog.setOnDismissListener(dialog -> context.lastVerChecked = finalLatestVersion);
                            context.runOnUiThread(alertDialog::show);
                        });
                    } else if (toast)
                        context.getHandler().post(() -> context.runOnUiThread(() -> Toast.makeText(context,
                                rss.getString(R.string.no_update_found), Toast.LENGTH_SHORT).show()));
                    // return new String[]{ver, changelog, dl};
                }
            } catch (Exception e) {
                // context.showError(e);
                if (toast)
                    context.runOnUiThread(() -> Toast
                            .makeText(context, "Failed to check for update", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @NonNull
    private static HttpURLConnection getHttpURLConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                "https://api.github.com/repos/AbdurazaaqMohammed/AntiSplit-M/releases").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return conn;
    }
}
