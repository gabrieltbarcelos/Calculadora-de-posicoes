import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CalculationInput {
  name: string;
  x: number;
  y: number;
}

export interface Point {
  name: string;
  x: number;
  y: number;
}

export interface CalculationResponse {
  points: Point[];
}

@Injectable({ providedIn: 'root' })
export class CalculationService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/calculations';

  calculate(inputs: CalculationInput[]): Observable<CalculationResponse> {
    return this.http.post<CalculationResponse>(this.apiUrl, { inputs });
  }
}
