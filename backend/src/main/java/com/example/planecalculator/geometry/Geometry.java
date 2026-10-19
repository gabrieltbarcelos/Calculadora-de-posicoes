package com.example.planecalculator.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Geometry {

    private static final double EPS = 1e-6;

    private Geometry() {}

    public static double distance(Point2 a, Point2 b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    public static Point2 centroid(List<Point2> polygon) {
        if (polygon.isEmpty()) {
            return new Point2(0, 0);
        }
        double area = signedArea(polygon);
        if (Math.abs(area) < EPS) {
            double x = 0;
            double y = 0;
            for (Point2 point : polygon) {
                x += point.x();
                y += point.y();
            }
            return new Point2(x / polygon.size(), y / polygon.size());
        }
        double cx = 0;
        double cy = 0;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Point2 a = polygon.get(i);
            Point2 b = polygon.get((i + 1) % n);
            double cross = a.x() * b.y() - b.x() * a.y();
            cx += (a.x() + b.x()) * cross;
            cy += (a.y() + b.y()) * cross;
        }
        double factor = 1 / (6 * area);
        return new Point2(cx * factor, cy * factor);
    }

    public static double polygonArea(List<Point2> polygon) {
        return Math.abs(signedArea(polygon));
    }

    public static boolean pointInPolygon(Point2 point, List<Point2> polygon) {
        if (polygon.size() < 3) {
            return false;
        }
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            if (onSegment(point, polygon.get(i), polygon.get((i + 1) % n), 1e-6)) {
                return true;
            }
        }
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point2 a = polygon.get(i);
            Point2 b = polygon.get(j);
            boolean crosses = (a.y() > point.y()) != (b.y() > point.y());
            if (!crosses || Math.abs(b.y() - a.y()) < EPS) {
                continue;
            }
            double xCross = (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x();
            if (point.x() < xCross) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static double distanceToSegment(Point2 point, Point2 a, Point2 b) {
        double abx = b.x() - a.x();
        double aby = b.y() - a.y();
        double len2 = abx * abx + aby * aby;
        if (len2 < EPS) {
            return distance(point, a);
        }
        double t = ((point.x() - a.x()) * abx + (point.y() - a.y()) * aby) / len2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(point.x() - (a.x() + t * abx), point.y() - (a.y() + t * aby));
    }

    public static double distanceToPolygon(Point2 point, List<Point2> polygon) {
        if (pointInPolygon(point, polygon)) {
            return 0;
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < polygon.size(); i++) {
            Point2 a = polygon.get(i);
            Point2 b = polygon.get((i + 1) % polygon.size());
            best = Math.min(best, distanceToSegment(point, a, b));
        }
        return best;
    }

    /**
     * True when {@code point} sits strictly between {@code a} and {@code b}, within {@code tolerance} of the segment.
     * Endpoints themselves do not count.
     */
    public static boolean blocksSegment(Point2 point, Point2 a, Point2 b, double tolerance) {
        double abx = b.x() - a.x();
        double aby = b.y() - a.y();
        double len2 = abx * abx + aby * aby;
        if (len2 < EPS) {
            return false;
        }
        double t = ((point.x() - a.x()) * abx + (point.y() - a.y()) * aby) / len2;
        if (t <= 0.02 || t >= 0.98) {
            return false;
        }
        double closestX = a.x() + t * abx;
        double closestY = a.y() + t * aby;
        return Math.hypot(point.x() - closestX, point.y() - closestY) <= tolerance;
    }

    /**
     * 4 when the polygon crosses both axes of the umbilicus, 2 when it crosses only one, otherwise 1.
     */
    public static int quadrantCount(List<Point2> polygon) {
        double axis = 0.05;
        boolean positiveX = false;
        boolean negativeX = false;
        boolean positiveY = false;
        boolean negativeY = false;
        for (Point2 point : polygon) {
            if (point.x() > axis) {
                positiveX = true;
            }
            if (point.x() < -axis) {
                negativeX = true;
            }
            if (point.y() > axis) {
                positiveY = true;
            }
            if (point.y() < -axis) {
                negativeY = true;
            }
        }
        boolean crossesX = positiveX && negativeX;
        boolean crossesY = positiveY && negativeY;
        if (crossesX && crossesY) {
            return 4;
        }
        if (crossesX || crossesY) {
            return 2;
        }
        return 1;
    }

    public static boolean onTargetSide(Point2 sample, Point2 lineStart, Point2 lineEnd, Point2 target) {
        double side = cross(lineStart, lineEnd, target);
        if (Math.abs(side) < 0.05) {
            return false;
        }
        double sampleSide = cross(lineStart, lineEnd, sample);
        if (Math.abs(sampleSide) <= 0.2) {
            return true;
        }
        return Math.signum(sampleSide) == Math.signum(side);
    }

    public static List<Point2> rectangle(Point2 center, double width, double height) {
        double halfW = width / 2;
        double halfH = height / 2;
        return List.of(
                new Point2(center.x() - halfW, center.y() - halfH),
                new Point2(center.x() + halfW, center.y() - halfH),
                new Point2(center.x() + halfW, center.y() + halfH),
                new Point2(center.x() - halfW, center.y() + halfH)
        );
    }

    public static List<Point2> convexHull(List<Point2> points) {
        List<Point2> sorted = points.stream()
                .map(point -> new Point2(round(point.x()), round(point.y())))
                .distinct()
                .sorted(Comparator.comparingDouble(Point2::x).thenComparingDouble(Point2::y))
                .toList();
        if (sorted.size() <= 2) {
            return sorted;
        }
        List<Point2> lower = new ArrayList<>();
        for (Point2 point : sorted) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }
        List<Point2> upper = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Point2 point = sorted.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    public static List<Point2> bufferSegment(Point2 a, Point2 b, double halfWidth) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double len = Math.hypot(dx, dy);
        if (len < EPS) {
            return rectangle(a, halfWidth * 2, halfWidth * 2);
        }
        double nx = -dy / len * halfWidth;
        double ny = dx / len * halfWidth;
        return List.of(
                new Point2(a.x() + nx, a.y() + ny),
                new Point2(b.x() + nx, b.y() + ny),
                new Point2(b.x() - nx, b.y() - ny),
                new Point2(a.x() - nx, a.y() - ny)
        );
    }

    public static List<Double> steps(double min, double max, double step) {
        List<Double> values = new ArrayList<>();
        if (step <= 0 || max < min) {
            return values;
        }
        int guard = 0;
        for (double value = min; value <= max + 1e-9 && guard < 500; value += step, guard++) {
            values.add(round(value));
        }
        if (values.isEmpty()) {
            values.add(round(min));
        }
        return values;
    }

    public static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    private static double signedArea(List<Point2> polygon) {
        double area = 0;
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Point2 a = polygon.get(i);
            Point2 b = polygon.get((i + 1) % n);
            area += a.x() * b.y() - b.x() * a.y();
        }
        return area / 2;
    }

    private static double cross(Point2 origin, Point2 a, Point2 b) {
        return (a.x() - origin.x()) * (b.y() - origin.y()) - (a.y() - origin.y()) * (b.x() - origin.x());
    }

    private static boolean onSegment(Point2 point, Point2 a, Point2 b, double tolerance) {
        if (distanceToSegment(point, a, b) > tolerance) {
            return false;
        }
        double abx = b.x() - a.x();
        double aby = b.y() - a.y();
        double len2 = abx * abx + aby * aby;
        if (len2 < EPS) {
            return distance(point, a) <= tolerance;
        }
        double t = ((point.x() - a.x()) * abx + (point.y() - a.y()) * aby) / len2;
        return t >= -0.01 && t <= 1.01;
    }
}
