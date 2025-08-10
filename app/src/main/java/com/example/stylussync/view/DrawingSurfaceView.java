// 替换文件：app/src/main/java/com/example/stylussync/view/DrawingSurfaceView.java

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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class DrawingSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder mHolder;
    private Thread mDrawThread;
    private boolean mIsDrawing = false;

    private Bitmap mBitmap;
    private Canvas mBitmapCanvas;
    private final Paint mPaint;
    private final Paint mEraserPaint;
    private Path mCurrentPath;

    private final Deque<Stroke> mUndoStack = new ArrayDeque<>();
    private final Deque<Stroke> mRedoStack = new ArrayDeque<>();
    private Stroke mCurrentStroke;

    private int mCurrentColor = Color.BLACK;
    private float mCurrentBaseStrokeWidth = 10f;
    private boolean mIsEraserMode = false;

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
    }

    private void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return super.onTouchEvent(event);
        }

        // 检查橡皮擦按钮，切换模式
        setEraserMode((event.getButtonState() & MotionEvent.BUTTON_STYLUS_SECONDARY) != 0);

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
                mRedoStack.clear();
                updateHistoryState();
                break;

            case MotionEvent.ACTION_MOVE:
                if (mCurrentPath != null && mCurrentStroke != null) {
                    Point lastPoint = mCurrentStroke.points.get(mCurrentStroke.points.size() - 1);
                    mCurrentPath.quadTo(lastPoint.x, lastPoint.y, (x + lastPoint.x) / 2, (y + lastPoint.y) / 2);
                    mCurrentStroke.addPoint(point);
                }
                break;

            case MotionEvent.ACTION_UP:
                if (mCurrentPath != null && mCurrentStroke != null) {
                    mCurrentStroke.addPoint(point);
                    mUndoStack.push(mCurrentStroke);
                    commitStrokeToBitmap(mCurrentStroke); // 将完成的笔画固化到位图
                    if (mCallback != null) {
                        mCallback.onNewStroke(mCurrentStroke);
                    }
                    updateHistoryState();
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
                    canvas.drawColor(Color.WHITE);
                    if (mBitmap != null) {
                        canvas.drawBitmap(mBitmap, 0, 0, null);
                    }
                    drawCurrentPath(canvas);
                }
            } finally {
                if (canvas != null) {
                    mHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    // [最终修复] 提交笔画到位图的核心逻辑
    private void commitStrokeToBitmap(Stroke stroke) {
        if (mBitmapCanvas == null || stroke == null || stroke.points == null || stroke.points.size() < 2) {
            return;
        }

        // 1. 根据是否为橡皮擦模式选择画笔
        Paint paintToUse = stroke.isEraser ? mEraserPaint : mPaint;
        if (!stroke.isEraser) {
            paintToUse.setColor(stroke.color);
        }

        List<Point> points = stroke.points;

        // 2. 迭代笔画中的所有点，分段绘制
        // 这种分段绘制并动态调整宽度的经典方法，能同时保证笔画连续和压感有效
        for (int i = 1; i < points.size(); i++) {
            Point p1 = points.get(i - 1);
            Point p2 = points.get(i);

            // 3. 根据压力计算笔画宽度
            paintToUse.setStrokeWidth(p1.pressure * stroke.baseStrokeWidth);

            // 4. 绘制从 p1 到 p2 的一小段路径
            mBitmapCanvas.drawLine(p1.x, p1.y, p2.x, p2.y, paintToUse);
        }
    }


    private void drawCurrentPath(Canvas canvas) {
        if (mCurrentPath != null && mCurrentStroke != null) {
            Paint paintToUse = mIsEraserMode ? mEraserPaint : mPaint;
            if (!mIsEraserMode) {
                paintToUse.setColor(mCurrentColor);
            }
            // 实时预览时使用固定的基础宽度，以保证性能
            paintToUse.setStrokeWidth(mCurrentBaseStrokeWidth);
            canvas.drawPath(mCurrentPath, paintToUse);
        }
    }

    // 重绘所有笔画（用于撤销或加载文件）
    private void redrawAllStrokes() {
        if (mBitmapCanvas != null) {
            // 清空画布
            mBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            // 重新绘制所有在撤销栈中的笔画
            // 必须从栈底向上（FIFO）绘制，才能保证笔画叠加顺序正确
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
            commitStrokeToBitmap(redoneStroke); // 重做时只需将笔画绘制在最上层
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
        this.mIsEraserMode = false; // 选择颜色时，自动退出橡皮擦模式
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