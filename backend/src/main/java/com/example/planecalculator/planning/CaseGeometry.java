package com.example.planecalculator.planning;

import com.example.planecalculator.dto.PlanningRequest;
import com.example.planecalculator.dto.PlanningRequest.AnatomyInput;
import com.example.planecalculator.dto.PlanningRequest.ExclusionInput;
import com.example.planecalculator.geometry.Geometry;
import com.example.planecalculator.geometry.Point2;
import com.example.planecalculator.rules.EvalContext;
import com.example.planecalculator.rules.ExclusionZone;
import com.example.planecalculator.rules.NamedPoint;

import java.util.ArrayList;
import java.util.List;

final class CaseGeometry {

    final AnatomyInput anatomy;
    final List<Point2> abdomen;
    final List<Point2> defect;
    final List<Point2> workspace;
    final Point2 target;
    final List<NamedPoint> bones;
    final List<ExclusionZone> exclusions;
    final int quadrants;
    final int numberOfDefects;
    final boolean assistantRequired;

    private CaseGeometry(
            AnatomyInput anatomy,
            List<Point2> abdomen,
            List<Point2> defect,
            List<Point2> workspace,
            Point2 target,
            List<NamedPoint> bones,
            List<ExclusionZone> exclusions,
            int quadrants,
            int numberOfDefects,
            boolean assistantRequired
    ) {
        this.anatomy = anatomy;
        this.abdomen = abdomen;
        this.defect = defect;
        this.workspace = workspace;
        this.target = target;
        this.bones = bones;
        this.exclusions = exclusions;
        this.quadrants = quadrants;
        this.numberOfDefects = numberOfDefects;
        this.assistantRequired = assistantRequired;
    }

    static CaseGeometry from(PlanningRequest request, ProtocolParameters protocol) {
        AnatomyInput anatomy = normalized(request.anatomy());
        List<String> missing = missingLandmarks(anatomy);
        if (!missing.isEmpty()) {
            throw new PlanningException("Marcos obrigatórios ausentes: " + String.join(", ", missing) + ".");
        }
        if (request.defect().widthCm() <= 0 || request.defect().heightCm() <= 0) {
            throw new PlanningException("A hérnia precisa de largura e extensão craniocaudal positivas.");
        }
        int ports = request.procedure().numberOfRoboticPorts();
        if (ports < 3 || ports > 4) {
            throw new PlanningException("O protótipo aceita 3 ou 4 portais robóticos.");
        }

        List<Point2> abdomen = List.of(
                anatomy.xiphoid(),
                anatomy.costalRight(),
                anatomy.flankRight(),
                anatomy.asisRight(),
                anatomy.pubis(),
                anatomy.asisLeft(),
                anatomy.flankLeft(),
                anatomy.costalLeft()
        );
        Point2 center = request.defect().center();
        List<Point2> defect = Geometry.rectangle(center, request.defect().widthCm(), request.defect().heightCm());
        List<Point2> workspace = workspace(request, protocol, center);
        if (workspace.size() < 3) {
            throw new PlanningException("O campo-alvo precisa de pelo menos três vértices.");
        }
        List<ExclusionZone> exclusions = new ArrayList<>();
        if (request.exclusionZones() != null) {
            for (ExclusionInput zone : request.exclusionZones()) {
                if (zone.polygon() == null || zone.polygon().size() < 3) {
                    throw new PlanningException("Cada área de exclusão precisa de pelo menos três vértices.");
                }
                exclusions.add(new ExclusionZone(zone.category(), zone.polygon()));
            }
        }
        int defects = request.defect().numberOfDefects() == null ? 1 : request.defect().numberOfDefects();
        return new CaseGeometry(
                anatomy,
                abdomen,
                defect,
                workspace,
                Geometry.centroid(workspace),
                bones(anatomy),
                exclusions,
                Geometry.quadrantCount(workspace),
                defects,
                request.procedure().assistantPortRequired()
        );
    }

    EvalContext context(List<PortPlacement> ports, ProtocolParameters protocol) {
        return new EvalContext(
                abdomen,
                bones,
                exclusions,
                workspace,
                target,
                ports,
                protocol,
                assistantRequired,
                quadrants
        );
    }

    private static List<Point2> workspace(PlanningRequest request, ProtocolParameters protocol, Point2 center) {
        if (request.workspace().mode() == WorkspaceMode.CUSTOM) {
            return request.workspace().polygon() == null ? List.of() : request.workspace().polygon();
        }
        double overlap = protocol.meshOverlapCm();
        return Geometry.rectangle(
                center,
                request.defect().widthCm() + 2 * overlap,
                request.defect().heightCm() + 2 * overlap
        );
    }

    private static AnatomyInput normalized(AnatomyInput anatomy) {
        return new AnatomyInput(
                anatomy.xiphoid(),
                anatomy.pubis(),
                anatomy.asisRight(),
                anatomy.asisLeft(),
                anatomy.costalRight(),
                anatomy.costalLeft(),
                flank(anatomy.flankRight()),
                flank(anatomy.flankLeft()),
                anatomy.rectusRight(),
                anatomy.rectusLeft()
        );
    }

    private static Point2 flank(Point2 point) {
        if (point == null) {
            return null;
        }
        return new Point2(point.x(), 0);
    }

    private static List<String> missingLandmarks(AnatomyInput anatomy) {
        List<String> missing = new ArrayList<>();
        if (anatomy.xiphoid() == null) missing.add("processo xifoide");
        if (anatomy.pubis() == null) missing.add("sínfise púbica");
        if (anatomy.asisRight() == null) missing.add("EIAS direita");
        if (anatomy.asisLeft() == null) missing.add("EIAS esquerda");
        if (anatomy.costalRight() == null) missing.add("rebordo costal direito");
        if (anatomy.costalLeft() == null) missing.add("rebordo costal esquerdo");
        if (anatomy.flankRight() == null) missing.add("limite lateral direito");
        if (anatomy.flankLeft() == null) missing.add("limite lateral esquerdo");
        return missing;
    }

    private static List<NamedPoint> bones(AnatomyInput anatomy) {
        List<NamedPoint> bones = new ArrayList<>();
        addBone(bones, "processo xifoide", anatomy.xiphoid());
        addBone(bones, "sínfise púbica", anatomy.pubis());
        addBone(bones, "EIAS direita", anatomy.asisRight());
        addBone(bones, "EIAS esquerda", anatomy.asisLeft());
        addBone(bones, "rebordo costal direito", anatomy.costalRight());
        addBone(bones, "rebordo costal esquerdo", anatomy.costalLeft());
        return bones;
    }

    private static void addBone(List<NamedPoint> bones, String name, Point2 point) {
        if (point != null) {
            bones.add(new NamedPoint(name, point));
        }
    }
}
