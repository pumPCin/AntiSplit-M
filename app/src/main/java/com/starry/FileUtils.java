package com.starry;

import static com.abdurazaaqmohammed.AntiSplit.main.MainActivity.doesNotHaveStoragePerm;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class FileUtils {

    /*
    MIT License

    Copyright (c) 2023 Stɑrry Shivɑm

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.

    https://github.com/starry-shivam/FileUtils/blob/main/file-utils/src/main/java/com/starry/file_utils/FileUtils.kt
     */

    public static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final boolean supportsNewOutputStream = (Build.VERSION.SDK_INT > 25);

    public static OutputStream getOutputStream(String filepath) throws IOException {
        return getOutputStream(new File(filepath));
    }

    public static OutputStream getOutputStream(File file) throws IOException {
        return supportsNewOutputStream ? Files.newOutputStream(file.toPath(), java.nio.file.StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING) : new FileOutputStream(file);
    }

    public static OutputStream getOutputStream(Uri uri, Context context) throws IOException {
       // if(doesNotHaveStoragePerm(context))
            return context.getContentResolver().openOutputStream(uri);
       // File file = new File(getPath(uri, context));
       // return file.canWrite() ? getOutputStream(file) : context.getContentResolver().openOutputStream(uri);
    }

    public static InputStream getInputStream(File file) throws IOException {
        return supportsNewOutputStream ? Files.newInputStream(file.toPath(), StandardOpenOption.READ) : new FileInputStream(file);
    }

    public static InputStream getInputStream(String filePath) throws IOException {
        return supportsNewOutputStream ? Files.newInputStream(Paths.get(filePath), StandardOpenOption.READ) : new FileInputStream(filePath);
    }

    public static InputStream getInputStream(Uri uri, Context context) throws IOException {
       // if(doesNotHaveStoragePerm(context))
            return context.getContentResolver().openInputStream(uri);
        //File file = new File(getPath(uri, context));
        //return file.canRead() ? getInputStream(file) : context.getContentResolver().openInputStream(uri);
    }

    private static boolean fileExists(String filePath) {
        return new File(filePath).exists();
    }

    private static String getPathFromExtSD(String[] pathData) {
        String type = pathData[0];
        String relativePath = File.separator + pathData[1];
        String fullPath;

        if ("primary".equalsIgnoreCase(type)) {
            fullPath = Environment.getExternalStorageDirectory().toString() + relativePath;
            if (fileExists(fullPath)) {
                return fullPath;
            }
        }

        if ("home".equalsIgnoreCase(type)) {
            fullPath = "/storage/emulated/0/Documents" + relativePath;
            if (fileExists(fullPath)) {
                return fullPath;
            }
        }

        fullPath = System.getenv("SECONDARY_STORAGE") + relativePath;
        if (fileExists(fullPath)) {
            return fullPath;
        }

        fullPath = System.getenv("EXTERNAL_STORAGE") + relativePath;
        return fileExists(fullPath) ? fullPath : null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    @SuppressLint("NewApi")
    public static String getPath(Uri uri, Context context) throws IOException {
        String selection;
        String[] selectionArgs;

        String FALLBACK_COPY_FOLDER = "upload_part";
        if (isExternalStorageDocument(uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String fullPath = getPathFromExtSD(split);
            if (fullPath == null || !fileExists(fullPath)) {
                fullPath = copyFileToInternalStorageAndGetPath(uri, FALLBACK_COPY_FOLDER, context);
            }
            return TextUtils.isEmpty(fullPath) ? null : fullPath;
        }

        if (isDownloadsDocument(uri)) {
            try (Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String fileName = cursor.getString(0);
                    String path = Environment.getExternalStorageDirectory() + "/Download/" + fileName;
                    if (!TextUtils.isEmpty(path)) {
                        return path;
                    }
                }
            }

            String id = DocumentsContract.getDocumentId(uri);
            if (!TextUtils.isEmpty(id)) {
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "");
                }

                String[] contentUriPrefixesToTry = {
                        "content://downloads/public_downloads",
                        "content://downloads/my_downloads"
                };

                for (String contentUriPrefix : contentUriPrefixesToTry) {
                    try {
                        Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.parseLong(id));
                        return getDataColumn(context, contentUri, null, null);
                    } catch (NumberFormatException e) {
                        return uri.getPath().replaceFirst("^/document/raw:", "").replaceFirst("^raw:", "");
                    }
                }
            }
        }

        if (isMediaDocument(uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String type = split[0];

            Uri contentUri = null;
            if ("image".equals(type)) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            } else if ("document".equals(type)) {
                contentUri = MediaStore.Files.getContentUri(MediaStore.getVolumeName(uri));
            }

            selection = "_id=?";
            selectionArgs = new String[]{split[1]};
            return getDataColumn(context, contentUri, selection, selectionArgs);
        }

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return copyFileToInternalStorageAndGetPath(uri, FALLBACK_COPY_FOLDER, context);
            } else {
                return getDataColumn(context, uri, null, null);
            }
        }

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return copyFileToInternalStorageAndGetPath(uri, FALLBACK_COPY_FOLDER, context);
    }

    public static String copyFileToInternalStorageAndGetPath(Uri uri, String newDirName, Context context) throws IOException {
        return copyFileToInternalStorage(uri, newDirName, context).getPath();
    }

    public static File copyFileToInternalStorage(Uri uri, String newDirName, Context context) throws IOException {
        Cursor returnCursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null);
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();

        File output;
        if (TextUtils.isEmpty(newDirName)) {
            output = new File(context.getCacheDir() + File.separator + name);
        } else {
            String randomCollisionAvoidance = UUID.randomUUID().toString();
            File dir = new File(context.getCacheDir() + File.separator + newDirName + File.separator + randomCollisionAvoidance);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            output = new File(context.getCacheDir() + File.separator + newDirName + File.separator + randomCollisionAvoidance + File.separator + name);
        }

        try (OutputStream outputStream = FileUtils.getOutputStream(output); InputStream cursor = getInputStream(uri, context)) {
            int read;
            byte[] buffers = new byte[1024];
            while ((read = cursor.read(buffers)) != -1) {
                outputStream.write(buffers, 0, read);
            }
        }
        return output;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
}