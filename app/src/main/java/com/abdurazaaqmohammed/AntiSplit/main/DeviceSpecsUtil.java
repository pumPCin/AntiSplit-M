package com.abdurazaaqmohammed.AntiSplit.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import com.j256.simplezip.ZipFileInput;
import com.j256.simplezip.format.ZipFileHeader;
import com.starry.FileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DeviceSpecsUtil {

    public static List<String> getListOfSplits(Uri splitAPKUri, Context context) throws IOException {
        List<String> splits = new ArrayList<>();

        try (ZipFileInput zis = new ZipFileInput(FileUtils.getInputStream(splitAPKUri, context))) {
            ZipFileHeader header;
            while ((header = zis.readFileHeader()) != null) {
                final String name = header.getFileName();
                if (name.endsWith(".apk")) splits.add(name);
            }
        }

        return splits;
    }

    public static boolean shouldIncludeSplit(String name, Context context) {
        return name.equals("base.apk")
                || !name.startsWith("config") && !name.startsWith("split") // this is base.apk hopefully
                || shouldIncludeLang(name) || shouldIncludeArch(name) || shouldIncludeDpi(name, context);
    }

    public static boolean shouldIncludeLang(String name) {
        return name.contains(Locale.getDefault().getLanguage());
    }

    public static boolean shouldIncludeArch(String name) {
        String arch = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;
        return name.contains(arch) || name.replace('-', '_').contains(arch.replace('-', '_'));
    }

    public static boolean shouldIncludeDpi(String name, Context context) {
        String densityType = getDeviceDpi(context);
        return (name.endsWith(densityType) && !name.replace(densityType, "").endsWith("x")); // ensure that it does not select xxhdpi for xhdpi etc
    }

    public static String getDeviceDpi(Context context) {
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
            SharedPreferences.Editor e = context.getSharedPreferences("set", Context.MODE_PRIVATE).edit().putString("deviceDpi", densityType);
            if(LegacyUtils.supportsArraysCopyOf) e.apply();
            else e.commit();
        }
        return densityType;
    }
}