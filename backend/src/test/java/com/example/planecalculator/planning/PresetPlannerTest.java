package com.example.planecalculator.planning;

import com.example.planecalculator.dto.PlanningRequest;
import com.example.planecalculator.dto.PlanningRequest.AnatomyInput;
import com.example.planecalculator.dto.PlanningRequest.DefectInput;
import com.example.planecalculator.dto.PlanningRequest.ExclusionInput;
import com.example.planecalculator.dto.PlanningRequest.ProcedureInput;
import com.example.planecalculator.dto.PlanningRequest.WorkspaceInput;
import com.example.planecalculator.dto.PlanningResponse;
import com.example.planecalculator.dto.PlanningResponse.PortDto;
import com.example.planecalculator.geometry.Geometry;
import com.example.planecalculator.geometry.Point2;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetPlannerTest {

    private static ProtocolParameters protocol;
    private final PresetPlanner planner = new PresetPlanner();

    @BeforeAll
    static void loadProtocol() throws Exception {
        protocol = new ObjectMapper().readValue(new ClassPathResource("protocol-v0.1.json").getInputStream(), ProtocolParameters.class);
    }

    @Test
    void tappSuggestsCenteredCameraInsideThePreferredBand() {
        PlanningResponse response = planner.suggest(caseOf(
                SurgicalTechnique.TAPP,
                AccessSide.RIGHT,
                new Point2(0, 4),
                Geometry.rectangle(new Point2(0, 4), 2, 2),
                wideAnatomy(),
                List.of(),
                false
        ), protocol);

        PortDto camera = port(response, PortRole.CAMERA);
        assertTrue(response.configurationValid());
        assertEquals("TAPP", response.techniqueVariant());
        assertEquals("RIGHT", response.accessSide());
        assertEquals(4, camera.y(), 0.05);
        assertEquals(17.5, camera.x(), 0.05);
        assertEquals(8, response.admissibleRange().spacingCm(), 0.05);
        assertTrue(response.admissibleRange().offsetMinCm() >= 15);
        assertTrue(response.admissibleRange().offsetMaxCm() <= 20);
        assertEquals(12, port(response, PortRole.WORKING_1).y(), 0.05);
        assertEquals(-4, port(response, PortRole.WORKING_2).y(), 0.05);
    }

    @Test
    void forbiddenZoneRejectsTheIdealLine() {
        PlanningResponse response = planner.suggest(caseOf(
                SurgicalTechnique.TAPP,
                AccessSide.RIGHT,
                new Point2(0, 4),
                Geometry.rectangle(new Point2(0, 4), 2, 2),
                wideAnatomy(),
                List.of(new ExclusionInput(
                        "scar",
                        ExclusionType.SCAR,
                        ExclusionCategory.FORBIDDEN,
                        List.of(new Point2(16.5, -15), new Point2(22, -15), new Point2(22, 20), new Point2(16.5, 20))
                )),
                false
        ), protocol);

        assertTrue(response.configurationValid());
        assertTrue(response.ports().stream().allMatch(port -> port.x() < 16.5));
        assertEquals(16, port(response, PortRole.CAMERA).x(), 0.05);
    }

    @Test
    void portsStayClearOfBoneProminences() {
        AnatomyInput anatomy = wideAnatomy();
        AnatomyInput shifted = new AnatomyInput(
                anatomy.xiphoid(),
                anatomy.pubis(),
                anatomy.asisRight(),
                anatomy.asisLeft(),
                new Point2(17.5, 12),
                anatomy.costalLeft(),
                anatomy.flankRight(),
                anatomy.flankLeft(),
                anatomy.rectusRight(),
                anatomy.rectusLeft()
        );
        PlanningResponse response = planner.suggest(caseOf(
                SurgicalTechnique.TAPP,
                AccessSide.RIGHT,
                new Point2(0, 4),
                Geometry.rectangle(new Point2(0, 4), 2, 2),
                shifted,
                List.of(),
                false
        ), protocol);

        assertTrue(response.configurationValid());
        assertTrue(response.boneClearances().stream().allMatch(clearance -> clearance.distanceCm() >= 2));
    }

    @Test
    void autoSideAvoidsABlockedLeftApproach() {
        PlanningResponse response = planner.suggest(caseOf(
                SurgicalTechnique.TAPP,
                AccessSide.AUTO,
                new Point2(0, 4),
                Geometry.rectangle(new Point2(0, 4), 2, 2),
                wideAnatomy(),
                List.of(new ExclusionInput(
                        "left",
                        ExclusionType.SCAR,
                        ExclusionCategory.FORBIDDEN,
                        List.of(new Point2(-22, -15), new Point2(-14, -15), new Point2(-14, 20), new Point2(-22, 20))
                )),
                false
        ), protocol);

        assertEquals("RIGHT", response.accessSide());
        assertTrue(response.configurationValid());
    }

    @Test
    void narrowAbdomenUsesTheWidenedCameraBand() {
        AnatomyInput narrow = new AnatomyInput(
                new Point2(0, 18),
                new Point2(0, -16),
                new Point2(11, -14),
                new Point2(-11, -14),
                new Point2(12, 16),
                new Point2(-12, 16),
                new Point2(14, 0),
                new Point2(-14, 0),
                new Point2(4, 0),
                new Point2(-4, 0)
        );
        PlanningResponse response = planner.suggest(caseOf(
                SurgicalTechnique.TAPP,
                AccessSide.RIGHT,
                new Point2(0, 4),
                Geometry.rectangle(new Point2(0, 4), 2, 2),
                narrow,
                List.of(),
                false
        ), protocol);

        assertTrue(response.configurationValid());
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("faixa ampliada")));
        assertTrue(port(response, PortRole.CAMERA).x() >= 10);
        assertTrue(port(response, PortRole.CAMERA).x() <= 14);
    }

    @Test
    void etepSelectsVariantFromTargetHeight() {
        PlanningResponse lateral = suggestEtep(new Point2(0, 0));
        PlanningResponse cranial = suggestEtep(new Point2(0, 8));
        PlanningResponse caudal = suggestEtep(new Point2(0, -8));

        assertEquals("ETEP_LATERAL", lateral.techniqueVariant());
        assertEquals(6, port(lateral, PortRole.CAMERA).x(), 0.05);
        assertEquals(14, port(lateral, PortRole.CAMERA).y(), 0.05);
        assertTrue(lateral.configurationValid());

        assertEquals("ETEP_SUPRAPUBIC", cranial.techniqueVariant());
        assertEquals("suprapubico", cranial.suggestedDocking());
        assertTrue(cranial.ports().stream().anyMatch(port -> Math.abs(port.y() - (-17)) < 0.05));
        assertTrue(cranial.ports().stream().anyMatch(port -> Math.abs(port.y() - (-18)) < 0.05));

        assertEquals("ETEP_TOP", caudal.techniqueVariant());
        assertEquals("cranial", caudal.suggestedDocking());
        assertTrue(caudal.redockingAllowed());
    }

    @Test
    void bilateralRobotarRequestsDoubleDocking() {
        PlanningResponse response = planner.suggest(caseOf(
                SurgicalTechnique.ROBOTAR,
                AccessSide.RIGHT,
                new Point2(0, 4),
                Geometry.rectangle(new Point2(0, 4), 2, 2),
                wideAnatomy(),
                List.of(),
                true
        ), protocol);

        assertTrue(response.doubleDockingRecommended());
        assertEquals("lateral+double", response.suggestedDocking());
    }

    @Test
    void missingLandmarkIsRejected() {
        AnatomyInput incomplete = new AnatomyInput(
                null,
                new Point2(0, -20),
                new Point2(22, -16),
                new Point2(-22, -16),
                new Point2(24, 16),
                new Point2(-24, 16),
                new Point2(30, 0),
                new Point2(-30, 0),
                null,
                null
        );
        PlanningException error = assertThrows(PlanningException.class, () -> planner.suggest(caseOf(
                SurgicalTechnique.TAPP,
                AccessSide.RIGHT,
                new Point2(0, 4),
                Geometry.rectangle(new Point2(0, 4), 2, 2),
                incomplete,
                List.of(),
                false
        ), protocol));
        assertTrue(error.getMessage().contains("processo xifoide"));
    }

    private PlanningResponse suggestEtep(Point2 center) {
        return planner.suggest(caseOf(
                SurgicalTechnique.ETEP,
                AccessSide.RIGHT,
                center,
                Geometry.rectangle(center, 2, 2),
                wideAnatomy(),
                List.of(),
                false
        ), protocol);
    }

    private static PlanningRequest caseOf(
            SurgicalTechnique technique,
            AccessSide side,
            Point2 center,
            List<Point2> workspace,
            AnatomyInput anatomy,
            List<ExclusionInput> zones,
            boolean bilateral
    ) {
        return new PlanningRequest(
                null,
                anatomy,
                new DefectInput(center, 2, 2, 1, null),
                new WorkspaceInput(WorkspaceMode.CUSTOM, workspace),
                zones,
                new ProcedureInput(
                        RobotPlatform.DAVINCI_XI,
                        technique,
                        side,
                        3,
                        false,
                        bilateral,
                        MeasurementPhase.AFTER_INSUFFLATION,
                        null,
                        null,
                        null
                ),
                null
        );
    }

    private static AnatomyInput wideAnatomy() {
        return new AnatomyInput(
                new Point2(0, 22),
                new Point2(0, -20),
                new Point2(22, -16),
                new Point2(-22, -16),
                new Point2(24, 16),
                new Point2(-24, 16),
                new Point2(30, 0),
                new Point2(-30, 0),
                new Point2(8, 0),
                new Point2(-8, 0)
        );
    }

    private static PortDto port(PlanningResponse response, PortRole role) {
        return response.ports().stream().filter(port -> port.role() == role).findFirst().orElseThrow();
    }
}
