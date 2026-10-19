import { CommonModule } from '@angular/common';
import { Component, ElementRef, computed, inject, output, viewChild } from '@angular/core';
import { Point2, PortDto, PortRole, polygonPath } from './planning.models';
import { PlanningStore } from './planning-store.service';

type Point2Tuple = [number, number];

@Component({
  selector: 'app-abdomen-plane',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './abdomen-plane.component.html'
})
export class AbdomenPlaneComponent {
  readonly store = inject(PlanningStore);
  readonly portsCommitted = output<void>();
  readonly chart = { width: 700, height: 820, padding: 48 };
  private readonly defaultWaist = 11;
  private readonly waistProfile: Point2Tuple[] = [
    [0.50, 26], [0.58, 23], [0.76, 19], [0.90, 14], [0.86, 7],
    [1.00, 0], [1.16, -6], [1.22, -11], [1.08, -16], [0.58, -20], [0.18, -22]
  ];
  private readonly svgRef = viewChild<ElementRef<SVGSVGElement>>('svg');
  private readonly planeRef = viewChild<ElementRef<SVGGElement>>('plane');
  private dragging: PortRole | null = null;
  private moved = false;

  readonly abdomenRightX = computed(() => this.waistIntercept('abdomen-right', this.defaultWaist));
  readonly abdomenLeftX = computed(() => this.waistIntercept('abdomen-left', -this.defaultWaist));

  readonly torsoPoints = computed((): Point2Tuple[] => {
    const rightWaist = Math.abs(this.abdomenRightX());
    const leftWaist = Math.abs(this.abdomenLeftX());
    const rightSide = this.sideProfile(rightWaist);
    const leftSide = this.sideProfile(leftWaist).slice().reverse().map(([x, y]): Point2Tuple => [-x, y]);
    return [[0, 27], ...rightSide, [0, -22], ...leftSide];
  });

  readonly torsoPath = computed(() => this.closedSpline(this.torsoPoints()));

  readonly view = computed(() => {
    const extra = [
      ...this.store.abdomenPolygon(),
      ...this.store.defectPolygon(),
      ...this.store.workspacePolygon(),
      ...this.store.draftPoints(),
      ...this.store.bandPolygon(),
      ...this.store.exclusions().flatMap(zone => zone.polygon),
      ...(this.store.result()?.ports ?? []),
      ...(this.store.previewTarget() ? [this.store.previewTarget() as Point2] : [])
    ];
    const torso = this.torsoPoints();
    const landmarks = this.store.plottedLandmarks();
    const xs = [...torso.map(point => point[0]), 0, ...landmarks.map(point => point.x), ...extra.map(point => point.x)];
    const ys = [...torso.map(point => point[1]), 0, ...landmarks.map(point => point.y), ...extra.map(point => point.y)];
    const minX = Math.min(...xs);
    const maxX = Math.max(...xs);
    const minY = Math.min(...ys);
    const maxY = Math.max(...ys);
    const { width, height, padding } = this.chart;
    const scale = Math.min((width - 2 * padding) / (maxX - minX || 1), (height - 2 * padding) / (maxY - minY || 1));
    const contentW = (maxX - minX) * scale;
    const contentH = (maxY - minY) * scale;
    return {
      minX, maxX, minY, maxY, scale,
      offsetX: (width - contentW) / 2,
      offsetY: (height - contentH) / 2,
      width, height
    };
  });

  readonly gridX = computed(() => this.ticks(this.view().minX, this.view().maxX));
  readonly gridY = computed(() => this.ticks(this.view().minY, this.view().maxY));
  readonly polygon = polygonPath;

  svgX(x: number): number {
    const view = this.view();
    return view.offsetX + (x - view.minX) * view.scale;
  }

  svgY(y: number): number {
    const view = this.view();
    return view.height - view.offsetY - (y - view.minY) * view.scale;
  }

  planeTransform(): string {
    const view = this.view();
    return `translate(${this.svgX(0)} ${this.svgY(0)}) scale(${view.scale} ${-view.scale})`;
  }

  portClass(role: PortRole): string {
    if (role === 'CAMERA') return 'port camera-port';
    if (role === 'ASSISTANT') return 'port assistant-port';
    return 'port working-port';
  }

  onPlaneClick(event: MouseEvent): void {
    if (this.store.drawMode() === 'none' || this.moved) {
      this.moved = false;
      return;
    }
    const point = this.toPlane(event);
    if (point) {
      this.store.addDraftPoint(point);
    }
  }

  startDrag(event: PointerEvent, port: PortDto): void {
    if (this.store.drawMode() !== 'none') {
      return;
    }
    event.stopPropagation();
    this.dragging = port.role;
    this.moved = false;
    (event.currentTarget as Element).setPointerCapture(event.pointerId);
  }

  onPointerMove(event: PointerEvent): void {
    if (!this.dragging) {
      return;
    }
    const point = this.toPlane(event);
    if (!point) {
      return;
    }
    this.moved = true;
    this.store.movePort(this.dragging, point);
  }

  onPointerUp(): void {
    if (!this.dragging) {
      return;
    }
    const changed = this.moved;
    this.dragging = null;
    if (changed) {
      this.portsCommitted.emit();
    }
  }

  private toPlane(event: MouseEvent): Point2 | null {
    const svg = this.svgRef()?.nativeElement;
    const plane = this.planeRef()?.nativeElement;
    if (!svg || !plane) {
      return null;
    }
    const matrix = plane.getScreenCTM();
    if (!matrix) {
      return null;
    }
    const point = svg.createSVGPoint();
    point.x = event.clientX;
    point.y = event.clientY;
    const local = point.matrixTransform(matrix.inverse());
    return {
      x: Math.round(local.x * 100) / 100,
      y: Math.round(local.y * 100) / 100
    };
  }

  private waistIntercept(id: string, fallback: number): number {
    const item = this.store.landmarks().find(entry => entry.id === id);
    if (item?.x == null) {
      return fallback;
    }
    const width = Math.abs(item.x);
    return id === 'abdomen-left' ? -width : width;
  }

  private sideProfile(waist: number): Point2Tuple[] {
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

  private closedSpline(points: Point2Tuple[]): string {
    const count = points.length;
    if (count < 3) {
      return '';
    }
    let path = `M ${points[0][0]} ${points[0][1]}`;
    for (let i = 0; i < count; i++) {
      const p0 = points[(i - 1 + count) % count];
      const p1 = points[i];
      const p2 = points[(i + 1) % count];
      const p3 = points[(i + 2) % count];
      path += ` C ${p1[0] + (p2[0] - p0[0]) / 6} ${p1[1] + (p2[1] - p0[1]) / 6}` +
        ` ${p2[0] - (p3[0] - p1[0]) / 6} ${p2[1] - (p3[1] - p1[1]) / 6}` +
        ` ${p2[0]} ${p2[1]}`;
    }
    return `${path} Z`;
  }
}
