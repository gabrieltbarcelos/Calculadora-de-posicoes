import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlanningRequest, PlanningResponse, ProtocolParameters } from './planning.models';

@Injectable({ providedIn: 'root' })
export class PlanningApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api';

  suggest(request: PlanningRequest): Observable<PlanningResponse> {
    return this.http.post<PlanningResponse>(`${this.apiUrl}/planning`, request);
  }

  validate(request: PlanningRequest): Observable<PlanningResponse> {
    return this.http.post<PlanningResponse>(`${this.apiUrl}/planning/validate`, request);
  }

  protocol(): Observable<ProtocolParameters> {
    return this.http.get<ProtocolParameters>(`${this.apiUrl}/protocol`);
  }

  saveProtocol(protocol: ProtocolParameters): Observable<ProtocolParameters> {
    return this.http.put<ProtocolParameters>(`${this.apiUrl}/protocol`, protocol);
  }
}
