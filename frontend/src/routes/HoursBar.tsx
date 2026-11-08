import { barPercent, formatDashboardHours } from '../api/dashboard';

export interface HoursBarProps {
  approvedHours: number;
  pendingHours: number;
  /** Máximo `approved + pending` de la tabla: la barra es proporcional a ese máximo. */
  max: number;
}

/**
 * Barra horizontal CSS con un segmento de aprobadas (sólido) y otro de pendientes (rayado), más el
 * texto visible "Aprobadas: X h · Pendientes: Y h" (spec "Barras de horas distinguibles sin
 * depender del color"). La barra es decorativa (`aria-hidden`): el texto es la fuente de verdad.
 */
export function HoursBar({ approvedHours, pendingHours, max }: HoursBarProps) {
  const approvedWidth = barPercent(approvedHours, max);
  const pendingWidth = barPercent(pendingHours, max);

  return (
    <div className="hours-bar">
      <div className="hours-bar__track" aria-hidden="true" data-testid="hours-bar">
        <span
          className="hours-bar__segment hours-bar__segment--approved"
          data-segment="approved"
          style={{ width: `${approvedWidth}%` }}
        />
        <span
          className="hours-bar__segment hours-bar__segment--pending"
          data-segment="pending"
          style={{ width: `${pendingWidth}%` }}
        />
      </div>
      <p className="hours-bar__text">
        Aprobadas: {formatDashboardHours(approvedHours)} · Pendientes:{' '}
        {formatDashboardHours(pendingHours)}
      </p>
    </div>
  );
}
