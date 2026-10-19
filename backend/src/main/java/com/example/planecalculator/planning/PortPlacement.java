package com.example.planecalculator.planning;

import com.example.planecalculator.geometry.Point2;

public record PortPlacement(PortRole role, double x, double y) {
    public Point2 point() {
        return new Point2(x, y);
    }
}
