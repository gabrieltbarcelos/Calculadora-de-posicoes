package com.example.planecalculator.dto;

import com.example.planecalculator.geometry.Point2;
import com.example.planecalculator.planning.AccessSide;
import com.example.planecalculator.planning.ExclusionCategory;
import com.example.planecalculator.planning.ExclusionType;
import com.example.planecalculator.planning.MeasurementPhase;
import com.example.planecalculator.planning.PortRole;
import com.example.planecalculator.planning.RobotPlatform;
import com.example.planecalculator.planning.SurgicalTechnique;
import com.example.planecalculator.planning.WorkspaceMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlanningRequest(
        PatientInput patient,
        @NotNull @Valid AnatomyInput anatomy,
        @NotNull @Valid DefectInput defect,
        @NotNull @Valid WorkspaceInput workspace,
        List<@Valid ExclusionInput> exclusionZones,
        @NotNull @Valid ProcedureInput procedure,
        List<@Valid PortInput> ports
) {
    public record PatientInput(
            Double heightCm,
            Double weightKg,
            Double bmi,
            Double abdominalCircumferenceCm
    ) {}

    public record AnatomyInput(
            Point2 xiphoid,
            Point2 pubis,
            Point2 asisRight,
            Point2 asisLeft,
            Point2 costalRight,
            Point2 costalLeft,
            Point2 flankRight,
            Point2 flankLeft,
            Point2 rectusRight,
            Point2 rectusLeft
    ) {}

    public record DefectInput(
            @NotNull Point2 center,
            double widthCm,
            double heightCm,
            Integer numberOfDefects,
            String ehsLocation
    ) {}

    public record WorkspaceInput(
            @NotNull WorkspaceMode mode,
            List<Point2> polygon
    ) {}

    public record ExclusionInput(
            String id,
            @NotNull ExclusionType type,
            @NotNull ExclusionCategory category,
            @NotNull List<Point2> polygon
    ) {}

    public record ProcedureInput(
            RobotPlatform platform,
            @NotNull SurgicalTechnique technique,
            @NotNull AccessSide accessSide,
            int numberOfRoboticPorts,
            boolean assistantPortRequired,
            boolean bilateralTar,
            MeasurementPhase measurementPhase,
            Double pneumoperitoneumMmhg,
            Double tableFlexionDeg,
            String patientPosition
    ) {}

    public record PortInput(
            @NotNull PortRole role,
            double x,
            double y
    ) {}
}
