import { timeEntryStatusLabel, type TimeEntryStatus } from '../api/timeEntries';

const VARIANTS: Record<TimeEntryStatus, string> = {
  PENDING: 'pending',
  APPROVED: 'approved',
  REJECTED: 'rejected',
};

/** Estado del registro en español; el color nunca es la única señal (texto siempre visible). */
export function TimeEntryStatusBadge({ status }: { status: TimeEntryStatus }) {
  return <span className={`badge badge--${VARIANTS[status]}`}>{timeEntryStatusLabel(status)}</span>;
}
