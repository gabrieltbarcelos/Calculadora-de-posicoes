package com.example.planecalculator.controller;

import com.example.planecalculator.dto.PlanningRequest;
import com.example.planecalculator.dto.PlanningResponse;
import com.example.planecalculator.planning.ProtocolParameters;
import com.example.planecalculator.service.PlanningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @PostMapping("/planning")
    public PlanningResponse suggest(@Valid @RequestBody PlanningRequest request) {
        return planningService.suggest(request);
    }

    @PostMapping("/planning/validate")
    public PlanningResponse validate(@Valid @RequestBody PlanningRequest request) {
        return planningService.validate(request);
    }

    @GetMapping("/protocol")
    public ProtocolParameters protocol() {
        return planningService.protocol();
    }

    @PutMapping("/protocol")
    public ProtocolParameters updateProtocol(@RequestBody ProtocolParameters protocol) {
        return planningService.updateProtocol(protocol);
    }
}
