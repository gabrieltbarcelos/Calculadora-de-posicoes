import { CommonModule } from '@angular/common';
import { Component, inject, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  EXCLUSION_CATEGORY_LABELS,
  EXCLUSION_TYPE_LABELS,
  ExclusionCategory,
  ExclusionType,
  PROTOCOL_FIELDS,
  SurgicalTechnique,
  TECHNIQUE_LABELS
} from './planning.models';
import { PlanningStore } from './planning-store.service';

@Component({
  selector: 'app-planning-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './planning-form.component.html'
})
export class PlanningFormComponent {
  readonly store = inject(PlanningStore);
  readonly suggest = output<void>();
  readonly saveProtocol = output<void>();
  readonly protocolFields = PROTOCOL_FIELDS;
  readonly techniques = Object.entries(TECHNIQUE_LABELS) as [SurgicalTechnique, string][];
  readonly exclusionTypes = Object.entries(EXCLUSION_TYPE_LABELS) as [ExclusionType, string][];
  readonly exclusionCategories = Object.entries(EXCLUSION_CATEGORY_LABELS) as [ExclusionCategory, string][];

  exclusionTypesLabel(type: ExclusionType): string {
    return EXCLUSION_TYPE_LABELS[type];
  }

  exclusionCategoryLabel(category: ExclusionCategory): string {
    return EXCLUSION_CATEGORY_LABELS[category];
  }
}
