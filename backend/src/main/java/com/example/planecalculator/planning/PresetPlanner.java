package com.example.planecalculator.planning;

import com.example.planecalculator.dto.PlanningRequest;
import com.example.planecalculator.dto.PlanningRequest.AnatomyInput;
import com.example.planecalculator.dto.PlanningRequest.PortInput;
import com.example.planecalculator.dto.PlanningResponse;
import com.example.planecalculator.dto.PlanningResponse.AdmissibleRange;
import com.example.planecalculator.dto.PlanningResponse.ConstraintResult;
import com.example.planecalculator.dto.PlanningResponse.InterportalDistance;
import com.example.planecalculator.dto.PlanningResponse.PortDto;
import com.example.planecalculator.geometry.Geometry;
import com.example.planecalculator.geometry.Point2;
import com.example.planecalculator.rules.ConstraintEvaluator;
import com.example.planecalculator.rules.Evaluation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class PresetPlanner {

    private static final double TARM_TOLERANCE_CM = 2;
    private static final double SUPRAPUBIC_INSET_CM = 1;

    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();

    public PlanningResponse suggest(PlanningRequest request, ProtocolParameters protocol) {
        CaseGeometry geometry = CaseGeometry.from(request, protocol);
        TechniqueVariant variant = resolve(request.procedure().technique(), geometry.target, protocol);
        SearchHit hit = null;
        for (AccessSide side : sides(request, geometry, variant)) {
            hit = better(hit, search(side, variant, geometry, request, protocol), geometry);
        }
        if (hit == null) {
            throw new PlanningException("Não foi possível amostrar posições para essa anatomia.");
        }
        return assemble(request, protocol, geometry, hit, true);
    }

    public PlanningResponse validate(PlanningRequest request, ProtocolParameters protocol) {
        if (request.ports() == null || request.ports().isEmpty()) {
            throw new PlanningException("Informe os portais para validar.");
        }
        CaseGeometry geometry = CaseGeometry.from(request, protocol);
        TechniqueVariant variant = resolve(request.procedure().technique(), geometry.target, protocol);
        List<PortPlacement> ports = new ArrayList<>();
        for (PortInput port : request.ports()) {
            ports.add(new PortPlacement(port.role(), port.x(), port.y()));
        }
        Evaluation evaluation = evaluator.evaluate(geometry.context(ports, protocol));
        AccessSide side = request.procedure().accessSide();
        if (side == AccessSide.AUTO) {
            PortPlacement camera = cameraOf(ports);
            side = camera != null && camera.x() < geometry.target.x() ? AccessSide.LEFT : AccessSide.RIGHT;
        }
        Candidate candidate = new Candidate(0, 0, 0, ports, evaluation, 0);
        SearchHit hit = new SearchHit(side, variant, Phase.FALLBACK, candidate, List.of(), evaluation.hardRulesPassed());
        PlanningResponse response = assemble(request, protocol, geometry, hit, false);
        List<String> warnings = new ArrayList<>();
        warnings.add("Avaliação das posições informadas, sem nova sugestão.");
        warnings.addAll(response.warnings());
        return new PlanningResponse(
                response.target(),
                response.abdomenPolygon(),
                response.defectPolygon(),
                response.workspacePolygon(),
                response.techniqueVariant(),
                response.accessSide(),
                response.suggestedDocking(),
                response.suggestedCartPosition(),
                response.doubleDockingRecommended(),
                response.redockingAllowed(),
                response.configurationValid(),
                false,
                response.ports(),
                null,
                response.interportalDistances(),
                response.cameraTargetDistance(),
                response.boneClearances(),
                response.workspaceDistances(),
                response.constraints(),
                warnings,
                response.rationale()
        );
    }

    private SearchHit search(
            AccessSide side,
            TechniqueVariant variant,
            CaseGeometry geometry,
            PlanningRequest request,
            ProtocolParameters protocol
    ) {
        int count = request.procedure().numberOfRoboticPorts();
        List<Candidate> all = new ArrayList<>();
        for (Phase phase : List.of(Phase.PREFERRED, Phase.WIDENED, Phase.RELAXED)) {
            List<Candidate> found = generate(variant, side, phase, count, geometry, protocol);
            all.addAll(found);
            List<Candidate> valid = found.stream().filter(candidate -> candidate.evaluation.hardRulesPassed()).toList();
            if (!valid.isEmpty()) {
                Candidate best = valid.stream().min(Comparator.comparingDouble(Candidate::score)).orElseThrow();
                return new SearchHit(side, variant, phase, best, valid, true);
            }
        }
        if (all.isEmpty()) {
            throw new PlanningException("Não foi possível amostrar posições para o lado " + sideLabel(side) + ".");
        }
        Candidate least = all.stream()
                .min(Comparator.comparingInt((Candidate candidate) -> candidate.evaluation.failedHardCount())
                        .thenComparingDouble(Candidate::score))
                .orElseThrow();
        return new SearchHit(side, variant, Phase.FALLBACK, least, List.of(), false);
    }

    private List<Candidate> generate(
            TechniqueVariant variant,
            AccessSide side,
            Phase phase,
            int count,
            CaseGeometry geometry,
            ProtocolParameters protocol
    ) {
        return switch (variant) {
            case TAPP, ROBOTAR -> lateral(variant, side, phase, count, geometry, protocol, true);
            case TARM_TARUP -> lateral(variant, side, phase, count, geometry, protocol, false);
            case ETEP_LATERAL, ETEP_TOP -> etepLine(variant, side, phase, count, geometry, protocol);
            case ETEP_SUPRAPUBIC -> suprapubic(side, phase, count, geometry, protocol);
        };
    }

    private List<Candidate> lateral(
            TechniqueVariant variant,
            AccessSide side,
            Phase phase,
            int count,
            CaseGeometry geometry,
            ProtocolParameters protocol,
            boolean offsetFromTarget
    ) {
        double[] bounds = lateralBounds(variant, phase, side, geometry, protocol);
        boolean enforceRectus = phase != Phase.RELAXED;
        List<Candidate> found = new ArrayList<>();
        for (double offset : Geometry.steps(bounds[0], bounds[1], protocol.samplingStepCm())) {
            double lineX = offsetFromTarget
                    ? geometry.target.x() + side.sign() * offset
                    : side.sign() * offset;
            if (enforceRectus && !lateralToRectus(side, lineX, geometry.anatomy)) {
                continue;
            }
            for (double spacing : spacingSamples(protocol)) {
                List<PortPlacement> robotic = placeVertical(lineX, geometry.target.y(), spacing, count);
                found.add(finish(variant, side, offset, spacing, geometry.target.y(), true, robotic, geometry, protocol));
            }
        }
        return found;
    }

    private List<Candidate> etepLine(
            TechniqueVariant variant,
            AccessSide side,
            Phase phase,
            int count,
            CaseGeometry geometry,
            ProtocolParameters protocol
    ) {
        double preferredAnchor = preferredAnchor(variant, side, geometry, protocol);
        List<Double> anchors = phase == Phase.PREFERRED
                ? List.of(Geometry.round(preferredAnchor))
                : Geometry.steps(preferredAnchor - 20, preferredAnchor + 6, protocol.samplingStepCm());
        double[] bounds = etepOffsetBounds(side, phase, geometry, protocol);
        boolean enforceRectus = phase != Phase.RELAXED;
        List<Candidate> found = new ArrayList<>();
        for (double offset : Geometry.steps(bounds[0], bounds[1], protocol.samplingStepCm())) {
            double lineX = side.sign() * offset;
            if (!medialToRectus(side, lineX, geometry.anatomy, enforceRectus)) {
                continue;
            }
            for (double anchor : anchors) {
                for (double spacing : spacingSamples(protocol)) {
                    List<PortPlacement> robotic = placeCaudalSeries(lineX, anchor, spacing, count);
                    found.add(finish(variant, side, offset, spacing, anchor, true, robotic, geometry, protocol));
                }
            }
        }
        return found;
    }

    private List<Candidate> suprapubic(
            AccessSide side,
            Phase phase,
            int count,
            CaseGeometry geometry,
            ProtocolParameters protocol
    ) {
        double preferredY = -protocol.etepUmbilicalCaudalOffsetCm();
        double[] bounds = phase == Phase.PREFERRED ? new double[]{0.5, 2} : new double[]{0.3, 3};
        List<Double> anchors = phase == Phase.PREFERRED
                ? List.of(Geometry.round(preferredY))
                : Geometry.steps(preferredY - 4, preferredY + 4, protocol.samplingStepCm());
        List<Candidate> found = new ArrayList<>();
        for (double inset : Geometry.steps(bounds[0], bounds[1], protocol.samplingStepCm())) {
            for (double anchor : anchors) {
                List<PortPlacement> robotic = placeSuprapubic(inset, anchor, side, count, geometry.anatomy, protocol);
                if (robotic == null) {
                    continue;
                }
                double spacing = minPair(robotic);
                found.add(finish(TechniqueVariant.ETEP_SUPRAPUBIC, side, inset, spacing, anchor, false, robotic, geometry, protocol));
            }
        }
        return found;
    }

    private Candidate finish(
            TechniqueVariant variant,
            AccessSide side,
            double offset,
            double spacing,
            double anchorY,
            boolean scoreSpacing,
            List<PortPlacement> robotic,
            CaseGeometry geometry,
            ProtocolParameters protocol
    ) {
        List<PortPlacement> ports = geometry.assistantRequired
                ? attachAssistant(robotic, side, geometry, protocol)
                : robotic;
        Evaluation evaluation = evaluator.evaluate(geometry.context(ports, protocol));
        double score = 0;
        if (scoreSpacing) {
            score += Math.abs(spacing - protocol.interportalIdealCm()) * 100;
        }
        score += Math.abs(offset - preferredOffset(variant, protocol)) * 10;
        score += Math.abs(anchorY - preferredAnchor(variant, side, geometry, protocol)) * 5;
        double cameraDistance = evaluation.cameraTargetDistance();
        if (Double.isFinite(cameraDistance)) {
            double ideal = (protocol.cameraTargetPreferredMinCm() + protocol.cameraTargetPreferredMaxCm()) / 2;
            score += Math.abs(cameraDistance - ideal);
            if (cameraDistance < protocol.cameraTargetPreferredMinCm()
                    || cameraDistance > protocol.cameraTargetPreferredMaxCm()) {
                score += 15;
            }
        } else {
            score += 1000;
        }
        score += avoidPenalty(evaluation.minAvoidDistance());
        return new Candidate(offset, spacing, anchorY, ports, evaluation, score);
    }

    private List<PortPlacement> attachAssistant(
            List<PortPlacement> robotic,
            AccessSide side,
            CaseGeometry geometry,
            ProtocolParameters protocol
    ) {
        List<PortPlacement> best = null;
        int bestFailures = Integer.MAX_VALUE;
        for (Point2 seed : assistantSeeds(robotic, side)) {
            List<PortPlacement> ports = new ArrayList<>(robotic);
            ports.add(new PortPlacement(PortRole.ASSISTANT, Geometry.round(seed.x()), Geometry.round(seed.y())));
            Evaluation evaluation = evaluator.evaluate(geometry.context(ports, protocol));
            if (evaluation.hardRulesPassed()) {
                return ports;
            }
            if (evaluation.failedHardCount() < bestFailures) {
                bestFailures = evaluation.failedHardCount();
                best = ports;
            }
        }
        return best == null ? robotic : best;
    }

    private List<Point2> assistantSeeds(List<PortPlacement> robotic, AccessSide side) {
        PortPlacement cranial = robotic.stream().max(Comparator.comparingDouble(PortPlacement::y)).orElseThrow();
        PortPlacement caudal = robotic.stream().min(Comparator.comparingDouble(PortPlacement::y)).orElseThrow();
        PortPlacement camera = cameraOf(robotic);
        if (camera == null) {
            camera = cranial;
        }
        int sign = side.sign();
        double[] distances = {8, 9, 10, 12, 14, 16};
        List<Point2> seeds = new ArrayList<>();
        for (double distance : distances) {
            seeds.add(new Point2(camera.x() + sign * distance, camera.y()));
            seeds.add(new Point2(caudal.x() + sign * distance, caudal.y() - 4));
            seeds.add(new Point2(caudal.x(), caudal.y() - distance));
            seeds.add(new Point2(cranial.x(), cranial.y() + distance));
        }
        return seeds;
    }

    private PlanningResponse assemble(
            PlanningRequest request,
            ProtocolParameters protocol,
            CaseGeometry geometry,
            SearchHit hit,
            boolean generated
    ) {
        Evaluation evaluation = hit.chosen.evaluation;
        boolean h11 = geometry.quadrants > 2;
        boolean cameraOut = evaluation.constraints().stream()
                .anyMatch(rule -> rule.id().equals("H05") && !rule.passed());
        boolean bilateral = request.procedure().technique() == SurgicalTechnique.ROBOTAR
                && request.procedure().bilateralTar();
        boolean doubleDocking = h11 || cameraOut || bilateral;
        boolean redocking = hit.variant == TechniqueVariant.ETEP_TOP || bilateral;
        String docking = docking(hit.variant, bilateral);
        String cart = cart(hit.variant, hit.side);
        AdmissibleRange range = generated ? range(hit) : null;
        List<String> warnings = warnings(request, geometry, hit, evaluation, bilateral);
        List<String> rationale = rationale(protocol, geometry, hit, evaluation, docking, cart, range, doubleDocking, generated);
        double cameraDistance = Double.isFinite(evaluation.cameraTargetDistance())
                ? Geometry.round(evaluation.cameraTargetDistance())
                : 0;
        return new PlanningResponse(
                new Point2(Geometry.round(geometry.target.x()), Geometry.round(geometry.target.y())),
                geometry.abdomen,
                geometry.defect,
                geometry.workspace,
                hit.variant.name(),
                hit.side.name(),
                docking,
                cart,
                doubleDocking,
                redocking,
                evaluation.hardRulesPassed(),
                generated,
                hit.chosen.ports.stream()
                        .map(port -> new PortDto(port.role(), portLabel(port.role()), Geometry.round(port.x()), Geometry.round(port.y())))
                        .toList(),
                range,
                evaluation.interportalDistances(),
                cameraDistance,
                evaluation.boneClearances(),
                evaluation.workspaceDistances(),
                evaluation.constraints(),
                warnings,
                rationale
        );
    }

    private AdmissibleRange range(SearchHit hit) {
        List<Candidate> valid = hit.matches;
        Candidate chosen = hit.chosen;
        double min = valid.stream().mapToDouble(Candidate::offset).min().orElse(chosen.offset);
        double max = valid.stream().mapToDouble(Candidate::offset).max().orElse(chosen.offset);
        return new AdmissibleRange(
                Geometry.round(min),
                Geometry.round(max),
                Geometry.round(chosen.offset),
                Geometry.round(chosen.spacing),
                hit.side.name(),
                band(valid, chosen)
        );
    }

    private List<Point2> band(List<Candidate> valid, Candidate chosen) {
        List<Point2> points = new ArrayList<>();
        List<Candidate> source = valid.isEmpty() ? List.of(chosen) : valid;
        for (Candidate candidate : source) {
            for (PortPlacement port : candidate.ports) {
                if (port.role().robotic()) {
                    points.add(port.point());
                }
            }
        }
        if (points.isEmpty()) {
            return List.of();
        }
        List<Point2> hull = Geometry.convexHull(points);
        if (hull.size() >= 3 && Geometry.polygonArea(hull) >= 0.5) {
            return hull;
        }
        Point2 start = points.get(0);
        Point2 end = points.get(0);
        double best = -1;
        for (Point2 first : points) {
            for (Point2 second : points) {
                double span = Geometry.distance(first, second);
                if (span > best) {
                    best = span;
                    start = first;
                    end = second;
                }
            }
        }
        return Geometry.bufferSegment(start, end, 0.45);
    }

    private List<String> warnings(
            PlanningRequest request,
            CaseGeometry geometry,
            SearchHit hit,
            Evaluation evaluation,
            boolean bilateral
    ) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>(evaluation.warnings());
        if (request.procedure().measurementPhase() == MeasurementPhase.BEFORE_INSUFFLATION) {
            warnings.add("As medidas foram informadas antes da insuflação. As regras assumem medidas após o pneumoperitônio.");
        }
        if (missingRectus(hit, geometry.anatomy)) {
            warnings.add("A borda lateral do reto não foi informada. A sugestão segue só as demais referências da técnica.");
        }
        if (geometry.numberOfDefects > 1) {
            warnings.add("Há " + geometry.numberOfDefects + " defeitos registrados. A geometria usa o defeito principal.");
        }
        if (bilateral) {
            warnings.add("TAR bilateral: double docking é a estratégia padrão.");
        }
        if (hit.phase == Phase.WIDENED) {
            warnings.add("A faixa preferencial da técnica não teve candidato válido. A sugestão usa a faixa ampliada.");
        }
        if (hit.phase == Phase.RELAXED) {
            warnings.add("A posição em relação à borda do reto foi relaxada para encontrar uma configuração.");
        }
        if (hit.phase == Phase.FALLBACK && !hit.feasible) {
            warnings.add("Nenhuma amostra atendeu todas as regras duras. A posição exibida é a menos inválida.");
        }
        return new ArrayList<>(warnings);
    }

    private List<String> rationale(
            ProtocolParameters protocol,
            CaseGeometry geometry,
            SearchHit hit,
            Evaluation evaluation,
            String docking,
            String cart,
            AdmissibleRange range,
            boolean doubleDocking,
            boolean generated
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(variantText(hit.variant, geometry, protocol));
        String sideText = sideLabel(hit.side);
        if (generated) {
            lines.add("Lado de acesso: " + sideText + ". Docking sugerido: " + dockingLabel(docking) + ". Carrinho: " + cart + ".");
        } else {
            lines.add("Posições recebidas para conferência. Docking de referência: " + dockingLabel(docking) + ". Carrinho: " + cart + ".");
        }
        if (evaluation.hardRulesPassed()) {
            lines.add("Configuração aceita: " + pairText(evaluation.interportalDistances())
                    + "; câmera a " + ConstraintEvaluator.cm(evaluation.cameraTargetDistance())
                    + " cm do target; nenhuma área proibida interceptada.");
        } else {
            String failed = evaluation.constraints().stream()
                    .filter(rule -> !rule.passed() && isHard(rule.id()))
                    .map(ConstraintResult::id)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("regras duras");
            lines.add("Configuração não aceita. Regras duras pendentes: " + failed + ".");
        }
        if (range != null) {
            lines.add("Faixa admissível do deslocamento: " + ConstraintEvaluator.cm(range.offsetMinCm())
                    + " a " + ConstraintEvaluator.cm(range.offsetMaxCm())
                    + " cm. Recomendado: " + ConstraintEvaluator.cm(range.recommendedOffsetCm())
                    + " cm. Espaçamento da sugestão: " + ConstraintEvaluator.cm(range.spacingCm()) + " cm.");
        }
        if (doubleDocking) {
            lines.add("Double docking recomendado para este campo ou esta técnica.");
        }
        return lines;
    }

    private static String variantText(TechniqueVariant variant, CaseGeometry geometry, ProtocolParameters protocol) {
        String y = ConstraintEvaluator.cm(geometry.target.y());
        return switch (variant) {
            case TAPP -> "r-TAPP: três portais em linha, câmera no centro, lateral à borda do reto e a "
                    + ConstraintEvaluator.cm(protocol.tappPortToTargetMinCm()) + "–"
                    + ConstraintEvaluator.cm(protocol.tappPortToTargetMaxCm()) + " cm do target.";
            case TARM_TARUP -> "r-TARM/TARUP: linha lateral com referência de "
                    + ConstraintEvaluator.cm(protocol.tarmLineaAlbaOffsetCm()) + " cm da linha alba.";
            case ETEP_LATERAL -> "eTEP lateral: o target é central (y = " + y + " cm). Acesso cerca de "
                    + ConstraintEvaluator.cm(protocol.etepCostalCaudalOffsetCm()) + " cm caudal ao rebordo e "
                    + ConstraintEvaluator.cm(protocol.etepMidlineLateralMinCm()) + "–"
                    + ConstraintEvaluator.cm(protocol.etepMidlineLateralMaxCm()) + " cm da linha média.";
            case ETEP_SUPRAPUBIC -> "eTEP suprapúbico: o target é cranial (y = " + y
                    + " cm). Paramedianos acima da sínfise e acesso cerca de "
                    + ConstraintEvaluator.cm(protocol.etepUmbilicalCaudalOffsetCm()) + " cm abaixo do umbigo.";
            case ETEP_TOP -> "eTEP cranial: o target é caudal (y = " + y
                    + " cm). Portais de cranial para caudal, com redocking permitido.";
            case ROBOTAR -> "RoboTAR: portais laterais à bainha do reto.";
        };
    }

    private static boolean missingRectus(SearchHit hit, AnatomyInput anatomy) {
        if (hit.variant == TechniqueVariant.ETEP_SUPRAPUBIC) {
            return !usable(anatomy.rectusRight(), true) || !usable(anatomy.rectusLeft(), false);
        }
        return hit.side == AccessSide.LEFT
                ? !usable(anatomy.rectusLeft(), false)
                : !usable(anatomy.rectusRight(), true);
    }

    private static List<AccessSide> sides(PlanningRequest request, CaseGeometry geometry, TechniqueVariant variant) {
        AccessSide selected = request.procedure().accessSide();
        if (selected != AccessSide.AUTO) {
            return List.of(selected);
        }
        if (variant == TechniqueVariant.ETEP_SUPRAPUBIC || variant == TechniqueVariant.ETEP_TOP) {
            return List.of(AccessSide.RIGHT, AccessSide.LEFT);
        }
        return List.of(AccessSide.RIGHT, AccessSide.LEFT);
    }

    static TechniqueVariant resolve(SurgicalTechnique technique, Point2 target, ProtocolParameters protocol) {
        return switch (technique) {
            case TAPP -> TechniqueVariant.TAPP;
            case TARM_TARUP -> TechniqueVariant.TARM_TARUP;
            case ROBOTAR -> TechniqueVariant.ROBOTAR;
            case ETEP -> {
                if (target.y() > protocol.targetCranialThresholdCm()) {
                    yield TechniqueVariant.ETEP_SUPRAPUBIC;
                }
                if (target.y() < protocol.targetCaudalThresholdCm()) {
                    yield TechniqueVariant.ETEP_TOP;
                }
                yield TechniqueVariant.ETEP_LATERAL;
            }
        };
    }

    private static double[] lateralBounds(
            TechniqueVariant variant,
            Phase phase,
            AccessSide side,
            CaseGeometry geometry,
            ProtocolParameters protocol
    ) {
        if (variant == TechniqueVariant.TARM_TARUP) {
            double reference = protocol.tarmLineaAlbaOffsetCm();
            if (phase == Phase.PREFERRED) {
                return new double[]{reference - TARM_TOLERANCE_CM, reference + TARM_TOLERANCE_CM};
            }
            double flank = Math.abs(side == AccessSide.LEFT ? geometry.anatomy.flankLeft().x() : geometry.anatomy.flankRight().x());
            return new double[]{2, Math.max(flank - 1, reference)};
        }
        if (phase == Phase.PREFERRED) {
            return new double[]{protocol.tappPortToTargetMinCm(), protocol.tappPortToTargetMaxCm()};
        }
        return new double[]{protocol.cameraTargetMinCm(), protocol.cameraTargetMaxCm()};
    }

    private static double[] etepOffsetBounds(AccessSide side, Phase phase, CaseGeometry geometry, ProtocolParameters protocol) {
        if (phase == Phase.PREFERRED) {
            return new double[]{protocol.etepMidlineLateralMinCm(), protocol.etepMidlineLateralMaxCm()};
        }
        Point2 rectus = side == AccessSide.LEFT ? geometry.anatomy.rectusLeft() : geometry.anatomy.rectusRight();
        double limit = protocol.etepMidlineLateralMaxCm();
        if (phase != Phase.RELAXED && rectus != null) {
            limit = Math.abs(rectus.x()) - 0.3;
        } else {
            limit = Math.max(8, protocol.etepMidlineLateralMaxCm());
        }
        return new double[]{1.5, Math.max(1.5, limit)};
    }

    private static double preferredOffset(TechniqueVariant variant, ProtocolParameters protocol) {
        return switch (variant) {
            case TAPP, ROBOTAR -> (protocol.tappPortToTargetMinCm() + protocol.tappPortToTargetMaxCm()) / 2;
            case TARM_TARUP -> protocol.tarmLineaAlbaOffsetCm();
            case ETEP_LATERAL, ETEP_TOP -> protocol.etepMidlineLateralMaxCm();
            case ETEP_SUPRAPUBIC -> SUPRAPUBIC_INSET_CM;
        };
    }

    private static double preferredAnchor(
            TechniqueVariant variant,
            AccessSide side,
            CaseGeometry geometry,
            ProtocolParameters protocol
    ) {
        return switch (variant) {
            case TAPP, TARM_TARUP, ROBOTAR -> geometry.target.y();
            case ETEP_LATERAL -> costalY(side, geometry) - protocol.etepCostalCaudalOffsetCm();
            case ETEP_TOP -> geometry.anatomy.xiphoid().y() - protocol.subxiphoidRelevantCm();
            case ETEP_SUPRAPUBIC -> -protocol.etepUmbilicalCaudalOffsetCm();
        };
    }

    private static double costalY(AccessSide side, CaseGeometry geometry) {
        Point2 costal = side == AccessSide.LEFT ? geometry.anatomy.costalLeft() : geometry.anatomy.costalRight();
        return costal.y();
    }

    private static boolean lateralToRectus(AccessSide side, double lineX, AnatomyInput anatomy) {
        if (side == AccessSide.LEFT) {
            if (!usable(anatomy.rectusLeft(), false)) {
                return true;
            }
            return lineX <= anatomy.rectusLeft().x() + 1e-6;
        }
        if (!usable(anatomy.rectusRight(), true)) {
            return true;
        }
        return lineX + 1e-6 >= anatomy.rectusRight().x();
    }

    private static boolean medialToRectus(AccessSide side, double lineX, AnatomyInput anatomy, boolean enforce) {
        if (!enforce) {
            return side == AccessSide.LEFT ? lineX < 0 : lineX > 0;
        }
        if (side == AccessSide.LEFT) {
            if (!usable(anatomy.rectusLeft(), false)) {
                return lineX < 0;
            }
            return lineX < 0 && lineX + 1e-6 >= anatomy.rectusLeft().x();
        }
        if (!usable(anatomy.rectusRight(), true)) {
            return lineX > 0;
        }
        return lineX > 0 && lineX - 1e-6 <= anatomy.rectusRight().x();
    }

    private static boolean usable(Point2 rectus, boolean right) {
        if (rectus == null) {
            return false;
        }
        return right ? rectus.x() > 0.5 : rectus.x() < -0.5;
    }

    private static List<PortPlacement> placeVertical(double lineX, double centerY, double spacing, int count) {
        int cameraIndex = (count - 1) / 2;
        List<Point2> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double y = centerY + ((count - 1) / 2.0 - i) * spacing;
            points.add(new Point2(Geometry.round(lineX), Geometry.round(y)));
        }
        return assign(points, cameraIndex);
    }

    private static List<PortPlacement> placeCaudalSeries(double lineX, double cranialY, double spacing, int count) {
        List<Point2> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new Point2(Geometry.round(lineX), Geometry.round(cranialY - i * spacing)));
        }
        return assign(points, 0);
    }

    private static List<PortPlacement> placeSuprapubic(
            double inset,
            double initialY,
            AccessSide side,
            int count,
            AnatomyInput anatomy,
            ProtocolParameters protocol
    ) {
        double fallback = (protocol.etepMidlineLateralMinCm() + protocol.etepMidlineLateralMaxCm()) / 2;
        double rightX = usable(anatomy.rectusRight(), true) ? anatomy.rectusRight().x() - inset : fallback;
        double leftX = usable(anatomy.rectusLeft(), false) ? anatomy.rectusLeft().x() + inset : -fallback;
        if (rightX <= 0.4 || leftX >= -0.4) {
            return null;
        }
        double initialX = side == AccessSide.LEFT ? leftX : rightX;
        List<PortPlacement> ports = new ArrayList<>();
        ports.add(new PortPlacement(PortRole.CAMERA, Geometry.round(initialX), Geometry.round(initialY)));
        ports.add(new PortPlacement(
                PortRole.WORKING_1,
                Geometry.round(leftX),
                Geometry.round(anatomy.pubis().y() + protocol.etepLeftParamedianAbovePubisCm())
        ));
        ports.add(new PortPlacement(
                PortRole.WORKING_2,
                Geometry.round(rightX),
                Geometry.round(anatomy.pubis().y() + protocol.etepRightParamedianAbovePubisCm())
        ));
        if (count >= 4) {
            ports.add(new PortPlacement(
                    PortRole.WORKING_3,
                    Geometry.round(initialX),
                    Geometry.round(initialY - protocol.interportalIdealCm())
            ));
        }
        return ports;
    }

    private static List<PortPlacement> assign(List<Point2> cranialToCaudal, int cameraIndex) {
        List<PortPlacement> ports = new ArrayList<>();
        int working = 1;
        for (int i = 0; i < cranialToCaudal.size(); i++) {
            Point2 point = cranialToCaudal.get(i);
            PortRole role = i == cameraIndex ? PortRole.CAMERA : workingRole(working++);
            ports.add(new PortPlacement(role, point.x(), point.y()));
        }
        return ports;
    }

    private static PortRole workingRole(int index) {
        return switch (index) {
            case 1 -> PortRole.WORKING_1;
            case 2 -> PortRole.WORKING_2;
            default -> PortRole.WORKING_3;
        };
    }

    private static List<Double> spacingSamples(ProtocolParameters protocol) {
        List<Double> values = Geometry.steps(
                protocol.interportalMinCm(),
                protocol.interportalManufacturerMaxCm(),
                protocol.samplingStepCm()
        );
        double ideal = Geometry.round(protocol.interportalIdealCm());
        boolean present = values.stream().anyMatch(value -> Math.abs(value - ideal) < 1e-6);
        if (!present) {
            values.add(ideal);
            values.sort(Double::compare);
        }
        return values;
    }

    private static double minPair(List<PortPlacement> ports) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < ports.size(); i++) {
            for (int j = i + 1; j < ports.size(); j++) {
                min = Math.min(min, Geometry.distance(ports.get(i).point(), ports.get(j).point()));
            }
        }
        return Double.isFinite(min) ? min : 0;
    }

    private static double avoidPenalty(double minAvoidDistance) {
        if (minAvoidDistance == Double.POSITIVE_INFINITY) {
            return 0;
        }
        if (minAvoidDistance <= 0) {
            return 500;
        }
        return Math.max(0, 8 - minAvoidDistance) * 3;
    }

    private static SearchHit better(SearchHit current, SearchHit candidate, CaseGeometry geometry) {
        if (current == null) {
            return candidate;
        }
        if (current.feasible != candidate.feasible) {
            return candidate.feasible ? candidate : current;
        }
        if (!current.feasible) {
            int failed = Integer.compare(
                    candidate.chosen.evaluation.failedHardCount(),
                    current.chosen.evaluation.failedHardCount()
            );
            if (failed != 0) {
                return failed < 0 ? candidate : current;
            }
        } else if (candidate.phase != current.phase) {
            return candidate.phase.ordinal() < current.phase.ordinal() ? candidate : current;
        }
        int score = Double.compare(candidate.chosen.score, current.chosen.score);
        if (score != 0) {
            return score < 0 ? candidate : current;
        }
        return room(candidate, geometry) > room(current, geometry) ? candidate : current;
    }

    private static double room(SearchHit hit, CaseGeometry geometry) {
        Point2 flank = hit.side == AccessSide.LEFT ? geometry.anatomy.flankLeft() : geometry.anatomy.flankRight();
        PortPlacement camera = cameraOf(hit.chosen.ports);
        if (camera == null || flank == null) {
            return 0;
        }
        return Geometry.distance(camera.point(), flank);
    }

    private static PortPlacement cameraOf(List<PortPlacement> ports) {
        return ports.stream().filter(port -> port.role() == PortRole.CAMERA).findFirst().orElse(null);
    }

    private static String docking(TechniqueVariant variant, boolean bilateral) {
        if (variant == TechniqueVariant.ETEP_SUPRAPUBIC) {
            return "suprapubico";
        }
        if (variant == TechniqueVariant.ETEP_TOP) {
            return "cranial";
        }
        return bilateral ? "lateral+double" : "lateral";
    }

    private static String dockingLabel(String docking) {
        return switch (docking) {
            case "suprapubico" -> "suprapúbico";
            case "cranial" -> "cranial";
            case "lateral+double" -> "lateral com double docking";
            default -> "lateral";
        };
    }

    private static String cart(TechniqueVariant variant, AccessSide side) {
        if (variant == TechniqueVariant.ETEP_SUPRAPUBIC) {
            return "suprapúbico";
        }
        if (variant == TechniqueVariant.ETEP_TOP) {
            return "cranial";
        }
        return side == AccessSide.LEFT ? "lateral esquerda" : "lateral direita";
    }

    private static String sideLabel(AccessSide side) {
        return side == AccessSide.LEFT ? "esquerda" : "direita";
    }

    private static String portLabel(PortRole role) {
        return switch (role) {
            case CAMERA -> "Câmera";
            case WORKING_1 -> "Portal 1";
            case WORKING_2 -> "Portal 2";
            case WORKING_3 -> "Portal 3";
            case ASSISTANT -> "Auxiliar";
        };
    }

    private static String pairText(List<InterportalDistance> pairs) {
        if (pairs.isEmpty()) {
            return "sem pares robóticos";
        }
        List<String> parts = new ArrayList<>();
        for (InterportalDistance pair : pairs) {
            parts.add(ConstraintEvaluator.cm(pair.distanceCm()) + " cm");
        }
        return "distância interportal " + String.join(" e ", parts);
    }

    private static boolean isHard(String id) {
        return switch (id) {
            case "H01", "H02", "H03", "H04", "H05", "H06", "H07", "H08", "H09" -> true;
            default -> false;
        };
    }

    private enum Phase {
        PREFERRED,
        WIDENED,
        RELAXED,
        FALLBACK
    }

    private record Candidate(
            double offset,
            double spacing,
            double anchorY,
            List<PortPlacement> ports,
            Evaluation evaluation,
            double score
    ) {}

    private record SearchHit(
            AccessSide side,
            TechniqueVariant variant,
            Phase phase,
            Candidate chosen,
            List<Candidate> matches,
            boolean feasible
    ) {}
}
