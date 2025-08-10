package com.example.stylussync.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.NonNull;

import com.example.stylussync.data.Point;
import com.example.stylussync.data.Stroke;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DrawingSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // 绘图线程控制
    private SurfaceHolder mHolder;
    private Thread mDrawThread;
    private boolean mIsDrawing = false;

    // 画布与画笔
    private Bitmap mBitmap;
    private Canvas mBitmapCanvas;
    private final Paint mPaint;
    private final Paint mEraserPaint;
    private Path mCurrentPath;

    // 数据
    // 使用 CopyOnWriteArrayList 可以在遍历时安全地修改，避免多线程冲突
    private final List<Stroke> mStrokes = new CopyOnWriteArrayList<>();
    private Stroke mCurrentStroke;

    // 绘图属性
    private int mCurrentColor = Color.BLACK;
    private float mCurrentBaseStrokeWidth = 10f;
    private boolean mIsEraserMode = false;

    // 回调接口，用于通知 Activity 数据变化
    public interface DrawingCallback {
        void onNewStroke(Stroke stroke);
    }
    private DrawingCallback mCallback;


    public DrawingSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);

        mEraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEraserPaint.setStyle(Paint.Style.STROKE);
        mEraserPaint.setStrokeCap(Paint.Cap.ROUND);
        mEraserPaint.setStrokeJoin(Paint.Join.ROUND);
        // 关键：设置橡皮擦混合模式
        mEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    private void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setCallback(DrawingCallback callback) {
        this.mCallback = callback;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 只处理触控笔事件
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return super.onTouchEvent(event);
        }

        // 检查触控笔副按钮状态，切换橡皮擦
        if ((event.getButtonState() & MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
            setEraserMode(true);
        } else {
            setEraserMode(false);
        }

        float x = event.getX();
        float y = event.getY();
        float pressure = event.getPressure();

        Point point = new Point(x, y, pressure);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentPath = new Path();
                mCurrentPath.moveTo(x, y);
                mCurrentStroke = new Stroke(mCurrentColor, mCurrentBaseStrokeWidth, mIsEraserMode);
                mCurrentStroke.addPoint(point);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mCurrentPath != null) {
                    mCurrentPath.lineTo(x, y);
                    mCurrentStroke.addPoint(point);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mCurrentStroke != null) {
                    mStrokes.add(mCurrentStroke);
                    if (mCallback != null) {
                        mCallback.onNewStroke(mCurrentStroke); // 通知 Activity 新笔画已完成
                    }
                }
                mCurrentPath = null;
                mCurrentStroke = null;
                break;
        }
        return true;
    }

    @Override
    public void run() {
        while (mIsDrawing) {
            Canvas canvas = null;
            try {
                canvas = mHolder.lockCanvas();
                if (canvas != null) {
                    // 1. 清空屏幕
                    canvas.drawColor(Color.WHITE);
                    // 2. 将之前保存的笔画绘制到 Bitmap 上
                    drawStrokesToBitmap();
                    // 3. 将 Bitmap 绘制到主画布
                    canvas.drawBitmap(mBitmap, 0, 0, null);
                    // 4. 绘制当前正在画的路径
                    drawCurrentPath(canvas);
                }
            } finally {
                if (canvas != null) {
                    mHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    private void drawStrokesToBitmap() {
        if (mBitmap == null) {
            mBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            mBitmapCanvas = new Canvas(mBitmap);
        }
        // 清空 Bitmap 画布
        mBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        for (Stroke stroke : mStrokes) {
            Paint paintToUse = stroke.isEraser ? mEraserPaint : mPaint;

            // [修改] 只在不是橡皮擦模式时才设置颜色
            if (!stroke.isEraser) {
                paintToUse.setColor(stroke.color);
            }

            Path path = new Path();
            List<Point> points = stroke.points;
            if (points.size() > 1) { // 至少需要两个点才能形成路径
                path.moveTo(points.get(0).x, points.get(0).y);
                // 从第二个点开始循环
                for (int i = 1; i < points.size(); i++) {
                    Point p1 = points.get(i - 1);
                    Point p2 = points.get(i);

                    // 设置每个点的笔触宽度
                    paintToUse.setStrokeWidth(p2.pressure * stroke.baseStrokeWidth);

                    // 使用贝塞尔曲线使线条平滑
                    path.quadTo(p1.x, p1.y, (p1.x + p2.x) / 2, (p1.y + p2.y) / 2);

                    // 在每个小段上绘制，以体现压力变化
                    mBitmapCanvas.drawPath(path, paintToUse);

                    // 为下一次绘制重置路径起点
                    path.moveTo((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
                }
            }
        }
    }

    private void drawCurrentPath(Canvas canvas) {
        if (mCurrentPath != null && mCurrentStroke != null) {
            Paint paintToUse = mIsEraserMode ? mEraserPaint : mPaint;
            paintToUse.setColor(mCurrentColor);
            // 根据最后一点的压力值设置笔触宽度
            if (!mCurrentStroke.points.isEmpty()){
                float lastPressure = mCurrentStroke.points.get(mCurrentStroke.points.size()-1).pressure;
                paintToUse.setStrokeWidth(lastPressure * mCurrentBaseStrokeWidth);
            } else {
                paintToUse.setStrokeWidth(mCurrentBaseStrokeWidth);
            }
            canvas.drawPath(mCurrentPath, paintToUse);
        }
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        mIsDrawing = true;
        mDrawThread = new Thread(this);
        mDrawThread.start();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // 如果尺寸变化，需要重建 Bitmap
        if (mBitmap != null) {
            mBitmap.recycle();
        }
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mBitmapCanvas = new Canvas(mBitmap);
        // 重绘所有
        drawStrokesToBitmap();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        mIsDrawing = false;
        try {
            mDrawThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // --- 公共控制方法 ---

    public void clearCanvas() {
        mStrokes.clear();
        if (mBitmap != null) {
            mBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
    }

    public void setPenColor(int color) {
        this.mCurrentColor = color;
    }

    public void setStrokeWidth(float width) {
        this.mCurrentBaseStrokeWidth = width;
    }

    public void setEraserMode(boolean isEraser) {
        this.mIsEraserMode = isEraser;
    }

    public List<Stroke> getStrokes() {
        return this.mStrokes;
    }

    public void setStrokes(List<Stroke> strokes) {
        this.mStrokes.clear();
        this.mStrokes.addAll(strokes);
        // 强制重绘
        drawStrokesToBitmap();
    }
}