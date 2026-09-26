package com.example.planecalculator.service;

import com.example.planecalculator.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CalculationService {

    public CalculationResponse calculate(CalculationRequest request) {
        // TODO: Substitua esta fórmula pela regra real do seu sistema.
        // Por enquanto, apenas aplica uma pequena transformação para
        // demonstrar o fluxo Frontend -> Backend -> gráfico.
        List<PointDto> points = request.inputs().stream()
                .map(input -> new PointDto(
                        input.name(),
                        input.x() * 1.0,
                        input.y() * 1.0
                ))
                .toList();

        return new CalculationResponse(points);
    }
}
