package com.aefyr.pseudoapksigner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aefyr.pseudoapksigner.PseudoApkSigner;
import com.aefyr.pseudoapksigner.IOUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PseudoApkSignerWrapper {
    private static final String TAG = "PASWrapper";
    private static final String FILE_NAME_PAST = "testkey.past";
    private static final String FILE_NAME_PRIVATE_KEY = "testkey.pk8";

    @SuppressLint("StaticFieldLeak")// a p p l i c a t i o n   c o n t e x t

    private Context mContext;
    private PseudoApkSigner mPseudoApkSigner;


    public PseudoApkSignerWrapper(Context c) {
        mContext = c.getApplicationContext();
    }

    public void sign(InputStream inputApkFile, OutputStream outputSignedApkFile) throws Exception {

        checkAndPrepareSigningEnvironment();

        if (mPseudoApkSigner == null) {
            mPseudoApkSigner = new PseudoApkSigner(new File(getSigningEnvironmentDir(), FILE_NAME_PAST), new File(getSigningEnvironmentDir(), FILE_NAME_PRIVATE_KEY));
        }
            mPseudoApkSigner.sign(inputApkFile, outputSignedApkFile);
    }

    private void checkAndPrepareSigningEnvironment() throws Exception {
        File signingEnvironment = getSigningEnvironmentDir();
        File pastFile = new File(signingEnvironment, FILE_NAME_PAST);
        File privateKeyFile = new File(signingEnvironment, FILE_NAME_PRIVATE_KEY);

        if (pastFile.exists() && privateKeyFile.exists())
            return;

        Log.d(TAG, "Preparing signing environment...");
        signingEnvironment.mkdir();

        IOUtils.copyFileFromAssets(mContext, FILE_NAME_PAST, pastFile);
        IOUtils.copyFileFromAssets(mContext, FILE_NAME_PRIVATE_KEY, privateKeyFile);
    }

    private File getSigningEnvironmentDir() {
        return new File(mContext.getFilesDir(), "signing");
    }
}