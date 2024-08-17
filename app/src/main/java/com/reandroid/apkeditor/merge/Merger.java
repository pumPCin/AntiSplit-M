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
package com.reandroid.apkeditor.merge;

import android.content.Context;
import android.net.Uri;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;
import com.abdurazaaqmohammed.AntiSplit.main.SignUtil;
import com.j256.simplezip.ZipFileInput;
import com.j256.simplezip.format.GeneralPurposeFlag;
import com.j256.simplezip.format.ZipFileHeader;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.common.AndroidManifestHelper;
import com.reandroid.app.AndroidManifest;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class Merger {

    public interface LogListener {
        void onLog(String log);
        void onLog(int resID);

    }

    public static void run(InputStream ins, File cacheDir, Uri out, Context context, List<String> splits, boolean signApk, boolean revanced) throws Exception {
        LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.searching));

        if(ins!=null) {
            try (ZipFileInput zis = new ZipFileInput(ins)) {
                ZipFileHeader header;
                while ((header = zis.readFileHeader()) != null) {
                    final String name = header.getFileName();
                    if (name.endsWith(".apk")) {
                        if ((splits != null && !splits.isEmpty() && splits.contains(name)))
                            LogUtil.logMessage(MainActivity.rss.getString(R.string.skipping) + name + MainActivity.rss.getString(R.string.unselected));
                        else {
                            File file = new File(cacheDir, name);
                            String canonicalizedPath = file.getCanonicalPath();
                            if (!canonicalizedPath.startsWith(cacheDir.getCanonicalPath() + File.separator)) {
                                throw new IOException("Zip entry is outside of the target dir: " + name);
                            }
                            try (OutputStream os = FileUtils.getOutputStream(file);
                                 InputStream is = zis.openFileDataInputStream(false)) {
                                FileUtils.copyFile(is, os);
                            }
                            LogUtil.logMessage("Extracted " + name);
                        }
                    } else
                        LogUtil.logMessage(MainActivity.rss.getString(R.string.skipping) + name + MainActivity.rss.getString(R.string.not_apk));
                }
            }
        }

        ApkBundle bundle = new ApkBundle();
        bundle.loadApkDirectory(cacheDir, false);
        LogUtil.logMessage("Found modules: " + bundle.getApkModuleList().size());

        try(ApkModule mergedModule = bundle.mergeModules()) {
            if (mergedModule.hasAndroidManifest()) {
                AndroidManifestBlock manifest = mergedModule.getAndroidManifest();
                LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.sanitizing_manifest));
                int ID_requiredSplitTypes = 0x0101064e;
                int ID_splitTypes = 0x0101064f;

                AndroidManifestHelper.removeAttributeFromManifestById(manifest,
                        ID_requiredSplitTypes);
                AndroidManifestHelper.removeAttributeFromManifestById(manifest,
                        ID_splitTypes);
                AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                        AndroidManifest.NAME_splitTypes);

                AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                        AndroidManifest.NAME_requiredSplitTypes);
                AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                        AndroidManifest.NAME_splitTypes);
                AndroidManifestHelper.removeAttributeFromManifestAndApplication(manifest,
                        AndroidManifest.ID_extractNativeLibs
                );
                AndroidManifestHelper.removeAttributeFromManifestAndApplication(manifest,
                        AndroidManifest.ID_isSplitRequired
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
                                                LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.removed_table_entry) + " " + path);
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
                    LogUtil.logMessage("Removed-element : <" + meta.getName() + "> name=\""
                            + AndroidManifestHelper.getNamedValue(meta) + "\"");
                    application.remove(meta);
                }
                manifest.refresh();
            }
            LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.saving));

            if (revanced || signApk) {
                File temp = new File(cacheDir, "temp.apk");
                mergedModule.writeApk(temp);
                // The apk does not need to be signed to patch with ReVanced and it will make this already long crap take even more time
                // but someone is probably going to try to install it before patching and complain
                // and to avoid confusion/mistakes the sign apk option in the app should not be toggled off when revanced option is on
                if (revanced) {
                    LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.fixing));
                    // Copying the contents of the zip to a new one works on most JRE implementations of java.util.zip but not on Android,
                    // the exact same problem happens in ReVanced.
                    try (com.j256.simplezip.ZipFileInput zfi = new com.j256.simplezip.ZipFileInput(temp);
                    com.j256.simplezip.ZipFileOutput zfo = new com.j256.simplezip.ZipFileOutput(signApk ? FileUtils.getOutputStream(temp = new File(cacheDir, "toSign.apk")) : FileUtils.getOutputStream(out, context))) {
                        ZipFileHeader header;
                        while((header = zfi.readFileHeader()) != null) {
                            ZipFileHeader.Builder b = ZipFileHeader.builder();
                            b.setCompressedSize(header.getCompressedSize());
                            b.setCrc32(header.getCrc32());
                            b.setCompressionMethod(header.getCompressionMethod());
                            b.setFileName(header.getFileName());
                            b.setGeneralPurposeFlags(header.getGeneralPurposeFlags());
                            b.clearGeneralPurposeFlag(GeneralPurposeFlag.DATA_DESCRIPTOR);
                            // b.setExtraFieldBytes(header.getExtraFieldBytes());
                            b.setLastModifiedDate(header.getLastModifiedDate());
                            b.setVersionNeeded(header.getVersionNeeded());
                            b.setUncompressedSize(header.getUncompressedSize());
                            zfo.writeFileHeader(b.build());
                            zfo.writeRawFileData(zfi.openFileDataInputStream(true));
                         //   zfo.finishFileData();
                        }
                    }
                }
                if (signApk) {
                    LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.signing));
                    boolean noPerm = MainActivity.doesNotHaveStoragePerm(context);
                    File stupid = new File(noPerm ? (cacheDir + File.separator + "stupid.apk") : FileUtils.getPath(out, context));
                    try {
                        SignUtil.signDebugKey(context, temp, stupid);
                        if (noPerm) FileUtils.copyFile(stupid, FileUtils.getOutputStream(out, context));
                    } catch (Exception e) {
                        SignUtil.signPseudoApkSigner(temp, context, out, e);
                    }
                }
            } else mergedModule.writeApk(FileUtils.getOutputStream(out, context));
        }
        bundle.close();
    }
}