package com.example.planecalculator.dto;

import jakarta.validation.constraints.NotBlank;

public record CalculationInput(
        @NotBlank String name,
        double x,
        double y
) {}
