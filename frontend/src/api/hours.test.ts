import { describe, expect, it } from 'vitest';
import {
  addDays,
  formatDate,
  formatHours,
  HOURS_FORMAT_MESSAGE,
  HOURS_INCREMENT_MESSAGE,
  HOURS_RANGE_MESSAGE,
  isIsoDate,
  parseHours,
  todayInLaPaz,
} from './hours';

describe('parseHours (tarea 3.1)', () => {
  it('acepta coma o punto decimal: "1,5" y "1.5" → 1.5', () => {
    expect(parseHours('1,5')).toEqual({ ok: true, value: 1.5 });
    expect(parseHours('1.5')).toEqual({ ok: true, value: 1.5 });
    expect(parseHours(' 2,5 ')).toEqual({ ok: true, value: 2.5 });
  });

  // Scenario: Límites válidos
  it('acepta los límites 0,1 y 24 y ceros finales ("1,50", "24,0")', () => {
    expect(parseHours('0,1')).toEqual({ ok: true, value: 0.1 });
    expect(parseHours('24')).toEqual({ ok: true, value: 24 });
    expect(parseHours('24,0')).toEqual({ ok: true, value: 24 });
    expect(parseHours('1,50')).toEqual({ ok: true, value: 1.5 });
  });

  // Scenario: Incremento inválido
  it('rechaza "1,25" por incremento antes que por rango', () => {
    expect(parseHours('1,25')).toEqual({ ok: false, message: HOURS_INCREMENT_MESSAGE });
    expect(parseHours('30,25')).toEqual({ ok: false, message: HOURS_INCREMENT_MESSAGE });
  });

  // Scenario: Cero horas / Más de 24 horas
  it('rechaza 0 y más de 24 horas', () => {
    expect(parseHours('0')).toEqual({ ok: false, message: HOURS_RANGE_MESSAGE });
    expect(parseHours('0,0')).toEqual({ ok: false, message: HOURS_RANGE_MESSAGE });
    expect(parseHours('24,1')).toEqual({ ok: false, message: HOURS_RANGE_MESSAGE });
  });

  // Scenario: Horas negativas
  it('rechaza negativos, vacío y texto con formato inválido', () => {
    for (const text of ['-1', '', 'abc', '1,', ',5', '1,5,0', '1 5', '1e1']) {
      expect(parseHours(text)).toEqual({ ok: false, message: HOURS_FORMAT_MESSAGE });
    }
  });
});

describe('formatHours', () => {
  it('muestra coma decimal y omite ",0"', () => {
    expect(formatHours(1.5)).toBe('1,5');
    expect(formatHours(8)).toBe('8');
    expect(formatHours(0.1)).toBe('0,1');
    expect(formatHours(23.9)).toBe('23,9');
  });
});

describe('fechas en America/La_Paz', () => {
  // Scenario: Borde de medianoche entre UTC y La Paz
  it('2026-11-08T02:30Z todavía es 2026-11-07 en La Paz', () => {
    expect(todayInLaPaz(new Date('2026-11-08T02:30:00Z'))).toBe('2026-11-07');
    expect(todayInLaPaz(new Date('2026-11-08T04:00:00Z'))).toBe('2026-11-08');
  });

  it('suma días de calendario y valida fechas ISO', () => {
    expect(addDays('2026-11-07', 7)).toBe('2026-11-14');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(isIsoDate('2026-11-07')).toBe(true);
    expect(isIsoDate('2026-02-30')).toBe(false);
    expect(isIsoDate('07/11/2026')).toBe(false);
  });

  it('formatea fechas como dd/mm/aaaa sin desplazar el día', () => {
    expect(formatDate('2026-11-07')).toBe('07/11/2026');
    expect(formatDate('no-es-fecha')).toBe('no-es-fecha');
  });
});
