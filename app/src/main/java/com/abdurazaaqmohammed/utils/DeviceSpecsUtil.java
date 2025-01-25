package com.abdurazaaqmohammed.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;
import com.j256.simplezip.ZipFileInput;
import com.j256.simplezip.format.ZipFileHeader;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;
import com.starry.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DeviceSpecsUtil {

    private final MainActivity context;
    public final String lang;
    private final String densityType;
    public static ArchiveFile zipFile = null;

    public DeviceSpecsUtil(MainActivity context) {
        this.context = context;
        this.lang = Locale.getDefault().getLanguage();
        this.densityType = getDeviceDpi();
    }

    public List<String> getSplitsForDevice(Uri uri) throws IOException {
        List<String> splits = getListOfSplits(uri);
        switch (splits.size()) {
            case 4:
            case 3:
            case 2:
                splits.clear();
                break;
            default:
                List<String> copy = List.copyOf(splits);
                List<String> toRemove = new ArrayList<>();
                boolean splitApkContainsArch = false;
                for (int i = 0; i < splits.size(); i++) {
                    final String thisSplit = splits.get(i);
                    if (!splitApkContainsArch && DeviceSpecsUtil.isArch(thisSplit)) {
                        splitApkContainsArch = true;
                    }
                    if (shouldIncludeSplit(thisSplit))
                        toRemove.add(thisSplit);
                }
                if (splitApkContainsArch) {
                    boolean selectedSplitsContainsArch = false;
                    for (int i = 0; i < copy.size(); i++) {
                        final String thisSplit = copy.get(i);
                        if (DeviceSpecsUtil.isArch(thisSplit) && toRemove.contains(thisSplit)) {
                            selectedSplitsContainsArch = true;
                            break;
                        }
                    }
                    if (!selectedSplitsContainsArch) {
                        context.getLogger().logMessage("Could not find device architecture, selecting all architectures");
                        for (int i = 0; i < splits.size(); i++) {
                            final String thisSplit = splits.get(i);
                            if (DeviceSpecsUtil.isArch(thisSplit)) toRemove.add(thisSplit); // select all to be sure
                        }
                    }
                }

                boolean didNotFindDpi = true;
                for (int i = 0; i < copy.size(); i++) {
                    String thisSplit = copy.get(i);
                    if (thisSplit.contains("dpi") && toRemove.contains(thisSplit)) {
                        didNotFindDpi = false;
                        break;
                    }
                }
                if (didNotFindDpi) {
                    for (int i = 0; i < splits.size(); i++) {
                        String thisSplit = splits.get(i);
                        if (thisSplit.contains("hdpi"))
                            toRemove.add(thisSplit);
                    }
                }
                splits.removeAll(toRemove);
        }
        return splits;
    }

    private List<String> getListOfSplitsFromFile(File file) throws IOException {
        List<String> splits = new ArrayList<>();

        // Do not close this ZipFile it could be used later in merger
        for(InputSource inputSource : (zipFile = new ArchiveFile(file)).getInputSources()) {
            String name = inputSource.getName();
            if (name.endsWith(".apk")) splits.add(name);
        }

        return splits;
    }

    public List<String> getListOfSplits(Uri splitAPKUri) throws IOException {
        List<String> splits = new ArrayList<>();
        File file = new File(FileUtils.getPath(splitAPKUri, context));
        if(file.canRead()) return getListOfSplitsFromFile(file);

        try (InputStream is = context.getContentResolver().openInputStream(splitAPKUri);
                ZipFileInput zis = new ZipFileInput(is)) {
            ZipFileHeader header;
            while ((header = zis.readFileHeader()) != null) {
                final String name = header.getFileName();
                if (name.endsWith(".apk")) splits.add(name);
            }
        } catch (Exception e) {
            try(InputStream is = context.getContentResolver().openInputStream(splitAPKUri)) {
                com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, file = new File(context.getCacheDir(), file.getName()));
                return getListOfSplitsFromFile(file);
            }
        }
        if(splits.size() > 1) return splits;
        try(InputStream is = context.getContentResolver().openInputStream(splitAPKUri)) {
            com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, file = new File(context.getCacheDir(), file.getName()));
            return getListOfSplitsFromFile(file);
        }
    }

    public static boolean isArch(String thisSplit) {
        return thisSplit.contains("armeabi") || thisSplit.contains("arm64") || thisSplit.contains("x86") || thisSplit.contains("mips");
    }

    public static boolean isBaseApk(String name) {
        return name.equals("base.apk") || !name.startsWith("config") && !name.startsWith("split"); // this is base.apk hopefully
    }

    public boolean shouldIncludeSplit(String name) {
        return isBaseApk(name) || shouldIncludeLang(name) || shouldIncludeArch(name) || shouldIncludeDpi(name);
    }

    public boolean shouldIncludeLang(String name) {
        return name.contains(lang);
    }

    public boolean shouldIncludeArch(String name) {
        return name.contains(Build.CPU_ABI) || name.replace('-', '_').contains(Build.CPU_ABI.replace('-', '_'));
    }

    public boolean shouldIncludeDpi(String name) {
        return (name.endsWith(densityType) && !name.replace(densityType, "").endsWith("x")); // ensure that it does not select xxhdpi for xhdpi etc
    }

    public String getDeviceDpi() {
        String densityType;
        if(TextUtils.isEmpty(densityType = context.getSharedPreferences("set", Context.MODE_PRIVATE).getString("deviceDpi", ""))) {
            switch (context.getResources().getDisplayMetrics().densityDpi) {
                case DisplayMetrics.DENSITY_LOW:
                    densityType = "ldpi";
                    break;
                case DisplayMetrics.DENSITY_MEDIUM:
                case DisplayMetrics.DENSITY_140:
                    densityType = "mdpi";
                    break;
                case DisplayMetrics.DENSITY_XHIGH:
                case DisplayMetrics.DENSITY_280:
                case DisplayMetrics.DENSITY_260:
                case DisplayMetrics.DENSITY_300:
                    densityType = "xhdpi";
                    break;
                case DisplayMetrics.DENSITY_340:
                case DisplayMetrics.DENSITY_360:
                case DisplayMetrics.DENSITY_390:
                case DisplayMetrics.DENSITY_400:
                case DisplayMetrics.DENSITY_420:
                case DisplayMetrics.DENSITY_440:
                case DisplayMetrics.DENSITY_450:
                case DisplayMetrics.DENSITY_XXHIGH:
                    densityType = "xxhdpi";
                    break;
                case DisplayMetrics.DENSITY_520:
                case DisplayMetrics.DENSITY_560:
                case DisplayMetrics.DENSITY_600:
                case DisplayMetrics.DENSITY_XXXHIGH:
                    densityType = "xxxhdpi";
                    break;
                case DisplayMetrics.DENSITY_TV:
                    densityType = "tvdpi";
                    break;
                case DisplayMetrics.DENSITY_HIGH:
                case DisplayMetrics.DENSITY_220:
                case DisplayMetrics.DENSITY_200:
                case DisplayMetrics.DENSITY_180:
                default:
                    densityType = "hdpi";
                    break;
            }
            densityType += ".apk";
            context.getSharedPreferences("set", Context.MODE_PRIVATE).edit().putString("deviceDpi", densityType).apply();
        }
        return densityType;
    }
}