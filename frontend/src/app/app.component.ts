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
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly chart = { width: 700, height: 820, padding: 56 };

  /**
   * Silhueta frontal em coordenadas do plano: (0, 0) = umbigo.
   * +Y sobe (cabeça), +X vai para a direita da imagem.
   * Direita/esquerda dos marcos são da pessoa (vista anterior).
   */
  private readonly torsoRight: Point2[] = [
    [0, 56],
    [3.6, 55.4],
    [7.2, 52.2],
    [8.4, 47],
    [7.6, 41.2],
    [4.4, 37.6],
    [3.8, 34.2],
    [11.5, 32.6],
    [20.2, 31.8],
    [24.4, 28.2],
    [26.6, 21],
    [27.2, 11],
    [26.4, 1],
    [24.8, -9],
    [23.2, -16.2],
    [21.2, -18.4],
    [19.6, -16],
    [19.8, -8],
    [19.2, 2.5],
    [17.2, 14.5],
    [12.8, 21.4],
    [12.4, 12.2],
    [11.4, 4.2],
    [10.8, -0.6],
    [14.2, -6.2],
    [16.6, -11.4],
    [16.2, -18.4],
    [13.4, -26.2],
    [8.2, -28.4],
    [3.2, -24],
    [0, -19]
  ];

  private readonly bodyExtent = { minX: -34, maxX: 34, minY: -32, maxY: 60 };

  landmarks = signal<Landmark[]>([
    { id: 'xiphoid', label: 'Processo xifoide', code: 'Px', x: null, y: null },
    { id: 'pubic-symphysis', label: 'Sínfise púbica', code: 'SP', x: null, y: null },
    { id: 'asis-right', label: 'EIAS (Espinha ilíaca anterossuperior) direita', code: 'EIAS D', x: null, y: null },
    { id: 'asis-left', label: 'EIAS (Espinha ilíaca anterossuperior) esquerda', code: 'EIAS E', x: null, y: null },
    { id: 'costal-right', label: 'Rebordo costal direito', code: 'RC D', x: null, y: null },
    { id: 'costal-left', label: 'Rebordo costal esquerdo', code: 'RC E', x: null, y: null },
    { id: 'abdomen-right', label: 'Limite lateral direito do abdome na altura do umbigo', code: 'Abd D', x: null, y: null },
    { id: 'abdomen-left', label: 'Limite lateral esquerdo do abdome na altura do umbigo', code: 'Abd E', x: null, y: null },
    { id: 'rectus-right', label: 'Limite lateral direito dos músculos retos do abdome', code: 'Reto D', x: null, y: null, optional: true },
    { id: 'rectus-left', label: 'Limite lateral esquerdo dos músculos retos do abdome', code: 'Reto E', x: null, y: null, optional: true }
  ]);

  includeRectusLimits = signal(false);
  points = signal<Point[]>([]);
  error = signal('');

  requiredLandmarks = computed(() => this.landmarks().filter(item => !item.optional));
  optionalLandmarks = computed(() => this.landmarks().filter(item => item.optional));

  visibleLandmarks = computed(() => {
    const includeRectus = this.includeRectusLimits();
    return this.landmarks().filter(item => !item.optional || includeRectus);
  });

  plottedLandmarks = computed(() =>
    this.visibleLandmarks().filter(
      (item): item is Landmark & { x: number; y: number } => item.x != null && item.y != null
    )
  );

  view = computed(() => {
    const pts = [...this.points(), ...this.plottedLandmarks()];
    // Vista anterior: X anatômico positivo (direita da pessoa) aparece à esquerda da imagem.
    const xs = [this.bodyExtent.minX, this.bodyExtent.maxX, 0, ...pts.map(p => -p.x)];
    const ys = [this.bodyExtent.minY, this.bodyExtent.maxY, 0, ...pts.map(p => p.y)];
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

  torsoPath = computed(() => {
    const right = this.torsoRight;
    const left = right
      .slice(1, -1)
      .reverse()
      .map(([x, y]): Point2 => [-x, y]);
    return this.closedSpline([...right, ...left]);
  });

  gridX = computed(() => this.ticks(this.view().minX, this.view().maxX));
  gridY = computed(() => this.ticks(this.view().minY, this.view().maxY));

  constructor(private readonly calculationService: CalculationService) {}

  setCoord(id: string, axis: Axis, value: number | string | null): void {
    const parsed = value === null || value === '' || Number.isNaN(Number(value)) ? null : Number(value);
    this.landmarks.update(items =>
      items.map(item => item.id === id ? { ...item, [axis]: parsed } : item)
    );
  }

  calculate(): void {
    const inputs: CalculationInput[] = this.plottedLandmarks().map(item => ({
      name: item.label,
      x: item.x,
      y: item.y
    }));

    if (inputs.length === 0) {
      this.error.set('Preencha ao menos um marco anatômico com X e Y.');
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
    return v.offsetX + (-x - v.minX) * v.scale;
  }

  svgY(y: number): number {
    const v = this.view();
    return v.height - v.offsetY - (y - v.minY) * v.scale;
  }

  planeTransform(): string {
    const v = this.view();
    return `translate(${this.svgX(0)} ${this.svgY(0)}) scale(${v.scale} ${-v.scale})`;
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
