// 替换文件：app/src/main/java/com/example/stylussync/activity/FileListAdapter.java
package com.example.stylussync.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.stylussync.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

    private List<String> fileList = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(String fileName);
        void onDeleteClick(String fileName, int position);
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
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(fileName, holder.getAdapterPosition());
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
            Collections.addAll(this.fileList, files);
        }
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < fileList.size()) {
            fileList.remove(position);
            notifyItemRemoved(position);
        }
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;
        ImageButton deleteButton;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.text_view_filename);
            deleteButton = itemView.findViewById(R.id.btn_delete_file);
        }
    }
}