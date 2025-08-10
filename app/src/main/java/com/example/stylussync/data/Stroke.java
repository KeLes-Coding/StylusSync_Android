package com.example.stylussync.data;

import java.util.ArrayList;
import java.util.List;

public class Stroke {
    public List<Point> points;
    public int color;
    public float baseStrokeWidth;
    // 可选：增加一个字段判断是否为橡皮擦笔画
    public boolean isEraser;

    public Stroke(int color, float baseStrokeWidth, boolean isEraser) {
        this.points = new ArrayList<>();
        this.color = color;
        this.baseStrokeWidth = baseStrokeWidth;
        this.isEraser = isEraser;
    }

    public void addPoint(Point point) {
        this.points.add(point);
    }
}