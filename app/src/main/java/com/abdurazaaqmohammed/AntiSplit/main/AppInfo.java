package com.abdurazaaqmohammed.AntiSplit.main;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public String name;
    String packageName;
    Drawable icon;
    public long lastUpdated;
    public long firstInstall;

    public AppInfo(String name, Drawable icon, String packageName, long lastUpdated, long firstInstall) {
        this.name = name;
        this.icon = icon;
        this.packageName = packageName;
        this.lastUpdated = lastUpdated;
        this.firstInstall = firstInstall;
    }
}
