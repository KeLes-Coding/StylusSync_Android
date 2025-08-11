// 替换文件：app/src/main/java/com/example/stylussync/view/DrawingSurfaceView.java

package com.example.stylussync.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.NonNull;

import com.example.stylussync.data.Point;
import com.example.stylussync.data.Stroke;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class DrawingSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder mHolder;
    private Thread mDrawThread;
    private boolean mIsDrawing = false;

    // --- 画布与画笔 ---
    private Bitmap mBitmap;
    private Canvas mBitmapCanvas;
    private final Paint mPaint;
    private final Paint mEraserPaint;
    private final Paint mHoverPaint; // 【新增】用于绘制悬停预览光标的画笔

    // --- 笔画数据与历史记录 ---
    private final Deque<Stroke> mUndoStack = new ArrayDeque<>();
    private final Deque<Stroke> mRedoStack = new ArrayDeque<>();
    private Stroke mCurrentStroke;

    // --- 状态 ---
    private int mCurrentColor = Color.BLACK;
    private float mCurrentBaseStrokeWidth = 10f;
    private boolean mIsEraserMode = false;
    private boolean mIsHovering = false; // 【新增】标记触摸笔是否正在悬停
    private float mHoverX, mHoverY;      // 【新增】悬停的坐标

    // --- 回调 ---
    public interface DrawingCallback {
        void onNewStroke(Stroke stroke);
        void onHistoryChanged(boolean canUndo, boolean canRedo);
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
        mEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // 【新增】初始化悬停光标的画笔
        mHoverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    private void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    /**
     * 【核心新增】处理悬停事件，用于显示预览光标
     */
    @Override
    public boolean onHoverEvent(MotionEvent event) {
        // 只响应触摸笔的悬停事件
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return super.onHoverEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                mIsHovering = true;
                mHoverX = event.getX();
                mHoverY = event.getY();
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                mIsHovering = false;
                break;
        }
        return true;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return super.onTouchEvent(event);
        }

        // 当触摸笔接触屏幕时，应隐藏悬停光标
        mIsHovering = false;

        float x = event.getX();
        float y = event.getY();
        float pressure = event.getPressure();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentStroke = new Stroke(mCurrentColor, mCurrentBaseStrokeWidth, mIsEraserMode);
                mCurrentStroke.addPoint(new Point(x, y, pressure));
                mRedoStack.clear();
                updateHistoryState();
                break;

            case MotionEvent.ACTION_MOVE:
                if (mCurrentStroke != null && !mCurrentStroke.points.isEmpty()) {
                    Point lastPoint = mCurrentStroke.points.get(mCurrentStroke.points.size() - 1);
                    Point newPoint = new Point(x, y, pressure);
                    drawSegment(lastPoint, newPoint, mCurrentStroke);
                    mCurrentStroke.addPoint(newPoint);
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mCurrentStroke != null) {
                    mUndoStack.push(mCurrentStroke);
                    if (mCallback != null) {
                        mCallback.onNewStroke(mCurrentStroke);
                    }
                    updateHistoryState();
                }
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
                    // 1. 绘制背景和已完成的笔画
                    canvas.drawColor(Color.WHITE);
                    if (mBitmap != null) {
                        canvas.drawBitmap(mBitmap, 0, 0, null);
                    }
                    // 2. 【核心修改】如果正在悬停，则绘制预览光标
                    if (mIsHovering) {
                        drawHoverPreview(canvas);
                    }
                }
            } finally {
                if (canvas != null) {
                    mHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    /**
     * 【核心新增】根据当前模式（画笔/橡皮擦）绘制不同的悬停预览光标
     */
    private void drawHoverPreview(Canvas canvas) {
        if (mIsEraserMode) {
            // 橡皮擦模式：绘制一个半透明的灰色圆圈，代表擦除区域
            float radius = mCurrentBaseStrokeWidth / 2;
            mHoverPaint.setColor(0x80888888); // 半透明灰色
            mHoverPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(mHoverX, mHoverY, radius, mHoverPaint);
        } else {
            // 画笔模式：绘制一个与笔画颜色和大小一致的实心圆点
            float radius = mCurrentBaseStrokeWidth / 2;
            mHoverPaint.setColor(mCurrentColor);
            mHoverPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(mHoverX, mHoverY, radius, mHoverPaint);
        }
    }

    private void drawSegment(Point p1, Point p2, Stroke stroke) {
        if (mBitmapCanvas == null) return;

        Paint paintToUse = stroke.isEraser ? mEraserPaint : mPaint;
        if (!stroke.isEraser) {
            paintToUse.setColor(stroke.color);
        }

        // 【核心修复】判断当前是在实时绘制还是在重绘
        float baseWidth;
        if (stroke == mCurrentStroke) {
            // 如果是正在绘制的当前笔画，直接使用最新的宽度值
            baseWidth = this.mCurrentBaseStrokeWidth;
        } else {
            // 如果是重绘（来自撤销栈），使用该笔画被保存时的宽度
            baseWidth = stroke.baseStrokeWidth;
        }

        float avgPressure = (p1.pressure + p2.pressure) / 2;
        // 使用正确的 baseWidth 来计算最终宽度
        float strokeWidth = Math.max(1, avgPressure * baseWidth);
        paintToUse.setStrokeWidth(strokeWidth);

        mBitmapCanvas.drawLine(p1.x, p1.y, p2.x, p2.y, paintToUse);
    }

    private void commitStrokeToBitmap(Stroke stroke) {
        if (mBitmapCanvas == null || stroke == null || stroke.points == null || stroke.points.size() < 2) {
            return;
        }
        List<Point> points = stroke.points;
        for (int i = 1; i < points.size(); i++) {
            drawSegment(points.get(i - 1), points.get(i), stroke);
        }
    }

    private void redrawAllStrokes() {
        if (mBitmapCanvas != null) {
            mBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            for (Stroke stroke : new ArrayList<>(mUndoStack)) {
                commitStrokeToBitmap(stroke);
            }
        }
    }

    // --- 撤销/重做/历史记录管理 ---
    public void undo() {
        if (!mUndoStack.isEmpty()) {
            mRedoStack.push(mUndoStack.pop());
            redrawAllStrokes();
            updateHistoryState();
        }
    }

    public void redo() {
        if (!mRedoStack.isEmpty()) {
            Stroke redoneStroke = mRedoStack.pop();
            mUndoStack.push(redoneStroke);
            commitStrokeToBitmap(redoneStroke);
            updateHistoryState();
        }
    }

    private void updateHistoryState() {
        if (mCallback != null) {
            mCallback.onHistoryChanged(!mUndoStack.isEmpty(), !mRedoStack.isEmpty());
        }
    }

    // --- SurfaceView 生命周期方法 ---
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        mIsDrawing = true;
        mDrawThread = new Thread(this);
        mDrawThread.start();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (mBitmap != null) {
            mBitmap.recycle();
        }
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mBitmapCanvas = new Canvas(mBitmap);
        redrawAllStrokes();
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
    public void setCallback(DrawingCallback callback) {
        this.mCallback = callback;
    }

    public void clearCanvas() {
        mUndoStack.clear();
        mRedoStack.clear();
        if (mBitmapCanvas != null) {
            mBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }
        updateHistoryState();
    }

    public void setPenColor(int color) {
        this.mCurrentColor = color;
        this.mIsEraserMode = false;
    }

    public void setStrokeWidth(float width) {
        this.mCurrentBaseStrokeWidth = width;
    }

    public void setEraserMode(boolean isEraser) {
        this.mIsEraserMode = isEraser;
    }

    public List<Stroke> getStrokes() {
        return new ArrayList<>(mUndoStack);
    }

    public void setStrokes(List<Stroke> strokes) {
        mUndoStack.clear();
        mRedoStack.clear();
        if (strokes != null) {
            mUndoStack.addAll(strokes);
        }
        redrawAllStrokes();
        updateHistoryState();
    }
}