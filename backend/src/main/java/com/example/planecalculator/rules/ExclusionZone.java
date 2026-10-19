package com.example.planecalculator.rules;

import com.example.planecalculator.geometry.Point2;
import com.example.planecalculator.planning.ExclusionCategory;

import java.util.List;

public record ExclusionZone(ExclusionCategory category, List<Point2> polygon) {}
