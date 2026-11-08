import { matterStatusLabel, type MatterStatus } from '../api/clientsMatters';

/** Estado del asunto en español (`Abierto` / `Cerrado`); el color nunca es la única señal. */
export function MatterStatusBadge({ status }: { status: MatterStatus }) {
  return (
    <span className={`badge badge--${status === 'OPEN' ? 'open' : 'closed'}`}>
      {matterStatusLabel(status)}
    </span>
  );
}
