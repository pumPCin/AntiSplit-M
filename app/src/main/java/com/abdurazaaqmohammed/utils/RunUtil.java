package com.abdurazaaqmohammed.utils;


import android.os.Handler;
import android.text.TextUtils;
import android.widget.Toast;

import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RunUtil {

    private final Handler handler;
    private final MainActivity context;
    private final CharSequence msg;

    public RunUtil(MainActivity context) {
        this(null, context, null);
    }

    public RunUtil(Handler handler, MainActivity context, CharSequence msg) {
        this.handler = handler;
        this.context = context;
        this.msg = msg;
    }

    public void runInBackground(Callable<Boolean> callable) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(callable);

        executor.submit(() -> {
            try {
                Boolean success = future.get();

                if (success && handler != null && !TextUtils.isEmpty(msg))
                    handler.post(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                (context).showError(e);
            }
        });
    }

    public void runInBackground(Callable<Boolean> callable, Runnable doAfter, boolean onlyIfSuccessful) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(callable);

        executor.submit(() -> {
            try {
                if (future.get() && handler != null) handler.post(doAfter);
            } catch (Exception e) {
                if (!onlyIfSuccessful && handler != null) handler.post(doAfter);
                (context).showError(e);
            }
        });
    }
}