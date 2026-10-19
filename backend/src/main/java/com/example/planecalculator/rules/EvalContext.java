package com.example.planecalculator.rules;

import com.example.planecalculator.geometry.Point2;
import com.example.planecalculator.planning.ExclusionCategory;
import com.example.planecalculator.planning.PortPlacement;
import com.example.planecalculator.planning.ProtocolParameters;

import java.util.List;

public record EvalContext(
        List<Point2> abdomen,
        List<NamedPoint> bones,
        List<ExclusionZone> exclusions,
        List<Point2> workspace,
        Point2 target,
        List<PortPlacement> ports,
        ProtocolParameters protocol,
        boolean assistantRequired,
        int quadrantCount
) {}
