package com.example.planecalculator.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ProtocolService {

    private final ObjectMapper mapper;
    private final Path overridePath;
    private ProtocolParameters current;

    @Autowired
    public ProtocolService(ObjectMapper mapper) {
        this(mapper, Path.of("data", "protocol-v0.1.json"));
    }

    ProtocolService(ObjectMapper mapper, Path overridePath) {
        this.mapper = mapper;
        this.overridePath = overridePath;
        this.current = readDefaults();
        if (Files.exists(overridePath)) {
            try {
                current = mapper.readValue(overridePath.toFile(), ProtocolParameters.class);
            } catch (IOException exception) {
                current = readDefaults();
            }
        }
    }

    public synchronized ProtocolParameters current() {
        return current;
    }

    public synchronized ProtocolParameters update(ProtocolParameters next) {
        validate(next);
        try {
            if (overridePath.getParent() != null) {
                Files.createDirectories(overridePath.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(overridePath.toFile(), next);
        } catch (IOException exception) {
            throw new PlanningException("Não foi possível gravar o protocolo.");
        }
        current = next;
        return current;
    }

    private ProtocolParameters readDefaults() {
        try (InputStream input = new ClassPathResource("protocol-v0.1.json").getInputStream()) {
            return mapper.readValue(input, ProtocolParameters.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Protocolo padrão ausente.", exception);
        }
    }

    static void validate(ProtocolParameters protocol) {
        if (protocol.samplingStepCm() < 0.1 || protocol.samplingStepCm() > 2) {
            throw new PlanningException("O passo de amostragem deve ficar entre 0,1 e 2 cm.");
        }
        if (protocol.interportalManufacturerMinCm() <= 0
                || protocol.interportalMinCm() < protocol.interportalManufacturerMinCm()
                || protocol.interportalIdealCm() < protocol.interportalMinCm()
                || protocol.interportalIdealCm() > protocol.interportalManufacturerMaxCm()
                || protocol.interportalManufacturerMaxCm() < protocol.interportalMinCm()) {
            throw new PlanningException("A faixa interportal do protocolo é inconsistente.");
        }
        if (protocol.cameraTargetMinCm() <= 0
                || protocol.cameraTargetMinCm() >= protocol.cameraTargetMaxCm()
                || protocol.cameraTargetPreferredMinCm() < protocol.cameraTargetMinCm()
                || protocol.cameraTargetPreferredMaxCm() > protocol.cameraTargetMaxCm()
                || protocol.cameraTargetPreferredMinCm() > protocol.cameraTargetPreferredMaxCm()) {
            throw new PlanningException("A faixa da câmera ao target é inconsistente.");
        }
        if (protocol.tappPortToTargetMinCm() <= 0
                || protocol.tappPortToTargetMinCm() > protocol.tappPortToTargetMaxCm()) {
            throw new PlanningException("A faixa do TAPP ao target é inconsistente.");
        }
        if (protocol.boneClearanceMinCm() <= 0 || protocol.assistantToRoboticMinCm() <= 0) {
            throw new PlanningException("As folgas mínimas devem ser positivas.");
        }
        if (protocol.meshOverlapCm() < 0 || protocol.collinearityToleranceCm() < 0) {
            throw new PlanningException("Sobreposição e tolerância de colinearidade não podem ser negativas.");
        }
        if (protocol.etepMidlineLateralMinCm() <= 0
                || protocol.etepMidlineLateralMinCm() > protocol.etepMidlineLateralMaxCm()) {
            throw new PlanningException("A faixa lateral do eTEP é inconsistente.");
        }
        if (protocol.targetCranialThresholdCm() <= protocol.targetCaudalThresholdCm()) {
            throw new PlanningException("O limiar cranial do target precisa ser maior que o caudal.");
        }
    }
}
