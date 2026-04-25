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
import android.os.Build;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Merger {

    private File workingDirectory;
    private final MainActivity context;
    private final MyAPKLogger logger;
    private final Resources rss;
    public Uri signedApk;

    public Merger(File workingDirectory, MainActivity context) {
        this.workingDirectory = workingDirectory;
        this.rss = (this.context = context).getRss();
        this.logger = context.getLogger();
    }

    public void setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    private void extractAndLoadFromInputStream(Uri splitAPKUri,  List<String> splitsToNotInclude, ApkBundle bundle) throws IOException {
        logger.logMessage(splitAPKUri.getPath());

        //bundle.setAPKLogger(context.getLogger()); // This spams the log
        boolean checkSplits = splitsToNotInclude != null && !splitsToNotInclude.isEmpty();
        try (InputStream is = FileUtils.getInputStream(splitAPKUri, context);
             ZipFileInput zis = new ZipFileInput(is)) {
            ZipFileHeader header;
            while ((header = zis.readFileHeader()) != null) {
                String name = header.getFileName();
                if (name.endsWith(".apk")) {
                    if ((checkSplits && splitsToNotInclude.contains(name)))
                        logger.logMessage(rss.getString(R.string.skipping) + name + rss.getString(R.string.unselected));
                    else {
                        File file = new File(workingDirectory, name);
                        if (file.getCanonicalPath().startsWith(workingDirectory.getCanonicalPath() + File.separator)) {
                            File parentDir = file.getParentFile();
                            if (parentDir != null && !parentDir.exists()) {
                                parentDir.mkdirs();
                            }

                            logger.logMessage("Extracted " + name + " (" + zis.readFileDataToFile(file) +" bytes)");
                        } else throw new IOException("Zip entry is outside of the target dir: " + name);
                    }
                } else logger.logMessage(rss.getString(R.string.skipping) + name + rss.getString(R.string.not_apk));
            }
            try {
                bundle.loadApkDirectory(workingDirectory);
            } catch (FileNotFoundException fileNotFoundException) {
                String path;
                try {
                    path= FileUtils.getPath(splitAPKUri, context);
                } catch (Exception e) {
                    path = context.getOriginalFileName(splitAPKUri);
                }
                throw(new IOException(fileNotFoundException.getMessage() + " file " + splitAPKUri + ' ' + path, fileNotFoundException));
            }
        } catch (Exception e) {
            // If the above failed it probably did not copy any files
            // so might as well do it this way instead of trying unreliable methods to see if we need to do this
            // and possibly copying the file for no reason
            extractAndLoadFromZipFile(splitAPKUri, splitsToNotInclude, bundle, checkSplits);
        }
    }

    /** @noinspection ResultOfMethodCallIgnored*/
    private void extractAndLoadFromZipFile(Uri splitAPKUri,  List<String> splitsToNotInclude, ApkBundle bundle, boolean checkSplits) throws IOException {
        // Check if already copied the file earlier to get list of splitsToNotInclude.
        long size;
        boolean notAlreadyCopied = DeviceSpecsUtil.zipFile == null;
        if (notAlreadyCopied) {
            boolean cantReadFile = com.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm(context);
            File inputZipFile = null;
            String path;

            if(!cantReadFile) try {
                path = FileUtils.getPath(splitAPKUri, context);
                cantReadFile = TextUtils.isEmpty(path) || !((inputZipFile= new File(path)).canRead());
            } catch (Exception exception) {
                cantReadFile = true;
            }

            if (cantReadFile) {
                try(InputStream is = context.getContentResolver().openInputStream(splitAPKUri)) {
                    //File parentFile = cacheDir.getParentFile();
                    // com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, input = new File(parentFile != null && parentFile.canRead() ? parentFile : cacheDir, input == null ? context.getOriginalFileName(in) :  input.getName()));
                    inputZipFile = new File(workingDirectory, System.currentTimeMillis() + '_' + context.getOriginalFileName(splitAPKUri));
                    com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, inputZipFile);
                }
            }
            size = inputZipFile.length();
            ArchiveFile zf = new ArchiveFile(inputZipFile);
            extractZipFile(zf, checkSplits, splitsToNotInclude);
            if (cantReadFile) inputZipFile.delete();
        } else {

            extractZipFile(DeviceSpecsUtil.zipFile, checkSplits, splitsToNotInclude);
            size = DeviceSpecsUtil.zipFile.size();
        }
        try {
            bundle.loadApkDirectory(workingDirectory);
        } catch (FileNotFoundException fileNotFoundException) {
            String path;
            try {
                path = FileUtils.getPath(splitAPKUri, context);
            } catch (Exception exception) {
                path = context.getOriginalFileName(splitAPKUri);
            }
            throw(new IOException(fileNotFoundException.getMessage() + " file " + splitAPKUri + ' ' + path + ' ' + (notAlreadyCopied ? "size" : "length") +' ' + size, fileNotFoundException));
        }
    }

    private void extractZipFile(ArchiveFile zf, boolean checkSplits, List<String> splitsToNotInclude) throws IOException {
        for(InputSource archiveEntry : zf.getInputSources()) {
            String name = archiveEntry.getName();
            if (name.endsWith(".apk")) {
                if ((checkSplits && splitsToNotInclude.contains(name)))
                    logger.logMessage(rss.getString(R.string.skipping) + name + rss.getString(R.string.unselected));
                else try (InputStream is = archiveEntry.openStream()) {
                   // com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, new File(cacheDir, name));
                    File outputFile = new File(workingDirectory, name);
                    if (outputFile.getCanonicalPath().startsWith(workingDirectory.getCanonicalPath() + File.separator)) {
                        File parentDir = outputFile.getParentFile();
                        if (parentDir != null && !parentDir.exists()) {
                            parentDir.mkdirs();
                        }
                        com.abdurazaaqmohammed.utils.FileUtils.copyFile(is, outputFile);
                    } else {
                        logger.logMessage("Skipped invalid path: " + name);
                    }
                }
            } else logger.logMessage(rss.getString(R.string.skipping) + name + rss.getString(R.string.not_apk));
        }
        zf.close();
    }

    public File run(ApkBundle bundle, boolean signApk, boolean force) throws Exception {
        MyAPKLogger logger = context.getLogger();
        logger.logMessage("Found modules: " + bundle.getApkModuleList().size());
        //final boolean[] saveToCacheDir = {true}; // I found writeApk(OutputStream) is really slow and writing to file and copying is actually faster
        final boolean[] sign = {signApk};
        File[] splits = workingDirectory.listFiles();
        for(File split : splits) {
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
                        context.runOnUiThread(new MaterialAlertDialogBuilder(context).setTitle(rss.getString(R.string.warning)).setMessage(R.string.pairip_warning)
                        .setPositiveButton("OK", (dialog, which) -> {
                            //saveToCacheDir[0] = true;
                            sign[0] = false;
                            latch.countDown();
                        }).setNegativeButton(rss.getString(R.string.cancel), (dialog, which) -> {
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

            File mergedAPK = new File(workingDirectory, "merged_" + System.currentTimeMillis() + ".apk");
            mergedModule.writeApk(mergedAPK);
            if (sign[0]) {
                logger.logMessage((R.string.signing));
                File outputSigned = new File(workingDirectory, System.currentTimeMillis() + "signed.apk");
               // try {
                SignUtil.signDebugKey(context, mergedAPK, outputSigned);
                signedApk = FileProvider.getUriForFile(context, "com.abdurazaaqmohammed.AntiSplit.fileprovider", outputSigned);
                return outputSigned;
              //  } catch (Exception e) {
                //    SignUtil.signPseudoApkSigner(mergedAPK, context, out, e); // Just forget about this because I never figure out how to solve any of the error from it and it probably does not help
              //  }
            } else {
                return mergedAPK;
            }
        }
    }


    public File run(Uri splitAPKUri, List<String> splitsToNotInclude, boolean signApk, boolean force) throws Exception {
        MyAPKLogger logger = context.getLogger();
        logger.logMessage((R.string.searching));
        try (ApkBundle bundle = new ApkBundle()) {
            if (splitAPKUri == null) {
                // Multiple splits from a split apk, already copied to cache dir
                try {
                    bundle.loadApkDirectory(workingDirectory);
                } catch (FileNotFoundException fileNotFoundException) {
                    if(splitsToNotInclude != null) throw(new IOException(fileNotFoundException.getMessage() + " file " + splitsToNotInclude, fileNotFoundException));
                    else throw fileNotFoundException;
                }
            }
            else {
                logger.logMessage("MIME Type " + context.getContentResolver().getType(splitAPKUri));
                boolean notAlreadyCopied = DeviceSpecsUtil.zipFile == null;
                boolean checkSplits = splitsToNotInclude != null && !splitsToNotInclude.isEmpty();
                if (notAlreadyCopied) {
                    if (Build.VERSION.SDK_INT < 23) {
                        logger.logMessage(splitAPKUri.getPath());

                        extractAndLoadFromZipFile(splitAPKUri, splitsToNotInclude, bundle, checkSplits);
                    } else extractAndLoadFromInputStream(splitAPKUri, splitsToNotInclude, bundle);
                } else {
                    extractZipFile(DeviceSpecsUtil.zipFile, checkSplits, splitsToNotInclude);
                }
            }
            return run(bundle, signApk, force);
        }
    }
}