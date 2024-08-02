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
import com.abdurazaaqmohammed.AntiSplit.main.DeviceSpecsUtil;
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
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class Merger {

    public interface LogListener {
        void onLog(String log);
        void onLog(int resID);

    }

    public static void run(InputStream ins, File cacheDir, OutputStream out, Uri xapkUri, Context context, List<String> splits, boolean signApk) throws Exception {
        LogUtil.logMessage(R.string.searching);

        if(ins!=null) {
            if(xapkUri == null) {
                byte[] buffer = new byte[1024];
                try (ZipInputStream zis = new ZipInputStream(ins)) {
                    ZipEntry zipEntry = zis.getNextEntry();
                    while (zipEntry != null) {
                        final String name = zipEntry.getName();
                        if (name.endsWith(".apk")) {
                            if((splits != null && !splits.isEmpty() && splits.contains(name))) LogUtil.logMessage(context.getString(R.string.skipping) + name + context.getString(R.string.unselected));
                            else {
                                File file = new File(cacheDir, name);
                                String canonicalizedPath = file.getCanonicalPath();
                                if (!canonicalizedPath.startsWith(cacheDir.getCanonicalPath() + File.separator)) {
                                    throw new IOException("Zip entry is outside of the target dir: " + name);
                                }
                                OutputStream fos = FileUtils.getOutputStream(file);
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                                fos.close();
                                LogUtil.logMessage("Extracted " + name);
                            }
                        } else LogUtil.logMessage(context.getString(R.string.skipping) + name + context.getString(R.string.not_apk));
                        zipEntry = zis.getNextEntry();
                    }
                    zis.closeEntry();
                }
            } else {
                LogUtil.logMessage(R.string.detected_xapk); //ZipInputStream is reading XAPK files as if all files inside the splits were in 1 zip which breaks everything
                File bruh = DeviceSpecsUtil.splitApkPath == null ? new File(FileUtils.getPath(xapkUri, context)) : DeviceSpecsUtil.splitApkPath; // if file was already copied to get splits list do not copy it again
                final boolean couldntRead = !bruh.canRead();
                if (couldntRead) bruh = FileUtils.copyFileToInternalStorage(xapkUri, null, context);
                try (ZipFile zipFile = new ZipFile(bruh)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();

                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String fileName = entry.getName();

                        if (fileName.endsWith(".apk")) {
                            if((splits != null && !splits.isEmpty() && splits.contains(fileName))) LogUtil.logMessage(context.getString(R.string.skipping) + fileName + context.getString(R.string.unselected));
                            else {
                                File outFile = new File(cacheDir, fileName);
                                File parentDir = outFile.getParentFile();
                                if (!parentDir.exists()) {
                                    parentDir.mkdirs();
                                }

                                try (InputStream is = zipFile.getInputStream(entry);
                                     OutputStream fos = FileUtils.getOutputStream(outFile)) {
                                    byte[] buffy = new byte[1024];
                                    int len;
                                    while ((len = is.read(buffy)) > 0) {
                                        fos.write(buffy, 0, len);
                                    }
                                }
                            }
                        } else LogUtil.logMessage(context.getString(R.string.skipping) + fileName + context.getString(R.string.not_apk));
                    }
                    if(couldntRead) bruh.delete();
                }
            }
        }

        ApkBundle bundle = new ApkBundle();
        bundle.loadApkDirectory(cacheDir, false);
        LogUtil.logMessage("Found modules: " + bundle.getApkModuleList().size());

        ApkModule mergedModule = bundle.mergeModules();
        if(mergedModule.hasAndroidManifest()) {
            AndroidManifestBlock manifest = mergedModule.getAndroidManifest();
            LogUtil.logMessage(R.string.sanitizing_manifest);
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
                                            LogUtil.logMessage(context.getString(R.string.removed_table_entry) + " " + path);
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
        LogUtil.logMessage((R.string.saving));

        if(signApk) {
            final File temp = new File(cacheDir, "temp.apk");
            mergedModule.writeApk(temp);
            LogUtil.logMessage(R.string.signing);
            try (InputStream fis = FileUtils.getInputStream(temp)) {
                new com.aefyr.pseudoapksigner.PseudoApkSignerWrapper(context).sign(fis, out);
            } catch (Exception e) {
                LogUtil.logMessage(R.string.sign_failed);
                mergedModule.writeApk(out);
                throw(e); // for showError
            }
        } else mergedModule.writeApk(out);
        mergedModule.close();
        bundle.close();
    }
}