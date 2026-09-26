package com.example.planecalculator.controller;

import com.example.planecalculator.dto.CalculationRequest;
import com.example.planecalculator.dto.CalculationResponse;
import com.example.planecalculator.service.CalculationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calculations")
public class CalculationController {

    private final CalculationService calculationService;

    public CalculationController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @PostMapping
    public CalculationResponse calculate(@Valid @RequestBody CalculationRequest request) {
        return calculationService.calculate(request);
    }
}
