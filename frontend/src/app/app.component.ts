import { CommonModule } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CalculationInput, CalculationService, Point } from './calculation.service';

type Point2 = [number, number];
type Axis = 'x' | 'y';

export interface Landmark {
  id: string;
  label: string;
  code: string;
  x: number | null;
  y: number | null;
  optional?: boolean;
  xOnly?: boolean;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly chart = { width: 700, height: 820, padding: 48 };
  private readonly defaultWaist = 11;

  /**
   * Perfil do hemitórax em fração da cintura (x = 1 no umbigo).
   * Sem cabeça nem braços: do tórax superior à pelve.
   */
  private readonly waistProfile: Point2[] = [
    [0.50, 26],
    [0.58, 23],
    [0.76, 19],
    [0.90, 14],
    [0.86, 7],
    [1.00, 0],
    [1.16, -6],
    [1.22, -11],
    [1.08, -16],
    [0.58, -20],
    [0.18, -22]
  ];

  landmarks = signal<Landmark[]>([
    { id: 'xiphoid', label: 'Processo xifoide', code: 'Px', x: null, y: null },
    { id: 'pubic-symphysis', label: 'Sínfise púbica', code: 'SP', x: null, y: null },
    { id: 'asis-right', label: 'EIAS (Espinha ilíaca anterossuperior) direita', code: 'EIAS D', x: null, y: null },
    { id: 'asis-left', label: 'EIAS (Espinha ilíaca anterossuperior) esquerda', code: 'EIAS E', x: null, y: null },
    { id: 'costal-right', label: 'Rebordo costal direito', code: 'RC D', x: null, y: null },
    { id: 'costal-left', label: 'Rebordo costal esquerdo', code: 'RC E', x: null, y: null },
    { id: 'abdomen-right', label: 'Limite lateral direito do abdome na altura do umbigo', code: 'Abd D', x: null, y: 0, xOnly: true },
    { id: 'abdomen-left', label: 'Limite lateral esquerdo do abdome na altura do umbigo', code: 'Abd E', x: null, y: 0, xOnly: true },
    { id: 'rectus-right', label: 'Limite lateral direito dos músculos retos do abdome', code: 'Reto D', x: null, y: null, optional: true },
    { id: 'rectus-left', label: 'Limite lateral esquerdo dos músculos retos do abdome', code: 'Reto E', x: null, y: null, optional: true }
  ]);

  includeRectusLimits = signal(false);
  height = signal<number | null>(null);
  weight = signal<number | null>(null);
  abdominalCircumference = signal<number | null>(null);
  points = signal<Point[]>([]);
  error = signal('');

  bmi = computed(() => {
    const height = this.height();
    const weight = this.weight();
    if (height == null || weight == null || height <= 0 || weight <= 0) {
      return null;
    }
    const meters = height > 3 ? height / 100 : height;
    return weight / (meters * meters);
  });

  bmiClass = computed(() => {
    const value = this.bmi();
    if (value == null) {
      return '';
    }
    if (value < 18.5) {
      return 'baixo peso';
    }
    if (value < 25) {
      return 'eutrófico';
    }
    if (value < 30) {
      return 'sobrepeso';
    }
    if (value < 35) {
      return 'obesidade grau I';
    }
    if (value < 40) {
      return 'obesidade grau II';
    }
    return 'obesidade grau III';
  });

  requiredLandmarks = computed(() => this.landmarks().filter(item => !item.optional));
  optionalLandmarks = computed(() => this.landmarks().filter(item => item.optional));

  visibleLandmarks = computed(() => {
    const includeRectus = this.includeRectusLimits();
    return this.landmarks().filter(item => !item.optional || includeRectus);
  });

  abdomenRightX = computed(() => this.waistIntercept('abdomen-right', this.defaultWaist));
  abdomenLeftX = computed(() => this.waistIntercept('abdomen-left', -this.defaultWaist));

  torsoPoints = computed((): Point2[] => {
    const rightWaist = Math.abs(this.abdomenRightX());
    const leftWaist = Math.abs(this.abdomenLeftX());
    const top: Point2 = [0, 27];
    const bottom: Point2 = [0, -22];
    const rightSide = this.sideProfile(rightWaist);
    const leftSide = this.sideProfile(leftWaist)
      .slice()
      .reverse()
      .map(([x, y]): Point2 => [-x, y]);
    return [top, ...rightSide, bottom, ...leftSide];
  });

  plottedLandmarks = computed(() =>
    this.visibleLandmarks()
      .map(item => {
        const coords = this.landmarkCoords(item);
        return coords ? { ...item, ...coords } : null;
      })
      .filter((item): item is Landmark & { x: number; y: number } => item != null)
  );

  view = computed(() => {
    const torso = this.torsoPoints();
    const pts = [...this.points(), ...this.plottedLandmarks()];
    const xs = [...torso.map(p => p[0]), 0, ...pts.map(p => p.x)];
    const ys = [...torso.map(p => p[1]), 0, ...pts.map(p => p.y)];
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    const { width, height, padding } = this.chart;
    const scale = Math.min(
      (width - 2 * padding) / (maxX - minX || 1),
      (height - 2 * padding) / (maxY - minY || 1)
    );
    const contentW = (maxX - minX) * scale;
    const contentH = (maxY - minY) * scale;
    return {
      minX,
      maxX,
      minY,
      maxY,
      scale,
      offsetX: (width - contentW) / 2,
      offsetY: (height - contentH) / 2,
      width,
      height
    };
  });

  torsoPath = computed(() => this.closedSpline(this.torsoPoints()));

  gridX = computed(() => this.ticks(this.view().minX, this.view().maxX));
  gridY = computed(() => this.ticks(this.view().minY, this.view().maxY));

  constructor(private readonly calculationService: CalculationService) {}

  setNumeric(field: 'height' | 'weight' | 'abdominalCircumference', value: number | string | null): void {
    this[field].set(this.parseNumber(value));
  }

  setCoord(id: string, axis: Axis, value: number | string | null): void {
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

  calculate(): void {
    const inputs: CalculationInput[] = this.plottedLandmarks().map(item => ({
      name: item.label,
      x: item.x,
      y: item.y
    }));

    if (inputs.length === 0) {
      this.error.set('Preencha ao menos um marco anatômico.');
      return;
    }

    this.error.set('');
    this.calculationService.calculate(inputs).subscribe({
      next: response => this.points.set(response.points),
      error: () => this.error.set('Não foi possível conectar ao backend.')
    });
  }

  svgX(x: number): number {
    const v = this.view();
    return v.offsetX + (x - v.minX) * v.scale;
  }

  svgY(y: number): number {
    const v = this.view();
    return v.height - v.offsetY - (y - v.minY) * v.scale;
  }

  planeTransform(): string {
    const v = this.view();
    return `translate(${this.svgX(0)} ${this.svgY(0)}) scale(${v.scale} ${-v.scale})`;
  }

  private landmarkCoords(item: Landmark): { x: number; y: number } | null {
    if (item.x == null) {
      return null;
    }
    if (item.xOnly) {
      const width = Math.abs(item.x);
      return {
        x: item.id === 'abdomen-left' ? -width : width,
        y: 0
      };
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

  private waistIntercept(id: string, fallback: number): number {
    const item = this.landmarks().find(entry => entry.id === id);
    if (item?.x == null) {
      return fallback;
    }
    const width = Math.abs(item.x);
    return id === 'abdomen-left' ? -width : width;
  }

  private sideProfile(waist: number): Point2[] {
    const width = Math.max(waist, 0.5);
    return this.waistProfile.map(([factor, y]) => [factor * width, y]);
  }

  private ticks(min: number, max: number): number[] {
    const range = max - min;
    const step = range > 200 ? 50 : range > 90 ? 10 : 5;
    const start = Math.ceil(min / step) * step;
    const values: number[] = [];
    for (let value = start; value <= max + 1e-9; value += step) {
      values.push(Number(value.toFixed(4)));
    }
    return values;
  }

  private closedSpline(points: Point2[]): string {
    const n = points.length;
    if (n < 3) {
      return '';
    }

    let d = `M ${points[0][0]} ${points[0][1]}`;
    for (let i = 0; i < n; i++) {
      const p0 = points[(i - 1 + n) % n];
      const p1 = points[i];
      const p2 = points[(i + 1) % n];
      const p3 = points[(i + 2) % n];
      d += ` C ${p1[0] + (p2[0] - p0[0]) / 6} ${p1[1] + (p2[1] - p0[1]) / 6}` +
        ` ${p2[0] - (p3[0] - p1[0]) / 6} ${p2[1] - (p3[1] - p1[1]) / 6}` +
        ` ${p2[0]} ${p2[1]}`;
    }
    return `${d} Z`;
  }
}
