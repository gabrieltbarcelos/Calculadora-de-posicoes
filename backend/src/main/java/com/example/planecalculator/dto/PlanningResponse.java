package com.example.planecalculator.dto;

import com.example.planecalculator.geometry.Point2;
import com.example.planecalculator.planning.PortRole;

import java.util.List;

public record PlanningResponse(
        Point2 target,
        List<Point2> abdomenPolygon,
        List<Point2> defectPolygon,
        List<Point2> workspacePolygon,
        String techniqueVariant,
        String accessSide,
        String suggestedDocking,
        String suggestedCartPosition,
        boolean doubleDockingRecommended,
        boolean redockingAllowed,
        boolean configurationValid,
        boolean generatedByPreset,
        List<PortDto> ports,
        AdmissibleRange admissibleRange,
        List<InterportalDistance> interportalDistances,
        double cameraTargetDistance,
        List<BoneClearance> boneClearances,
        List<WorkspaceDistance> workspaceDistances,
        List<ConstraintResult> constraints,
        List<String> warnings,
        List<String> rationale
) {
    public record PortDto(PortRole role, String label, double x, double y) {}

    public record AdmissibleRange(
            double offsetMinCm,
            double offsetMaxCm,
            double recommendedOffsetCm,
            double spacingCm,
            String side,
            List<Point2> bandPolygon
    ) {}

    public record InterportalDistance(String fromRole, String toRole, double distanceCm) {}

    public record BoneClearance(String portRole, String bone, double distanceCm) {}

    public record WorkspaceDistance(String portRole, String sample, double distanceCm) {}

    public record ConstraintResult(String id, String description, boolean passed, String detail) {}
}
