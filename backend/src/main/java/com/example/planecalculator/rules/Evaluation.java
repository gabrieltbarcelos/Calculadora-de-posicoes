package com.example.planecalculator.rules;

import com.example.planecalculator.dto.PlanningResponse.BoneClearance;
import com.example.planecalculator.dto.PlanningResponse.ConstraintResult;
import com.example.planecalculator.dto.PlanningResponse.InterportalDistance;
import com.example.planecalculator.dto.PlanningResponse.WorkspaceDistance;

import java.util.List;

public record Evaluation(
        List<ConstraintResult> constraints,
        List<String> warnings,
        List<InterportalDistance> interportalDistances,
        double cameraTargetDistance,
        List<BoneClearance> boneClearances,
        List<WorkspaceDistance> workspaceDistances,
        boolean hardRulesPassed,
        int failedHardCount,
        double minAvoidDistance
) {}
