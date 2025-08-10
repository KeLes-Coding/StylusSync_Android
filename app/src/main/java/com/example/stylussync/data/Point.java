package com.example.stylussync.data;

public class Point {
    public float x;
    public float y;
    // 文档中简写为 p，这里为了清晰使用 pressure
    public float pressure;

    public Point(float x, float y, float pressure) {
        this.x = x;
        this.y = y;
        this.pressure = pressure;
    }
}