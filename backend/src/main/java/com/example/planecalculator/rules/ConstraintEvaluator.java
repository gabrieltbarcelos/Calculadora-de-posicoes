package com.example.planecalculator.rules;

import com.example.planecalculator.dto.PlanningResponse.BoneClearance;
import com.example.planecalculator.dto.PlanningResponse.ConstraintResult;
import com.example.planecalculator.dto.PlanningResponse.InterportalDistance;
import com.example.planecalculator.dto.PlanningResponse.WorkspaceDistance;
import com.example.planecalculator.geometry.Geometry;
import com.example.planecalculator.geometry.Point2;
import com.example.planecalculator.planning.ExclusionCategory;
import com.example.planecalculator.planning.PortPlacement;
import com.example.planecalculator.planning.PortRole;
import com.example.planecalculator.planning.ProtocolParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConstraintEvaluator {

    public Evaluation evaluate(EvalContext context) {
        List<PortPlacement> ports = context.ports();
        List<PortPlacement> robotic = ports.stream().filter(port -> port.role().robotic()).toList();
        PortPlacement camera = ports.stream().filter(port -> port.role() == PortRole.CAMERA).findFirst().orElse(null);
        PortPlacement assistant = ports.stream().filter(port -> port.role() == PortRole.ASSISTANT).findFirst().orElse(null);
        ProtocolParameters protocol = context.protocol();

        List<InterportalDistance> pairs = interportal(robotic);
        List<BoneClearance> bones = boneClearances(ports, context.bones());
        List<WorkspaceDistance> workspaceDistances = workspaceDistances(robotic, context.workspace(), context.target());
        double cameraDistance = camera == null ? Double.NaN : Geometry.distance(camera.point(), context.target());
        double minAvoid = minAvoidDistance(ports, context);

        List<String> outside = new ArrayList<>();
        List<String> forbidden = new ArrayList<>();
        List<String> tooClose = new ArrayList<>();
        List<String> boneHits = new ArrayList<>();
        for (PortPlacement port : ports) {
            if (!Geometry.pointInPolygon(port.point(), context.abdomen())) {
                outside.add(label(port.role()));
            }
            if (insideForbidden(port.point(), context)) {
                forbidden.add(label(port.role()));
            }
        }
        for (InterportalDistance pair : pairs) {
            if (pair.distanceCm() + 1e-6 < protocol.interportalMinCm()) {
                tooClose.add(pair.fromRole() + "–" + pair.toRole() + " (" + cm(pair.distanceCm()) + " cm)");
            }
        }
        for (BoneClearance clearance : bones) {
            if (clearance.distanceCm() + 1e-6 < protocol.boneClearanceMinCm()) {
                boneHits.add(clearance.portRole() + " a " + cm(clearance.distanceCm()) + " cm de " + clearance.bone());
            }
        }

        boolean h01 = outside.isEmpty();
        boolean h02 = forbidden.isEmpty();
        boolean h03 = tooClose.isEmpty() && robotic.size() >= 2;
        boolean h04 = boneHits.isEmpty();
        boolean h05 = camera != null
                && cameraDistance + 1e-6 >= protocol.cameraTargetMinCm()
                && cameraDistance - 1e-6 <= protocol.cameraTargetMaxCm();
        boolean h06 = assistantClearance(assistant, robotic, context, protocol);
        boolean h07 = assistant == null || !blocksTarget(assistant, robotic, context.target(), protocol);
        boolean h08 = !roboticBlocksTarget(robotic, context.target(), protocol);
        boolean h09 = coversWorkspace(robotic, context);
        boolean h10 = h05;
        boolean h11 = context.quadrantCount() <= 2;

        List<ConstraintResult> constraints = List.of(
                result("H01", "Todo portal deve estar dentro da parede abdominal.", h01,
                        h01 ? "Todos os portais estão na parede." : "Fora da parede: " + String.join(", ", outside) + "."),
                result("H02", "Nenhum portal pode estar em área proibida.", h02,
                        h02 ? "Nenhuma área proibida interceptada." : "Em área proibida: " + String.join(", ", forbidden) + "."),
                result("H03", "Distância entre portais robóticos deve ser de pelo menos " + cm(protocol.interportalMinCm()) + " cm.", h03,
                        h03Detail(h03, robotic.size(), tooClose)),
                result("H04", "Distância de qualquer portal a uma proeminência óssea deve ser de pelo menos " + cm(protocol.boneClearanceMinCm()) + " cm.", h04,
                        h04 ? "Folga óssea respeitada." : "Folga óssea insuficiente: " + String.join("; ", boneHits) + "."),
                result("H05", "A câmera deve ficar entre " + cm(protocol.cameraTargetMinCm()) + " e " + cm(protocol.cameraTargetMaxCm()) + " cm do target.", h05,
                        camera == null ? "Câmera ausente." : "Câmera a " + cm(cameraDistance) + " cm do target."),
                result("H06", "O portal auxiliar deve ficar a pelo menos " + cm(protocol.assistantToRoboticMinCm()) + " cm de qualquer portal robótico.", h06,
                        assistantDetail(assistant, context.assistantRequired(), robotic, protocol)),
                result("H07", "O portal auxiliar não deve ficar entre um portal robótico e o target.", h07,
                        h07 ? "Auxiliar fora do corredor até o target." : "O auxiliar intercepta o corredor de um portal robótico até o target."),
                result("H08", "Um portal robótico não deve ficar entre outro portal robótico e o target.", h08,
                        h08 ? "Nenhum portal robótico bloqueia o caminho até o target." : "Há portal robótico no corredor de outro portal até o target."),
                result("H09", "Os portais devem cobrir o centro e os extremos do campo-alvo, do lado do target.", h09,
                        h09 ? "Centro e vértices do campo estão do lado do target." : "Parte do campo ficou do lado oposto da linha de portais."),
                result("H10", "Se a câmera não puder ficar a 10–20 cm do target, indicar reavaliação ou double docking.", h10,
                        h10 ? "A câmera cabe na faixa de 10–20 cm." : "A câmera ficou fora de 10–20 cm. Reavaliar ou considerar double docking."),
                result("H11", "Se o campo ultrapassar dois quadrantes, indicar double docking.", h11,
                        h11 ? "O campo ocupa até dois quadrantes." : "O campo cruza os dois eixos do umbigo e pede double docking.")
        );

        List<String> warnings = new ArrayList<>();
        for (InterportalDistance pair : pairs) {
            if (pair.distanceCm() <= protocol.interportalManufacturerMinCm() + 0.05) {
                warnings.add("Distância interportal de " + cm(pair.distanceCm())
                        + " cm está na faixa excepcional do fabricante (6 cm) e não é aceita pelo protocolo.");
            }
        }
        if (minAvoid <= 0) {
            warnings.add("Há portal sobre área marcada para evitar.");
        } else if (minAvoid < 2) {
            warnings.add("Há portal a menos de 2 cm de uma área marcada para evitar.");
        }
        if (!h10) {
            warnings.add("Necessidade de reavaliação ou double docking: a câmera não ficou entre 10 e 20 cm do target.");
        }
        if (!h11) {
            warnings.add("O campo cirúrgico ultrapassa dois quadrantes. Considerar double docking.");
        }

        boolean hard = h01 && h02 && h03 && h04 && h05 && h06 && h07 && h08 && h09;
        int failed = 0;
        if (!h01) failed++;
        if (!h02) failed++;
        if (!h03) failed++;
        if (!h04) failed++;
        if (!h05) failed++;
        if (!h06) failed++;
        if (!h07) failed++;
        if (!h08) failed++;
        if (!h09) failed++;

        return new Evaluation(
                constraints,
                warnings,
                pairs,
                cameraDistance,
                bones,
                workspaceDistances,
                hard,
                failed,
                minAvoid
        );
    }

    private static boolean assistantClearance(
            PortPlacement assistant,
            List<PortPlacement> robotic,
            EvalContext context,
            ProtocolParameters protocol
    ) {
        if (!context.assistantRequired() && assistant == null) {
            return true;
        }
        if (assistant == null) {
            return false;
        }
        for (PortPlacement port : robotic) {
            if (Geometry.distance(assistant.point(), port.point()) + 1e-6 < protocol.assistantToRoboticMinCm()) {
                return false;
            }
        }
        return true;
    }

    private static String assistantDetail(
            PortPlacement assistant,
            boolean required,
            List<PortPlacement> robotic,
            ProtocolParameters protocol
    ) {
        if (!required && assistant == null) {
            return "Portal auxiliar não solicitado.";
        }
        if (assistant == null) {
            return "Portal auxiliar solicitado e ausente.";
        }
        double min = Double.POSITIVE_INFINITY;
        for (PortPlacement port : robotic) {
            min = Math.min(min, Geometry.distance(assistant.point(), port.point()));
        }
        return "Menor distância do auxiliar a um portal robótico: " + cm(min) + " cm (mínimo " + cm(protocol.assistantToRoboticMinCm()) + " cm).";
    }

    private static boolean blocksTarget(PortPlacement candidate, List<PortPlacement> others, Point2 target, ProtocolParameters protocol) {
        for (PortPlacement other : others) {
            if (other.role() == candidate.role()) {
                continue;
            }
            if (Geometry.blocksSegment(candidate.point(), other.point(), target, protocol.collinearityToleranceCm())) {
                return true;
            }
        }
        return false;
    }

    private static boolean roboticBlocksTarget(List<PortPlacement> robotic, Point2 target, ProtocolParameters protocol) {
        for (PortPlacement candidate : robotic) {
            for (PortPlacement other : robotic) {
                if (candidate.role() == other.role()) {
                    continue;
                }
                if (Geometry.blocksSegment(candidate.point(), other.point(), target, protocol.collinearityToleranceCm())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean coversWorkspace(List<PortPlacement> robotic, EvalContext context) {
        if (robotic.size() < 2 || context.workspace().isEmpty()) {
            return false;
        }
        Point2 start = robotic.get(0).point();
        Point2 end = robotic.get(0).point();
        double best = -1;
        for (int i = 0; i < robotic.size(); i++) {
            for (int j = i + 1; j < robotic.size(); j++) {
                double span = Geometry.distance(robotic.get(i).point(), robotic.get(j).point());
                if (span > best) {
                    best = span;
                    start = robotic.get(i).point();
                    end = robotic.get(j).point();
                }
            }
        }
        for (Point2 sample : samples(context.workspace(), context.target())) {
            if (!Geometry.onTargetSide(sample, start, end, context.target())) {
                return false;
            }
        }
        return true;
    }

    private static String h03Detail(boolean passed, int roboticCount, List<String> tooClose) {
        if (passed) {
            return "Espaçamento robótico dentro do mínimo do protocolo.";
        }
        if (roboticCount < 2) {
            return "São necessários pelo menos dois portais robóticos.";
        }
        return "Abaixo do mínimo: " + String.join(", ", tooClose) + ".";
    }

    private static List<Point2> samples(List<Point2> workspace, Point2 target) {
        List<Point2> samples = new ArrayList<>();
        samples.add(target);
        samples.addAll(workspace);
        return samples;
    }

    private static List<InterportalDistance> interportal(List<PortPlacement> robotic) {
        List<InterportalDistance> pairs = new ArrayList<>();
        for (int i = 0; i < robotic.size(); i++) {
            for (int j = i + 1; j < robotic.size(); j++) {
                PortPlacement a = robotic.get(i);
                PortPlacement b = robotic.get(j);
                pairs.add(new InterportalDistance(
                        a.role().name(),
                        b.role().name(),
                        Geometry.round(Geometry.distance(a.point(), b.point()))
                ));
            }
        }
        return pairs;
    }

    private static List<BoneClearance> boneClearances(List<PortPlacement> ports, List<NamedPoint> bones) {
        List<BoneClearance> clearances = new ArrayList<>();
        for (PortPlacement port : ports) {
            for (NamedPoint bone : bones) {
                clearances.add(new BoneClearance(
                        port.role().name(),
                        bone.name(),
                        Geometry.round(Geometry.distance(port.point(), bone.point()))
                ));
            }
        }
        return clearances;
    }

    private static List<WorkspaceDistance> workspaceDistances(
            List<PortPlacement> robotic,
            List<Point2> workspace,
            Point2 target
    ) {
        List<WorkspaceDistance> distances = new ArrayList<>();
        List<Point2> samples = samples(workspace, target);
        for (PortPlacement port : robotic) {
            for (int i = 0; i < samples.size(); i++) {
                String sample = i == 0 ? "centro" : "vértice " + i;
                distances.add(new WorkspaceDistance(
                        port.role().name(),
                        sample,
                        Geometry.round(Geometry.distance(port.point(), samples.get(i)))
                ));
            }
        }
        return distances;
    }

    private static boolean insideForbidden(Point2 point, EvalContext context) {
        return context.exclusions().stream()
                .filter(zone -> zone.category() == ExclusionCategory.FORBIDDEN)
                .anyMatch(zone -> Geometry.pointInPolygon(point, zone.polygon()));
    }

    private static double minAvoidDistance(List<PortPlacement> ports, EvalContext context) {
        double min = Double.POSITIVE_INFINITY;
        for (ExclusionZone zone : context.exclusions()) {
            if (zone.category() != ExclusionCategory.AVOID) {
                continue;
            }
            for (PortPlacement port : ports) {
                min = Math.min(min, Geometry.distanceToPolygon(port.point(), zone.polygon()));
            }
        }
        return min;
    }

    private static ConstraintResult result(String id, String description, boolean passed, String detail) {
        return new ConstraintResult(id, description, passed, detail);
    }

    public static String label(PortRole role) {
        return switch (role) {
            case CAMERA -> "câmera";
            case WORKING_1 -> "portal 1";
            case WORKING_2 -> "portal 2";
            case WORKING_3 -> "portal 3";
            case ASSISTANT -> "portal auxiliar";
        };
    }

    public static String cm(double value) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value);
    }
}
