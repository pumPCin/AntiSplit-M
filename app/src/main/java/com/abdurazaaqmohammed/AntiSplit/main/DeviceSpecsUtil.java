package com.abdurazaaqmohammed.AntiSplit.main;

import static com.abdurazaaqmohammed.AntiSplit.main.MainActivity.getOriginalFileName;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;

import com.starry.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class DeviceSpecsUtil {


    public static String deviceArch = (
            (Build.VERSION.SDK_INT > 20) ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI)
            .replace('-', '_'); // fix the format

    public static List<String> getListOfSplits(Uri splitAPKUri, Context context) throws IOException {
        List<String> splits = new ArrayList<>();

        if (splitAPKUri.getPath().endsWith("xapk")) {
            File bruh = new File(FileUtils.getPath(splitAPKUri, context));
            final boolean couldntRead = !bruh.canRead();
            if (couldntRead) {
                // copy to cache dir if no permission
                bruh = new File(context.getExternalCacheDir() + File.separator + getOriginalFileName(context, splitAPKUri));
                try (InputStream ins = context.getContentResolver().openInputStream(splitAPKUri)) {
                    OutputStream fos = FileUtils.getOutputStream(bruh);
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = ins.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                }
            }
            try (ZipFile zipFile = new ZipFile(bruh)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    String fileName = entries.nextElement().getName();
                    if (fileName.endsWith(".apk")) splits.add(fileName);
                }
                if (couldntRead) bruh.delete();
            }
        } else {
            try (ZipInputStream zis = new ZipInputStream(context.getContentResolver().openInputStream(splitAPKUri))) {
                ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    final String name = zipEntry.getName();
                    if (name.endsWith(".apk")) splits.add(name);
                    zipEntry = zis.getNextEntry();
                }
                zis.closeEntry();
            }
        }
        return splits;
    }

    public static boolean shouldIncludeSplit(String name, Context context) {
        if (name.equals("base.apk")
                || !name.startsWith("config") && !name.startsWith("split") // this is base.apk probably
                || name.contains(Locale.getDefault().getLanguage()) // this should probably not be cached
                || name.contains(DeviceSpecsUtil.deviceArch)) return true;
        final String densityType = getDeviceDpi(context);
        return (name.endsWith(densityType) && !name.replace(densityType, "").endsWith("x")); // ensure that it dont select xxhdpi for xhdpi etc
    }

    public static String getDeviceDpi(Context context) {
        String densityType;
        if((densityType = context.getSharedPreferences("set", Context.MODE_PRIVATE).getString("deviceDpi", "")).isEmpty()) {
            switch (context.getResources().getDisplayMetrics().densityDpi) {
                case DisplayMetrics.DENSITY_LOW:
                    densityType = "ldpi";
                    break;
                case DisplayMetrics.DENSITY_MEDIUM:
                case DisplayMetrics.DENSITY_140:
                    densityType = "mdpi";
                    break;
                case DisplayMetrics.DENSITY_HIGH:
                case DisplayMetrics.DENSITY_220:
                case DisplayMetrics.DENSITY_200:
                case DisplayMetrics.DENSITY_180:
                    densityType = "hdpi";
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
                default:
                    densityType = "unknown";
                    break;
            }
            densityType += ".apk";
            context.getSharedPreferences("set", Context.MODE_PRIVATE).edit().putString("deviceDpi", densityType).apply();
        }
        return densityType;
    }
}