package com.example.stylussync.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.stylussync.R;
import java.util.ArrayList;
import java.util.List;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

    private List<String> fileList = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(String fileName);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_drawing, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        String fileName = fileList.get(position);
        holder.fileNameTextView.setText(fileName);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(fileName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    public void setFiles(String[] files) {
        this.fileList.clear();
        if (files != null) {
            java.util.Collections.addAll(this.fileList, files);
        }
        notifyDataSetChanged();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.text_view_filename);
        }
    }
}