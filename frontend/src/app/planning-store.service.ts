import { Injectable, computed, signal } from '@angular/core';
import {
  AccessSide,
  DrawMode,
  ExclusionCategory,
  ExclusionType,
  ExclusionZone,
  PlanningRequest,
  PlanningResponse,
  Point2,
  PortRole,
  ProtocolParameters,
  SurgicalTechnique,
  WorkspaceMode,
  centroid,
  rectangle
} from './planning.models';

export interface Landmark {
  id: string;
  label: string;
  code: string;
  x: number | null;
  y: number | null;
  optional?: boolean;
  xOnly?: boolean;
}

@Injectable({ providedIn: 'root' })
export class PlanningStore {
  readonly landmarks = signal<Landmark[]>([
    { id: 'xiphoid', label: 'Processo xifoide', code: 'Px', x: null, y: null },
    { id: 'pubic-symphysis', label: 'Sínfise púbica', code: 'SP', x: null, y: null },
    { id: 'asis-right', label: 'EIAS direita', code: 'EIAS D', x: null, y: null },
    { id: 'asis-left', label: 'EIAS esquerda', code: 'EIAS E', x: null, y: null },
    { id: 'costal-right', label: 'Rebordo costal direito', code: 'RC D', x: null, y: null },
    { id: 'costal-left', label: 'Rebordo costal esquerdo', code: 'RC E', x: null, y: null },
    { id: 'abdomen-right', label: 'Limite lateral direito do abdome na altura do umbigo', code: 'Abd D', x: null, y: 0, xOnly: true },
    { id: 'abdomen-left', label: 'Limite lateral esquerdo do abdome na altura do umbigo', code: 'Abd E', x: null, y: 0, xOnly: true },
    { id: 'rectus-right', label: 'Limite lateral direito dos músculos retos', code: 'Reto D', x: null, y: null, optional: true },
    { id: 'rectus-left', label: 'Limite lateral esquerdo dos músculos retos', code: 'Reto E', x: null, y: null, optional: true }
  ]);

  readonly includeRectus = signal(false);
  readonly height = signal<number | null>(null);
  readonly weight = signal<number | null>(null);
  readonly abdominalCircumference = signal<number | null>(null);

  readonly defectX = signal<number | null>(null);
  readonly defectY = signal<number | null>(null);
  readonly defectWidth = signal<number | null>(null);
  readonly defectHeight = signal<number | null>(null);
  readonly numberOfDefects = signal(1);
  readonly ehsLocation = signal('');

  readonly workspaceMode = signal<WorkspaceMode>('CALCULATED');
  readonly workspacePoints = signal<Point2[]>([]);
  readonly exclusions = signal<ExclusionZone[]>([]);
  readonly exclusionType = signal<ExclusionType>('SCAR');
  readonly exclusionCategory = signal<ExclusionCategory>('FORBIDDEN');
  readonly drawMode = signal<DrawMode>('none');
  readonly draftPoints = signal<Point2[]>([]);

  readonly platform = signal<'DAVINCI_X' | 'DAVINCI_XI'>('DAVINCI_XI');
  readonly technique = signal<SurgicalTechnique>('TAPP');
  readonly accessSide = signal<AccessSide>('AUTO');
  readonly numberOfRoboticPorts = signal(3);
  readonly assistantPortRequired = signal(false);
  readonly bilateralTar = signal(false);
  readonly measurementPhase = signal<'AFTER_INSUFFLATION' | 'BEFORE_INSUFFLATION'>('AFTER_INSUFFLATION');
  readonly pneumoperitoneum = signal<number | null>(null);
  readonly tableFlexion = signal<number | null>(null);
  readonly patientPosition = signal('');

  readonly protocol = signal<ProtocolParameters | null>(null);
  readonly result = signal<PlanningResponse | null>(null);
  readonly preservedBand = signal<PlanningResponse['admissibleRange']>(null);
  readonly error = signal('');
  readonly loading = signal(false);
  readonly protocolMessage = signal('');

  readonly bmi = computed(() => {
    const height = this.height();
    const weight = this.weight();
    if (height == null || weight == null || height <= 0 || weight <= 0) {
      return null;
    }
    const meters = height > 3 ? height / 100 : height;
    return weight / (meters * meters);
  });

  readonly bmiClass = computed(() => {
    const value = this.bmi();
    if (value == null) {
      return '';
    }
    if (value < 18.5) return 'baixo peso';
    if (value < 25) return 'eutrófico';
    if (value < 30) return 'sobrepeso';
    if (value < 35) return 'obesidade grau I';
    if (value < 40) return 'obesidade grau II';
    return 'obesidade grau III';
  });

  readonly requiredLandmarks = computed(() => this.landmarks().filter(item => !item.optional));
  readonly optionalLandmarks = computed(() => this.landmarks().filter(item => item.optional));
  readonly visibleLandmarks = computed(() => {
    const includeRectus = this.includeRectus();
    return this.landmarks().filter(item => !item.optional || includeRectus);
  });

  readonly plottedLandmarks = computed(() =>
    this.visibleLandmarks()
      .map(item => {
        const coords = this.landmarkPoint(item);
        return coords ? { ...item, ...coords } : null;
      })
      .filter((item): item is Landmark & Point2 => item != null)
  );

  readonly defectCenter = computed<Point2 | null>(() => {
    const x = this.defectX();
    const y = this.defectY();
    return x == null || y == null ? null : { x, y };
  });

  readonly defectPolygon = computed(() => {
    const center = this.defectCenter();
    const width = this.defectWidth();
    const height = this.defectHeight();
    if (!center || width == null || height == null || width <= 0 || height <= 0) {
      return [];
    }
    return rectangle(center, width, height);
  });

  readonly defectLimits = computed(() => {
    const center = this.defectCenter();
    const width = this.defectWidth();
    const height = this.defectHeight();
    if (!center || width == null || height == null || width <= 0 || height <= 0) {
      return null;
    }
    return {
      right: center.x + width / 2,
      left: center.x - width / 2,
      cranial: center.y + height / 2,
      caudal: center.y - height / 2
    };
  });

  readonly workspacePolygon = computed(() => {
    if (this.workspaceMode() === 'CUSTOM') {
      return this.workspacePoints();
    }
    const center = this.defectCenter();
    const width = this.defectWidth();
    const height = this.defectHeight();
    if (!center || width == null || height == null || width <= 0 || height <= 0) {
      return [];
    }
    const overlap = this.protocol()?.meshOverlapCm ?? 5;
    return rectangle(center, width + 2 * overlap, height + 2 * overlap);
  });

  readonly previewTarget = computed(() => {
    const result = this.result();
    if (result?.target) {
      return result.target;
    }
    return centroid(this.workspacePolygon());
  });

  readonly abdomenPolygon = computed(() => {
    const ids = [
      'xiphoid',
      'costal-right',
      'abdomen-right',
      'asis-right',
      'pubic-symphysis',
      'asis-left',
      'abdomen-left',
      'costal-left'
    ];
    const points = ids.map(id => this.point(id));
    return points.every((point): point is Point2 => point != null) ? points : [];
  });

  readonly bandPolygon = computed(() =>
    this.result()?.admissibleRange?.bandPolygon ?? this.preservedBand()?.bandPolygon ?? []
  );

  setNumeric(field: 'height' | 'weight' | 'abdominalCircumference' | 'defectX' | 'defectY' | 'defectWidth' | 'defectHeight' | 'pneumoperitoneum' | 'tableFlexion', value: number | string | null): void {
    this[field].set(this.parseNumber(value));
  }

  setCoord(id: string, axis: 'x' | 'y', value: number | string | null): void {
    const parsed = this.parseNumber(value);
    this.landmarks.update(items =>
      items.map(item => {
        if (item.id !== id) {
          return item;
        }
        if (item.xOnly) {
          return { ...item, x: parsed, y: 0 };
        }
        return { ...item, [axis]: parsed };
      })
    );
  }

  setProtocolField(key: keyof ProtocolParameters, value: number | string | null): void {
    const parsed = this.parseNumber(value);
    if (parsed == null) {
      return;
    }
    this.protocol.update(current => current ? { ...current, [key]: parsed } : current);
    this.protocolMessage.set('');
  }

  startWorkspaceDrawing(): void {
    this.workspaceMode.set('CUSTOM');
    this.drawMode.set('workspace');
    this.draftPoints.set([...this.workspacePoints()]);
  }

  startExclusionDrawing(): void {
    this.drawMode.set('exclusion');
    this.draftPoints.set([]);
  }

  addDraftPoint(point: Point2): void {
    if (this.drawMode() === 'none') {
      return;
    }
    this.draftPoints.update(points => [...points, point]);
  }

  undoDraft(): void {
    this.draftPoints.update(points => points.slice(0, -1));
  }

  closeDraft(): void {
    const points = this.draftPoints();
    if (points.length < 3) {
      this.error.set('Feche o polígono com pelo menos três pontos.');
      return;
    }
    if (this.drawMode() === 'workspace') {
      this.workspacePoints.set(points);
      this.workspaceMode.set('CUSTOM');
    }
    if (this.drawMode() === 'exclusion') {
      this.exclusions.update(zones => [
        ...zones,
        {
          id: crypto.randomUUID(),
          type: this.exclusionType(),
          category: this.exclusionCategory(),
          polygon: points
        }
      ]);
    }
    this.draftPoints.set([]);
    this.drawMode.set('none');
    this.error.set('');
  }

  cancelDraft(): void {
    this.draftPoints.set([]);
    this.drawMode.set('none');
  }

  removeExclusion(id: string): void {
    this.exclusions.update(zones => zones.filter(zone => zone.id !== id));
  }

  clearWorkspace(): void {
    this.workspacePoints.set([]);
    this.draftPoints.set([]);
    this.drawMode.set('none');
  }

  movePort(role: PortRole, point: Point2): void {
    this.result.update(current => {
      if (!current) {
        return current;
      }
      return {
        ...current,
        ports: current.ports.map(port => port.role === role ? { ...port, x: point.x, y: point.y } : port)
      };
    });
  }

  buildRequest(ports: { role: PortRole; x: number; y: number }[] | null = null): PlanningRequest | string {
    const required = [
      ['xiphoid', 'processo xifoide'],
      ['pubic-symphysis', 'sínfise púbica'],
      ['asis-right', 'EIAS direita'],
      ['asis-left', 'EIAS esquerda'],
      ['costal-right', 'rebordo costal direito'],
      ['costal-left', 'rebordo costal esquerdo'],
      ['abdomen-right', 'limite lateral direito'],
      ['abdomen-left', 'limite lateral esquerdo']
    ] as const;
    const missing = required.filter(([id]) => this.point(id) == null).map(([, label]) => label);
    if (missing.length > 0) {
      return `Preencha os marcos: ${missing.join(', ')}.`;
    }
    const center = this.defectCenter();
    const width = this.defectWidth();
    const height = this.defectHeight();
    const missingDefect = [
      center == null ? 'o centro' : null,
      width == null || width <= 0 ? 'a largura transversal' : null,
      height == null || height <= 0 ? 'a extensão craniocaudal' : null
    ].filter((item): item is string => item != null);
    if (center == null || width == null || height == null || width <= 0 || height <= 0) {
      const listed = missingDefect.length === 1
        ? missingDefect[0]
        : `${missingDefect.slice(0, -1).join(', ')} e ${missingDefect.at(-1)}`;
      return `Informe ${listed} da hérnia.`;
    }
    if (this.workspaceMode() === 'CUSTOM' && this.workspacePoints().length < 3) {
      return 'Marque o campo-alvo com pelo menos três pontos ou use a área calculada.';
    }
    const defects = this.numberOfDefects();
    return {
      patient: {
        heightCm: this.height(),
        weightKg: this.weight(),
        bmi: this.bmi(),
        abdominalCircumferenceCm: this.abdominalCircumference()
      },
      anatomy: {
        xiphoid: this.point('xiphoid'),
        pubis: this.point('pubic-symphysis'),
        asisRight: this.point('asis-right'),
        asisLeft: this.point('asis-left'),
        costalRight: this.point('costal-right'),
        costalLeft: this.point('costal-left'),
        flankRight: this.point('abdomen-right'),
        flankLeft: this.point('abdomen-left'),
        rectusRight: this.includeRectus() ? this.point('rectus-right') : null,
        rectusLeft: this.includeRectus() ? this.point('rectus-left') : null
      },
      defect: {
        center,
        widthCm: width,
        heightCm: height,
        numberOfDefects: defects > 0 ? defects : 1,
        ehsLocation: this.ehsLocation().trim() || null
      },
      workspace: {
        mode: this.workspaceMode(),
        polygon: this.workspaceMode() === 'CUSTOM' ? this.workspacePoints() : null
      },
      exclusionZones: this.exclusions(),
      procedure: {
        platform: this.platform(),
        technique: this.technique(),
        accessSide: this.accessSide(),
        numberOfRoboticPorts: this.numberOfRoboticPorts(),
        assistantPortRequired: this.assistantPortRequired(),
        bilateralTar: this.technique() === 'ROBOTAR' && this.bilateralTar(),
        measurementPhase: this.measurementPhase(),
        pneumoperitoneumMmhg: this.pneumoperitoneum(),
        tableFlexionDeg: this.tableFlexion(),
        patientPosition: this.patientPosition().trim() || null
      },
      ports
    };
  }

  private point(id: string): Point2 | null {
    const item = this.landmarks().find(entry => entry.id === id);
    return item ? this.landmarkPoint(item) : null;
  }

  private landmarkPoint(item: Landmark): Point2 | null {
    if (item.x == null) {
      return null;
    }
    if (item.xOnly) {
      const width = Math.abs(item.x);
      return { x: item.id === 'abdomen-left' ? -width : width, y: 0 };
    }
    if (item.y == null) {
      return null;
    }
    return { x: item.x, y: item.y };
  }

  private parseNumber(value: number | string | null): number | null {
    if (value === null || value === '' || Number.isNaN(Number(value))) {
      return null;
    }
    return Number(value);
  }
}
