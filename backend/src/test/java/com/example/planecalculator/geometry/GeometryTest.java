package com.example.planecalculator.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryTest {

    @Test
    void pointInPolygonIncludesInteriorAndBoundary() {
        List<Point2> square = square();
        assertTrue(Geometry.pointInPolygon(new Point2(5, 5), square));
        assertTrue(Geometry.pointInPolygon(new Point2(0, 5), square));
        assertFalse(Geometry.pointInPolygon(new Point2(11, 5), square));
    }

    @Test
    void centroidOfRectangleIsTheCenter() {
        List<Point2> rectangle = Geometry.rectangle(new Point2(2, -3), 4, 6);
        Point2 centroid = Geometry.centroid(rectangle);
        assertEquals(2, centroid.x(), 1e-6);
        assertEquals(-3, centroid.y(), 1e-6);
    }

    @Test
    void quadrantCountFollowsTheUmbilicalAxes() {
        assertEquals(1, Geometry.quadrantCount(Geometry.rectangle(new Point2(4, 4), 2, 2)));
        assertEquals(2, Geometry.quadrantCount(Geometry.rectangle(new Point2(0, 4), 4, 2)));
        assertEquals(4, Geometry.quadrantCount(Geometry.rectangle(new Point2(0, 0), 4, 4)));
    }

    @Test
    void blocksSegmentIgnoresEndpoints() {
        Point2 start = new Point2(0, 0);
        Point2 end = new Point2(10, 0);
        assertTrue(Geometry.blocksSegment(new Point2(5, 0.4), start, end, 1));
        assertFalse(Geometry.blocksSegment(new Point2(0, 0), start, end, 1));
        assertFalse(Geometry.blocksSegment(new Point2(5, 2), start, end, 1));
    }

    private static List<Point2> square() {
        return List.of(new Point2(0, 0), new Point2(10, 0), new Point2(10, 10), new Point2(0, 10));
    }
}
