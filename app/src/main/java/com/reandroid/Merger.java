/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.abdurazaaqmohammed.AntiSplit.main.MyAPKLogger;
import com.abdurazaaqmohammed.utils.DeviceSpecsUtil;
import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;
import com.abdurazaaqmohammed.utils.SignUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.j256.simplezip.ZipFileInput;
import com.j256.simplezip.format.ZipFileHeader;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.common.AndroidManifestHelper;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.InputSource;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValue;
import com.reandroid.arsc.value.ValueType;
import com.starry.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Merger {

    /** @noinspection ResultOfMethodCallIgnored*/
    private static void extractAndLoad(Uri in, File cacheDir, MainActivity context, List<String> splits, ApkBundle bundle, MyAPKLogger logger) throws IOException {
        logger.logMessage(in.getPath());

        //bundle.setAPKLogger(context.getLogger()); // This spams the log
        boolean checkSplits = splits != null && !splits.isEmpty();
        try (InputStream is = FileUtils.getInputStream(in, context);
             ZipFileInput zis = new ZipFileInput(is)) {
            ZipFileHeader header;
            while ((header = zis.readFileHeader()) != null) {
                String name = header.getFileName();
                if (name.endsWith(".apk")) {
                    if ((checkSplits && splits.contains(name)))
                        logger.logMessage(context.getRss().getString(R.string.skipping) + name + context.getRss().getString(R.string.unselected));
                    else {
                        File file = new File(cacheDir, name);
                        if (file.getCanonicalPath().startsWith(cacheDir.getCanonicalPath() + File.separator))
                            zis.readFileDataToFile(file);
                        else throw new IOException("Zip entry is outside of the target dir: " + name);

                        logger.logMessage("Extracted " + name);
                    }
                } else logger.logMessage(context.getRss().getString(R.string.skipping) + name + context.getRss().getString(R.string.not_apk));
            }
            try {
                bundle.loadApkDirectory(cacheDir);
            } catch (FileNotFoundException fileNotFoundException) {
                String path;
                try {
                    path= FileUtils.getPath(in, context);
                } catch (Exception esds) {
                    path = context.getOriginalFileName(in);
                }
                throw(new IOException(fileNotFoundException.getMessage() + " file " + in + ' ' + path, fileNotFoundException));
            }
        } catch (Exception e) {
            // If the above failed it probably did not copy any files
            // so might as well do it this way instead of trying unreliable methods to see if we need to do this
            // and possibly copying the file for no reason

            // Check if already copied the file earlier to get list of splits.
            long size;
            boolean notAlreadyCopied = DeviceSpecsUtil.zipFile == null;
            if (notAlreadyCopied) {
                File input = new File(FileUtils.getPath(in, context));
                boolean couldNotRead = !input.canRead();
                if (couldNotRead) try(InputStream is = context.getContentResolver().openInputStream(in)) {
                    File parentFile = cacheDir.getParentFile();
                    com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, input = new File(parentFile != null && parentFile.canRead() ? parentFile : cacheDir, input.getName()));
                }
                size = input.length();
                ArchiveFile zf = new ArchiveFile(input);
                extractZipFile(zf, checkSplits, splits, cacheDir, logger, context.getRss());
                if (couldNotRead) input.delete();
            } else {
                extractZipFile(DeviceSpecsUtil.zipFile, checkSplits, splits, cacheDir, logger, context.getRss());
                size = DeviceSpecsUtil.zipFile.size();
            }
            try {
                bundle.loadApkDirectory(cacheDir);
            } catch (FileNotFoundException fileNotFoundException) {
                String path;
                try {
                    path= FileUtils.getPath(in, context);
                } catch (Exception esds) {
                    path = context.getOriginalFileName(in);
                }
                throw(new IOException(fileNotFoundException.getMessage() + " file " + in + ' ' + path + ' ' + (notAlreadyCopied ? "size" : "length") +' ' + size, fileNotFoundException));
            }
        }
    }

    private static void extractZipFile(ArchiveFile zf, boolean checkSplits, List<String> splits, File cacheDir, APKLogger logger, Resources rss) throws IOException {
        for(InputSource archiveEntry : zf.getInputSources()) {
            String name = archiveEntry.getName();
            if (name.endsWith(".apk")) {
                if ((checkSplits && splits.contains(name)))
                    logger.logMessage(rss.getString(R.string.skipping) + name + rss.getString(R.string.unselected));
                else try (InputStream is = archiveEntry.openStream()) {
                    com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, new File(cacheDir, name));
                }
            } else logger.logMessage(rss.getString(R.string.skipping) + name + rss.getString(R.string.not_apk));
        }
        zf.close();
    }

    public static void run(ApkBundle bundle, File cacheDir, Uri out, MainActivity context, boolean signApk, boolean force) throws IOException, InterruptedException {
        MyAPKLogger logger = context.getLogger();
        logger.logMessage("Found modules: " + bundle.getApkModuleList().size());
        final boolean[] saveToCacheDir = {true}; // I found writeApk(OutputStream) is really slow and writing to file and copying is actually faster
        final boolean[] sign = {signApk};
        for(File split : cacheDir.listFiles()) {
            String splitName = split.getName();
            String arch = null;
            String var = "x86";
            if(splitName.contains(var)) arch = var;
            else if(splitName.contains(var = "x86_64") || splitName.contains("x86-64") || splitName.contains("x64")) arch = var;
            else if(splitName.contains("arm64")) arch = "arm64-v8a";
            else if(splitName.contains("v7a") || splitName.contains("arm7")) arch = "armeabi-v7a";
            if(arch != null) try (ApkModule zf = ApkModule.loadApkFile(split, splitName)) {
                if (zf.containsFile("lib" + File.separator + arch + File.separator + "libpairipcore.so")) {
                    final CountDownLatch latch = new CountDownLatch(1);
                    context.getHandler().post(() ->
                        context.runOnUiThread(new MaterialAlertDialogBuilder(context).setTitle(context.getRss().getString(R.string.warning)).setMessage(R.string.pairip_warning)
                        .setPositiveButton("OK", (dialog, which) -> {
                            saveToCacheDir[0] = true;
                            sign[0] = false;
                            latch.countDown();
                        }).setNegativeButton(context.getRss().getString(R.string.cancel), (dialog, which) -> {
                            context.startActivity(new Intent(context, MainActivity.class));
                            context.finishAffinity();
                            latch.countDown();
                        }).create()::show));
                    latch.await();
                    break;
                }
            }
        }
        try (ApkModule mergedModule = bundle.mergeModules(!force)) { // I guess force meant force throw ..
            if (mergedModule.hasAndroidManifest()) {
                AndroidManifestBlock manifest = mergedModule.getAndroidManifest();
                logger.logMessage((R.string.sanitizing_manifest));

                AndroidManifestHelper.removeAttributeFromManifestById(manifest,
                        AndroidManifest.ID_requiredSplitTypes, logger);
                AndroidManifestHelper.removeAttributeFromManifestById(manifest,
                        AndroidManifest.ID_splitTypes, logger);
                AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                        AndroidManifest.NAME_splitTypes, logger);

                AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                        AndroidManifest.NAME_requiredSplitTypes, logger);
                AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                        AndroidManifest.NAME_splitTypes, logger);
                AndroidManifestHelper.removeAttributeFromManifestAndApplication(manifest,
                        AndroidManifest.ID_extractNativeLibs, logger, AndroidManifest.NAME_extractNativeLibs
                );
                AndroidManifestHelper.removeAttributeFromManifestAndApplication(manifest,
                        AndroidManifest.ID_isSplitRequired, logger,AndroidManifest.NAME_isSplitRequired
                );

                ResXmlElement application = manifest.getApplicationElement();
                List<ResXmlElement> splitMetaDataElements =
                        AndroidManifestHelper.listSplitRequired(application);
                boolean splits_removed = false;
                for (ResXmlElement meta : splitMetaDataElements) {
                    if (!splits_removed) {
                        boolean result = false;
                        ResXmlAttribute nameAttribute = meta.searchAttributeByResourceId(AndroidManifest.ID_name);
                        if (nameAttribute != null) {
                            if ("com.android.vending.splits".equals(nameAttribute.getValueAsString())) {
                                ResXmlAttribute valueAttribute = meta.searchAttributeByResourceId(
                                        AndroidManifest.ID_value);
                                if (valueAttribute == null) {
                                    valueAttribute = meta.searchAttributeByResourceId(
                                            AndroidManifest.ID_resource);
                                }
                                if (valueAttribute != null
                                        && valueAttribute.getValueType() == ValueType.REFERENCE) {
                                    if (mergedModule.hasTableBlock()) {
                                        TableBlock tableBlock = mergedModule.getTableBlock();
                                        ResourceEntry resourceEntry = tableBlock.getResource(valueAttribute.getData());
                                        if (resourceEntry != null) {
                                            ZipEntryMap zipEntryMap = mergedModule.getZipEntryMap();
                                            for (Entry entry : resourceEntry) {
                                                if (entry == null) {
                                                    continue;
                                                }
                                                ResValue resValue = entry.getResValue();
                                                if (resValue == null) {
                                                    continue;
                                                }
                                                String path = resValue.getValueAsString();
                                                logger.logMessage((R.string.removed_table_entry) + " " + path);
                                                //Remove file entry
                                                zipEntryMap.remove(path);
                                                // It's not safe to destroy entry, resource id might be used in dex code.
                                                // Better replace it with boolean value.
                                                entry.setNull(true);
                                                SpecTypePair specTypePair = entry.getTypeBlock()
                                                        .getParentSpecTypePair();
                                                specTypePair.removeNullEntries(entry.getId());
                                            }
                                            result = true;
                                        }
                                    }
                                }
                            }
                        }
                        splits_removed = result;
                    }
                    logger. logMessage("Removed-element : <" + meta.getName() + "> name=\"" + AndroidManifestBlock.getAndroidNameValue(meta) + "\"");
                    application.remove(meta);
                }
                manifest.refresh();
            }
            logger.logMessage((R.string.saving));

            File temp;
            if (sign[0]) {
                mergedModule.writeApk(temp = new File(cacheDir, "temp.apk"));
                logger.logMessage((R.string.signing));
                boolean saveToCache = com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(context);
                String p;
                File signed = new File(saveToCache || (saveToCache = TextUtils.isEmpty(p = FileUtils.getPath(out, context))) ? (cacheDir + File.separator + "signed.apk") : p);
                try {
                    SignUtil.signDebugKey(context, temp, signed);
                    if (saveToCache) try(OutputStream os = context.getContentResolver().openOutputStream(signedApk = out)) {
                        com.abdurazaaqmohammed.utils.FileUtils.copyFile(signed, os);
                    } else signedApk = FileProvider.getUriForFile(context, "com.abdurazaaqmohammed.AntiSplit.fileprovider", signed);
                } catch (Exception e) {
                    SignUtil.signPseudoApkSigner(temp, context, out, e);
                }
            } else if (saveToCacheDir[0]) {
                temp = new File(cacheDir, "temp.apk");
                mergedModule.writeApk(temp);
                try(OutputStream os = FileUtils.getOutputStream(out, context)) {
                    com.abdurazaaqmohammed.utils.FileUtils.copyFile(temp, os);
                }
            } else {
                try(OutputStream os = FileUtils.getOutputStream(out, context)) {
                    mergedModule.writeApk(os);
                }
            }
        }
    }

    public static Uri signedApk;

    public static void run(Uri in, File cacheDir, Uri out, MainActivity context, List<String> splits, boolean signApk, boolean force) throws Exception {
        MyAPKLogger logger = context.getLogger();
        logger.logMessage((R.string.searching));
        try (ApkBundle bundle = new ApkBundle()) {
            if (in == null) {
                try {
                    bundle.loadApkDirectory(cacheDir);
                } catch (FileNotFoundException fileNotFoundException) {
                    throw(new IOException(fileNotFoundException.getMessage() + " file " + splits.toString(), fileNotFoundException));
                }
            }
            else extractAndLoad(in, cacheDir, context, splits, bundle, logger);
            run(bundle, cacheDir, out, context, signApk, force);
        }
    }
}
