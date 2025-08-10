package com.example.stylussync.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.stylussync.R;
import com.example.stylussync.data.Stroke;
import com.example.stylussync.network.WebSocketClient;
import com.example.stylussync.storage.FileRepository;
import com.example.stylussync.view.DrawingSurfaceView;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DrawingActivity extends AppCompatActivity
        implements DrawingSurfaceView.DrawingCallback, WebSocketClient.StatusListener {

    private static final String TAG = "DrawingActivity";

    // UI & View
    private DrawingSurfaceView drawingSurfaceView;
    private Button btnEraser;
    private TextView textViewStatus;

    // State
    private boolean isEraserActive = false;

    // Modules
    private FileRepository fileRepository;
    private WebSocketClient webSocketClient;
    private Gson gson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);

        // --- 初始化模块 ---
        fileRepository = new FileRepository(this);
        webSocketClient = new WebSocketClient();
        webSocketClient.setStatusListener(this);
        gson = new Gson();

        // --- 初始化视图控件 ---
        drawingSurfaceView = findViewById(R.id.drawing_surface_view);
        textViewStatus = findViewById(R.id.text_view_status);
        Button btnConnect = findViewById(R.id.btn_connect);
        Button btnColorBlack = findViewById(R.id.btn_color_black);
        Button btnColorRed = findViewById(R.id.btn_color_red);
        Button btnColorBlue = findViewById(R.id.btn_color_blue);
        SeekBar seekBarStrokeWidth = findViewById(R.id.seekbar_stroke_width);
        btnEraser = findViewById(R.id.btn_eraser);
        Button btnClear = findViewById(R.id.btn_clear);
        Button btnSave = findViewById(R.id.btn_save);

        // 设置绘图视图的回调
        drawingSurfaceView.setCallback(this);

        // --- 设置监听器 ---
        btnConnect.setOnClickListener(v -> showConnectDialog());

        btnColorBlack.setOnClickListener(v -> {
            resetEraserMode();
            drawingSurfaceView.setPenColor(Color.BLACK);
        });
        btnColorRed.setOnClickListener(v -> {
            resetEraserMode();
            drawingSurfaceView.setPenColor(Color.RED);
        });
        btnColorBlue.setOnClickListener(v -> {
            resetEraserMode();
            drawingSurfaceView.setPenColor(Color.BLUE);
        });

        btnClear.setOnClickListener(v -> {
            drawingSurfaceView.clearCanvas();
            sendControlMessage("clear_canvas");
        });

        btnEraser.setOnClickListener(v -> {
            isEraserActive = !isEraserActive;
            drawingSurfaceView.setEraserMode(isEraserActive);
            if (isEraserActive) {
                btnEraser.setText("画笔");
                btnEraser.setBackgroundColor(Color.LTGRAY);
            } else {
                btnEraser.setText("橡皮擦");
                btnEraser.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        btnSave.setOnClickListener(v -> showSaveDialog());

        seekBarStrokeWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float strokeWidth = Math.max(1, progress / 2.0f);
                drawingSurfaceView.setStrokeWidth(strokeWidth);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 检查是否有文件需要加载
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(FileListActivity.EXTRA_FILENAME)) {
            String fileName = intent.getStringExtra(FileListActivity.EXTRA_FILENAME);
            setTitle(fileName);
            List<Stroke> strokes = fileRepository.loadDrawing(fileName);
            if (strokes != null) {
                drawingSurfaceView.setStrokes(strokes);
            } else {
                Toast.makeText(this, "加载文件失败: " + fileName, Toast.LENGTH_LONG).show();
            }
        } else {
            setTitle("新建绘图");
        }
    }

    @Override
    public void onNewStroke(Stroke stroke) {
        Log.d(TAG, "New stroke finished. Points: " + stroke.points.size());
        sendDrawMessage(stroke);
    }

    @Override
    public void onStatusUpdate(String status) {
        runOnUiThread(() -> textViewStatus.setText("状态: " + status));
    }

    private void showConnectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("连接到服务器");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("例如: 192.168.1.100:8080");
        builder.setView(input);
        builder.setPositiveButton("连接", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                webSocketClient.connect(url + "/ws");
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showSaveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("保存绘图");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        input.setText("Drawing_" + timeStamp);
        input.selectAll();
        builder.setView(input);
        builder.setPositiveButton("保存", (dialog, which) -> {
            String fileName = input.getText().toString().trim();
            if (fileName.isEmpty()) {
                Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean success = fileRepository.saveDrawing(drawingSurfaceView.getStrokes(), fileName);
            if (success) {
                Toast.makeText(this, "保存成功: " + fileName + ".json", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void sendDrawMessage(Stroke stroke) {
        class DrawMessage {
            final String type = "draw";
            final Stroke data;
            DrawMessage(Stroke s) { this.data = s; }
        }
        String json = gson.toJson(new DrawMessage(stroke));
        webSocketClient.send(json);
    }

    private void sendControlMessage(String event) {
        class ControlMessage {
            final String type = "control";
            final String event_name; // 为了避免与Java关键字冲突，可以使用不同的命名
            ControlMessage(String e) { this.event_name = e; }
        }
        String json = gson.toJson(new ControlMessage(event));
        webSocketClient.send(json);
    }

    private void resetEraserMode() {
        if (isEraserActive) {
            isEraserActive = false;
            drawingSurfaceView.setEraserMode(false);
            btnEraser.setText("橡皮擦");
            btnEraser.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }
    }
}