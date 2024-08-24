package com.reandroid.apkeditor.merge;

import static com.abdurazaaqmohammed.AntiSplit.main.MainActivity.toggleAnimation;

import android.widget.ImageView;

import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;

import java.util.concurrent.CountDownLatch;

public class LogUtil {
    private static Merger.LogListener logListener;

    public static boolean logEnabled;

    public static void setLogListener(Merger.LogListener listener) {
        logListener = listener;
    }



    public static void logMessage(String msg) {
        if (logListener != null && logEnabled) {
            logListener.onLog(msg);
        }
    }
    public static void logMessage(int resID) {
        if (logListener != null && logEnabled) {
            logListener.onLog(resID);
        }
    }
}
