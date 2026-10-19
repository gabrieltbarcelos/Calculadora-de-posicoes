package com.example.planecalculator.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void savesAnOverrideAndRejectsAnInconsistentProtocol() throws Exception {
        ProtocolParameters defaults = mapper.readValue(
                new ClassPathResource("protocol-v0.1.json").getInputStream(),
                ProtocolParameters.class
        );
        assertEquals(8, defaults.interportalIdealCm());

        Map<String, Object> values = mapper.convertValue(defaults, new TypeReference<>() {});
        values.put("samplingStepCm", 0);
        ProtocolParameters invalid = mapper.convertValue(values, ProtocolParameters.class);
        assertThrows(PlanningException.class, () -> ProtocolService.validate(invalid));

        values.put("samplingStepCm", 0.5);
        values.put("interportalIdealCm", 8.5);
        ProtocolParameters tweaked = mapper.convertValue(values, ProtocolParameters.class);
        Path file = Files.createTempDirectory("surgiaxis-protocol").resolve("protocol-v0.1.json");
        ProtocolService service = new ProtocolService(mapper, file);
        assertEquals(8.5, service.update(tweaked).interportalIdealCm());
        assertTrue(Files.exists(file));
        assertEquals(8.5, mapper.readValue(file.toFile(), ProtocolParameters.class).interportalIdealCm());
    }
}
