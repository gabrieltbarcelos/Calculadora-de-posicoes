package com.example.planecalculator.service;

import com.example.planecalculator.dto.PlanningRequest;
import com.example.planecalculator.dto.PlanningResponse;
import com.example.planecalculator.planning.PresetPlanner;
import com.example.planecalculator.planning.ProtocolParameters;
import com.example.planecalculator.planning.ProtocolService;
import org.springframework.stereotype.Service;

@Service
public class PlanningService {

    private final ProtocolService protocolService;
    private final PresetPlanner planner;

    public PlanningService(ProtocolService protocolService, PresetPlanner planner) {
        this.protocolService = protocolService;
        this.planner = planner;
    }

    public PlanningResponse suggest(PlanningRequest request) {
        return planner.suggest(request, protocolService.current());
    }

    public PlanningResponse validate(PlanningRequest request) {
        return planner.validate(request, protocolService.current());
    }

    public ProtocolParameters protocol() {
        return protocolService.current();
    }

    public ProtocolParameters updateProtocol(ProtocolParameters protocol) {
        return protocolService.update(protocol);
    }
}
