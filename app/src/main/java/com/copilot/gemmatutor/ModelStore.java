package com.copilot.gemmatutor;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class ModelStore {
    static final String MODEL_REPO = "litert-community/gemma-4-E2B-it-litert-lm";
    static final String MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm";
    static final String MODEL_DOWNLOAD_URL = "https://huggingface.co/" + MODEL_REPO + "/resolve/main/" + MODEL_FILE_NAME + "?download=true";
    static final long EXPECTED_SIZE_BYTES = 2_583_085_056L;

    private ModelStore() {}

    static File modelFile(Context context) {
        File dir = new File(context.getFilesDir(), "models");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, MODEL_FILE_NAME);
    }

    static boolean hasModel(Context context) {
        File file = modelFile(context);
        return file.exists() && file.length() > 500L * 1024L * 1024L;
    }

    static File importModel(Context context, Uri uri) throws IOException {
        File destination = modelFile(context);
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IOException("无法打开模型文件");
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return destination;
    }

    static File downloadGemma4(Context context, ProgressCallback callback) throws IOException {
        File destination = modelFile(context);
        File partial = new File(destination.getParentFile(), MODEL_FILE_NAME + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(MODEL_DOWNLOAD_URL).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(60_000);
        connection.connect();
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " while downloading Gemma 4 E2B");
        }
        long total = connection.getContentLengthLong();
        if (total <= 0) total = EXPECTED_SIZE_BYTES;
        long copied = 0L;
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                copied += read;
                if (callback != null) callback.onProgress(copied, total);
            }
        } finally {
            connection.disconnect();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("无法替换旧模型文件");
        }
        if (!partial.renameTo(destination)) {
            throw new IOException("模型下载完成但保存失败");
        }
        return destination;
    }

    interface ProgressCallback {
        void onProgress(long copiedBytes, long totalBytes);
    }
}