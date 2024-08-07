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
import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;
import com.android.apksig.ApkSigner;
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
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
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

    public static void run(InputStream ins, File cacheDir, Uri out, Uri xapkUri, Context context, List<String> splits, boolean signApk) throws Exception {
        LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.searching));

        if(ins!=null) {
            if(xapkUri == null) {
                byte[] buffer = new byte[1024];
                try (ZipInputStream zis = new ZipInputStream(ins)) {
                    ZipEntry zipEntry = zis.getNextEntry();
                    while (zipEntry != null) {
                        final String name = zipEntry.getName();
                        if (name.endsWith(".apk")) {
                            if((splits != null && !splits.isEmpty() && splits.contains(name))) LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.skipping) + name + com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.unselected));
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
                        } else LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.skipping) + name + com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.not_apk));
                        zipEntry = zis.getNextEntry();
                    }
                    zis.closeEntry();
                }
            } else {
                LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.detected_xapk)); //ZipInputStream is reading XAPK files as if all files inside the splits were in 1 zip which breaks everything
                File bruh = DeviceSpecsUtil.splitApkPath == null ? new File(FileUtils.getPath(xapkUri, context)) : DeviceSpecsUtil.splitApkPath; // if file was already copied to get splits list do not copy it again
                final boolean couldntRead = !bruh.canRead();
                if (couldntRead) bruh = FileUtils.copyFileToInternalStorage(xapkUri, context);
                try (ZipFile zipFile = new ZipFile(bruh)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();

                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String fileName = entry.getName();

                        if (fileName.endsWith(".apk")) {
                            if((splits != null && !splits.isEmpty() && splits.contains(fileName))) LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.skipping) + fileName + com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.unselected));
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
                        } else LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.skipping) + fileName + com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.not_apk));
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

        if(signApk) {
            final File temp = new File(cacheDir, "temp.apk");
            mergedModule.writeApk(temp);
            LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.signing));
            try {
                /*final String FILE_NAME_PAST = "testkey.past";
                final String FILE_NAME_PRIVATE_KEY = "testkey.pk8";
                File signingEnvironment = new File(context.getFilesDir(), "signing");
                File pastFile = new File(signingEnvironment, FILE_NAME_PAST);
                File privateKeyFile = new File(signingEnvironment, FILE_NAME_PRIVATE_KEY);

                if (!pastFile.exists() || !privateKeyFile.exists()) {
                    signingEnvironment.mkdir();
                    IOUtils.copyFileFromAssets(context, FILE_NAME_PAST, pastFile);
                    IOUtils.copyFileFromAssets(context, FILE_NAME_PRIVATE_KEY, privateKeyFile);
                }

                PseudoApkSigner.sign(fis, out, pastFile, privateKeyFile);*/
                char[] password = "android".toCharArray();

                KeyStore keystore = KeyStore.getInstance("PKCS12");
                keystore.load(context.getAssets().open("debug.keystore"), password);

                String alias = keystore.aliases().nextElement();
                KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keystore.getEntry(alias, new KeyStore.PasswordProtection(password));
                PrivateKey privateKey = privateKeyEntry.getPrivateKey();

                ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder("CERT",
                        privateKey,
                        Collections.singletonList((X509Certificate) keystore.getCertificate(alias))).build();
                ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(signerConfig));
                builder.setInputApk(temp);
                boolean noPerm = MainActivity.doesNotHaveStoragePerm(context);
                File stupid = new File(noPerm ? (context.getCacheDir() + File.separator + "stupid.apk") : FileUtils.getPath(out, context));
                builder.setOutputApk(stupid);
                builder.setCreatedBy("Android Gradle 8.0.2");
                builder.setV2SigningEnabled(true);
                builder.setV3SigningEnabled(true);
                ApkSigner signer = builder.build();
                signer.sign();
                if(noPerm) {
                    FileUtils.copyFile(stupid, FileUtils.getOutputStream(out, context));
                    stupid.delete();
                }
            } catch (Exception e) {
                LogUtil.logMessage(com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss.getString(R.string.sign_failed));
                FileUtils.copyFile(temp, FileUtils.getOutputStream(out, context));
                throw(e); // for showError
            }
        } else mergedModule.writeApk(FileUtils.getOutputStream(out, context));
        mergedModule.close();
        bundle.close();
    }
}