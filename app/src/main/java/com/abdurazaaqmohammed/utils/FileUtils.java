package com.abdurazaaqmohammed.utils;


import android.content.Context;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    public static File copyFileFromAssetsAndGetFile(String fileName, Context context) throws IOException {
        File destinationFile = new File(context.getFilesDir(), fileName);
        if(!destinationFile.exists()) try(InputStream is = context.getAssets().open(fileName)) {
            copyFile(is, destinationFile);
        }
        return destinationFile;
    }

    public static File getUnusedFile(File file) {
        int i = 0;
        while(file.exists()) {
            i++;
            String fileName = file.getName();
            String extension = FilenameUtils.getExtension(fileName);
            file = new File(file.getParentFile(), fileName.replace(extension, "").replaceFirst("_\\d+$", "") + '_' + i + '.' + extension);
        }
        return file;
    }

    public static File getUnusedFile(String file) {
        return getUnusedFile(new File(file));
    }

    public static OutputStream getOutputStream(String filepath) throws IOException {
        return getOutputStream(new File(filepath));
    }

    public static void copyFile(File sourceFile, File destinationFile) throws IOException {
        try (InputStream is = getInputStream(sourceFile);
             OutputStream os = getOutputStream(destinationFile)) {
            copyFile(is, os);
        }
    }

    public static void copyFile(InputStream is, OutputStream os) throws IOException {
        if(LegacyUtils.supportsWriteExternalStorage) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
        } else android.os.FileUtils.copy(is, os);
    }

    public static InputStream getInputStream(File file) throws IOException {
        return LegacyUtils.supportsFileChannel ?
                StreamBackups.getInputStream(file)
                : new FileInputStream(file);
    }

    public static InputStream getInputStream(String filePath) throws IOException {
        return getInputStream(new File(filePath));
    }

    public static void copyFile(InputStream is, File destinationFile) throws IOException {
        try (OutputStream os = getOutputStream(destinationFile)) {
            copyFile(is, os);
        }
    }

    public static void copyFile(File in, OutputStream os) throws IOException {
        try(InputStream is = getInputStream(in)) {
            copyFile(is, os);
        }
    }

    public static OutputStream getOutputStream(File file) throws IOException {
        return LegacyUtils.supportsFileChannel ?
                StreamBackups.getOutputStream(file)
                : new FileOutputStream(file);
    }
}