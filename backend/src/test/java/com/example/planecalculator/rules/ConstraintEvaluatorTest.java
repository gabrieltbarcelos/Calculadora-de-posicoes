package com.example.planecalculator.rules;

import com.example.planecalculator.geometry.Point2;
import com.example.planecalculator.planning.PortPlacement;
import com.example.planecalculator.planning.PortRole;
import com.example.planecalculator.planning.ProtocolParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintEvaluatorTest {

    private static ProtocolParameters protocol;
    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();

    @BeforeAll
    static void loadProtocol() throws Exception {
        protocol = new ObjectMapper().readValue(new ClassPathResource("protocol-v0.1.json").getInputStream(), ProtocolParameters.class);
    }

    @Test
    void boneClearanceFailsInsideTwoCentimeters() {
        Evaluation evaluation = evaluator.evaluate(context(List.of(
                new PortPlacement(PortRole.CAMERA, 0, 16.5),
                new PortPlacement(PortRole.WORKING_1, 0, 8),
                new PortPlacement(PortRole.WORKING_2, 0, 0)
        ), List.of()));
        assertFalse(rule(evaluation, "H04"));
    }

    @Test
    void workspaceOnTheFarSideFailsCoverage() {
        Evaluation evaluation = evaluator.evaluate(context(List.of(
                new PortPlacement(PortRole.CAMERA, 6, 0),
                new PortPlacement(PortRole.WORKING_1, 6, 8),
                new PortPlacement(PortRole.WORKING_2, 6, -8)
        ), List.of(new Point2(2, 1), new Point2(3, 1), new Point2(3, -1), new Point2(2, -1))));
        assertFalse(rule(evaluation, "H09"));
    }

    private EvalContext context(List<PortPlacement> ports, List<Point2> workspace) {
        List<Point2> field = workspace.isEmpty()
                ? List.of(new Point2(-2, -1), new Point2(-1, -1), new Point2(-1, 1), new Point2(-2, 1))
                : workspace;
        Point2 target = new Point2(-1.5, 0);
        if (!workspace.isEmpty()) {
            target = new Point2(11, 0);
        }
        return new EvalContext(
                List.of(new Point2(-20, 20), new Point2(20, 20), new Point2(20, -20), new Point2(-20, -20)),
                List.of(new NamedPoint("processo xifoide", new Point2(0, 18))),
                List.of(),
                field,
                target,
                ports,
                protocol,
                false,
                1
        );
    }

    private static boolean rule(Evaluation evaluation, String id) {
        return evaluation.constraints().stream()
                .filter(constraint -> constraint.id().equals(id))
                .findFirst()
                .orElseThrow()
                .passed();
    }
}
