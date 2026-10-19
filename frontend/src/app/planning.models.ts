export interface Point2 {
  x: number;
  y: number;
}

export type SurgicalTechnique = 'TAPP' | 'TARM_TARUP' | 'ETEP' | 'ROBOTAR';
export type AccessSide = 'RIGHT' | 'LEFT' | 'AUTO';
export type WorkspaceMode = 'CALCULATED' | 'CUSTOM';
export type ExclusionType = 'SCAR' | 'OSTOMY' | 'MESH' | 'INCISION' | 'CUSTOM';
export type ExclusionCategory = 'FORBIDDEN' | 'AVOID';
export type PortRole = 'CAMERA' | 'WORKING_1' | 'WORKING_2' | 'WORKING_3' | 'ASSISTANT';
export type DrawMode = 'none' | 'workspace' | 'exclusion';

export interface ProtocolParameters {
  interportalIdealCm: number;
  interportalMinCm: number;
  interportalManufacturerMinCm: number;
  interportalManufacturerMaxCm: number;
  cameraTargetMinCm: number;
  cameraTargetMaxCm: number;
  cameraTargetPreferredMinCm: number;
  cameraTargetPreferredMaxCm: number;
  boneClearanceMinCm: number;
  assistantToRoboticMinCm: number;
  tappPortToTargetMinCm: number;
  tappPortToTargetMaxCm: number;
  tarmLineaAlbaOffsetCm: number;
  etepCostalCaudalOffsetCm: number;
  etepMidlineLateralMinCm: number;
  etepMidlineLateralMaxCm: number;
  etepUmbilicalCaudalOffsetCm: number;
  etepLeftParamedianAbovePubisCm: number;
  etepRightParamedianAbovePubisCm: number;
  subxiphoidRelevantCm: number;
  meshOverlapCm: number;
  collinearityToleranceCm: number;
  targetCranialThresholdCm: number;
  targetCaudalThresholdCm: number;
  samplingStepCm: number;
}

export interface ExclusionZone {
  id: string;
  type: ExclusionType;
  category: ExclusionCategory;
  polygon: Point2[];
}

export interface PortDto {
  role: PortRole;
  label: string;
  x: number;
  y: number;
}

export interface AdmissibleRange {
  offsetMinCm: number;
  offsetMaxCm: number;
  recommendedOffsetCm: number;
  spacingCm: number;
  side: string;
  bandPolygon: Point2[];
}

export interface ConstraintResult {
  id: string;
  description: string;
  passed: boolean;
  detail: string;
}

export interface PlanningResponse {
  target: Point2;
  abdomenPolygon: Point2[];
  defectPolygon: Point2[];
  workspacePolygon: Point2[];
  techniqueVariant: string;
  accessSide: string;
  suggestedDocking: string;
  suggestedCartPosition: string;
  doubleDockingRecommended: boolean;
  redockingAllowed: boolean;
  configurationValid: boolean;
  generatedByPreset: boolean;
  ports: PortDto[];
  admissibleRange: AdmissibleRange | null;
  interportalDistances: { fromRole: string; toRole: string; distanceCm: number }[];
  cameraTargetDistance: number;
  boneClearances: { portRole: string; bone: string; distanceCm: number }[];
  workspaceDistances: { portRole: string; sample: string; distanceCm: number }[];
  constraints: ConstraintResult[];
  warnings: string[];
  rationale: string[];
}

export interface PlanningRequest {
  patient: {
    heightCm: number | null;
    weightKg: number | null;
    bmi: number | null;
    abdominalCircumferenceCm: number | null;
  };
  anatomy: {
    xiphoid: Point2 | null;
    pubis: Point2 | null;
    asisRight: Point2 | null;
    asisLeft: Point2 | null;
    costalRight: Point2 | null;
    costalLeft: Point2 | null;
    flankRight: Point2 | null;
    flankLeft: Point2 | null;
    rectusRight: Point2 | null;
    rectusLeft: Point2 | null;
  };
  defect: {
    center: Point2;
    widthCm: number;
    heightCm: number;
    numberOfDefects: number;
    ehsLocation: string | null;
  };
  workspace: {
    mode: WorkspaceMode;
    polygon: Point2[] | null;
  };
  exclusionZones: ExclusionZone[];
  procedure: {
    platform: 'DAVINCI_X' | 'DAVINCI_XI';
    technique: SurgicalTechnique;
    accessSide: AccessSide;
    numberOfRoboticPorts: number;
    assistantPortRequired: boolean;
    bilateralTar: boolean;
    measurementPhase: 'AFTER_INSUFFLATION' | 'BEFORE_INSUFFLATION';
    pneumoperitoneumMmhg: number | null;
    tableFlexionDeg: number | null;
    patientPosition: string | null;
  };
  ports: { role: PortRole; x: number; y: number }[] | null;
}

export const PROTOCOL_FIELDS: { key: keyof ProtocolParameters; label: string }[] = [
  { key: 'interportalIdealCm', label: 'Distância ideal entre portais' },
  { key: 'interportalMinCm', label: 'Distância mínima do protocolo' },
  { key: 'interportalManufacturerMinCm', label: 'Mínimo excepcional do fabricante' },
  { key: 'interportalManufacturerMaxCm', label: 'Máximo do fabricante entre portais' },
  { key: 'cameraTargetMinCm', label: 'Câmera–target mínimo' },
  { key: 'cameraTargetMaxCm', label: 'Câmera–target máximo' },
  { key: 'cameraTargetPreferredMinCm', label: 'Câmera–target preferencial mínimo' },
  { key: 'cameraTargetPreferredMaxCm', label: 'Câmera–target preferencial máximo' },
  { key: 'boneClearanceMinCm', label: 'Folga mínima até proeminência óssea' },
  { key: 'assistantToRoboticMinCm', label: 'Auxiliar até portal robótico' },
  { key: 'tappPortToTargetMinCm', label: 'TAPP: distância mínima da linha ao target' },
  { key: 'tappPortToTargetMaxCm', label: 'TAPP: distância máxima da linha ao target' },
  { key: 'tarmLineaAlbaOffsetCm', label: 'TARM/TARUP: referência da linha alba' },
  { key: 'etepCostalCaudalOffsetCm', label: 'eTEP lateral: caudal ao rebordo' },
  { key: 'etepMidlineLateralMinCm', label: 'eTEP: lateral mínimo da linha média' },
  { key: 'etepMidlineLateralMaxCm', label: 'eTEP: lateral máximo da linha média' },
  { key: 'etepUmbilicalCaudalOffsetCm', label: 'eTEP suprapúbico: abaixo do umbigo' },
  { key: 'etepLeftParamedianAbovePubisCm', label: 'Paramediano esquerdo acima da sínfise' },
  { key: 'etepRightParamedianAbovePubisCm', label: 'Paramediano direito acima da sínfise' },
  { key: 'subxiphoidRelevantCm', label: 'eTEP cranial: abaixo do xifoide' },
  { key: 'meshOverlapCm', label: 'Sobreposição do campo calculado' },
  { key: 'collinearityToleranceCm', label: 'Tolerância de corredor até o target' },
  { key: 'targetCranialThresholdCm', label: 'Limiar cranial do target' },
  { key: 'targetCaudalThresholdCm', label: 'Limiar caudal do target' },
  { key: 'samplingStepCm', label: 'Passo de amostragem' }
];

export const TECHNIQUE_LABELS: Record<SurgicalTechnique, string> = {
  TAPP: 'r-TAPP',
  TARM_TARUP: 'r-TARM/TARUP',
  ETEP: 'eTEP',
  ROBOTAR: 'RoboTAR'
};

export const VARIANT_LABELS: Record<string, string> = {
  TAPP: 'r-TAPP',
  TARM_TARUP: 'r-TARM/TARUP',
  ETEP_LATERAL: 'eTEP lateral',
  ETEP_SUPRAPUBIC: 'eTEP suprapúbico',
  ETEP_TOP: 'eTEP cranial',
  ROBOTAR: 'RoboTAR'
};

export const EXCLUSION_TYPE_LABELS: Record<ExclusionType, string> = {
  SCAR: 'Cicatriz',
  OSTOMY: 'Ostomia',
  MESH: 'Tela prévia',
  INCISION: 'Incisão anterior',
  CUSTOM: 'Outra'
};

export const EXCLUSION_CATEGORY_LABELS: Record<ExclusionCategory, string> = {
  FORBIDDEN: 'Proibida',
  AVOID: 'Evitar se possível'
};

export function rectangle(center: Point2, width: number, height: number): Point2[] {
  const halfW = width / 2;
  const halfH = height / 2;
  return [
    { x: center.x - halfW, y: center.y - halfH },
    { x: center.x + halfW, y: center.y - halfH },
    { x: center.x + halfW, y: center.y + halfH },
    { x: center.x - halfW, y: center.y + halfH }
  ];
}

export function centroid(points: Point2[]): Point2 | null {
  if (points.length === 0) {
    return null;
  }
  const total = points.reduce((sum, point) => ({ x: sum.x + point.x, y: sum.y + point.y }), { x: 0, y: 0 });
  return { x: total.x / points.length, y: total.y / points.length };
}

export function polygonPath(points: Point2[]): string {
  if (points.length === 0) {
    return '';
  }
  const [first, ...rest] = points;
  return `M ${first.x} ${first.y} ${rest.map(point => `L ${point.x} ${point.y}`).join(' ')} Z`;
}
