package com.example.stylussync.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
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

        adapter.setOnItemClickListener(fileName -> {
            Intent intent = new Intent(FileListActivity.this, DrawingActivity.class);
            intent.putExtra(EXTRA_FILENAME, fileName);
            startActivity(intent);
        });
    }

    private void loadFiles() {
        String[] files = fileRepository.listDrawingFiles();
        adapter.setFiles(files);

        if (files == null || files.length == 0) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }
}