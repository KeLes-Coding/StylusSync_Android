package com.example.stylussync.network;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketClient {

    private static final String TAG = "WebSocketClient";
    private WebSocket webSocket;
    private OkHttpClient client;

    // 定义一个回调接口，用于通知 Activity 连接状态的变化
    public interface StatusListener {
        void onStatusUpdate(String status);
    }
    private StatusListener statusListener;

    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }

    public void connect(String url) {
        if (webSocket != null) {
            disconnect();
        }

        // 确保 URL 以 ws:// 或 wss:// 开头
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            url = "ws://" + url;
        }

        Log.d(TAG, "Connecting to: " + url);
        if (statusListener != null) {
            statusListener.onStatusUpdate("正在连接...");
        }

        // 创建 OkHttpClient 实例
        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // 无读取超时
                .build();

        // 创建 WebSocket 请求
        Request request = new Request.Builder()
                .url(url)
                .build();

        // 创建 WebSocket 连接
        webSocket = client.newWebSocket(request, new StylusWebSocketListener());
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnected");
            webSocket = null;
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client = null;
        }
        Log.d(TAG, "Disconnected.");
        if (statusListener != null) {
            statusListener.onStatusUpdate("已断开");
        }
    }

    public boolean send(String message) {
        if (webSocket != null) {
            Log.d(TAG, "Sending: " + message);
            return webSocket.send(message);
        } else {
            Log.w(TAG, "Cannot send, WebSocket is not connected.");
            return false;
        }
    }

    private final class StylusWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            super.onOpen(webSocket, response);
            Log.i(TAG, "WebSocket connection opened!");
            if (statusListener != null) {
                statusListener.onStatusUpdate("已连接");
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            super.onMessage(webSocket, text);
            // MVP 阶段，客户端只发送数据，不处理接收到的消息
            Log.i(TAG, "Received message: " + text);
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            super.onClosing(webSocket, code, reason);
            Log.i(TAG, "WebSocket closing: " + code + " / " + reason);
            if (statusListener != null) {
                statusListener.onStatusUpdate("正在断开...");
            }
        }



        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            super.onClosed(webSocket, code, reason);
            Log.i(TAG, "WebSocket closed: " + code + " / " + reason);
            if (statusListener != null) {
                statusListener.onStatusUpdate("已断开");
            }
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
            super.onFailure(webSocket, t, response);
            Log.e(TAG, "WebSocket connection failure", t);
            if (statusListener != null) {
                statusListener.onStatusUpdate("连接失败: " + t.getMessage());
            }
        }
    }
}