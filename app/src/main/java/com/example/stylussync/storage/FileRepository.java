package com.example.stylussync.storage;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.example.stylussync.AppExecutors;
import com.example.stylussync.data.Stroke;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

public class FileRepository {

    private static final String TAG = "FileRepository";
    private final Context context;
    private final Gson gson;
    private final AppExecutors executors;

    // 回调接口
    public interface RepositoryCallback<T> {
        void onComplete(T result);
    }

    public FileRepository(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.executors = AppExecutors.getInstance();
    }

    private File getStorageDir() {
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "StylusSync_Drawings");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Failed to create storage directory");
            }
        }
        return dir;
    }

    // 异步保存绘图
    public void saveDrawing(List<Stroke> strokes, String fileName, RepositoryCallback<Boolean> callback) {
        executors.diskIO().execute(() -> {
            String finalFileName = fileName.toLowerCase().endsWith(".json") ? fileName : fileName + ".json";
            File file = new File(getStorageDir(), finalFileName);
            String json = gson.toJson(strokes);
            boolean success = false;
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json);
                Log.d(TAG, "Drawing saved successfully to " + file.getAbsolutePath());
                success = true;
            } catch (IOException e) {
                Log.e(TAG, "Error saving drawing", e);
            }
            final boolean result = success;
            executors.mainThread().execute(() -> callback.onComplete(result));
        });
    }

    // 异步加载绘图
    public void loadDrawing(String fileName, RepositoryCallback<List<Stroke>> callback) {
        executors.diskIO().execute(() -> {
            File file = new File(getStorageDir(), fileName);
            if (!file.exists()) {
                Log.e(TAG, "File not found: " + fileName);
                executors.mainThread().execute(() -> callback.onComplete(null));
                return;
            }

            List<Stroke> strokes = null;
            try (FileReader reader = new FileReader(file)) {
                Type strokeListType = new TypeToken<List<Stroke>>() {}.getType();
                strokes = gson.fromJson(reader, strokeListType);
            } catch (Exception e) { // 捕获更广泛的异常，如JsonSyntaxException
                Log.e(TAG, "Error loading or parsing drawing", e);
            }
            final List<Stroke> result = strokes;
            executors.mainThread().execute(() -> callback.onComplete(result));
        });
    }

    // 异步列出所有已保存的绘图文件名
    public void listDrawingFiles(RepositoryCallback<String[]> callback) {
        executors.diskIO().execute(() -> {
            File dir = getStorageDir();
            String[] fileList = dir.list((d, name) -> name.toLowerCase().endsWith(".json"));
            executors.mainThread().execute(() -> callback.onComplete(fileList));
        });
    }

    public void deleteDrawing(String fileName, RepositoryCallback<Boolean> callback) {
        executors.diskIO().execute(() -> {
            File file = new File(getStorageDir(), fileName);
            boolean success = false;
            if (file.exists()) {
                success = file.delete();
            }
            final boolean result = success;
            executors.mainThread().execute(() -> callback.onComplete(result));
        });
    }
}