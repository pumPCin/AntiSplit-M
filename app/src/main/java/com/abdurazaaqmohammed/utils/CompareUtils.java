package com.abdurazaaqmohammed.utils;

import com.abdurazaaqmohammed.AntiSplit.main.AppInfo;

import java.util.Locale;

public class CompareUtils {
    public static int compareAppInfoByName(AppInfo p1, AppInfo p2) {
        return p1.name.toLowerCase(Locale.ROOT).compareTo(p2.name.toLowerCase(Locale.ROOT));
    }
    public static int compareByName(String p1, String p2) {
        return p1.toLowerCase(Locale.ROOT).compareTo(p2.toLowerCase(Locale.ROOT));
    }
    public long getSortField(AppInfo appInfo, int sortMode) {
        return sortMode == 1 ? appInfo.lastUpdated : appInfo.firstInstall;
    }
}