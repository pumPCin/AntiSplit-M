package com.abdurazaaqmohammed.AntiSplit.main;

import android.view.View;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.reandroid.apk.APKLogger;

public class MyAPKLogger implements APKLogger {
    private final MainActivity mainActivity;
    private final TextView logField;
    private final NestedScrollView scrollView;

    MyAPKLogger(MainActivity mainActivity, TextView logField, NestedScrollView scrollView) {
        this.mainActivity = mainActivity;
        this.logField = logField;
        this.scrollView = scrollView;
    }

    @Override
    public void logMessage(String s) {
        if (mainActivity.logEnabled) {
            mainActivity.getHandler().post(() -> logField.append(new StringBuilder(s).append('\n')));
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    @Override
    public void logError(String s, Throwable throwable) {
        mainActivity.showError(throwable);
    }

    @Override
    public void logVerbose(String s) {
        logMessage(s);
    }

    public void logMessage(int resourceId) {
        if (mainActivity.logEnabled) logMessage(mainActivity.getRss().getString(resourceId));
    }
}
