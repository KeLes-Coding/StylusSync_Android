// 替换文件：app/src/main/java/com/example/stylussync/activity/FileListActivity.java
package com.example.stylussync.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stylussync.R;
import com.example.stylussync.storage.FileRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class FileListActivity extends AppCompatActivity {

    public static final String EXTRA_FILENAME = "EXTRA_FILENAME";

    private FileRepository fileRepository;
    private FileListAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        fileRepository = new FileRepository(this);
        recyclerView = findViewById(R.id.recycler_view_files);
        emptyView = findViewById(R.id.text_view_empty);
        FloatingActionButton fab = findViewById(R.id.fab_new_drawing);

        setupRecyclerView();

        fab.setOnClickListener(v -> {
            Intent intent = new Intent(FileListActivity.this, DrawingActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFiles();
    }

    private void setupRecyclerView() {
        adapter = new FileListAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemClickListener(new FileListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String fileName) {
                Intent intent = new Intent(FileListActivity.this, DrawingActivity.class);
                intent.putExtra(EXTRA_FILENAME, fileName);
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(String fileName, int position) {
                showDeleteConfirmationDialog(fileName, position);
            }
        });
    }

    private void showDeleteConfirmationDialog(String fileName, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage("确定要删除 " + fileName + " 吗？此操作无法撤销。")
                .setPositiveButton("删除", (dialog, which) -> {
                    fileRepository.deleteDrawing(fileName, success -> {
                        if (success) {
                            Toast.makeText(this, "已删除 " + fileName, Toast.LENGTH_SHORT).show();
                            adapter.removeItem(position);
                            // 检查列表是否为空
                            if (adapter.getItemCount() == 0) {
                                emptyView.setVisibility(View.VISIBLE);
                            }
                        } else {
                            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadFiles() {
        fileRepository.listDrawingFiles(files -> {
            adapter.setFiles(files);
            if (files == null || files.length == 0) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        });
    }
}