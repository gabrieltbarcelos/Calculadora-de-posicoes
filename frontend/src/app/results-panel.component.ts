import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { VARIANT_LABELS } from './planning.models';
import { PlanningStore } from './planning-store.service';

@Component({
  selector: 'app-results-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './results-panel.component.html'
})
export class ResultsPanelComponent {
  readonly store = inject(PlanningStore);
  readonly variantLabel = computed(() => {
    const variant = this.store.result()?.techniqueVariant;
    return variant ? VARIANT_LABELS[variant] ?? variant : '';
  });
  readonly closestBones = computed(() => {
    const result = this.store.result();
    if (!result) {
      return [];
    }
    const byPort = new Map<string, { portRole: string; bone: string; distanceCm: number }>();
    for (const clearance of result.boneClearances) {
      const current = byPort.get(clearance.portRole);
      if (!current || clearance.distanceCm < current.distanceCm) {
        byPort.set(clearance.portRole, clearance);
      }
    }
    return [...byPort.values()];
  });

  portLabel(role: string): string {
    return this.store.result()?.ports.find(port => port.role === role)?.label ?? role;
  }
}
