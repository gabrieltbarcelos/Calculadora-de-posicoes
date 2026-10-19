import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { switchMap } from 'rxjs';
import { AbdomenPlaneComponent } from './abdomen-plane.component';
import { PlanningApiService } from './planning-api.service';
import { PlanningFormComponent } from './planning-form.component';
import { PlanningRequest } from './planning.models';
import { PlanningStore } from './planning-store.service';
import { ResultsPanelComponent } from './results-panel.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [PlanningFormComponent, AbdomenPlaneComponent, ResultsPanelComponent],
  templateUrl: './app.component.html'
})
export class AppComponent {
  private readonly api = inject(PlanningApiService);
  readonly store = inject(PlanningStore);

  constructor() {
    this.api.protocol().subscribe({
      next: protocol => this.store.protocol.set(protocol),
      error: () => this.store.error.set('Não foi possível carregar o protocolo. O servidor está em localhost:8080?')
    });
  }

  suggest(): void {
    const request = this.store.buildRequest();
    if (typeof request === 'string') {
      this.store.error.set(request);
      return;
    }
    this.run(request);
  }

  saveProtocol(): void {
    const protocol = this.store.protocol();
    if (!protocol) {
      return;
    }
    this.api.saveProtocol(protocol).subscribe({
      next: saved => {
        this.store.protocol.set(saved);
        this.store.protocolMessage.set('Parâmetros gravados.');
        this.store.error.set('');
      },
      error: (error: HttpErrorResponse) => this.store.error.set(this.message(error))
    });
  }

  validate(): void {
    const ports = this.store.result()?.ports.map(port => ({ role: port.role, x: port.x, y: port.y })) ?? null;
    const request = this.store.buildRequest(ports);
    if (typeof request === 'string') {
      this.store.error.set(request);
      return;
    }
    this.store.loading.set(true);
    this.api.validate(request).subscribe({
      next: result => {
        this.store.result.set({ ...result, admissibleRange: result.admissibleRange ?? this.store.preservedBand() });
        this.store.loading.set(false);
        this.store.error.set('');
      },
      error: (error: HttpErrorResponse) => {
        this.store.loading.set(false);
        this.store.error.set(this.message(error));
      }
    });
  }

  private run(request: PlanningRequest): void {
    this.store.loading.set(true);
    this.store.error.set('');
    const protocol = this.store.protocol();
    const call = protocol
      ? this.api.saveProtocol(protocol).pipe(switchMap(saved => {
          this.store.protocol.set(saved);
          return this.api.suggest(request);
        }))
      : this.api.suggest(request);
    call.subscribe({
      next: result => {
        this.store.result.set(result);
        this.store.preservedBand.set(result.admissibleRange);
        this.store.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.store.loading.set(false);
        this.store.error.set(this.message(error));
      }
    });
  }

  private message(error: HttpErrorResponse): string {
    const body = error.error;
    if (body && typeof body.message === 'string') {
      return body.message;
    }
    return 'Não foi possível conectar ao backend.';
  }
}
