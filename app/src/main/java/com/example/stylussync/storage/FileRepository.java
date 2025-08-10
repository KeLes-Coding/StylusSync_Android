package com.example.stylussync.storage;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

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

    public FileRepository(Context context) {
        this.context = context;
        this.gson = new Gson();
    }

    // 获取存储绘图文件的目录
    private File getStorageDir() {
        // 使用 getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        // 将文件保存在应用的专属外部存储区域的 Documents 目录下。
        // 这样做不需要额外的存储权限，且应用卸载后文件会自动删除。
        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "StylusSync_Drawings");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Failed to create storage directory");
            }
        }
        return dir;
    }

    // 保存绘图
    public boolean saveDrawing(List<Stroke> strokes, String fileName) {
        if (!fileName.toLowerCase().endsWith(".json")) {
            fileName += ".json";
        }
        File file = new File(getStorageDir(), fileName);
        String json = gson.toJson(strokes);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
            Log.d(TAG, "Drawing saved successfully to " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving drawing", e);
            return false;
        }
    }

    // 加载绘图
    public List<Stroke> loadDrawing(String fileName) {
        File file = new File(getStorageDir(), fileName);
        if (!file.exists()) {
            Log.e(TAG, "File not found: " + fileName);
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            Type strokeListType = new TypeToken<List<Stroke>>() {}.getType();
            return gson.fromJson(reader, strokeListType);
        } catch (IOException e) {
            Log.e(TAG, "Error loading drawing", e);
            return null;
        }
    }

    // 列出所有已保存的绘图文件名
    public String[] listDrawingFiles() {
        File dir = getStorageDir();
        return dir.list((d, name) -> name.toLowerCase().endsWith(".json"));
    }
}
