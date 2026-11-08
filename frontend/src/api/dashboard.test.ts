import { describe, expect, it } from 'vitest';
import { barPercent, formatDashboardHours, isEmptySummary } from './dashboard';

describe('formatDashboardHours', () => {
  it.each([
    [3.5, '3,5 h'],
    [4, '4 h'],
    [0, '0 h'],
    [0.5, '0,5 h'],
    [9.5, '9,5 h'],
    [120.1, '120,1 h'],
  ])('%s → "%s"', (hours, text) => {
    expect(formatDashboardHours(hours)).toBe(text);
  });
});

describe('barPercent', () => {
  it('es proporcional al máximo de la tabla', () => {
    expect(barPercent(3.5, 4)).toBeCloseTo(87.5);
    expect(barPercent(0.5, 4)).toBeCloseTo(12.5);
    expect(barPercent(4, 4)).toBe(100);
  });

  it('vale 0 con máximo 0 o con horas en cero, y nunca supera 100', () => {
    expect(barPercent(0, 0)).toBe(0);
    expect(barPercent(0, 4)).toBe(0);
    expect(barPercent(5, 4)).toBe(100);
  });
});

describe('isEmptySummary', () => {
  const base = { from: '2026-10-09', to: '2026-11-07', byMatter: [], byLawyer: [] };

  it('es vacío solo cuando ambos totales son cero', () => {
    expect(isEmptySummary({ ...base, totals: { approvedHours: 0, pendingHours: 0 } })).toBe(true);
    expect(isEmptySummary({ ...base, totals: { approvedHours: 0, pendingHours: 0.1 } })).toBe(
      false,
    );
    expect(isEmptySummary({ ...base, totals: { approvedHours: 1, pendingHours: 0 } })).toBe(false);
  });
});
