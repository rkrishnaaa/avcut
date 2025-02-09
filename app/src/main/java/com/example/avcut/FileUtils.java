package com.example.avcut;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    public static String getRealPathFromURI(Context context, Uri uri) {
        // For MediaStore content URIs
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return copyFileToCache(context, uri); // API 29+ (Scoped Storage)
            } else {
                return getMediaStorePath(context, uri); // API 26-28
            }
        }
        // If it's a file URI, return the path directly
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    // ✅ For API 29+ (Android 10+), copy file to cache and return new path
    private static String copyFileToCache(Context context, Uri uri) {
        try {
            File cacheDir = context.getCacheDir();
            File tempFile = new File(cacheDir, "tempFile_" + System.currentTimeMillis());

            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            OutputStream outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();

            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ✅ For API 26 to 28, get real file path from MediaStore (only works before Scoped Storage)
    private static String getMediaStorePath(Context context, Uri uri) {
        String[] projection = {MediaStore.Video.Media.DATA};
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            if (cursor.moveToFirst()) {
                String filePath = cursor.getString(columnIndex);
                cursor.close();
                return filePath;
            }
            cursor.close();
        }
        return null;
    }
}

