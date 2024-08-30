package com.abdurazaaqmohammed.AntiSplit.main;

import android.graphics.drawable.Drawable;

public class AppInfo {
    String name;
    String packageName;
    Drawable icon;

    public AppInfo(String name, Drawable icon, String packageName) {
        this.name = name;
        this.icon = icon;
        this.packageName = packageName;
    }
}
